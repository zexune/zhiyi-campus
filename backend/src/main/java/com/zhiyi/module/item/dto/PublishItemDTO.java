package com.zhiyi.module.item.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PublishItemDTO {
    @NotBlank(message = "发布类型不能为空")
    @Pattern(regexp = "SELL|BUY|SWAP|ERRAND", message = "发布类型只能是 SELL、BUY、SWAP 或 ERRAND")
    private String type;

    @NotBlank(message = "标题不能为空")
    @Size(min = 2, max = 50, message = "标题需为2-50字")
    private String title;

    @NotBlank(message = "描述不能为空")
    @Size(max = 500, message = "描述不能超过500字")
    private String description;

    @NotNull(message = "所属大类不能为空")
    private Long categoryId;

    @DecimalMin(value = "0.01", message = "价格不能低于0.01")
    @Digits(integer = 8, fraction = 2, message = "价格最多8位整数和2位小数")
    private BigDecimal price;

    @NotEmpty(message = "至少上传1张图片")
    @Size(max = 9, message = "最多上传9张图片")
    private List<@NotBlank(message = "图片地址不能为空") String> images;

    // 交易地点：ERRAND 类型不需要（有 pickup/delivery 代替），其余类型必填
    @Size(max = 255, message = "交易地点不能超过255字")
    private String tradeLocation;

    @Size(max = 255, message = "取件地点不能超过255字")
    private String pickupLocation;

    @Size(max = 255, message = "送达地点不能超过255字")
    private String deliveryLocation;

    private LocalDateTime deadlineTime;

    @AssertTrue(message = "发布类型对应的价格、取送地点或截止时间不符合要求")
    public boolean isTypeDetailsValid() {
        if ("SWAP".equals(type)) {
            return price == null;
        }
        if ("ERRAND".equals(type)) {
            // ERRAND：悬赏 ¥1-20，无需 tradeLocation（以 pickup/delivery 代替）
            return price != null
                    && price.compareTo(BigDecimal.ONE) >= 0
                    && price.compareTo(new BigDecimal("20")) <= 0
                    && pickupLocation != null && !pickupLocation.isBlank()
                    && deliveryLocation != null && !deliveryLocation.isBlank()
                    && deadlineTime != null && deadlineTime.isAfter(LocalDateTime.now());
        }
        // SELL / BUY：tradeLocation 必填
        return price != null
                && tradeLocation != null && !tradeLocation.isBlank()
                && (deadlineTime == null || deadlineTime.isAfter(LocalDateTime.now()));
    }
}
