package com.zhiyi.module.user.support;

/**
 * 同一买家-卖家对成交的经验衰减规则（防小号互刷）。
 *
 * 第 1 次成交全额；第 2-5 次回购打 5 折；第 6 次起打 2 折。
 * 衰减只作用于经验收益，不影响交易本身、信誉雷达或流水——
 * 频繁回购是真实关系时理应继续，但成长体系不再为它付费。
 */
public final class PairDecayRule {

    private PairDecayRule() {
    }

    /**
     * @param completedCount 该买家-卖家对累计已完成订单数（含本次）
     * @return 经验收益系数：1.0 / 0.5 / 0.2
     */
    public static double factorOf(long completedCount) {
        if (completedCount <= 1) {
            return 1.0;
        }
        if (completedCount <= 5) {
            return 0.5;
        }
        return 0.2;
    }
}
