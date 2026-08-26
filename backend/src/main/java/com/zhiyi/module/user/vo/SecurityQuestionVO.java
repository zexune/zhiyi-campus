package com.zhiyi.module.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 密保问题查询响应（替代曾经的 Map&lt;String,String&gt; 弱类型响应）。 */
@Data
@AllArgsConstructor
public class SecurityQuestionVO {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "该学号预设的密保问题")
    private String question;
}
