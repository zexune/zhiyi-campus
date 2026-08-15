package com.zhiyi.module.trade.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.SchoolScopeGuard;
import com.zhiyi.common.annotation.RetryOnDeadlock;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ItemType;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.common.enums.OrderStatus;
import com.zhiyi.common.enums.WalletLogType;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.item.service.TagQueryService;
import com.zhiyi.module.trade.dto.CreateOrderDTO;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.entity.ItemReservation;
import com.zhiyi.module.trade.mapper.ItemReservationMapper;
import com.zhiyi.module.trade.entity.WalletLog;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.mapper.WalletLogMapper;
import com.zhiyi.module.trade.vo.OrderVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.service.UserGrowthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Recover;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 担保交易核心服务 —— 下单、确认收货、取消订单。
 *
 * 所有写操作都在 @Transactional 中完成，保证：
 * 余额变动 + 订单状态 + 流水写入 + 商品状态联动 + 经验值结算
 * 要么全部成功，要么全部回滚。
 *
 * 并发安全：confirmReceipt / cancelOrder 的状态更新使用
 * WHERE status = 'WAITING_MEET' 原子条件，防止重复执行。
 *
 * 死锁防护（双层）：
 * 1. 锁序约定 —— 三个资金方法一致地"先取 sys_user 行锁，后取 item_reservation 行锁"，
 *    消除"下单 vs 取消"并发时 user→reservation 与 reservation→user 的交叉等待；
 * 2. 残余死锁（如并发 INSERT IGNORE 的间隙锁竞争）由 @RetryOnDeadlock 自动重试兜底，
 *    每次重试开启全新事务，失败轮次的写入已全部回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final SysUserMapper sysUserMapper;
    private final ItemMapper itemMapper;
    private final TradeOrderMapper orderMapper;
    private final ItemReservationMapper reservationMapper;
    private final WalletLogMapper walletLogMapper;
    private final UserGrowthService growthService;
    private final OrderViewAssembler orderViewAssembler;
    private final TagQueryService tagQueryService;

    // ================================================================
    // 下单
    // ================================================================

    /**
     * 买家下单：扣款冻结 → 原子预占商品 → 创建订单。商品状态不承载订单状态。
     */
    @RetryOnDeadlock
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long buyerId, CreateOrderDTO dto) {
        // 1. 加载商品，校验可购买
        Item item = itemMapper.selectById(dto.getItemId());
        if (item == null) {
            throw new BusinessException(ResultCode.ITEM_NOT_ON_SALE);
        }
        if (item.getStatus() != ItemStatus.ON_SALE) {
            throw new BusinessException(ResultCode.ITEM_NOT_ON_SALE);
        }
        if (item.getModerationStatus() != ModerationStatus.PASSED) {
            throw new BusinessException(ResultCode.ITEM_NOT_ON_SALE);
        }
        if (item.getType() != ItemType.SELL) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持购买出售类型的商品，求购请直接联系发布者");
        }
        if (item.getPublisherId().equals(buyerId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能购买自己发布的商品");
        }

        BigDecimal price = item.getPrice();

        // 3. 检查余额
        SysUser buyer = sysUserMapper.selectById(buyerId);
        if (buyer == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        SchoolScopeGuard.requireSame(
                buyer.getSchoolId(), item.getSchoolId(), "仅支持购买本校商品");
        if (buyer.getWalletBalance().compareTo(price) < 0) {
            throw new BusinessException(ResultCode.BALANCE_NOT_ENOUGH);
        }

        // 4. 卖家当前学校也必须与商品及买家一致，避免转校后的旧商品形成跨校交易。
        SysUser seller = sysUserMapper.selectById(item.getPublisherId());
        if (seller == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        SchoolScopeGuard.requireSame(
                buyer.getSchoolId(), seller.getSchoolId(), "仅支持同校交易");

        // 5. 原子扣款（WHERE 条件兜底余额不足的并发竞态）。
        //    先于预占执行，保证与 confirmReceipt / cancelOrder 的行锁获取顺序一致（见类注释）。
        LambdaUpdateWrapper<SysUser> deduct = new LambdaUpdateWrapper<>();
        deduct.setSql("wallet_balance = wallet_balance - {0}", price)
              .eq(SysUser::getId, buyerId)
              .ge(SysUser::getWalletBalance, price);
        if (sysUserMapper.update(null, deduct) == 0) {
            throw new BusinessException(ResultCode.BALANCE_NOT_ENOUGH);
        }

        // 6. 回读最新余额
        SysUser buyerAfter = sysUserMapper.selectById(buyerId);

        // 7. 数据库主键保证同一商品只能存在一个有效预占；冲突时整体回滚（含扣款）。
        if (reservationMapper.tryReserve(item.getId(), buyerId) == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该商品已被他人抢先下单");
        }

        // 8. 创建订单
        TradeOrder order = new TradeOrder();
        order.setItemId(item.getId());
        order.setBuyerId(buyerId);
        order.setSellerId(item.getPublisherId());
        order.setPrice(price);
        order.setStatus(OrderStatus.WAITING_MEET);
        orderMapper.insert(order);

        ItemReservation reservation = new ItemReservation();
        reservation.setItemId(item.getId());
        reservation.setOrderId(order.getId());
        reservationMapper.updateById(reservation);

        // 9. 买家支出流水
        WalletLog paymentLog = new WalletLog();
        paymentLog.setUserId(buyerId);
        paymentLog.setType(WalletLogType.PAYMENT);
        paymentLog.setAmount(price.negate());
        paymentLog.setBalanceAfter(buyerAfter.getWalletBalance());
        paymentLog.setOrderId(order.getId());
        paymentLog.setRemark("购买商品：" + item.getTitle());
        walletLogMapper.insert(paymentLog);

        // 10. 获取卖家昵称作为对方显示
        String sellerNickname = seller != null ? seller.getNickname() : null;

        log.info("订单创建成功 orderId={} buyer={} seller={} price={}",
                order.getId(), buyerId, item.getPublisherId(), price);

        tagQueryService.invalidate(item.getSchoolId());
        return orderViewAssembler.assemble(order, item, sellerNickname);
    }

    // ================================================================
    // 确认收货
    // ================================================================

    /**
     * 买家确认收货：订单完成 → 卖家收款 → 双方加经验 → 商品标记 SOLD
     *
     * 使用原子 UPDATE（WHERE status = 'WAITING_MEET'）防止并发重复打款。
     */
    @RetryOnDeadlock
    @Transactional(rollbackFor = Exception.class)
    public OrderVO confirmReceipt(Long orderId, Long buyerId) {
        // 1. 加载订单，校验
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有买家才能确认收货");
        }

        // 2. 原子更新订单状态 —— 只有 WAITING_MEET → COMPLETED 才生效
        LocalDateTime completedAt = LocalDateTime.now();
        LambdaUpdateWrapper<TradeOrder> completeWrapper = new LambdaUpdateWrapper<>();
        completeWrapper.set(TradeOrder::getStatus, OrderStatus.COMPLETED)
                       .set(TradeOrder::getCompletedAt, completedAt)
                       .eq(TradeOrder::getId, orderId)
                       .eq(TradeOrder::getStatus, OrderStatus.WAITING_MEET);
        if (orderMapper.update(null, completeWrapper) == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }
        // 数据库使用原子 UPDATE，返回对象也同步成新状态，避免 API 响应仍显示 WAITING_MEET。
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(completedAt);

        BigDecimal price = order.getPrice();

        // 3. 卖家收款（原子加余额）
        LambdaUpdateWrapper<SysUser> income = new LambdaUpdateWrapper<>();
        income.setSql("wallet_balance = wallet_balance + {0}", price)
              .eq(SysUser::getId, order.getSellerId());
        sysUserMapper.update(null, income);

        SysUser sellerAfter = sysUserMapper.selectById(order.getSellerId());

        // 4. 卖家收入流水
        WalletLog incomeLog = new WalletLog();
        incomeLog.setUserId(order.getSellerId());
        incomeLog.setType(WalletLogType.INCOME);
        incomeLog.setAmount(price);
        incomeLog.setBalanceAfter(sellerAfter != null ? sellerAfter.getWalletBalance() : BigDecimal.ZERO);
        incomeLog.setOrderId(orderId);
        incomeLog.setRemark("售出商品收入");
        walletLogMapper.insert(incomeLog);

        // 5. 商品标记已售出
        Item item = itemMapper.selectById(order.getItemId());
        if (item != null) {
            item.setStatus(ItemStatus.SOLD);
            itemMapper.updateById(item);
            tagQueryService.invalidate(item.getSchoolId());
        }
        reservationMapper.deleteById(order.getItemId());

        // 6. 双方加经验（使用 REQUIRED 传播，加入当前事务）
        growthService.addExp(order.getBuyerId(),
                UserGrowthService.EXP_ORDER_COMPLETED, "买家完成订单");
        growthService.addExp(order.getSellerId(),
                UserGrowthService.EXP_ORDER_COMPLETED, "卖家完成订单");

        log.info("订单确认收货 orderId={} seller={} amount={}", orderId, order.getSellerId(), price);

        String sellerNickname = sellerAfter != null ? sellerAfter.getNickname() : null;
        return orderViewAssembler.assemble(order, item, sellerNickname);
    }

    // ================================================================
    // 取消订单
    // ================================================================

    /**
     * 买家取消订单：退款 → 订单取消 → 释放商品预占。商品状态始终保持 ON_SALE。
     *
     * 使用原子 UPDATE（WHERE status = 'WAITING_MEET'）防止并发重复退款。
     */
    @RetryOnDeadlock
    @Transactional(rollbackFor = Exception.class)
    public OrderVO cancelOrder(Long orderId, Long buyerId) {
        // 1. 加载订单，校验
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有买家才能取消订单");
        }

        BigDecimal price = order.getPrice();

        // 2. 原子更新订单状态 —— 只有 WAITING_MEET → CANCELLED 才生效
        LocalDateTime cancelledAt = LocalDateTime.now();
        LambdaUpdateWrapper<TradeOrder> cancelWrapper = new LambdaUpdateWrapper<>();
        cancelWrapper.set(TradeOrder::getStatus, OrderStatus.CANCELLED)
                     .set(TradeOrder::getCancelledAt, cancelledAt)
                     .eq(TradeOrder::getId, orderId)
                     .eq(TradeOrder::getStatus, OrderStatus.WAITING_MEET);
        if (orderMapper.update(null, cancelWrapper) == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(cancelledAt);

        // 3. 买家退款（原子加余额）
        LambdaUpdateWrapper<SysUser> refund = new LambdaUpdateWrapper<>();
        refund.setSql("wallet_balance = wallet_balance + {0}", price)
              .eq(SysUser::getId, buyerId);
        sysUserMapper.update(null, refund);

        SysUser buyerAfter = sysUserMapper.selectById(buyerId);

        // 4. 退款流水
        WalletLog refundLog = new WalletLog();
        refundLog.setUserId(buyerId);
        refundLog.setType(WalletLogType.REFUND);
        refundLog.setAmount(price);
        refundLog.setBalanceAfter(buyerAfter != null ? buyerAfter.getWalletBalance() : BigDecimal.ZERO);
        refundLog.setOrderId(orderId);
        refundLog.setRemark("取消订单退款");
        walletLogMapper.insert(refundLog);

        // 5. 释放商品预占，商品交易状态不变
        Item item = itemMapper.selectById(order.getItemId());
        reservationMapper.deleteById(order.getItemId());
        if (item != null) tagQueryService.invalidate(item.getSchoolId());

        // 6. 获取卖家昵称作为对方显示
        SysUser seller = sysUserMapper.selectById(order.getSellerId());
        String sellerNickname = seller != null ? seller.getNickname() : null;

        log.info("订单取消 orderId={} buyer={} refund={}", orderId, buyerId, price);

        return orderViewAssembler.assemble(order, item, sellerNickname);
    }

    // ================================================================
    // 死锁重试兜底
    // ================================================================

    /**
     * @RetryOnDeadlock 重试耗尽后不再向上抛 500，转为明确的业务冲突提示。
     * 匹配签名：三个资金方法均为双参数方法。
     */
    @Recover
    public OrderVO recoverFromDeadlock(ConcurrencyFailureException e, Object first, Object second) {
        log.warn("资金事务重试后仍遭遇锁冲突 first={} second={}", first, second, e);
        throw new BusinessException(ResultCode.CONFLICT, "当前交易繁忙，请稍后重试");
    }

}
