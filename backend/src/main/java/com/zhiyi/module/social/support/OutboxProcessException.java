package com.zhiyi.module.social.support;

/**
 * 事件级处理失败：携带 outbox_event 数据库主键，
 * 由失败记录器在独立事务中递增 attempts / 计算重试或死信。
 */
public class OutboxProcessException extends RuntimeException {

    private final long eventId;

    public OutboxProcessException(long eventId, String message, Throwable cause) {
        super("outbox_event[" + eventId + "] " + message, cause);
        this.eventId = eventId;
    }

    public long eventId() {
        return eventId;
    }
}
