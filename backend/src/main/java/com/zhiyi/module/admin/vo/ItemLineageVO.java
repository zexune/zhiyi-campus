package com.zhiyi.module.admin.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品传承链响应体（D3）
 */
@Data
public class ItemLineageVO {
    private Long itemId;
    private String itemTitle;
    /** 传承链节点（从发布者到最后一任买家） */
    private List<LineageNode> chain;

    @Data
    public static class LineageNode {
        /** 用户ID */
        private Long userId;
        /** 用户昵称 */
        private String nickname;
        /** 角色标签：PUBLISHER 发布者 / BUYER 买家 */
        private String role;
        /** 成交价（发布者为 null） */
        private BigDecimal price;
        /** 成交时间（发布者为发布时间） */
        private LocalDateTime time;
    }
}
