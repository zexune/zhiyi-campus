package com.zhiyi.module.social.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class ChatThreadVO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String conversationId;
    private ChatUserVO peer;
    /** 客服等会话无关联商品，序列化为显式 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private ChatItemSummaryVO relatedItem;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ChatMessageVO> messages;
    /** 是否还有更早的消息（keyset 分页），前端据此展示"加载更早"。 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean hasMore;
}
