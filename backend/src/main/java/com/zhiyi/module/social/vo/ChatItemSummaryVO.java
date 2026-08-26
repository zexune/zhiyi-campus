package com.zhiyi.module.social.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ChatItemSummaryVO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;
    /** 商品类型：SWAP 时 price 恒为 null（以物换物不标价），其余类型必有金额 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"SELL", "BUY", "SWAP", "ERRAND"})
    private String type;
    /** SELL/BUY/ERRAND 必有金额；SWAP 恒为显式 null，前端按 (type, price) 渲染 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private BigDecimal price;
    /** 商品无封面图时序列化为显式 null（与 OrderVO.itemCover 同一语义，不使用空字符串） */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String coverImage;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
}
