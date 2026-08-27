package com.zhiyi.module.social.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatUserVO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickname;
    /** 未上传自定义头像时序列化为显式 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String avatar;
    private Integer level;
    private String levelTitle;
}
