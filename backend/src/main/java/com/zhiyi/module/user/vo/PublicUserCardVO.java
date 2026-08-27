package com.zhiyi.module.user.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 公开用户名片 —— 供商品详情和聊天展示，只包含允许匿名访问的字段。
 */
@Data
@AllArgsConstructor
public class PublicUserCardVO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickname;
    /** 未上传自定义头像时序列化为显式 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String avatar;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer level;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String levelTitle;
    /** 学校名称（A8：公开名片展示归属校，未绑定为 null） */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String schoolName;
}
