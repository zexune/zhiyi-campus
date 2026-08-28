package com.zhiyi.module.social.event;

/**
 * 领域事件：会话已读状态变化（ackRead 标记了接收方的未读消息）。
 *
 * 推送对象由消费方从 conversationId 推导——被标记为已读的消息全部来自
 * 对端（会话严格双方），因此"你的消息已被读"应通知对端。同样只在
 * 事务提交后消费，回滚不推送。
 */
public record ChatReadAckedEvent(
        Long readerId,
        String conversationId) {
}
