package com.zhiyi.module.social.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatSendDTO {
    private String conversationId;

    @NotNull(message = "接收者不能为空")
    private Long receiverId;

    private Long relatedItemId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 1000, message = "消息不能超过1000字")
    private String content;
}
