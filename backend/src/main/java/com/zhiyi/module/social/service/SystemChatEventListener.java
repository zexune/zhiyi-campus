package com.zhiyi.module.social.service;

import com.zhiyi.module.user.event.UserLevelUpEvent;
import com.zhiyi.module.user.event.UserPunishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

/**
 * A 模块提交事务后，C 模块写入站内系统消息。
 */
@Component
@RequiredArgsConstructor
public class SystemChatEventListener {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ChatService chatService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLevelUp(UserLevelUpEvent event) {
        chatService.sendSystemMessage(
                event.userId(),
                "恭喜升级到 Lv." + event.newLevel() + "，当前经验 " + event.expAfter() + "。继续保持靠谱交易记录。"
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPunished(UserPunishedEvent event) {
        StringBuilder content = new StringBuilder("你的账号收到平台处理：")
                .append(event.type())
                .append("。原因：")
                .append(event.reason());
        if (event.banUntilTime() != null) {
            content.append("，封禁至 ").append(event.banUntilTime().format(TIME_FORMATTER));
        } else if (event.banDays() != null) {
            content.append("，封禁 ").append(event.banDays()).append(" 天");
        }
        chatService.sendSystemMessage(event.userId(), content.toString());
    }
}
