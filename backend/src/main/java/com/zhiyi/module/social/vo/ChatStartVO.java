package com.zhiyi.module.social.vo;

import lombok.Data;

@Data
public class ChatStartVO {
    private String conversationId;
    private ChatUserVO peer;
    private ChatItemSummaryVO relatedItem;
}
