package com.zhiyi.module.social.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ChatStartVO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String conversationId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private ChatUserVO peer;
    /** 会话可无关联商品（客服会话），序列化为显式 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private ChatItemSummaryVO relatedItem;
}
