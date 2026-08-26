package com.zhiyi.module.trade.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ItemType;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.common.enums.OrderCancelReason;
import com.zhiyi.common.enums.OrderStatus;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.common.enums.WalletLogType;
import com.zhiyi.common.support.IdempotencyService;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.social.service.OutboxService;
import com.zhiyi.module.trade.dto.CreateOrderDTO;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.entity.WalletLog;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.mapper.TradeReviewMapper;
import com.zhiyi.module.trade.mapper.WalletLogMapper;
import com.zhiyi.module.trade.vo.OrderVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.service.UserGrowthService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.function.Supplier;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderService 单元测试 —— 适配 v3.1 并发重构。
 *
 * 新流程：createOrder/confirmReceipt/cancelOrder 均经幂等协议执行
 * （测试 mock IdempotencyService 直接运行业务函数）；
 * 下单 = 无锁 peek 商品 → 升序 NOWAIT 锁双方用户 → 商品 NOWAIT 重读 →
 * 条件迁移 ON_SALE→RESERVED → 原子扣款 → 插单 → PAYMENT 流水；
 * 确认收货/取消 = 无锁 peek 订单 → 升序锁用户 → 商品/订单 FOR UPDATE →
 * 条件状态迁移 → 资金结算 → 流水（INCOME/REFUND）→ Outbox/经验。
 * item_reservation 已淘汰，覆盖双下单冲突、余额不足、状态迁移失败、买家被封禁等关键并发分支。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private TradeOrderMapper orderMapper;
    @Mock private TradeReviewMapper reviewMapper;
    @Mock private WalletLogMapper walletLogMapper;
    @Mock private UserGrowthService growthService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private OutboxService outboxService;

    private OrderService orderService;

    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_ID = 2L;
    private static final Long ITEM_ID = 100L;
    private static final BigDecimal PRICE = new BigDecimal("99.00");
    private static final String KEY = "11111111-1111-1111-1111-111111111111";

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize(TradeOrder.class, TradeOrderMapper.class);
        initialize(SysUser.class, SysUserMapper.class);
        initialize(Item.class, ItemMapper.class);
    }

    @BeforeEach
    void setUp() {
        orderService = new OrderService(sysUserMapper, itemMapper, orderMapper,
                walletLogMapper, growthService,
                new OrderViewAssembler(itemMapper, sysUserMapper, reviewMapper),
                idempotencyService, outboxService);
        // 跳过幂等协议：直接执行业务 Supplier（第 6 个参数）
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(5)).get())
                .when(idempotencyService)
                .execute(any(), any(), any(), any(), any(), any());
    }

    /** 构造一个在售的 SELL 商品 */
    private Item onSaleItem() {
        Item item = new Item();
        item.setId(ITEM_ID);
        item.setType(ItemType.SELL);
        item.setStatus(ItemStatus.ON_SALE);
        item.setModerationStatus(ModerationStatus.PASSED);
        item.setPrice(PRICE);
        item.setPublisherId(SELLER_ID);
        item.setSchoolId(1L);
        item.setTitle("测试商品");
        return item;
    }

    /** 构造一个余额充足的买家 */
    private SysUser buyer(BigDecimal balance) {
        SysUser user = new SysUser();
        user.setId(BUYER_ID);
        user.setNickname("买家小王");
        user.setSchoolId(1L);
        user.setStatus(UserStatus.ACTIVE);
        user.setWalletBalance(balance);
        return user;
    }

    /** 构造卖家 */
    private SysUser seller() {
        SysUser seller = new SysUser();
        seller.setId(SELLER_ID);
        seller.setNickname("卖家老张");
        seller.setSchoolId(1L);
        seller.setStatus(UserStatus.ACTIVE);
        seller.setWalletBalance(BigDecimal.ZERO);
        return seller;
    }

    /** 打桩：下单路径的锁与读取序列（lenient：提前抛出的路径走不到后续锁桩） */
    private void arrangeCreateOrderLocks(Item item, SysUser buyer, SysUser seller) {
        lenient().when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
        lenient().when(sysUserMapper.selectByIdForUpdateNowait(BUYER_ID)).thenReturn(buyer);
        lenient().when(sysUserMapper.selectByIdForUpdateNowait(SELLER_ID)).thenReturn(seller);
        lenient().when(itemMapper.selectByIdForUpdateNowait(ITEM_ID)).thenReturn(item);
    }

    // ================================================================
    // 下单
    // ================================================================

    @Nested
    class CreateOrder {

        @Test
        void shouldCreateOrderSuccessfully() {
            Item item = onSaleItem();
            SysUser b = buyer(new BigDecimal("200.00"));
            SysUser s = seller();
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);
            arrangeCreateOrderLocks(item, b, s);
            when(itemMapper.update(isNull(), any())).thenReturn(1);   // ON_SALE→RESERVED
            when(sysUserMapper.update(isNull(), any())).thenReturn(1); // 原子扣款
            when(sysUserMapper.selectById(BUYER_ID)).thenReturn(b);    // 扣款后回读
            when(orderMapper.insert(any(TradeOrder.class))).thenAnswer(inv -> {
                TradeOrder o = inv.getArgument(0);
                o.setId(1L);
                return 1;
            });

            OrderVO vo = orderService.createOrder(BUYER_ID, dto, KEY);

            assertNotNull(vo);
            assertEquals(ITEM_ID, vo.getItemId());
            assertEquals(BUYER_ID, vo.getBuyerId());
            assertEquals(SELLER_ID, vo.getSellerId());
            assertEquals("WAITING_MEET", vo.getStatus());
            assertEquals(s.getNickname(), vo.getPeerNickname());
            // 商品迁移走条件 UPDATE（锁序内重读后迁移），不做整行覆盖
            verify(itemMapper).update(isNull(), any());
            verify(itemMapper, never()).updateById(any(Item.class));

            ArgumentCaptor<TradeOrder> orderCaptor = ArgumentCaptor.forClass(TradeOrder.class);
            verify(orderMapper).insert(orderCaptor.capture());
            assertEquals(PRICE, orderCaptor.getValue().getPrice());
            assertEquals(OrderStatus.WAITING_MEET, orderCaptor.getValue().getStatus());

            // 验买家 PAYMENT 流水（恰一条，uk_wallet_order_type 兜底）
            ArgumentCaptor<WalletLog> logCaptor = ArgumentCaptor.forClass(WalletLog.class);
            verify(walletLogMapper).insert(logCaptor.capture());
            WalletLog payment = logCaptor.getValue();
            assertEquals(WalletLogType.PAYMENT, payment.getType());
            assertEquals(PRICE.negate(), payment.getAmount());
            assertEquals(1L, payment.getOrderId());

            // 确认收货才加经验/发通知，下单阶段不触发
            verifyNoInteractions(growthService, outboxService);
        }

        @Test
        void shouldRejectBuyType() {
            Item item = onSaleItem();
            item.setType(ItemType.BUY);
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);
            arrangeCreateOrderLocks(item, buyer(new BigDecimal("200.00")), seller());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto, KEY));
            assertTrue(ex.getMessage().contains("求购"));
            verify(orderMapper, never()).insert(any(TradeOrder.class));
        }

        @Test
        void shouldRejectOwnItem() {
            Item item = onSaleItem();
            item.setPublisherId(BUYER_ID); // 发布者就是买家自己
            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);

            assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto, KEY));
            verify(orderMapper, never()).insert(any(TradeOrder.class));
        }

        @Test
        void shouldRejectOffShelfItem() {
            Item item = onSaleItem();
            item.setStatus(ItemStatus.OFF_SHELF);
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);
            arrangeCreateOrderLocks(item, buyer(new BigDecimal("200.00")), seller());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto, KEY));
            assertEquals(ResultCode.ITEM_NOT_ON_SALE.getCode(), ex.getCode());
            verify(orderMapper, never()).insert(any(TradeOrder.class));
        }

        @Test
        void shouldRejectItemStillUnderModeration() {
            Item item = onSaleItem();
            item.setModerationStatus(ModerationStatus.PENDING);
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);
            arrangeCreateOrderLocks(item, buyer(new BigDecimal("200.00")), seller());

            assertThrows(BusinessException.class, () -> orderService.createOrder(BUYER_ID, dto, KEY));
            verify(itemMapper, never()).update(isNull(), any());
        }

        @Test
        void shouldRejectDuplicateActiveOrder() {
            // 双下单并发：后到者在条件迁移 ON_SALE→RESERVED 处影响 0 行
            Item item = onSaleItem();
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);
            arrangeCreateOrderLocks(item, buyer(new BigDecimal("200.00")), seller());
            when(itemMapper.update(isNull(), any())).thenReturn(0); // 已被抢先迁移

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto, KEY));
            assertEquals(ResultCode.CONFLICT.getCode(), ex.getCode());
            assertTrue(ex.getMessage().contains("抢先"));
            verify(sysUserMapper, never()).update(isNull(), any());
            verify(orderMapper, never()).insert(any(TradeOrder.class));
        }

        @Test
        void shouldRejectInsufficientBalance() {
            Item item = onSaleItem();
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);
            arrangeCreateOrderLocks(item, buyer(new BigDecimal("10.00")), seller());
            when(itemMapper.update(isNull(), any())).thenReturn(1);
            // 原子扣款 WHERE balance >= price 影响 0 行：余额不足兜底
            when(sysUserMapper.update(isNull(), any())).thenReturn(0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto, KEY));
            assertEquals(ResultCode.BALANCE_NOT_ENOUGH.getCode(), ex.getCode());
            verify(orderMapper, never()).insert(any(TradeOrder.class));
        }

        @Test
        void shouldRejectCrossSchoolItem() {
            Item item = onSaleItem();
            item.setSchoolId(2L);
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);
            arrangeCreateOrderLocks(item, buyer(new BigDecimal("200.00")), seller());

            BusinessException error = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto, KEY));
            assertEquals(403, error.getCode());
            verify(sysUserMapper, never()).update(isNull(), any());
        }

        @Test
        void shouldRejectSellerWhoMovedToAnotherSchool() {
            Item item = onSaleItem();
            SysUser s = seller();
            s.setSchoolId(2L);
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);
            arrangeCreateOrderLocks(item, buyer(new BigDecimal("200.00")), s);

            BusinessException error = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto, KEY));
            assertEquals(403, error.getCode());
            verify(sysUserMapper, never()).update(isNull(), any());
        }

        @Test
        void shouldRejectBannedBuyer() {
            // 买家被封禁：锁后重检状态拒绝，订单留给封禁事务的自动取消流程
            Item item = onSaleItem();
            SysUser b = buyer(new BigDecimal("200.00"));
            b.setStatus(UserStatus.BANNED_TEMP);
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);
            arrangeCreateOrderLocks(item, b, seller());

            BusinessException error = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto, KEY));
            assertEquals(ResultCode.USER_STATUS_ERROR.getCode(), error.getCode());
            verify(itemMapper, never()).update(isNull(), any());
        }

        @Test
        void shouldRejectBannedSeller() {
            Item item = onSaleItem();
            SysUser s = seller();
            s.setStatus(UserStatus.BANNED_PERM);
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);
            arrangeCreateOrderLocks(item, buyer(new BigDecimal("200.00")), s);

            BusinessException error = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto, KEY));
            assertEquals(ResultCode.USER_STATUS_ERROR.getCode(), error.getCode());
        }

        @Test
        void busyUserLockMapsToTradeBusy() {
            // NOWAIT 锁繁忙（errno 3572）：映射为可重试背压，不进入死锁重试
            Item item = onSaleItem();
            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
            SQLException nowait = new SQLException("Lock wait", "HY000", 3572);
            when(sysUserMapper.selectByIdForUpdateNowait(any()))
                    .thenThrow(new DataAccessResourceFailureException("nowait", nowait));
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);

            BusinessException error = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto, KEY));
            assertEquals(ResultCode.TRADE_BUSY.getCode(), error.getCode());
            assertEquals(ResultCode.RequestOutcome.UNKNOWN, error.effectiveRequestOutcome());
        }

        @Test
        void busyItemLockMapsToTradeBusy() {
            Item item = onSaleItem();
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);
            arrangeCreateOrderLocks(item, buyer(new BigDecimal("200.00")), seller());
            SQLException nowait = new SQLException("Lock wait", "HY000", 3572);
            when(itemMapper.selectByIdForUpdateNowait(any()))
                    .thenThrow(new DataAccessResourceFailureException("nowait", nowait));

            BusinessException error = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto, KEY));
            assertEquals(ResultCode.TRADE_BUSY.getCode(), error.getCode());
            assertEquals(ResultCode.RequestOutcome.UNKNOWN, error.effectiveRequestOutcome());
        }
    }

    // ================================================================
    // 确认收货
    // ================================================================

    @Nested
    class ConfirmReceipt {

        private TradeOrder waitingOrder() {
            TradeOrder order = new TradeOrder();
            order.setId(1L);
            order.setItemId(ITEM_ID);
            order.setBuyerId(BUYER_ID);
            order.setSellerId(SELLER_ID);
            order.setPrice(PRICE);
            order.setStatus(OrderStatus.WAITING_MEET);
            return order;
        }

        private Item reservedItem() {
            Item item = onSaleItem();
            item.setStatus(ItemStatus.RESERVED);
            return item;
        }

        private TradeOrder completedOrder() {
            TradeOrder order = waitingOrder();
            order.setStatus(OrderStatus.COMPLETED);
            order.setCompletedAt(java.time.LocalDateTime.now());
            return order;
        }

        private void arrangeConfirmFlow(TradeOrder peek, Item item, SysUser buyer, SysUser seller) {
            when(orderMapper.selectById(1L)).thenReturn(peek, completedOrder());
            when(sysUserMapper.selectByIdForUpdateNowait(BUYER_ID)).thenReturn(buyer);
            when(sysUserMapper.selectByIdForUpdateNowait(SELLER_ID)).thenReturn(seller);
            when(itemMapper.selectByIdForUpdate(ITEM_ID)).thenReturn(item);
            when(orderMapper.selectByIdForUpdate(1L)).thenReturn(peek);
            // 收款后回读卖家（INCOME 流水余额与昵称展示；提前失败的用例不触发，故 lenient）
            lenient().when(sysUserMapper.selectById(SELLER_ID)).thenReturn(seller);
        }

        @Test
        void shouldCompleteOrderSuccessfully() {
            TradeOrder order = waitingOrder();
            Item item = reservedItem();
            SysUser b = buyer(new BigDecimal("101.00")); // 剩余余额
            SysUser s = seller();
            arrangeConfirmFlow(order, item, b, s);
            when(orderMapper.update(isNull(), any())).thenReturn(1); // WAITING_MEET→COMPLETED
            when(itemMapper.update(isNull(), any())).thenReturn(1);  // RESERVED→SOLD
            when(sysUserMapper.update(isNull(), any())).thenReturn(1); // 卖家收款

            OrderVO vo = orderService.confirmReceipt(1L, BUYER_ID, KEY);

            assertNotNull(vo);
            assertEquals("COMPLETED", vo.getStatus());
            assertEquals(s.getNickname(), vo.getPeerNickname());

            // 卖家 INCOME 流水
            ArgumentCaptor<WalletLog> logCaptor = ArgumentCaptor.forClass(WalletLog.class);
            verify(walletLogMapper).insert(logCaptor.capture());
            assertEquals(WalletLogType.INCOME, logCaptor.getValue().getType());
            assertEquals(PRICE, logCaptor.getValue().getAmount());
            assertEquals(1L, logCaptor.getValue().getOrderId());

            // 双方加经验 + 买卖双方独立 event_id 的完成通知
            verify(growthService).addExp(eq(BUYER_ID), eq(UserGrowthService.EXP_ORDER_COMPLETED), anyString());
            verify(growthService).addExp(eq(SELLER_ID), eq(UserGrowthService.EXP_ORDER_COMPLETED), anyString());
            verify(outboxService, times(2)).appendNotice(any(), eq(OutboxService.AGGREGATE_ORDER),
                    eq(1L), eq(OutboxService.EVENT_ORDER_COMPLETED), any(), anyString());
        }

        @Test
        void shouldRejectNonBuyer() {
            TradeOrder order = waitingOrder();
            when(orderMapper.selectById(1L)).thenReturn(order);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.confirmReceipt(1L, 999L, KEY)); // 不是买家
            assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        }

        @Test
        void shouldRejectAlreadyCompleted() {
            TradeOrder order = waitingOrder();
            arrangeConfirmFlow(order, reservedItem(), buyer(BigDecimal.ZERO), seller());
            when(orderMapper.update(isNull(), any())).thenReturn(0); // 并发已处理

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.confirmReceipt(1L, BUYER_ID, KEY));
            assertEquals(ResultCode.ORDER_STATUS_ERROR.getCode(), ex.getCode());
            verify(walletLogMapper, never()).insert(any(WalletLog.class));
            verifyNoInteractions(growthService, outboxService);
        }

        @Test
        void shouldRejectWhenItemStateInconsistent() {
            TradeOrder order = waitingOrder();
            arrangeConfirmFlow(order, reservedItem(), buyer(BigDecimal.ZERO), seller());
            when(orderMapper.update(isNull(), any())).thenReturn(1);
            when(itemMapper.update(isNull(), any())).thenReturn(0); // RESERVED→SOLD 失败

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.confirmReceipt(1L, BUYER_ID, KEY));
            assertEquals(ResultCode.CONFLICT.getCode(), ex.getCode());
            verify(walletLogMapper, never()).insert(any(WalletLog.class));
        }

        @Test
        void shouldRejectBannedBuyer() {
            // 锁后重检买家 ACTIVE：与并发封禁互斥
            TradeOrder order = waitingOrder();
            SysUser b = buyer(BigDecimal.ZERO);
            b.setStatus(UserStatus.BANNED_TEMP);
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(sysUserMapper.selectByIdForUpdateNowait(BUYER_ID)).thenReturn(b);
            when(sysUserMapper.selectByIdForUpdateNowait(SELLER_ID)).thenReturn(seller());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.confirmReceipt(1L, BUYER_ID, KEY));
            assertEquals(ResultCode.USER_STATUS_ERROR.getCode(), ex.getCode());
            verify(orderMapper, never()).update(isNull(), any());
        }
    }

    // ================================================================
    // 取消订单
    // ================================================================

    @Nested
    class CancelOrder {

        private TradeOrder waitingOrder() {
            TradeOrder order = new TradeOrder();
            order.setId(1L);
            order.setItemId(ITEM_ID);
            order.setBuyerId(BUYER_ID);
            order.setSellerId(SELLER_ID);
            order.setPrice(PRICE);
            order.setStatus(OrderStatus.WAITING_MEET);
            return order;
        }

        private Item reservedItem() {
            Item item = onSaleItem();
            item.setStatus(ItemStatus.RESERVED);
            return item;
        }

        private TradeOrder cancelledOrder() {
            TradeOrder order = waitingOrder();
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelReason(OrderCancelReason.USER_CANCEL);
            order.setCancelledAt(java.time.LocalDateTime.now());
            return order;
        }

        private void arrangeCancelFlow(TradeOrder peek, Item item, SysUser buyer, SysUser seller) {
            when(orderMapper.selectById(1L)).thenReturn(peek, cancelledOrder());
            when(sysUserMapper.selectByIdForUpdateNowait(BUYER_ID)).thenReturn(buyer);
            when(sysUserMapper.selectByIdForUpdateNowait(SELLER_ID)).thenReturn(seller);
            when(itemMapper.selectByIdForUpdate(ITEM_ID)).thenReturn(item);
            when(orderMapper.selectByIdForUpdate(1L)).thenReturn(peek);
        }

        @Test
        void shouldCancelSuccessfullyAndRelistItemWhenSellerActive() {
            TradeOrder order = waitingOrder();
            Item item = reservedItem();
            SysUser b = buyer(BigDecimal.ZERO);
            SysUser s = seller();
            arrangeCancelFlow(order, item, b, s);
            when(orderMapper.update(isNull(), any())).thenReturn(1);
            when(itemMapper.update(isNull(), any())).thenReturn(1);
            when(sysUserMapper.update(isNull(), any())).thenReturn(1); // 退款

            OrderVO vo = orderService.cancelOrder(1L, BUYER_ID, KEY);

            assertNotNull(vo);
            assertEquals("CANCELLED", vo.getStatus());
            assertEquals(s.getNickname(), vo.getPeerNickname());

            // 退款流水
            ArgumentCaptor<WalletLog> logCaptor = ArgumentCaptor.forClass(WalletLog.class);
            verify(walletLogMapper).insert(logCaptor.capture());
            assertEquals(WalletLogType.REFUND, logCaptor.getValue().getType());
            assertEquals(PRICE, logCaptor.getValue().getAmount());

            // 买家取消不发系统通知、不加经验
            verifyNoInteractions(outboxService, growthService);
        }

        @Test
        void shouldOffShelfRejectedItemWhenModerationFailed() {
            // 审核未通过：取消后商品目标状态为 OFF_SHELF 而非 ON_SALE
            TradeOrder order = waitingOrder();
            Item item = reservedItem();
            item.setModerationStatus(ModerationStatus.REJECTED);
            arrangeCancelFlow(order, item, buyer(BigDecimal.ZERO), seller());
            when(orderMapper.update(isNull(), any())).thenReturn(1);
            when(itemMapper.update(isNull(), any())).thenReturn(1);
            when(sysUserMapper.update(isNull(), any())).thenReturn(1);

            assertDoesNotThrow(() -> orderService.cancelOrder(1L, BUYER_ID, KEY));
            verify(itemMapper).update(isNull(), any());
        }

        @Test
        void shouldRejectNonBuyer() {
            TradeOrder order = waitingOrder();
            when(orderMapper.selectById(1L)).thenReturn(order);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.cancelOrder(1L, 999L, KEY));
            assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        }

        @Test
        void shouldRejectAlreadyCancelled() {
            TradeOrder order = waitingOrder();
            arrangeCancelFlow(order, reservedItem(), buyer(BigDecimal.ZERO), seller());
            when(orderMapper.update(isNull(), any())).thenReturn(0); // 并发已取消

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.cancelOrder(1L, BUYER_ID, KEY));
            assertEquals(ResultCode.ORDER_STATUS_ERROR.getCode(), ex.getCode());
            verify(sysUserMapper, never()).update(isNull(), any());
        }

        @Test
        void shouldRejectWhenItemStateInconsistent() {
            TradeOrder order = waitingOrder();
            arrangeCancelFlow(order, reservedItem(), buyer(BigDecimal.ZERO), seller());
            when(orderMapper.update(isNull(), any())).thenReturn(1);
            when(itemMapper.update(isNull(), any())).thenReturn(0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.cancelOrder(1L, BUYER_ID, KEY));
            assertEquals(ResultCode.CONFLICT.getCode(), ex.getCode());
            verify(walletLogMapper, never()).insert(any(WalletLog.class));
        }
    }
}
