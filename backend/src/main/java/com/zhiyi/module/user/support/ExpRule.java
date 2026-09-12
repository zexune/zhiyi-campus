package com.zhiyi.module.user.support;

/**
 * 经验事件目录 —— 全部可加经验的来源、分值与限额的唯一真相源。
 *
 * 设计纪律（防刷三原则）：
 * 1. 可重复来源必须有每日上限（dailyCap，0 = 不设限）：刷发布/刷好评的收益封顶；
 * 2. 一次性任务必须带唯一去重键（oneTime）：并发重复领取被 uk_exp_dedup 兜底；
 * 3. 同一买家-卖家对成交的经验收益按历史成交次数衰减（{@link PairDecayRule}）：
 *    真实回购照常成交，只是经验打折，小号互刷在经济学上不成立。
 *
 * 分值口径：卖方是信誉叙事的主体，完成订单卖 40 / 买 15 拉开角色权重；
 * 发布与好评是低信任信号，小分值 + 日上限；首次完善资料是引导性一次性奖励。
 */
public enum ExpRule {

    /** 完成订单（卖方视角）：受回购衰减 */
    ORDER_SELLER(40, 0, false, "完成订单（卖出）"),
    /** 完成订单（买方视角）：受回购衰减 */
    ORDER_BUYER(15, 0, false, "完成订单（买入）"),
    /** 发布商品：每日上限 2 次 */
    ITEM_PUBLISHED(5, 2, false, "发布商品"),
    /** 收到 4-5 星好评：每日上限 3 次 */
    GOOD_REVIEW(10, 3, false, "收到好评"),
    /** 首次完善校园资料（校区/学院/年级/宿舍楼齐全）：一次性 */
    PROFILE_COMPLETED(20, 0, true, "首次完善校园资料");

    /** 基础分值（衰减前） */
    private final int points;
    /** 每日上限次数；0 = 不设限 */
    private final int dailyCap;
    /** 是否一次性任务（按 user + 规则键全局唯一） */
    private final boolean oneTime;
    /** 流水展示文案（exp_log.reason 前缀） */
    private final String label;

    ExpRule(int points, int dailyCap, boolean oneTime, String label) {
        this.points = points;
        this.dailyCap = dailyCap;
        this.oneTime = oneTime;
        this.label = label;
    }

    public int points() {
        return points;
    }

    public int dailyCap() {
        return dailyCap;
    }

    public boolean oneTime() {
        return oneTime;
    }

    public String label() {
        return label;
    }

    /** 一次性任务的全局去重键（uk_exp_dedup 的 dedup_key 取值） */
    public String dedupKey() {
        return name();
    }
}
