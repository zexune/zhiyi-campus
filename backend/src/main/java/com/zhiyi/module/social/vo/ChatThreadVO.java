package com.zhiyi.module.social.vo;

import lombok.Data;

import java.util.List;

@Data
public class ChatThreadVO {
    private String conversationId;
    private ChatUserVO peer;
    private ChatItemSummaryVO relatedItem;
    private List<ChatMessageVO> messages;
    /** 是否还有更早的消息（keyset 分页），前端据此展示"加载更早"。 */
    private Boolean hasMore;
}
