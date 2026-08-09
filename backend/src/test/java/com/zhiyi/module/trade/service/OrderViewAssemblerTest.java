package com.zhiyi.module.trade.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.enums.OrderStatus;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.entity.TradeReview;
import com.zhiyi.module.trade.mapper.TradeReviewMapper;
import com.zhiyi.module.trade.vo.OrderVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderViewAssemblerTest {

    @Mock private ItemMapper itemMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private TradeReviewMapper reviewMapper;
    private OrderViewAssembler assembler;

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "order-view-assembler-test");
        assistant.setCurrentNamespace(TradeReviewMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, TradeReview.class);
    }

    @BeforeEach
    void setUp() {
        assembler = new OrderViewAssembler(itemMapper, userMapper, reviewMapper);
    }

    @Test
    void assemblesTwentyBoughtOrdersWithThreeBatchQueries() {
        List<TradeOrder> orders = LongStream.rangeClosed(1, 20)
                .mapToObj(this::completedOrder)
                .toList();
        List<Item> items = orders.stream().map(order -> {
            Item item = new Item();
            item.setId(order.getItemId());
            item.setTitle("商品" + order.getItemId());
            item.setImages(List.of("/cover/" + order.getItemId() + ".jpg"));
            return item;
        }).toList();
        List<SysUser> sellers = orders.stream().map(order -> {
            SysUser seller = new SysUser();
            seller.setId(order.getSellerId());
            seller.setNickname("卖家" + order.getSellerId());
            return seller;
        }).toList();
        TradeReview reviewed = new TradeReview();
        reviewed.setOrderId(1L);

        when(itemMapper.selectByIds(any())).thenReturn(items);
        when(userMapper.selectByIds(any())).thenReturn(sellers);
        when(reviewMapper.selectList(any())).thenReturn(List.of(reviewed));

        Page<TradeOrder> source = new Page<>(1, 20, 20);
        source.setRecords(new ArrayList<>(orders));
        var result = assembler.assemblePage(source, OrderViewAssembler.Perspective.BUYER);

        assertEquals(20, result.getRecords().size());
        OrderVO first = result.getRecords().getFirst();
        assertEquals("商品101", first.getItemTitle());
        assertEquals("卖家201", first.getPeerNickname());
        assertEquals(Boolean.TRUE, first.getReviewed());

        verify(itemMapper).selectByIds(any());
        verify(userMapper).selectByIds(any());
        verify(reviewMapper).selectList(any());
        verify(itemMapper, never()).selectById(any());
        verify(userMapper, never()).selectById(any());
    }

    private TradeOrder completedOrder(long id) {
        TradeOrder order = new TradeOrder();
        order.setId(id);
        order.setItemId(100L + id);
        order.setBuyerId(7L);
        order.setSellerId(200L + id);
        order.setStatus(OrderStatus.COMPLETED);
        return order;
    }
}
