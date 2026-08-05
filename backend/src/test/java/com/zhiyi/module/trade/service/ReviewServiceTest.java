package com.zhiyi.module.trade.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.trade.dto.ReviewDTO;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.entity.TradeReview;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.mapper.TradeReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ReviewService 单元测试（A7）—— 覆盖评价的前置校验与写入。
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private TradeOrderMapper orderMapper;
    @Mock private TradeReviewMapper reviewMapper;

    private ReviewService reviewService;

    private static final Long ORDER_ID = 100L;
    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_ID = 2L;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(orderMapper, reviewMapper);
    }

    private TradeOrder completedOrder() {
        TradeOrder order = new TradeOrder();
        order.setId(ORDER_ID);
        order.setBuyerId(BUYER_ID);
        order.setSellerId(SELLER_ID);
        order.setStatus("COMPLETED");
        return order;
    }

    private ReviewDTO dto(int rating, boolean accurate, String comment) {
        ReviewDTO d = new ReviewDTO();
        d.setRating(rating);
        d.setAccurate(accurate);
        d.setComment(comment);
        return d;
    }

    @Test
    void buyerReviewsCompletedOrder_persistsReviewWithSellerAsTarget() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(completedOrder());
        when(reviewMapper.selectCount(any())).thenReturn(0L);

        reviewService.review(ORDER_ID, BUYER_ID, dto(5, true, "很棒"));

        ArgumentCaptor<TradeReview> captor = ArgumentCaptor.forClass(TradeReview.class);
        verify(reviewMapper).insert(captor.capture());
        TradeReview saved = captor.getValue();
        assertEquals(ORDER_ID, saved.getOrderId());
        assertEquals(BUYER_ID, saved.getReviewerId());
        assertEquals(SELLER_ID, saved.getTargetId());
        assertEquals(5, saved.getRating());
        assertTrue(saved.getAccurate());
        assertEquals("很棒", saved.getComment());
    }

    @Test
    void nonExistentOrderIsRejected() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.review(ORDER_ID, BUYER_ID, dto(5, true, null)));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        verify(reviewMapper, never()).insert(any());
    }

    @Test
    void onlyBuyerCanReview() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(completedOrder());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.review(ORDER_ID, SELLER_ID, dto(5, true, null)));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        verify(reviewMapper, never()).insert(any());
    }

    @Test
    void unfinishedOrderCannotBeReviewed() {
        TradeOrder waiting = completedOrder();
        waiting.setStatus("WAITING_MEET");
        when(orderMapper.selectById(ORDER_ID)).thenReturn(waiting);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.review(ORDER_ID, BUYER_ID, dto(5, true, null)));
        assertEquals(ResultCode.ORDER_STATUS_ERROR.getCode(), ex.getCode());
        verify(reviewMapper, never()).insert(any());
    }

    @Test
    void secondReviewOnSameOrderIsRejected() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(completedOrder());
        when(reviewMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.review(ORDER_ID, BUYER_ID, dto(4, true, null)));
        assertEquals(ResultCode.ORDER_ALREADY_REVIEWED.getCode(), ex.getCode());
        verify(reviewMapper, never()).insert(any());
    }

    @Test
    void concurrentDuplicateInsertReturnsAlreadyReviewedError() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(completedOrder());
        when(reviewMapper.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("uk_order"))
                .when(reviewMapper).insert(any(TradeReview.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.review(ORDER_ID, BUYER_ID, dto(5, true, null)));

        assertEquals(ResultCode.ORDER_ALREADY_REVIEWED.getCode(), ex.getCode());
    }
}
