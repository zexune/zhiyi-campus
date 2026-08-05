package com.zhiyi.module.user.event;

import java.time.LocalDateTime;

/**
 * 用户受到处罚事件。聊天模块可在事务提交后监听并生成系统消息。
 */
public record UserPunishedEvent(
        Long userId,
        String type,
        String reason,
        Integer banDays,
        LocalDateTime banUntilTime
) {
}
