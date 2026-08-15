package com.zhiyi.module.social.dto;

import lombok.Data;

/**
 * 会话级聚合投影 —— 由 ChatMessageMapper#aggregateConversations 的 GROUP BY 查询填充，
 * 每个会话一行（最后消息、对端、关联商品、未读数），替代把用户全部历史消息拉进内存聚合。
 */
@Data
public class ConversationAggregate {

    private String conversationId;
    private Long lastMessageId;
    private Long peerId;
    private Long relatedItemId;
    private Long unreadCount;
}
