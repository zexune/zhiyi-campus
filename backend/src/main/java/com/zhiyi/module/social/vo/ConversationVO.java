package com.zhiyi.module.social.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String conversationId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private ChatUserVO peer;
    /** 纯文字会话无关联商品，序列化为显式 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private ChatItemSummaryVO relatedItem;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String lastMessage;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime lastMessageTime;
    /** 会话最后消息 id：客户端 keyset 翻页时作为 beforeMessageId 回传 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long lastMessageId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private long unreadCount;
}
