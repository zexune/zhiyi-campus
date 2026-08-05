package com.zhiyi.module.social.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageVO {
    private Long id;
    private String conversationId;
    private Long senderId;
    private Long receiverId;
    private String content;
    private Long relatedItemId;
    private Boolean isRead;
    private Boolean mine;
    private LocalDateTime createdAt;
}
