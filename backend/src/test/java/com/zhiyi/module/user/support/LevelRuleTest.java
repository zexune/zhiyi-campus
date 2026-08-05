package com.zhiyi.module.user.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 等级规则单元测试（需求 1.5 阈值表）
 */
class LevelRuleTest {

    @Test
    void levelThresholds() {
        assertEquals(1, LevelRule.levelOf(0));
        assertEquals(1, LevelRule.levelOf(99));
        assertEquals(2, LevelRule.levelOf(100));
        assertEquals(2, LevelRule.levelOf(299));
        assertEquals(3, LevelRule.levelOf(300));
        assertEquals(3, LevelRule.levelOf(599));
        assertEquals(4, LevelRule.levelOf(600));
        assertEquals(4, LevelRule.levelOf(999));
        assertEquals(5, LevelRule.levelOf(1000));
        assertEquals(5, LevelRule.levelOf(99999));
    }

    @Test
    void titles() {
        assertEquals("初来乍到", LevelRule.titleOf(1));
        assertEquals("校园传奇", LevelRule.titleOf(5));
        // 越界钳制
        assertEquals("初来乍到", LevelRule.titleOf(0));
        assertEquals("校园传奇", LevelRule.titleOf(99));
    }

    @Test
    void nextLevelExp() {
        assertEquals(100, LevelRule.nextLevelExp(1));
        assertEquals(300, LevelRule.nextLevelExp(2));
        assertEquals(600, LevelRule.nextLevelExp(3));
        assertEquals(1000, LevelRule.nextLevelExp(4));
        assertNull(LevelRule.nextLevelExp(5));  // 满级
    }

    @Test
    void currentLevelBaseExp() {
        assertEquals(0, LevelRule.currentLevelBaseExp(1));
        assertEquals(100, LevelRule.currentLevelBaseExp(2));
        assertEquals(1000, LevelRule.currentLevelBaseExp(5));
    }
}
