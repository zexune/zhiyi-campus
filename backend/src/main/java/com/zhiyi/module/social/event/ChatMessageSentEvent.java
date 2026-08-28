package com.zhiyi.module.social.event;

/**
 * 领域事件：一条聊天消息已落库（用户发送 / 管理端回复 / Outbox 系统通知共用）。
 *
 * 由业务事务内通过 ApplicationEventPublisher 发布，经
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 在提交后消费：
 * SSE 推送绝不能先于数据库提交可见，否则客户端收到事件后重拉会读不到消息，
 * 且该消息在下次触发前将永远丢失。事务回滚时不推送。
 */
public record ChatMessageSentEvent(
        Long receiverId,
        Long senderId,
        String conversationId,
        Long messageId) {
}
