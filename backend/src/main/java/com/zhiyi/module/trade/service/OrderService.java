package com.zhiyi.module.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.SchoolScopeGuard;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.trade.dto.CreateOrderDTO;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.entity.TradeReview;
import com.zhiyi.module.trade.entity.WalletLog;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.mapper.TradeReviewMapper;
import com.zhiyi.module.trade.mapper.WalletLogMapper;
import com.zhiyi.module.trade.vo.OrderVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.service.UserGrowthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 担保交易核心服务 —— 下单、确认收货、取消订单。
 *
 * 所有写操作都在 @Transactional 中完成，保证：
 * 余额变动 + 订单状态 + 流水写入 + 商品状态联动 + 经验值结算
 * 要么全部成功，要么全部回滚。
 *
 * 并发安全：confirmReceipt / cancelOrder 的状态更新使用
 * WHERE status = 'WAITING_MEET' 原子条件，防止重复执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final SysUserMapper sysUserMapper;
    private final ItemMapper itemMapper;
    private final TradeOrderMapper orderMapper;
    private final TradeReviewMapper reviewMapper;
    private final WalletLogMapper walletLogMapper;
    private final UserGrowthService growthService;

    // ================================================================
    // 下单
    // ================================================================

    /**
     * 买家下单：扣款冻结 → 创建订单 → 商品标记 PENDING
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long buyerId, CreateOrderDTO dto) {
        // 1. 加载商品，校验可购买
        Item item = itemMapper.selectById(dto.getItemId());
        if (item == null) {
            throw new BusinessException(ResultCode.ITEM_NOT_ON_SALE);
        }
        if (!"ON_SALE".equals(item.getStatus())) {
            throw new BusinessException(ResultCode.ITEM_NOT_ON_SALE);
        }
        if (!"SELL".equals(item.getType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持购买出售类型的商品，求购请直接联系发布者");
        }
        if (item.getDeadlineTime() != null
                && !item.getDeadlineTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品已过截止时间，无法下单");
        }
        if (item.getPublisherId().equals(buyerId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能购买自己发布的商品");
        }

        // 2. 同一商品不能有进行中的订单（首次检查）
        Long activeCount = orderMapper.selectCount(
                new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getItemId, item.getId())
                        .eq(TradeOrder::getStatus, "WAITING_MEET"));
        if (activeCount > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该商品已被他人抢先下单");
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

        // 5. 原子扣款（WHERE 条件兜底余额不足的并发竞态）
        LambdaUpdateWrapper<SysUser> deduct = new LambdaUpdateWrapper<>();
        deduct.setSql("wallet_balance = wallet_balance - {0}", price)
              .eq(SysUser::getId, buyerId)
              .ge(SysUser::getWalletBalance, price);
        if (sysUserMapper.update(null, deduct) == 0) {
            throw new BusinessException(ResultCode.BALANCE_NOT_ENOUGH);
        }

        // 6. 二次检查：扣款后再次确认没有并发创建了同一商品的活跃订单
        Long recheckCount = orderMapper.selectCount(
                new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getItemId, item.getId())
                        .eq(TradeOrder::getStatus, "WAITING_MEET"));
        if (recheckCount > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该商品已被他人抢先下单");
        }

        // 7. 回读最新余额
        SysUser buyerAfter = sysUserMapper.selectById(buyerId);

        // 8. 创建订单
        TradeOrder order = new TradeOrder();
        order.setItemId(item.getId());
        order.setBuyerId(buyerId);
        order.setSellerId(item.getPublisherId());
        order.setPrice(price);
        order.setStatus("WAITING_MEET");
        orderMapper.insert(order);

        // 9. 买家支出流水
        WalletLog paymentLog = new WalletLog();
        paymentLog.setUserId(buyerId);
        paymentLog.setType("PAYMENT");
        paymentLog.setAmount(price.negate());
        paymentLog.setBalanceAfter(buyerAfter.getWalletBalance());
        paymentLog.setOrderId(order.getId());
        paymentLog.setRemark("购买商品：" + item.getTitle());
        walletLogMapper.insert(paymentLog);

        // 10. 商品标记交易中
        item.setStatus("PENDING");
        itemMapper.updateById(item);

        // 11. 获取卖家昵称作为对的显示方
        String sellerNickname = seller != null ? seller.getNickname() : null;

        log.info("订单创建成功 orderId={} buyer={} seller={} price={}",
                order.getId(), buyerId, item.getPublisherId(), price);

        return toVO(order, item, sellerNickname, null);
    }

    // ================================================================
    // 确认收货
    // ================================================================

    /**
     * 买家确认收货：订单完成 → 卖家收款 → 双方加经验 → 商品标记 SOLD
     *
     * 使用原子 UPDATE（WHERE status = 'WAITING_MEET'）防止并发重复打款。
     */
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
        completeWrapper.set(TradeOrder::getStatus, "COMPLETED")
                       .set(TradeOrder::getCompletedAt, completedAt)
                       .eq(TradeOrder::getId, orderId)
                       .eq(TradeOrder::getStatus, "WAITING_MEET");
        if (orderMapper.update(null, completeWrapper) == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }
        // 数据库使用原子 UPDATE，返回对象也同步成新状态，避免 API 响应仍显示 WAITING_MEET。
        order.setStatus("COMPLETED");
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
        incomeLog.setType("INCOME");
        incomeLog.setAmount(price);
        incomeLog.setBalanceAfter(sellerAfter != null ? sellerAfter.getWalletBalance() : BigDecimal.ZERO);
        incomeLog.setOrderId(orderId);
        incomeLog.setRemark("售出商品收入");
        walletLogMapper.insert(incomeLog);

        // 5. 商品标记已售出
        Item item = itemMapper.selectById(order.getItemId());
        if (item != null) {
            item.setStatus("SOLD");
            itemMapper.updateById(item);
        }

        // 6. 双方加经验（使用 REQUIRED 传播，加入当前事务）
        growthService.addExp(order.getBuyerId(),
                UserGrowthService.EXP_ORDER_COMPLETED, "买家完成订单");
        growthService.addExp(order.getSellerId(),
                UserGrowthService.EXP_ORDER_COMPLETED, "卖家完成订单");

        log.info("订单确认收货 orderId={} seller={} amount={}", orderId, order.getSellerId(), price);

        String sellerNickname = sellerAfter != null ? sellerAfter.getNickname() : null;
        return toVO(order, item, sellerNickname, null);
    }

    // ================================================================
    // 取消订单
    // ================================================================

    /**
     * 买家取消订单：退款 → 订单取消 → 商品恢复 ON_SALE
     *
     * 使用原子 UPDATE（WHERE status = 'WAITING_MEET'）防止并发重复退款。
     */
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
        cancelWrapper.set(TradeOrder::getStatus, "CANCELLED")
                     .set(TradeOrder::getCancelledAt, cancelledAt)
                     .eq(TradeOrder::getId, orderId)
                     .eq(TradeOrder::getStatus, "WAITING_MEET");
        if (orderMapper.update(null, cancelWrapper) == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }
        order.setStatus("CANCELLED");
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
        refundLog.setType("REFUND");
        refundLog.setAmount(price);
        refundLog.setBalanceAfter(buyerAfter != null ? buyerAfter.getWalletBalance() : BigDecimal.ZERO);
        refundLog.setOrderId(orderId);
        refundLog.setRemark("取消订单退款");
        walletLogMapper.insert(refundLog);

        // 5. 商品恢复在售
        Item item = itemMapper.selectById(order.getItemId());
        if (item != null) {
            item.setStatus("ON_SALE");
            itemMapper.updateById(item);
        }

        // 6. 获取卖家昵称作为对方显示
        SysUser seller = sysUserMapper.selectById(order.getSellerId());
        String sellerNickname = seller != null ? seller.getNickname() : null;

        log.info("订单取消 orderId={} buyer={} refund={}", orderId, buyerId, price);

        return toVO(order, item, sellerNickname, null);
    }

    // ================================================================
    // 查询（供 4.3 使用）
    // ================================================================

    /** 我买的 */
    public IPage<OrderVO> getBoughtOrders(Long userId, int page, int size, String status) {
        LambdaQueryWrapper<TradeOrder> q = new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getBuyerId, userId)
                .eq(status != null && !status.isEmpty(), TradeOrder::getStatus, status)
                .orderByDesc(TradeOrder::getCreatedAt);

        Page<TradeOrder> p = new Page<>(page, size);
        IPage<TradeOrder> result = orderMapper.selectPage(p, q);

        return result.convert(order -> {
            Item item = itemMapper.selectById(order.getItemId());
            SysUser seller = sysUserMapper.selectById(order.getSellerId());
            OrderVO vo = toVO(order, item, seller != null ? seller.getNickname() : null, null);
            // 仅已完成订单需要评价入口：查一次是否已评，供前端控制按钮显隐
            if ("COMPLETED".equals(order.getStatus())) {
                Long reviewed = reviewMapper.selectCount(new LambdaQueryWrapper<TradeReview>()
                        .eq(TradeReview::getOrderId, order.getId()));
                vo.setReviewed(reviewed != null && reviewed > 0);
            }
            return vo;
        });
    }

    /** 我卖的 */
    public IPage<OrderVO> getSoldOrders(Long userId, int page, int size, String status) {
        LambdaQueryWrapper<TradeOrder> q = new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getSellerId, userId)
                .eq(status != null && !status.isEmpty(), TradeOrder::getStatus, status)
                .orderByDesc(TradeOrder::getCreatedAt);

        Page<TradeOrder> p = new Page<>(page, size);
        IPage<TradeOrder> result = orderMapper.selectPage(p, q);

        return result.convert(order -> {
            Item item = itemMapper.selectById(order.getItemId());
            SysUser buyer = sysUserMapper.selectById(order.getBuyerId());
            return toVO(order, item, null, buyer != null ? buyer.getNickname() : null);
        });
    }

    // ================================================================
    // 内部工具
    // ================================================================

    private OrderVO toVO(TradeOrder order, Item item, String peerNicknameForBuyer, String peerNicknameForSeller) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setItemId(order.getItemId());
        vo.setBuyerId(order.getBuyerId());
        vo.setSellerId(order.getSellerId());
        vo.setPrice(order.getPrice());
        vo.setStatus(order.getStatus());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setCompletedAt(order.getCompletedAt());
        vo.setCancelledAt(order.getCancelledAt());

        if (item != null) {
            vo.setItemTitle(item.getTitle());
            // 提取首张图片作为封面
            String images = item.getImages();
            if (images != null && images.length() > 2) {
                try {
                    // images 是 JSON 数组字符串如 ["url1","url2"]
                    String first = images.replaceAll("^\\[\\s*\"", "")
                                        .replaceAll("\".*$", "");
                    vo.setItemCover(first);
                } catch (Exception ignored) {
                    vo.setItemCover(null);
                }
            }
        }

        // peerNicknameForBuyer 是卖家昵称，peerNicknameForSeller 是买家昵称
        // 调用方根据场景传入对应的对方昵称
        vo.setPeerNickname(peerNicknameForBuyer != null ? peerNicknameForBuyer : peerNicknameForSeller);

        return vo;
    }
}
