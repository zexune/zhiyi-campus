package com.zhiyi.module.trade.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 交易评价请求体（A7）。
 */
@Data
public class ReviewDTO {

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低 1 星")
    @Max(value = 5, message = "评分最高 5 星")
    private Integer rating;

    /** 描述是否与实物相符，默认相符 */
    private Boolean accurate = Boolean.TRUE;

    @Size(max = 200, message = "评价内容最多 200 字")
    private String comment;
}
