package com.zhiyi.module.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.OrderStatus;
import com.zhiyi.module.trade.dto.ReviewDTO;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.entity.TradeReview;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.mapper.TradeReviewMapper;
import com.zhiyi.module.user.service.UserGrowthService;
import com.zhiyi.module.user.support.ExpRule;
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
 * - 一单一评，重复评价被拒绝（DB 层 uk_order 兜底并发）；
 * - 4-5 星好评为卖家带来小额经验（每日上限见 {@link ExpRule#GOOD_REVIEW}），
 *   与评价写入同事务：评价回滚则经验不发放。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final TradeOrderMapper orderMapper;
    private final TradeReviewMapper reviewMapper;
    private final UserGrowthService growthService;

    @Transactional(rollbackFor = Exception.class)
    public TradeReview review(Long orderId, Long buyerId, ReviewDTO dto) {
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有买家才能评价");
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "订单未完成，暂不能评价");
        }

        Long existing = reviewMapper.selectCount(
                new LambdaQueryWrapper<TradeReview>().eq(TradeReview::getOrderId, orderId));
        if (existing > 0) {
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

        // 4-5 星好评奖励卖家经验（同事务，评价回滚则经验随回滚；日上限由目录控制）
        if (entity.getRating() != null && entity.getRating() >= 4) {
            growthService.award(entity.getTargetId(), ExpRule.GOOD_REVIEW);
        }

        log.info("交易评价写入 orderId={} reviewer={} target={} rating={}",
                orderId, buyerId, order.getSellerId(), entity.getRating());
        return entity;
    }
}
