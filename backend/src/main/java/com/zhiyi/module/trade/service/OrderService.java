package com.zhiyi.module.trade.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.SchoolScopeGuard;
import com.zhiyi.common.annotation.RetryOnDeadlock;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ItemType;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.common.enums.OrderCancelReason;
import com.zhiyi.common.enums.OrderStatus;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.common.enums.WalletLogType;
import com.zhiyi.common.support.IdempotencyService;
import com.zhiyi.common.support.LockFailureDetector;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.trade.dto.CreateOrderDTO;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.entity.WalletLog;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.mapper.WalletLogMapper;
import com.zhiyi.module.trade.vo.OrderVO;
import com.zhiyi.module.social.service.OutboxService;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.service.UserGrowthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 担保交易核心服务 —— 下单、确认收货、取消订单。
 *
 * 全局锁序（允许跳过，禁止反向）：
 * 协调行（幂等记录）→ 用户行（ID 升序）→ 商品行 → 订单行 → 流水/Outbox 插入。
 * 所有加锁路径在锁定后重读可变字段，不得使用无锁读取时的快照做业务判断。
 *
 * 状态机（item.status 是可交易性唯一权威来源，与 trade_order 通过不变量保持一致）：
 * - 下单：ON_SALE → RESERVED（条件迁移，失败即"已被抢先下单"）
 * - 确认：订单 WAITING_MEET → COMPLETED；商品 RESERVED → SOLD
 * - 买家取消：订单 WAITING_MEET → CANCELLED(USER_CANCEL)；商品按规则回 ON_SALE 或 OFF_SHELF
 * - 资金不变量：PAYMENT/INCOME/REFUND 各恰好一条，由 uk_wallet_order_type 数据库兜底。
 *
 * 背压：用户行与下单商品行使用 NOWAIT 锁定，锁繁忙映射为 TRADE_BUSY；
 * 当前事务虽会回滚，但不能排除同一幂等键的另一请求仍在执行，因此异常实例将
 * requestOutcome 保守标记为 UNKNOWN；
 * 事务外准入闸门由 TradingEntryService 承担；@RetryOnDeadlock 只重试真正的死锁/锁超时，
 * 且重试切面在事务切面之外，每次重试都是全新事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final SysUserMapper sysUserMapper;
    private final ItemMapper itemMapper;
    private final TradeOrderMapper orderMapper;
    private final WalletLogMapper walletLogMapper;
    private final UserGrowthService growthService;
    private final OrderViewAssembler orderViewAssembler;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;

    // ================================================================
    // 下单（幂等记录 → 用户升序 NOWAIT → 商品 NOWAIT 重读 → 条件迁移 → 订单）
    // ================================================================

    @RetryOnDeadlock
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long buyerId, CreateOrderDTO dto, String idempotencyKey) {
        return idempotencyService.execute(buyerId, IdempotencyService.OP_ORDER_CREATE,
                idempotencyKey, dto, OrderVO.class, () -> doCreateOrder(buyerId, dto));
    }

    private OrderVO doCreateOrder(Long buyerId, CreateOrderDTO dto) {
        // 1. 无锁读商品，仅取 sellerId（价格/状态等可变字段一律在锁后重读）
        Item peek = itemMapper.selectById(dto.getItemId());
        if (peek == null) {
            throw new BusinessException(ResultCode.ITEM_NOT_ON_SALE);
        }
        Long sellerId = peek.getPublisherId();
        if (sellerId.equals(buyerId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能购买自己发布的商品");
        }

        // 2. 按 ID 升序 NOWAIT 锁定买卖双方；繁忙直接返回可重试背压
        long firstId = Math.min(buyerId, sellerId);
        long secondId = Math.max(buyerId, sellerId);
        SysUser first = lockUserNowait(firstId);
        SysUser second = lockUserNowait(secondId);
        SysUser buyer = buyerId.equals(firstId) ? first : second;
        SysUser seller = sellerId.equals(firstId) ? first : second;
        if (buyer == null || buyer.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ResultCode.USER_STATUS_ERROR, "买家账户状态异常");
        }
        if (seller == null || seller.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ResultCode.USER_STATUS_ERROR, "卖家账户状态异常");
        }

        // 3. NOWAIT 锁定并重读商品；peek 只用于确定锁序，不作为业务判断依据
        Item item = lockItemNowait(dto.getItemId());
        if (item == null || !item.getPublisherId().equals(sellerId)) {
            throw new BusinessException(ResultCode.CONFLICT, "商品归属已变更，请重试");
        }
        SchoolScopeGuard.requireSame(buyer.getSchoolId(), item.getSchoolId(), "仅支持购买本校商品");
        SchoolScopeGuard.requireSame(buyer.getSchoolId(), seller.getSchoolId(), "仅支持同校交易");
        if (item.getType() != ItemType.SELL) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持购买出售类型的商品，求购请直接联系发布者");
        }
        if (item.getStatus() != ItemStatus.ON_SALE || item.getModerationStatus() != ModerationStatus.PASSED) {
            throw new BusinessException(ResultCode.ITEM_NOT_ON_SALE);
        }
        BigDecimal price = item.getPrice();

        // 4. 锁内条件迁移 ON_SALE → RESERVED；影响行数必须为 1
        int itemUpdated = itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, item.getId())
                .eq(Item::getStatus, ItemStatus.ON_SALE)
                .eq(Item::getModerationStatus, ModerationStatus.PASSED)
                .eq(Item::getType, ItemType.SELL)
                .set(Item::getStatus, ItemStatus.RESERVED));
        if (itemUpdated == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该商品已被他人抢先下单");
        }

        // 5. 原子扣款（WHERE 余额条件兜底并发竞态）
        int deducted = sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, buyerId)
                .ge(SysUser::getWalletBalance, price)
                .setSql("wallet_balance = wallet_balance - {0}", price));
        if (deducted == 0) {
            throw new BusinessException(ResultCode.BALANCE_NOT_ENOUGH);
        }
        SysUser buyerAfter = sysUserMapper.selectById(buyerId);

        // 6. 插入订单（uk_order_active_item 生成列唯一索引为最后防线）
        TradeOrder order = new TradeOrder();
        order.setItemId(item.getId());
        order.setBuyerId(buyerId);
        order.setSellerId(sellerId);
        order.setPrice(price);
        order.setStatus(OrderStatus.WAITING_MEET);
        orderMapper.insert(order);

        // 7. 买家 PAYMENT 流水（uk_wallet_order_type 防重）
        WalletLog paymentLog = new WalletLog();
        paymentLog.setUserId(buyerId);
        paymentLog.setType(WalletLogType.PAYMENT);
        paymentLog.setAmount(price.negate());
        paymentLog.setBalanceAfter(buyerAfter != null ? buyerAfter.getWalletBalance() : BigDecimal.ZERO);
        paymentLog.setOrderId(order.getId());
        paymentLog.setRemark("购买商品：" + item.getTitle());
        walletLogMapper.insert(paymentLog);

        log.info("订单创建成功 orderId={} buyer={} seller={} price={}",
                order.getId(), buyerId, sellerId, price);
        return orderViewAssembler.assemble(order, item, seller.getNickname());
    }

    // ================================================================
    // 确认收货（幂等记录 → 用户升序 → 商品 → 订单 → 条件迁移 → 结算 → Outbox）
    // ================================================================

    @RetryOnDeadlock
    @Transactional(rollbackFor = Exception.class)
    public OrderVO confirmReceipt(Long orderId, Long buyerId, String idempotencyKey) {
        return idempotencyService.execute(buyerId, IdempotencyService.OP_ORDER_CONFIRM,
                idempotencyKey, java.util.Map.of("orderId", orderId), OrderVO.class,
                () -> doConfirmReceipt(orderId, buyerId));
    }

    private OrderVO doConfirmReceipt(Long orderId, Long buyerId) {
        // 1. 无锁读订单，仅取参与者 ID
        TradeOrder peek = orderMapper.selectById(orderId);
        if (peek == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!peek.getBuyerId().equals(buyerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有买家才能确认收货");
        }

        // 2. 按 ID 升序锁定双方用户（锁后重检买家 ACTIVE：与并发封禁互斥）
        long firstId = Math.min(peek.getBuyerId(), peek.getSellerId());
        long secondId = Math.max(peek.getBuyerId(), peek.getSellerId());
        SysUser first = lockUserNowait(firstId);
        SysUser second = lockUserNowait(secondId);
        SysUser buyer = peek.getBuyerId().equals(firstId) ? first : second;
        if (buyer == null || buyer.getStatus() != UserStatus.ACTIVE) {
            // 买家恰被封禁：正常路径拒绝，订单留给封禁事务的自动取消流程处理
            throw new BusinessException(ResultCode.USER_STATUS_ERROR, "买家账户状态异常");
        }

        // 3. 锁定商品并重读
        Item item = itemMapper.selectByIdForUpdate(peek.getItemId());
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }

        // 4. 锁定订单并重读
        TradeOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }

        // 5. 订单状态原子迁移 WAITING_MEET → COMPLETED（数据库完成时间）
        int orderUpdated = orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getId, orderId)
                .eq(TradeOrder::getStatus, OrderStatus.WAITING_MEET)
                .set(TradeOrder::getStatus, OrderStatus.COMPLETED)
                .setSql("completed_at = CURRENT_TIMESTAMP(6)"));
        if (orderUpdated == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }

        // 6. 商品状态原子迁移：仅 RESERVED → SOLD
        int itemUpdated = itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, order.getItemId())
                .eq(Item::getStatus, ItemStatus.RESERVED)
                .set(Item::getStatus, ItemStatus.SOLD));
        if (itemUpdated == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "商品状态异常，交易数据不一致");
        }

        BigDecimal price = order.getPrice();

        // 7. 卖家收款 + INCOME 流水
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, order.getSellerId())
                .setSql("wallet_balance = wallet_balance + {0}", price));
        SysUser sellerAfter = sysUserMapper.selectById(order.getSellerId());

        WalletLog incomeLog = new WalletLog();
        incomeLog.setUserId(order.getSellerId());
        incomeLog.setType(WalletLogType.INCOME);
        incomeLog.setAmount(price);
        incomeLog.setBalanceAfter(sellerAfter != null ? sellerAfter.getWalletBalance() : BigDecimal.ZERO);
        incomeLog.setOrderId(orderId);
        incomeLog.setRemark("售出商品收入");
        walletLogMapper.insert(incomeLog);

        // 8. 双方经验（REQUIRED 传播，加入当前事务）
        growthService.addExp(order.getBuyerId(), UserGrowthService.EXP_ORDER_COMPLETED, "买家完成订单");
        growthService.addExp(order.getSellerId(), UserGrowthService.EXP_ORDER_COMPLETED, "卖家完成订单");

        // 9. Outbox：订单完成系统通知（买卖双方独立 event_id，同事务）
        outboxService.appendNotice("ORDER:" + orderId + ":COMPLETED:BUYER",
                OutboxService.AGGREGATE_ORDER, orderId, OutboxService.EVENT_ORDER_COMPLETED,
                order.getBuyerId(), "您购买的「" + item.getTitle() + "」订单已完成，交易金额 ¥"
                        + price.toPlainString() + "。");
        outboxService.appendNotice("ORDER:" + orderId + ":COMPLETED:SELLER",
                OutboxService.AGGREGATE_ORDER, orderId, OutboxService.EVENT_ORDER_COMPLETED,
                order.getSellerId(), "您售出的「" + item.getTitle() + "」订单已确认收货，¥"
                        + price.toPlainString() + " 已到账。");

        log.info("订单确认收货 orderId={} seller={} amount={}", orderId, order.getSellerId(), price);

        TradeOrder fresh = orderMapper.selectById(orderId);
        String sellerNickname = sellerAfter != null ? sellerAfter.getNickname() : null;
        return orderViewAssembler.assemble(fresh != null ? fresh : order, item, sellerNickname);
    }

    // ================================================================
    // 取消订单（锁序与确认收货相同；cancel_reason=USER_CANCEL）
    // ================================================================

    @RetryOnDeadlock
    @Transactional(rollbackFor = Exception.class)
    public OrderVO cancelOrder(Long orderId, Long buyerId, String idempotencyKey) {
        return idempotencyService.execute(buyerId, IdempotencyService.OP_ORDER_CANCEL,
                idempotencyKey, java.util.Map.of("orderId", orderId), OrderVO.class,
                () -> doCancelOrder(orderId, buyerId));
    }

    private OrderVO doCancelOrder(Long orderId, Long buyerId) {
        // 1. 无锁读订单，仅取参与者 ID
        TradeOrder peek = orderMapper.selectById(orderId);
        if (peek == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!peek.getBuyerId().equals(buyerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有买家才能取消订单");
        }

        // 2. 按 ID 升序锁定双方用户（锁后重检买家 ACTIVE）；复用锁定对象，禁止商品锁后再取用户锁
        long firstId = Math.min(peek.getBuyerId(), peek.getSellerId());
        long secondId = Math.max(peek.getBuyerId(), peek.getSellerId());
        SysUser first = lockUserNowait(firstId);
        SysUser second = lockUserNowait(secondId);
        SysUser buyer = peek.getBuyerId().equals(firstId) ? first : second;
        SysUser seller = peek.getSellerId().equals(firstId) ? first : second;
        if (buyer == null || buyer.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ResultCode.USER_STATUS_ERROR, "买家账户状态异常");
        }

        // 3. 锁定商品并重读
        Item item = itemMapper.selectByIdForUpdate(peek.getItemId());
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }

        // 4. 锁定订单并重读
        TradeOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        BigDecimal price = order.getPrice();

        // 5. 订单原子迁移：WAITING_MEET → CANCELLED(USER_CANCEL)，数据库取消时间
        int orderUpdated = orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getId, orderId)
                .eq(TradeOrder::getStatus, OrderStatus.WAITING_MEET)
                .set(TradeOrder::getStatus, OrderStatus.CANCELLED)
                .set(TradeOrder::getCancelReason, OrderCancelReason.USER_CANCEL)
                .setSql("cancelled_at = CURRENT_TIMESTAMP(6)"));
        if (orderUpdated == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }

        // 6. 商品迁移目标状态：审核通过且卖家正常 → 回 ON_SALE；否则 OFF_SHELF
        ItemStatus target = (item.getModerationStatus() == ModerationStatus.PASSED
                && seller != null && seller.getStatus() == UserStatus.ACTIVE)
                ? ItemStatus.ON_SALE : ItemStatus.OFF_SHELF;
        int itemUpdated = itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, order.getItemId())
                .eq(Item::getStatus, ItemStatus.RESERVED)
                .set(Item::getStatus, target));
        if (itemUpdated == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "商品状态异常");
        }

        // 7. 买家退款 + REFUND 流水
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, buyerId)
                .setSql("wallet_balance = wallet_balance + {0}", price));
        SysUser buyerAfter = sysUserMapper.selectById(buyerId);

        WalletLog refundLog = new WalletLog();
        refundLog.setUserId(buyerId);
        refundLog.setType(WalletLogType.REFUND);
        refundLog.setAmount(price);
        refundLog.setBalanceAfter(buyerAfter != null ? buyerAfter.getWalletBalance() : BigDecimal.ZERO);
        refundLog.setOrderId(orderId);
        refundLog.setRemark("取消订单退款");
        walletLogMapper.insert(refundLog);

        log.info("订单取消 orderId={} buyer={} refund={}", orderId, buyerId, price);

        TradeOrder fresh = orderMapper.selectById(orderId);
        String sellerNickname = seller != null ? seller.getNickname() : null;
        return orderViewAssembler.assemble(fresh != null ? fresh : order, item, sellerNickname);
    }

    // ================================================================
    // NOWAIT 锁定辅助（锁繁忙 → TRADE_BUSY，不进入死锁重试）
    // ================================================================

    private SysUser lockUserNowait(Long userId) {
        try {
            return sysUserMapper.selectByIdForUpdateNowait(userId);
        } catch (DataAccessException failure) {
            if (LockFailureDetector.isNowaitConflict(failure)) {
                throw new BusinessException(ResultCode.TRADE_BUSY)
                        .withRequestOutcome(ResultCode.RequestOutcome.UNKNOWN);
            }
            throw failure;
        }
    }

    private Item lockItemNowait(Long itemId) {
        try {
            return itemMapper.selectByIdForUpdateNowait(itemId);
        } catch (DataAccessException failure) {
            if (LockFailureDetector.isNowaitConflict(failure)) {
                throw new BusinessException(ResultCode.TRADE_BUSY)
                        .withRequestOutcome(ResultCode.RequestOutcome.UNKNOWN);
            }
            throw failure;
        }
    }

    // ================================================================
    // 死锁重试兜底说明
    // ================================================================

    // @RetryOnDeadlock 重试耗尽后不在此处挂 @Recover（spring-retry 的 recover
    // 方法匹配对多方法/代理场景存在版本怪癖）；耗尽的 ConcurrencyFailureException
    // 由编排层 TradingEntryService 统一转 TRADE_BUSY。当前事务虽已回滚，但仍无法
    // 排除同一幂等键的另一请求正在等待或执行，因此结果为 UNKNOWN。

    /** 供编排层校验订单参与者（无锁读，仅用于权限预检）。 */
    public boolean isBuyerOf(TradeOrder order, Long userId) {
        return order != null && Objects.equals(order.getBuyerId(), userId);
    }
}
