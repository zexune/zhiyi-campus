package com.zhiyi.module.user.support;

/**
 * 等级规则 —— 经验值阈值与称号（需求 1.5）
 *
 * | 等级 | 累计经验 | 称号     |
 * | Lv.1 | 0       | 初来乍到 |
 * | Lv.2 | 100     | 小有信誉 |
 * | Lv.3 | 300     | 值得信赖 |
 * | Lv.4 | 600     | 交易达人 |
 * | Lv.5 | 1000    | 校园传奇 |
 */
public final class LevelRule {

    public static final int MAX_LEVEL = 5;
    private static final int[] THRESHOLDS = {0, 100, 300, 600, 1000};
    private static final String[] TITLES = {"初来乍到", "小有信誉", "值得信赖", "交易达人", "校园传奇"};

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
