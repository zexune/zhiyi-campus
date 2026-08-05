package com.zhiyi.module.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.trade.dto.ReviewDTO;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.entity.TradeReview;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.mapper.TradeReviewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 交易评价服务（A7）—— 买家在订单完成后对卖家做一单一评。
 *
 * 约束：
 * - 订单必须存在且状态为 COMPLETED；
 * - 只有该订单的买家才能评价；
 * - 一单一评，重复评价被拒绝（DB 层 uk_order 兜底并发）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final TradeOrderMapper orderMapper;
    private final TradeReviewMapper reviewMapper;

    @Transactional(rollbackFor = Exception.class)
    public TradeReview review(Long orderId, Long buyerId, ReviewDTO dto) {
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有买家才能评价");
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "订单未完成，暂不能评价");
        }

        Long existing = reviewMapper.selectCount(
                new LambdaQueryWrapper<TradeReview>().eq(TradeReview::getOrderId, orderId));
        if (existing != null && existing > 0) {
            throw new BusinessException(ResultCode.ORDER_ALREADY_REVIEWED);
        }

        TradeReview entity = new TradeReview();
        entity.setOrderId(orderId);
        entity.setReviewerId(buyerId);
        entity.setTargetId(order.getSellerId());
        entity.setRating(dto.getRating());
        entity.setAccurate(dto.getAccurate() == null ? Boolean.TRUE : dto.getAccurate());
        entity.setComment(dto.getComment());
        try {
            reviewMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 并发请求可能同时通过前置查询，唯一键冲突仍统一转换为明确的业务错误。
            throw new BusinessException(ResultCode.ORDER_ALREADY_REVIEWED);
        }

        log.info("交易评价写入 orderId={} reviewer={} target={} rating={}",
                orderId, buyerId, order.getSellerId(), dto.getRating());
        return entity;
    }
}
