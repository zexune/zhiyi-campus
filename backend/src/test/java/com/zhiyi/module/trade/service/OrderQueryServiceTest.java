package com.zhiyi.module.trade.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.enums.OrderStatus;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.vo.OrderVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock private TradeOrderMapper orderMapper;
    @Mock private OrderViewAssembler assembler;
    private OrderQueryService service;

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.zhiyi.module.trade.mapper.TradeOrderMapper");
        TableInfoHelper.initTableInfo(assistant, TradeOrder.class);
    }

    @BeforeEach
    void setUp() {
        service = new OrderQueryService(orderMapper, assembler);
    }

    @Test
    void boughtOrdersUseBuyerPerspectiveAndNormalizePagination() {
        Page<TradeOrder> rows = new Page<>(1, 50, 0);
        rows.setRecords(List.of());
        Page<OrderVO> assembled = new Page<>(1, 50, 0);
        when(orderMapper.selectPage(any(), any())).thenReturn(rows);
        when(assembler.assemblePage(rows, OrderViewAssembler.Perspective.BUYER)).thenReturn(assembled);

        IPage<OrderVO> result = service.getBoughtOrders(7L, 0, 500, null);

        assertEquals(assembled, result);
        ArgumentCaptor<IPage<TradeOrder>> page = pageCaptor();
        verify(orderMapper).selectPage(page.capture(), any());
        assertEquals(1, page.getValue().getCurrent());
        assertEquals(50, page.getValue().getSize());
        verify(assembler).assemblePage(rows, OrderViewAssembler.Perspective.BUYER);
    }

    @Test
    @SuppressWarnings("unchecked")
    void statusFilterIsParsedAsDomainEnum() {
        Page<TradeOrder> rows = new Page<>(2, 10, 0);
        rows.setRecords(List.of());
        when(orderMapper.selectPage(any(), any())).thenReturn(rows);
        when(assembler.assemblePage(any(), eq(OrderViewAssembler.Perspective.BUYER)))
                .thenReturn(new Page<>());

        service.getBoughtOrders(7L, 2, 10, " completed ");

        ArgumentCaptor<Wrapper<TradeOrder>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).selectPage(any(), wrapper.capture());
        LambdaQueryWrapper<TradeOrder> query = (LambdaQueryWrapper<TradeOrder>) wrapper.getValue();
        query.getSqlSegment();
        assertTrue(query.getParamNameValuePairs().containsValue(7L));
        assertTrue(query.getParamNameValuePairs().containsValue(OrderStatus.COMPLETED));
    }

    @Test
    void soldOrdersUseSellerPerspective() {
        Page<TradeOrder> rows = new Page<>(1, 10, 0);
        rows.setRecords(List.of());
        Page<OrderVO> assembled = new Page<>();
        when(orderMapper.selectPage(any(), any())).thenReturn(rows);
        when(assembler.assemblePage(rows, OrderViewAssembler.Perspective.SELLER)).thenReturn(assembled);

        assertEquals(assembled, service.getSoldOrders(8L, 1, 10, "WAITING_MEET"));
        verify(assembler).assemblePage(rows, OrderViewAssembler.Perspective.SELLER);
    }

    @Test
    void invalidStatusFailsBeforeAnyDatabaseQuery() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getBoughtOrders(7L, 1, 10, "PAID"));

        assertEquals(400, error.getCode());
        assertEquals("订单状态不合法", error.getMessage());
        verify(orderMapper, never()).selectPage(any(), any());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<IPage<TradeOrder>> pageCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(IPage.class);
    }
}
