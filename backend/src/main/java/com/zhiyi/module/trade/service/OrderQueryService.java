package com.zhiyi.module.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.OrderStatus;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final TradeOrderMapper orderMapper;
    private final OrderViewAssembler assembler;

    public IPage<OrderVO> getBoughtOrders(Long userId, int page, int size, String status) {
        return query(userId, page, size, status, OrderViewAssembler.Perspective.BUYER);
    }

    public IPage<OrderVO> getSoldOrders(Long userId, int page, int size, String status) {
        return query(userId, page, size, status, OrderViewAssembler.Perspective.SELLER);
    }

    private IPage<OrderVO> query(Long userId,
                                 int page,
                                 int size,
                                 String status,
                                 OrderViewAssembler.Perspective perspective) {
        LambdaQueryWrapper<TradeOrder> wrapper = new LambdaQueryWrapper<TradeOrder>()
                .eq(perspective == OrderViewAssembler.Perspective.BUYER,
                        TradeOrder::getBuyerId, userId)
                .eq(perspective == OrderViewAssembler.Perspective.SELLER,
                        TradeOrder::getSellerId, userId)
                .orderByDesc(TradeOrder::getCreatedAt)
                .orderByDesc(TradeOrder::getId);
        try {
            OrderStatus parsed = OrderStatus.fromNullable(status);
            wrapper.eq(parsed != null, TradeOrder::getStatus, parsed);
        } catch (IllegalArgumentException invalidStatus) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "订单状态不合法");
        }
        IPage<TradeOrder> orders = orderMapper.selectPage(
                new Page<>(Math.max(1, page), Math.max(1, Math.min(size, 50))), wrapper);
        return assembler.assemblePage(orders, perspective);
    }
}
