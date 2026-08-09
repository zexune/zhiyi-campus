package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.OrderStatus;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.social.entity.ChatMessage;
import com.zhiyi.module.social.mapper.ChatMessageMapper;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.entity.TradeReview;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.mapper.TradeReviewMapper;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.vo.ReputationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 信誉雷达聚合服务（A6）—— 把交易、评价、聊天和独立处罚记录折算成六维 0-100 分值。
 *
 * 无交易/评价数据时相关维度给中性基线（60）；无有效处罚时合规度为 100。
 */
@Service
@RequiredArgsConstructor
public class ReputationService {

    /** 无样本时的中性基线 */
    private static final int NEUTRAL_BASELINE = 60;
    /** 活跃度统计窗口 */
    private static final int ACTIVITY_WINDOW_DAYS = 30;

    private final TradeOrderMapper orderMapper;
    private final TradeReviewMapper reviewMapper;
    private final ChatMessageMapper chatMapper;
    private final ItemMapper itemMapper;
    private final SysUserMapper userMapper;
    private final ReputationPenaltyService penaltyService;

    public ReputationVO compute(Long userId) {
        if (userId == null || userMapper.selectById(userId) == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        int completionRate = completionRate(userId);
        int responseSpeed = responseSpeed(userId);

        List<TradeReview> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<TradeReview>().eq(TradeReview::getTargetId, userId));
        int accuracy = accuracy(reviews);
        int praise = praise(reviews);
        int activity = activity(userId);
        int compliance = penaltyService.complianceScore(userId);

        return new ReputationVO(userId, completionRate, responseSpeed,
                accuracy, praise, activity, compliance, reviews.size());
    }

    /** 交易完成率：completed / (completed + cancelled)，无单给基线。 */
    private int completionRate(Long userId) {
        long completed = orderMapper.selectCount(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getSellerId, userId)
                .eq(TradeOrder::getStatus, OrderStatus.COMPLETED));
        long cancelled = orderMapper.selectCount(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getSellerId, userId)
                .eq(TradeOrder::getStatus, OrderStatus.CANCELLED));
        long total = completed + cancelled;
        if (total == 0) {
            return NEUTRAL_BASELINE;
        }
        return clamp((int) Math.round(completed * 100.0 / total));
    }

    /** 描述准确度：accurate 评价占比，无评价给基线。 */
    private int accuracy(List<TradeReview> reviews) {
        if (reviews.isEmpty()) {
            return NEUTRAL_BASELINE;
        }
        long accurate = reviews.stream().filter(r -> Boolean.TRUE.equals(r.getAccurate())).count();
        return clamp((int) Math.round(accurate * 100.0 / reviews.size()));
    }

    /** 历史好评：平均星级折算百分制，无评价给基线。 */
    private int praise(List<TradeReview> reviews) {
        if (reviews.isEmpty()) {
            return NEUTRAL_BASELINE;
        }
        double avg = reviews.stream().mapToInt(TradeReview::getRating).average().orElse(0);
        return clamp((int) Math.round(avg / 5.0 * 100));
    }

    /** 活跃度：近 30 天发布数 + 成交数，封顶 100。 */
    private int activity(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(ACTIVITY_WINDOW_DAYS);
        long published = itemMapper.selectCount(new LambdaQueryWrapper<Item>()
                .eq(Item::getPublisherId, userId)
                .ge(Item::getCreatedAt, since));
        long traded = orderMapper.selectCount(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getSellerId, userId)
                .eq(TradeOrder::getStatus, OrderStatus.COMPLETED)
                .ge(TradeOrder::getCompletedAt, since));
        return clamp((int) Math.min(100, published + traded));
    }

    /**
     * 响应速度：买家消息到卖家首次回复的平均间隔取反比。
     * 无对话样本给基线；间隔越短分越高（≤5min≈满分，≥12h≈低分）。
     */
    private int responseSpeed(Long userId) {
        List<ChatMessage> messages = chatMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .and(w -> w.eq(ChatMessage::getSenderId, userId)
                        .or().eq(ChatMessage::getReceiverId, userId)));
        if (messages.isEmpty()) {
            return NEUTRAL_BASELINE;
        }
        Set<Long> relatedItemIds = new HashSet<>();
        for (ChatMessage message : messages) {
            if (message.getRelatedItemId() != null) {
                relatedItemIds.add(message.getRelatedItemId());
            }
        }
        if (relatedItemIds.isEmpty()) {
            return NEUTRAL_BASELINE;
        }

        // 只保留该用户作为发布者的商品会话，排除其作为买家时的聊天和客服会话。
        List<Item> sellerItems = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                .in(Item::getId, relatedItemIds)
                .eq(Item::getPublisherId, userId));
        Set<Long> sellerItemIds = new HashSet<>();
        for (Item item : sellerItems) {
            sellerItemIds.add(item.getId());
        }
        if (sellerItemIds.isEmpty()) {
            return NEUTRAL_BASELINE;
        }

        List<ChatMessage> sellerMessages = messages.stream()
                .filter(message -> sellerItemIds.contains(message.getRelatedItemId()))
                .toList();
        List<Long> replyGaps = firstReplyGapsMinutes(userId, sellerMessages);
        if (replyGaps.isEmpty()) {
            return NEUTRAL_BASELINE;
        }
        double avgMinutes = replyGaps.stream().mapToLong(Long::longValue).average().orElse(0);
        // 5 分钟内视为满分，之后线性衰减，12 小时(720min)以上降到 20 分。
        if (avgMinutes <= 5) {
            return 100;
        }
        int score = (int) Math.round(100 - (avgMinutes - 5) / 715.0 * 80);
        return clamp(score);
    }

    /** 逐会话找出"收到买家消息 → 卖家首次回复"的分钟间隔。 */
    private List<Long> firstReplyGapsMinutes(Long userId, List<ChatMessage> messages) {
        Map<ConversationItemKey, List<ChatMessage>> byConversation = new HashMap<>();
        for (ChatMessage m : messages) {
            ConversationItemKey key = new ConversationItemKey(m.getConversationId(), m.getRelatedItemId());
            byConversation.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }
        List<Long> gaps = new ArrayList<>();
        for (List<ChatMessage> convo : byConversation.values()) {
            convo.sort(Comparator.comparing(ChatMessage::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            LocalDateTime pendingIncoming = null;
            for (ChatMessage m : convo) {
                boolean incoming = Objects.equals(userId, m.getReceiverId());
                boolean outgoing = Objects.equals(userId, m.getSenderId());
                if (incoming && pendingIncoming == null && m.getCreatedAt() != null) {
                    pendingIncoming = m.getCreatedAt();
                } else if (outgoing && pendingIncoming != null && m.getCreatedAt() != null) {
                    gaps.add(Duration.between(pendingIncoming, m.getCreatedAt()).toMinutes());
                    pendingIncoming = null;
                }
            }
        }
        return gaps;
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private record ConversationItemKey(String conversationId, Long relatedItemId) {
    }
}
