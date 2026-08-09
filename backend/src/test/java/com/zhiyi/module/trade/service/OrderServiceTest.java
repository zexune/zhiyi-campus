package com.zhiyi.module.trade.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderService 单元测试 —— 覆盖下单、确认收货、取消订单的正常路径、
 * 边界条件、并发竞态以及已修复的 Bug（BUY 类型拒绝、重复资金操作等）。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private TradeOrderMapper orderMapper;
    @Mock private TradeReviewMapper reviewMapper;
    @Mock private WalletLogMapper walletLogMapper;
    @Mock private UserGrowthService growthService;

    private OrderService orderService;

    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_ID = 2L;
    private static final Long ITEM_ID = 100L;
    private static final BigDecimal PRICE = new BigDecimal("99.00");

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initializeTableInfo(TradeOrder.class, "com.zhiyi.module.trade.mapper.TradeOrderMapper");
        initializeTableInfo(SysUser.class, "com.zhiyi.module.user.mapper.SysUserMapper");
        initializeTableInfo(Item.class, "com.zhiyi.module.item.mapper.ItemMapper");
    }

    private static void initializeTableInfo(Class<?> entityType, String namespace) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace(namespace);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    @BeforeEach
    void setUp() {
        orderService = new OrderService(sysUserMapper, itemMapper,
                orderMapper, reviewMapper, walletLogMapper, growthService);
    }

    /** 构造一个在售的 SELL 商品 */
    private Item onSaleItem() {
        Item item = new Item();
        item.setId(ITEM_ID);
        item.setType("SELL");
        item.setStatus("ON_SALE");
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
        user.setWalletBalance(balance);
        return user;
    }

    /** 构造卖家 */
    private SysUser seller() {
        SysUser seller = new SysUser();
        seller.setId(SELLER_ID);
        seller.setNickname("卖家老张");
        seller.setSchoolId(1L);
        seller.setWalletBalance(BigDecimal.ZERO);
        return seller;
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

            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
            when(orderMapper.selectCount(any())).thenReturn(0L);
            when(sysUserMapper.selectById(BUYER_ID)).thenReturn(b);
            when(sysUserMapper.update(nullable(SysUser.class), any())).thenReturn(1);
            when(sysUserMapper.selectById(BUYER_ID)).thenReturn(b, b); // 两次回读
            when(sysUserMapper.selectById(SELLER_ID)).thenReturn(s);
            when(orderMapper.insert(any(TradeOrder.class))).thenAnswer(inv -> {
                TradeOrder o = inv.getArgument(0);
                o.setId(1L);
                return 1;
            });

            OrderVO vo = orderService.createOrder(BUYER_ID, dto);

            assertNotNull(vo);
            assertEquals(ITEM_ID, vo.getItemId());
            assertEquals(BUYER_ID, vo.getBuyerId());
            assertEquals(SELLER_ID, vo.getSellerId());
            assertEquals("WAITING_MEET", vo.getStatus());
            assertEquals(s.getNickname(), vo.getPeerNickname());

            // 验流水
            ArgumentCaptor<WalletLog> logCaptor = ArgumentCaptor.forClass(WalletLog.class);
            verify(walletLogMapper).insert(logCaptor.capture());
            WalletLog payment = logCaptor.getValue();
            assertEquals("PAYMENT", payment.getType());
            assertEquals(PRICE.negate(), payment.getAmount());
        }

        @Test
        void shouldRejectBuyType() {
            Item item = onSaleItem();
            item.setType("BUY");
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);
            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto));
            assertTrue(ex.getMessage().contains("求购"));
        }

        @Test
        void shouldRejectItemPastItsDeadline() {
            Item item = onSaleItem();
            item.setDeadlineTime(LocalDateTime.now().minusMinutes(1));
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);
            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto));

            assertTrue(ex.getMessage().contains("截止时间"));
            verifyNoInteractions(orderMapper, sysUserMapper);
        }

        @Test
        void shouldRejectOwnItem() {
            Item item = onSaleItem();
            item.setPublisherId(BUYER_ID); // 发布者就是买家自己
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);
            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto));
        }

        @Test
        void shouldRejectOffShelfItem() {
            Item item = onSaleItem();
            item.setStatus("OFF_SHELF");
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);
            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto));
        }

        @Test
        void shouldRejectDuplicateActiveOrder() {
            Item item = onSaleItem();
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);
            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
            when(orderMapper.selectCount(any())).thenReturn(1L); // 已有活跃订单

            assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto));
        }

        @Test
        void shouldRejectInsufficientBalance() {
            Item item = onSaleItem();
            SysUser b = buyer(new BigDecimal("10.00")); // 余额不足
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);

            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
            when(orderMapper.selectCount(any())).thenReturn(0L);
            when(sysUserMapper.selectById(BUYER_ID)).thenReturn(b);

            assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto));
        }

        @Test
        void shouldRejectCrossSchoolBuyer() {
            Item item = onSaleItem();
            item.setSchoolId(2L);
            SysUser b = buyer(new BigDecimal("200.00"));
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);

            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
            when(orderMapper.selectCount(any())).thenReturn(0L);
            when(sysUserMapper.selectById(BUYER_ID)).thenReturn(b);

            BusinessException error = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto));
            assertEquals(403, error.getCode());
            verify(sysUserMapper, never()).update(nullable(SysUser.class), any());
        }

        @Test
        void shouldRejectSellerWhoMovedToAnotherSchool() {
            Item item = onSaleItem();
            SysUser b = buyer(new BigDecimal("200.00"));
            SysUser s = seller();
            s.setSchoolId(2L);
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);

            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
            when(orderMapper.selectCount(any())).thenReturn(0L);
            when(sysUserMapper.selectById(BUYER_ID)).thenReturn(b);
            when(sysUserMapper.selectById(SELLER_ID)).thenReturn(s);

            BusinessException error = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto));
            assertEquals(403, error.getCode());
            verify(sysUserMapper, never()).update(nullable(SysUser.class), any());
        }

        @Test
        void shouldRejectWhenDeductionFails() {
            Item item = onSaleItem();
            SysUser b = buyer(new BigDecimal("200.00"));
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setItemId(ITEM_ID);

            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
            when(orderMapper.selectCount(any())).thenReturn(0L);
            when(sysUserMapper.selectById(BUYER_ID)).thenReturn(b);
            when(sysUserMapper.selectById(SELLER_ID)).thenReturn(seller());
            when(sysUserMapper.update(nullable(SysUser.class), any())).thenReturn(0); // 扣款失败

            assertThrows(BusinessException.class,
                    () -> orderService.createOrder(BUYER_ID, dto));
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
            order.setStatus("WAITING_MEET");
            return order;
        }

        @Test
        void shouldCompleteOrderSuccessfully() {
            TradeOrder order = waitingOrder();
            Item item = onSaleItem();
            SysUser s = seller();

            when(orderMapper.selectById(1L)).thenReturn(order);
            when(orderMapper.update(nullable(TradeOrder.class), any())).thenReturn(1); // 原子更新成功
            when(sysUserMapper.update(nullable(SysUser.class), any())).thenReturn(1);
            when(sysUserMapper.selectById(SELLER_ID)).thenReturn(s);
            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            OrderVO vo = orderService.confirmReceipt(1L, BUYER_ID);

            assertNotNull(vo);
            assertEquals("COMPLETED", vo.getStatus());
            assertNotNull(vo.getCompletedAt());
            assertEquals(s.getNickname(), vo.getPeerNickname());

            // 卖家收入流水
            ArgumentCaptor<WalletLog> logCaptor = ArgumentCaptor.forClass(WalletLog.class);
            verify(walletLogMapper).insert(logCaptor.capture());
            assertEquals("INCOME", logCaptor.getValue().getType());

            // 双方加经验
            verify(growthService).addExp(eq(BUYER_ID), eq(UserGrowthService.EXP_ORDER_COMPLETED), anyString());
            verify(growthService).addExp(eq(SELLER_ID), eq(UserGrowthService.EXP_ORDER_COMPLETED), anyString());
        }

        @Test
        void shouldRejectNonBuyer() {
            TradeOrder order = waitingOrder();
            when(orderMapper.selectById(1L)).thenReturn(order);

            assertThrows(BusinessException.class,
                    () -> orderService.confirmReceipt(1L, 999L)); // 不是买家
        }

        @Test
        void shouldRejectAlreadyCompleted() {
            TradeOrder order = waitingOrder();
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(orderMapper.update(nullable(TradeOrder.class), any())).thenReturn(0); // 并发已处理

            assertThrows(BusinessException.class,
                    () -> orderService.confirmReceipt(1L, BUYER_ID));
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
            order.setStatus("WAITING_MEET");
            return order;
        }

        @Test
        void shouldCancelSuccessfully() {
            TradeOrder order = waitingOrder();
            Item item = onSaleItem();
            SysUser b = buyer(new BigDecimal("100.00"));
            SysUser s = seller();

            when(orderMapper.selectById(1L)).thenReturn(order);
            when(orderMapper.update(nullable(TradeOrder.class), any())).thenReturn(1); // 原子更新成功
            when(sysUserMapper.update(nullable(SysUser.class), any())).thenReturn(1);
            when(sysUserMapper.selectById(BUYER_ID)).thenReturn(b);
            when(sysUserMapper.selectById(SELLER_ID)).thenReturn(s);
            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            OrderVO vo = orderService.cancelOrder(1L, BUYER_ID);

            assertNotNull(vo);
            assertEquals("CANCELLED", vo.getStatus());
            assertNotNull(vo.getCancelledAt());
            assertEquals(s.getNickname(), vo.getPeerNickname());

            // 退款流水
            ArgumentCaptor<WalletLog> logCaptor = ArgumentCaptor.forClass(WalletLog.class);
            verify(walletLogMapper).insert(logCaptor.capture());
            assertEquals("REFUND", logCaptor.getValue().getType());
            assertEquals(PRICE, logCaptor.getValue().getAmount());
        }

        @Test
        void shouldRejectNonBuyer() {
            TradeOrder order = waitingOrder();
            when(orderMapper.selectById(1L)).thenReturn(order);

            assertThrows(BusinessException.class,
                    () -> orderService.cancelOrder(1L, 999L));
        }

        @Test
        void shouldRejectAlreadyCancelled() {
            TradeOrder order = waitingOrder();
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(orderMapper.update(nullable(TradeOrder.class), any())).thenReturn(0); // 并发已取消

            assertThrows(BusinessException.class,
                    () -> orderService.cancelOrder(1L, BUYER_ID));
        }
    }
}
