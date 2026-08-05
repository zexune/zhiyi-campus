package com.zhiyi.module.user.event;

/**
 * 用户等级提升事件。聊天模块可在事务提交后监听并生成系统消息。
 */
public record UserLevelUpEvent(
        Long userId,
        int oldLevel,
        int newLevel,
        int expAfter
) {
}
