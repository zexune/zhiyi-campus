package com.zhiyi.module.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 信誉雷达六维分值（A6）—— 每项 0-100，供前端雷达图渲染。
 *
 * 维度对应数据来源：
 * completionRate —— trade_order 完成/(完成+取消)
 * responseSpeed  —— chat_message 首次回复间隔反比
 * accuracy       —— trade_review.accurate 占比
 * praise         —— trade_review.rating 均值折算
 * activity       —— 近 30 天发布 + 成交数
 * compliance     —— 独立有效信誉处罚累计扣分后的合规度
 */
@Data
@AllArgsConstructor
public class ReputationVO {
    private Long userId;
    private int completionRate; // 交易完成率
    private int responseSpeed;  // 响应速度
    private int accuracy;       // 描述准确度
    private int praise;         // 历史好评
    private int activity;       // 活跃度
    private int compliance;     // 合规度
    private int reviewCount;    // 已收到的评价数（前端展示样本量）
}
