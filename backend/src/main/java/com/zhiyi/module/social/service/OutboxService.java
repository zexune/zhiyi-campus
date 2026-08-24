package com.zhiyi.module.social.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyi.module.social.entity.OutboxEvent;
import com.zhiyi.module.social.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 事务 Outbox 生产者：业务数据与事件在同一事务写入。
 *
 * 调用方必须在 @Transactional 业务事务内调用 append；提交前事件不可见，
 * 回滚时事件随业务一并消失，杜绝"AFTER_COMMIT 新开事务连锁失败"与"通知先于业务可见"两类问题。
 *
 * event_id 由生产者按业务唯一性生成（确定性构造）；同 event_id 重复追加视为
 * 同一逻辑通知的幂等复用，静默跳过。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    public static final String AGGREGATE_USER = "USER";
    public static final String AGGREGATE_ORDER = "ORDER";

    public static final String EVENT_USER_PUNISHED = "USER_PUNISHED";
    public static final String EVENT_USER_LEVEL_UP = "USER_LEVEL_UP";
    public static final String EVENT_ORDER_COMPLETED = "ORDER_COMPLETED";
    public static final String EVENT_ORDER_AUTO_CANCELLED = "ORDER_AUTO_CANCELLED";
    public static final String EVENT_ORDER_ADMIN_FORCE_CANCELLED = "ORDER_ADMIN_FORCE_CANCELLED";

    private final OutboxEventMapper outboxMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 追加一条面向单个接收者的系统通知事件。
     * 一条面向一个接收者的消息对应一个 event_id；买卖双方通知禁止共享 event_id。
     */
    public void appendNotice(String eventId,
                             String aggregateType,
                             Long aggregateId,
                             String eventType,
                             Long receiverId,
                             String content) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(toJson(receiverId, content));
        event.setStatus("PENDING");
        event.setAttempts(0);
        try {
            outboxMapper.insert(event);
        } catch (DuplicateKeyException alreadyAppended) {
            // 确定性 event_id 重复：同一逻辑通知已随先前事务落库，幂等跳过。
            log.info("Outbox 事件已存在，幂等跳过 eventId={}", eventId);
        }
    }

    private String toJson(Long receiverId, String content) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "receiverId", receiverId,
                    "content", content));
        } catch (Exception exception) {
            throw new IllegalStateException("Outbox payload serialization failed", exception);
        }
    }
}
