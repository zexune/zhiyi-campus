package com.zhiyi.module.social.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyi.module.social.entity.ChatMessage;
import com.zhiyi.module.social.entity.OutboxEvent;
import com.zhiyi.module.social.event.ChatMessageSentEvent;
import com.zhiyi.module.social.mapper.ChatMessageMapper;
import com.zhiyi.module.social.mapper.OutboxEventMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 单事件处理器：独立事务内"领取即处理"。
 *
 * 边界（任一失败都回滚消息与状态并抛出携带数据库主键的事件级异常）：
 * 1. 轮询 SQL 用数据库 CURRENT_TIMESTAMP(6) 判定到期，按 ID 领取一条并 FOR UPDATE SKIP LOCKED；
 * 2. 消息构建、SYSTEM 查询、JSON 解析、消息插入与 SENT 更新全部位于统一异常边界内；
 * 3. source_event_id 重复时，仅当既有消息的事件归属和接收者都一致才视为已投递；
 * 4. SENT 更新带 id=? AND status='PENDING'，影响行数必须为 1。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxProcessor {

    private final OutboxEventMapper outboxMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final SysUserMapper sysUserMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 处理一条到期事件；无事件返回 false。任何事件级失败抛 {@link OutboxProcessException}。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean processOne() {
        OutboxEvent event = outboxMapper.pollOnePending();
        if (event == null) {
            return false;
        }
        try {
            deliver(event);
        } catch (OutboxProcessException eventFailure) {
            throw eventFailure;
        } catch (Exception anyFailure) {
            throw new OutboxProcessException(event.getId(), "处理失败", anyFailure);
        }
        return true;
    }

    private void deliver(OutboxEvent event) {
        JsonNode payload = parsePayload(event);
        long receiverId = payload.path("receiverId").asLong(0);
        String content = payload.path("content").asText("");
        if (receiverId <= 0 || content.isBlank()) {
            throw new OutboxProcessException(event.getId(), "payload 非法", null);
        }

        SysUser system = sysUserMapper.selectSystemUser();
        if (system == null) {
            throw new OutboxProcessException(event.getId(), "SYSTEM 主体缺失", null);
        }
        if (system.getId().equals(receiverId)) {
            throw new OutboxProcessException(event.getId(), "接收者不能是 SYSTEM", null);
        }

        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId(system.getId(), receiverId));
        message.setSenderId(system.getId());
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setIsRead(false);
        message.setSourceEventId(event.getEventId());
        Long messageId;
        try {
            chatMessageMapper.insert(message);
            messageId = message.getId();
        } catch (DuplicateKeyException duplicated) {
            // 已投递过：既有消息承载同一事件，推送事件复用其 ID
            messageId = verifyDuplicateBelongsToEvent(event, message);
        }

        int updated = outboxMapper.markSent(event.getId());
        if (updated != 1) {
            throw new OutboxProcessException(event.getId(), "SENT 条件更新未生效", null);
        }
        eventPublisher.publishEvent(new ChatMessageSentEvent(
                receiverId, system.getId(), message.getConversationId(), messageId));
        log.debug("Outbox 事件已投递 eventId={} receiver={}", event.getEventId(), receiverId);
    }

    /** source_event_id 撞库：仅当既有消息接收者一致才视为已投递，否则进入失败通道。 */
    private Long verifyDuplicateBelongsToEvent(OutboxEvent event, ChatMessage expected) {
        ChatMessage existing = chatMessageMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSourceEventId, event.getEventId()));
        if (existing == null || !existing.getReceiverId().equals(expected.getReceiverId())) {
            throw new OutboxProcessException(event.getId(), "source_event_id 归属不一致", null);
        }
        return existing.getId();
    }

    private JsonNode parsePayload(OutboxEvent event) {
        try {
            return objectMapper.readTree(event.getPayload());
        } catch (Exception exception) {
            throw new OutboxProcessException(event.getId(), "payload 解析失败", exception);
        }
    }

    private String conversationId(Long a, Long b) {
        long left = Math.min(a, b);
        long right = Math.max(a, b);
        return left + "_" + right;
    }
}
