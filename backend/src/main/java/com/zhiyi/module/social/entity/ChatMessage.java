package com.zhiyi.module.social.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationId;
    private Long senderId;
    private Long receiverId;
    private String content;
    private Long relatedItemId;
    private Boolean isRead;
    /** 产生本消息的 Outbox 事件ID（系统消息专用，唯一索引防重复投递）。 */
    private String sourceEventId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
