package com.zhiyi.module.user.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.social.entity.ChatMessage;
import com.zhiyi.module.social.mapper.ChatMessageMapper;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.entity.TradeReview;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.mapper.TradeReviewMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.vo.ReputationVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReputationService 单元测试（A6）—— 校验六维聚合的取值范围与边界。
 */
@ExtendWith(MockitoExtension.class)
class ReputationServiceTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize(TradeOrder.class, TradeOrderMapper.class);
        initialize(TradeReview.class, TradeReviewMapper.class);
        initialize(ChatMessage.class, ChatMessageMapper.class);
        initialize(Item.class, ItemMapper.class);
    }

    @Mock private TradeOrderMapper orderMapper;
    @Mock private TradeReviewMapper reviewMapper;
    @Mock private ChatMessageMapper chatMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private ReputationPenaltyService penaltyService;

    private ReputationService reputationService;

    private static final Long USER_ID = 2L;

    @BeforeEach
    void setUp() {
        reputationService = new ReputationService(
                orderMapper, reviewMapper, chatMapper, itemMapper, userMapper, penaltyService);
        lenient().when(penaltyService.complianceScore(USER_ID)).thenReturn(100);
    }

    @Test
    void brandNewUserGetsNeutralBaseline() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        // 完全没有数据：完成率/响应/好评/准确度给中性基线 60，活跃度为 0
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(reviewMapper.selectList(any())).thenReturn(java.util.List.of());
        when(chatMapper.selectList(any())).thenReturn(java.util.List.of());
        when(itemMapper.selectCount(any())).thenReturn(0L);

        ReputationVO vo = reputationService.compute(USER_ID);

        assertEquals(USER_ID, vo.getUserId());
        assertEquals(60, vo.getCompletionRate());
        assertEquals(60, vo.getResponseSpeed());
        assertEquals(60, vo.getAccuracy());
        assertEquals(60, vo.getPraise());
        assertEquals(0, vo.getActivity());
        assertEquals(100, vo.getCompliance());
        assertEquals(0, vo.getReviewCount());
        assertScoresInRange(vo);
    }

    @Test
    void allDimensionsStayWithinZeroToHundred() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        when(orderMapper.selectCount(any())).thenReturn(50L);
        when(itemMapper.selectCount(any())).thenReturn(200L);
        when(reviewMapper.selectList(any())).thenReturn(java.util.List.of(
                review(5, true), review(5, true), review(4, true)));
        when(chatMapper.selectList(any())).thenReturn(java.util.List.of());

        ReputationVO vo = reputationService.compute(USER_ID);
        assertScoresInRange(vo);
        assertEquals(3, vo.getReviewCount());
        assertEquals(100, vo.getAccuracy()); // 3/3 准确
    }

    @Test
    void inaccurateReviewsLowerAccuracyScore() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectCount(any())).thenReturn(0L);
        when(chatMapper.selectList(any())).thenReturn(java.util.List.of());
        when(reviewMapper.selectList(any())).thenReturn(java.util.List.of(
                review(5, true), review(1, false), review(3, false), review(2, false)));

        ReputationVO vo = reputationService.compute(USER_ID);
        // 1/4 准确 => 25 分
        assertEquals(25, vo.getAccuracy());
        // 平均分 (5+1+3+2)/4 = 2.75 星 => 2.75/5*100 = 55
        assertEquals(55, vo.getPraise());
        assertScoresInRange(vo);
    }

    @Test
    void activePenaltiesOnlyLowerComplianceDimension() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectCount(any())).thenReturn(0L);
        when(chatMapper.selectList(any())).thenReturn(java.util.List.of());
        when(reviewMapper.selectList(any())).thenReturn(java.util.List.of(
                review(5, true), review(4, true)));
        when(penaltyService.complianceScore(USER_ID)).thenReturn(45);

        ReputationVO vo = reputationService.compute(USER_ID);

        assertEquals(100, vo.getAccuracy());
        assertEquals(90, vo.getPraise());
        assertEquals(45, vo.getCompliance());
        assertScoresInRange(vo);
    }

    @Test
    void missingUserIsRejectedBeforeAggregation() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reputationService.compute(USER_ID));

        assertEquals(ResultCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(orderMapper, never()).selectCount(any());
    }

    @Test
    void responseSpeedOnlyUsesItemsPublishedByTargetUser() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(reviewMapper.selectList(any())).thenReturn(java.util.List.of());
        when(itemMapper.selectCount(any())).thenReturn(0L);

        LocalDateTime start = LocalDateTime.of(2026, 7, 24, 10, 0);
        ChatMessage sellerIncoming = message(1L, 9L, USER_ID, 100L, start);
        ChatMessage sellerReply = message(2L, USER_ID, 9L, 100L, start.plusMinutes(60));
        ChatMessage buyerIncoming = message(3L, 8L, USER_ID, 200L, start);
        ChatMessage buyerReply = message(4L, USER_ID, 8L, 200L, start.plusMinutes(5));
        when(chatMapper.selectList(any())).thenReturn(java.util.List.of(
                sellerIncoming, sellerReply, buyerIncoming, buyerReply));

        Item sellerItem = new Item();
        sellerItem.setId(100L);
        sellerItem.setPublisherId(USER_ID);
        when(itemMapper.selectList(any())).thenReturn(java.util.List.of(sellerItem));

        ReputationVO vo = reputationService.compute(USER_ID);

        assertEquals(94, vo.getResponseSpeed());
    }

    private com.zhiyi.module.trade.entity.TradeReview review(int rating, boolean accurate) {
        com.zhiyi.module.trade.entity.TradeReview r =
                new com.zhiyi.module.trade.entity.TradeReview();
        r.setRating(rating);
        r.setAccurate(accurate);
        return r;
    }

    private ChatMessage message(long id, long senderId, long receiverId,
                                long relatedItemId, LocalDateTime createdAt) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setConversationId(Math.min(senderId, receiverId) + "_" + Math.max(senderId, receiverId));
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setRelatedItemId(relatedItemId);
        message.setCreatedAt(createdAt);
        return message;
    }

    private void assertScoresInRange(ReputationVO vo) {
        for (int score : new int[]{vo.getCompletionRate(), vo.getResponseSpeed(),
                vo.getAccuracy(), vo.getPraise(), vo.getActivity(), vo.getCompliance()}) {
            assertTrue(score >= 0 && score <= 100,
                    "维度分值应落在 0-100，实际为 " + score);
        }
    }
}
