package com.zhiyi.module.social.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageVO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String conversationId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long senderId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long receiverId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
    /** 与会话绑定后恒有值；系统兼容消息可为 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Long relatedItemId;
    /** 历史消息不携带已读态 */
    private Boolean isRead;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean mine;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;
}
