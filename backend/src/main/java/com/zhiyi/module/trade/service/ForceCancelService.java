package com.zhiyi.module.trade.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.OrderCancelReason;
import com.zhiyi.common.enums.OrderStatus;
import com.zhiyi.common.enums.WalletLogType;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.social.service.OutboxService;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.entity.WalletLog;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.mapper.WalletLogMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 强制撤单子例程（封禁自动取消 AUTO_CANCEL / 审核违规强制撤单 ADMIN_FORCE 共用）。
 *
 * 不可分割迁移：订单 CANCELLED + 显式原因 + 数据库取消时间 → 商品 RESERVED→OFF_SHELF →
 * 买家全额退款 + 唯一 REFUND 流水 → 买卖双方独立 event_id 的 Outbox 通知。
 * 任何一步失败（含影响行数校验）整体抛异常回滚调用方事务。
 *
 * 传播语义：REQUIRED —— 加入调用方事务（封禁事务或审核事务），
 * 撤单、退款、通知与封禁/审核决定同生共死，不存在半撤单状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForceCancelService {

    private final TradeOrderMapper orderMapper;
    private final ItemMapper itemMapper;
    private final SysUserMapper sysUserMapper;
    private final WalletLogMapper walletLogMapper;
    private final OutboxService outboxService;

    /**
     * 取消买家全部进行中订单（封禁路径）。调用方必须已持有买家用户行锁。
     *
     * 锁序说明（与全局规范 §3 的偏差声明）：本路径实际顺序为
     * "买家用户行 → 订单行（item_id 升序 FOR UPDATE）→ 商品行"，
     * 与规范表的"商品 → 订单"相反。安全性论证：
     * 1. 买家用户行锁是本路径与所有触单路径（下单/确认/取消都以双方用户行锁为第一资源）
     *    的公共前置——持锁期间不可能有新的下单/确认/取消进入；
     * 2. 能与"订单→商品"顺序成环的对端必须是"商品→订单"且不持该买家用户锁的事务；
     *    确认/取消路径先取用户锁再取商品锁，审核路径先取买家用户锁再取商品锁，
     *    均在用户行锁处与本路径汇合串行化，不存在无用户锁的"商品→订单"反向路径；
     * 3. 订单行锁必须用当前读（FOR UPDATE）：REPEATABLE READ 下无锁查询可能
     *    漏掉刚提交的挂单（违反 I11），此处不能先无锁查再补锁。
     *
     * @return 实际取消的订单数
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public int cancelActiveOrdersOfBuyer(Long buyerId, OrderCancelReason reason,
                                         String buyerNoticePrefix, String sellerNoticePrefix) {
        List<TradeOrder> actives = orderMapper.selectActiveByBuyerForUpdate(buyerId);
        int cancelled = 0;
        for (TradeOrder order : actives) {
            TradeOrder locked = orderMapper.selectByIdForUpdate(order.getId());
            if (locked == null || locked.getStatus() != OrderStatus.WAITING_MEET) {
                continue;
            }
            Item item = itemMapper.selectByIdForUpdate(locked.getItemId());
            cancelOne(locked, item, reason, buyerNoticePrefix, sellerNoticePrefix);
            cancelled++;
        }
        if (cancelled > 0) {
            log.warn("强制撤单完成 buyerId={} reason={} count={}", buyerId, reason, cancelled);
        }
        return cancelled;
    }

    /**
     * 取消指定商品进行中的订单（审核确认违规路径）。
     * 调用方必须已持有该商品行锁；与确认收货并发时由"买家用户锁 + 商品锁"串行化。
     *
     * @return true 表示存在并已取消；false 表示无进行中订单（确认收货可能已先行提交）。
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public boolean cancelActiveOrderOfItem(Long itemId, OrderCancelReason reason,
                                           String buyerNotice, String sellerNotice) {
        TradeOrder locked = orderMapper.selectActiveByItemIdForUpdate(itemId);
        if (locked == null) {
            return false;
        }
        Item item = itemMapper.selectByIdForUpdate(itemId);
        cancelOne(locked, item, reason, buyerNotice, sellerNotice);
        return true;
    }

    private void cancelOne(TradeOrder order, Item item, OrderCancelReason reason,
                           String buyerNoticePrefix, String sellerNoticePrefix) {
        // 1. 订单原子迁移：WAITING_MEET → CANCELLED + 显式原因 + 数据库取消时间
        int orderUpdated = orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getId, order.getId())
                .eq(TradeOrder::getStatus, OrderStatus.WAITING_MEET)
                .set(TradeOrder::getStatus, OrderStatus.CANCELLED)
                .set(TradeOrder::getCancelReason, reason)
                .setSql("cancelled_at = CURRENT_TIMESTAMP(6)"));
        if (orderUpdated == 0) {
            // 并发路径已迁移：合法幂等结果，交由调用方上层判定
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "订单状态已变更，撤单未执行");
        }

        // 2. 商品仅允许 RESERVED → OFF_SHELF（自动取消事务的即时后置条件；
        //    不影响卖家以后合法手动上架）
        if (item != null) {
            int itemUpdated = itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                    .eq(Item::getId, item.getId())
                    .eq(Item::getStatus, ItemStatus.RESERVED)
                    .set(Item::getStatus, ItemStatus.OFF_SHELF));
            if (itemUpdated == 0) {
                throw new BusinessException(ResultCode.CONFLICT, "商品状态异常，撤单中止");
            }
        }

        // 3. 买家全额退款（原子加余额）+ 唯一 REFUND 流水
        BigDecimal price = order.getPrice();
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, order.getBuyerId())
                .setSql("wallet_balance = wallet_balance + {0}", price));
        SysUser buyerAfter = sysUserMapper.selectById(order.getBuyerId());

        WalletLog refundLog = new WalletLog();
        refundLog.setUserId(order.getBuyerId());
        refundLog.setType(WalletLogType.REFUND);
        refundLog.setAmount(price);
        refundLog.setBalanceAfter(buyerAfter != null ? buyerAfter.getWalletBalance() : BigDecimal.ZERO);
        refundLog.setOrderId(order.getId());
        refundLog.setRemark(reason == OrderCancelReason.AUTO_CANCEL ? "账号封禁自动取消订单退款" : "违规强制撤单退款");
        walletLogMapper.insert(refundLog);

        // 4. 买卖双方独立 event_id 的 Outbox 通知
        String eventIdPrefix = "ORDER:" + order.getId() + ":" + reason.code();
        outboxService.appendNotice(eventIdPrefix + ":BUYER", OutboxService.AGGREGATE_ORDER,
                order.getId(), noticeEventType(reason), order.getBuyerId(),
                buyerNoticePrefix + "（订单 #" + order.getId() + "，退款 ¥" + price.toPlainString() + " 已到账）");
        outboxService.appendNotice(eventIdPrefix + ":SELLER", OutboxService.AGGREGATE_ORDER,
                order.getId(), noticeEventType(reason), order.getSellerId(),
                sellerNoticePrefix + "（订单 #" + order.getId() + "）");

        log.info("强制撤单 orderId={} item={} buyer={} reason={} refund={}",
                order.getId(), order.getItemId(), order.getBuyerId(), reason, price);
    }

    private String noticeEventType(OrderCancelReason reason) {
        return reason == OrderCancelReason.ADMIN_FORCE
                ? OutboxService.EVENT_ORDER_ADMIN_FORCE_CANCELLED
                : OutboxService.EVENT_ORDER_AUTO_CANCELLED;
    }
}
