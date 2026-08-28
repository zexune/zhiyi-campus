package com.zhiyi.module.social.vo;

/**
 * SSE 事件流（/api/chat/stream、/api/admin/chat/stream）event:chat 的 data 负载。
 *
 * 事件只做"变化信号 + 最小定位信息"，消息明细与未读数仍由前端收到事件后
 * 经既有 REST 端点重拉——SSE 不重复承载业务查询语义，契约面最小。
 */
public record ChatEventVO(String type, String conversationId, Long messageId, Long senderId) {

    public static final String TYPE_MESSAGE = "MESSAGE";
    public static final String TYPE_READ = "READ";

    public static ChatEventVO message(String conversationId, Long messageId, Long senderId) {
        return new ChatEventVO(TYPE_MESSAGE, conversationId, messageId, senderId);
    }

    public static ChatEventVO read(String conversationId) {
        return new ChatEventVO(TYPE_READ, conversationId, null, null);
    }
}
