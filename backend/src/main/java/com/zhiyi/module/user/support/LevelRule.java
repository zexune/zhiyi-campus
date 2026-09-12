package com.zhiyi.module.user.support;

/**
 * 等级规则 —— 经验值阈值与称号（需求 1.5）
 *
 * 10 级平滑递增曲线：相邻阈值增量从 60 逐级 +80（60/120/200/280/360/440/520/600/680），
 * 满级 3260 ≈ 纯卖家收益约 70 单，或混合来源（订单 + 发布 + 好评 + 资料）约 40-50 单。
 * 多来源经验目录（{@link ExpRule}）使单一刷单路径无法快速满级。
 *
 * | 等级  | 累计经验 | 称号     |
 * | Lv.1  | 0       | 初来乍到 |
 * | Lv.2  | 60      | 校园新面孔 |
 * | Lv.3  | 180     | 小有信誉 |
 * | Lv.4  | 380     | 渐入佳境 |
 * | Lv.5  | 660     | 值得信赖 |
 * | Lv.6  | 1020    | 口碑渐起 |
 * | Lv.7  | 1460    | 交易熟手 |
 * | Lv.8  | 1980    | 交易达人 |
 * | Lv.9  | 2580    | 声名远扬 |
 * | Lv.10 | 3260    | 校园传奇 |
 */
public final class LevelRule {

    public static final int MAX_LEVEL = 10;
    private static final int[] THRESHOLDS = {0, 60, 180, 380, 660, 1020, 1460, 1980, 2580, 3260};
    private static final String[] TITLES = {
            "初来乍到", "校园新面孔", "小有信誉", "渐入佳境", "值得信赖",
            "口碑渐起", "交易熟手", "交易达人", "声名远扬", "校园传奇"
    };

    private LevelRule() {
    }

    /** 根据累计经验计算等级 */
    public static int levelOf(int exp) {
        for (int lv = MAX_LEVEL; lv >= 1; lv--) {
            if (exp >= THRESHOLDS[lv - 1]) {
                return lv;
            }
        }
        return 1;
    }

    /** 等级称号 */
    public static String titleOf(int level) {
        int lv = Math.min(Math.max(level, 1), MAX_LEVEL);
        return TITLES[lv - 1];
    }

    /** 下一级所需累计经验；已满级返回 null */
    public static Integer nextLevelExp(int level) {
        if (level >= MAX_LEVEL) {
            return null;
        }
        return THRESHOLDS[level];
    }

    /** 当前等级起点经验（用于前端进度条） */
    public static int currentLevelBaseExp(int level) {
        int lv = Math.min(Math.max(level, 1), MAX_LEVEL);
        return THRESHOLDS[lv - 1];
    }
}
