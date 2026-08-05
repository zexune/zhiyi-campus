package com.zhiyi.module.social.vo;

import lombok.Data;

import java.util.List;

@Data
public class ChatThreadVO {
    private String conversationId;
    private ChatUserVO peer;
    private ChatItemSummaryVO relatedItem;
    private List<ChatMessageVO> messages;
}
