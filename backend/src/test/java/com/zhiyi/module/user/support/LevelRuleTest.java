package com.zhiyi.module.user.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 等级规则单元测试（10 级平滑递增曲线：60/120/200/280/360/440/520/600/680 增量）
 */
class LevelRuleTest {

    @Test
    void levelThresholds() {
        assertEquals(1, LevelRule.levelOf(0));
        assertEquals(1, LevelRule.levelOf(59));
        assertEquals(2, LevelRule.levelOf(60));
        assertEquals(2, LevelRule.levelOf(179));
        assertEquals(3, LevelRule.levelOf(180));
        assertEquals(4, LevelRule.levelOf(380));
        assertEquals(5, LevelRule.levelOf(660));
        assertEquals(6, LevelRule.levelOf(1020));
        assertEquals(7, LevelRule.levelOf(1460));
        assertEquals(8, LevelRule.levelOf(1980));
        assertEquals(9, LevelRule.levelOf(2580));
        assertEquals(10, LevelRule.levelOf(3260));
        assertEquals(10, LevelRule.levelOf(99999));
    }

    @Test
    void titles() {
        assertEquals("初来乍到", LevelRule.titleOf(1));
        assertEquals("值得信赖", LevelRule.titleOf(5));
        assertEquals("校园传奇", LevelRule.titleOf(10));
        // 越界钳制
        assertEquals("初来乍到", LevelRule.titleOf(0));
        assertEquals("校园传奇", LevelRule.titleOf(99));
    }

    @Test
    void nextLevelExp() {
        assertEquals(60, LevelRule.nextLevelExp(1));
        assertEquals(180, LevelRule.nextLevelExp(2));
        assertEquals(1020, LevelRule.nextLevelExp(5));
        assertEquals(3260, LevelRule.nextLevelExp(9));
        assertNull(LevelRule.nextLevelExp(10));  // 满级
    }

    @Test
    void currentLevelBaseExp() {
        assertEquals(0, LevelRule.currentLevelBaseExp(1));
        assertEquals(60, LevelRule.currentLevelBaseExp(2));
        assertEquals(660, LevelRule.currentLevelBaseExp(5));
        assertEquals(3260, LevelRule.currentLevelBaseExp(10));
    }

    @Test
    void curveIsMonotonicallyIncreasingWithGrowingSteps() {
        for (int lv = 2; lv <= LevelRule.MAX_LEVEL; lv++) {
            int current = LevelRule.currentLevelBaseExp(lv);
            int previous = LevelRule.currentLevelBaseExp(lv - 1);
            assertTrue(current > previous, "Lv." + lv + " 阈值必须高于上一级");
        }
        // 相邻增量逐级递增（平滑加速的成长曲线）
        for (int lv = 3; lv <= LevelRule.MAX_LEVEL; lv++) {
            int step = LevelRule.currentLevelBaseExp(lv) - LevelRule.currentLevelBaseExp(lv - 1);
            int previousStep = LevelRule.currentLevelBaseExp(lv - 1) - LevelRule.currentLevelBaseExp(lv - 2);
            assertTrue(step > previousStep, "Lv." + lv + " 增量必须大于上一段增量");
        }
    }
}
