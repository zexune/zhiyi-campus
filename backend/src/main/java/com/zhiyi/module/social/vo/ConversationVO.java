package com.zhiyi.module.social.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {
    private String conversationId;
    private ChatUserVO peer;
    private ChatItemSummaryVO relatedItem;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private long unreadCount;
}
