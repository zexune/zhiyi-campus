package com.zhiyi.module.social.support;

import com.zhiyi.module.social.event.ChatMessageSentEvent;
import com.zhiyi.module.social.event.ChatReadAckedEvent;
import com.zhiyi.module.social.vo.ChatEventVO;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天事件 SSE 广播器：按登录用户维护 emitter（同一用户多标签页多条连接），
 * 在业务事务提交后把"新消息 / 已读状态变化"以 event:chat 主动推给浏览器，
 * 替代前端定时轮询。
 *
 * 可靠性边界：
 * - 事件只携带变化信号与最小定位信息，客户端收到后重拉既有 REST 端点，
 *   因此连接断开期间的漏推由重连（open / 恢复可见）后的整段重拉兜底，
 *   不需要服务端补发窗口；
 * - 推送发生在 AFTER_COMMIT，客户端重拉时消息必定可见；
 * - 心跳为具名 ping 事件（客户端可探活静默断流），写失败即回收死连接；
 *   单连接超过 timeout 由容器触发 onTimeout 摘除，浏览器 EventSource 自动重连。
 */
@Slf4j
@Component
public class ChatEventBroadcaster {

    private static final String CHAT_EVENT_NAME = "chat";
    /** 建议浏览器断线后的重连节奏（毫秒），随 ready 事件下发。 */
    private static final long RECONNECT_TIME_MS = 3000;

    private final Map<Long, Set<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    @Value("${zhiyi.sse.timeout-ms:1800000}")
    private long timeoutMillis;

    /** 建立 SSE 连接：注册到当前用户名下，立即下发 ready（含重连节奏）。 */
    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        emittersByUser.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> discard(userId, emitter));
        emitter.onTimeout(() -> discard(userId, emitter));
        emitter.onError(error -> discard(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("ready").reconnectTime(RECONNECT_TIME_MS));
        } catch (IOException | IllegalStateException startupFailure) {
            // 握手即失败（连接已断/已初始化前异常）：直接摘除，交由客户端重连
            discard(userId, emitter);
        }
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(ChatMessageSentEvent event) {
        broadcast(event.receiverId(),
                ChatEventVO.message(event.conversationId(), event.messageId(), event.senderId()));
    }

    /** 已读变化的受影响方是被读消息的发送者（会话严格双方，即会话对端）。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReadAcked(ChatReadAckedEvent event) {
        peerOf(event).ifPresent(peerId -> broadcast(peerId, ChatEventVO.read(event.conversationId())));
    }

    private Optional<Long> peerOf(ChatReadAckedEvent event) {
        String[] ids = event.conversationId().split("_");
        if (ids.length != 2) {
            log.warn("READ 事件会话 ID 无法解析对端 conversationId={}", event.conversationId());
            return Optional.empty();
        }
        try {
            long left = Long.parseLong(ids[0]);
            long right = Long.parseLong(ids[1]);
            return Optional.of(left == event.readerId() ? right : left);
        } catch (NumberFormatException malformed) {
            log.warn("READ 事件会话 ID 无法解析对端 conversationId={}", event.conversationId());
            return Optional.empty();
        }
    }

    private void broadcast(Long userId, ChatEventVO payload) {
        Set<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : List.copyOf(emitters)) {
            deliverEvent(userId, emitter, payload);
        }
    }

    /** 心跳：具名 ping 事件（客户端可监听并据此探活）；写失败即回收死连接。 */
    @Scheduled(fixedDelayString = "${zhiyi.sse.heartbeat-interval-ms:20000}")
    public void heartbeat() {
        for (Map.Entry<Long, Set<SseEmitter>> entry : emittersByUser.entrySet()) {
            for (SseEmitter emitter : List.copyOf(entry.getValue())) {
                deliverHeartbeat(entry.getKey(), emitter);
            }
        }
    }

    /** 优雅停机：主动断开全部连接，浏览器随即按重连节奏迁移到存活实例。 */
    @PreDestroy
    public void shutdown() {
        emittersByUser.values().forEach(emitters -> emitters.forEach(this::completeQuietly));
        emittersByUser.clear();
    }

    /** 发送失败即摘除：等待心跳/下一次推送再次探活的半开连接没有保留价值。 */
    protected void deliverEvent(Long userId, SseEmitter emitter, ChatEventVO event) {
        send(userId, emitter, SseEmitter.event().name(CHAT_EVENT_NAME).data(event));
    }

    /**
     * 心跳用具名 ping 事件而非注释行：注释行会被客户端按规范丢弃、JS 无感知，
     * 半开连接（服务端写入进内核缓冲一直成功、数据永远到不了浏览器）只能等
     * timeout 兜底；具名事件让前端可做"超时未收到即主动重连"的探活。
     */
    protected void deliverHeartbeat(Long userId, SseEmitter emitter) {
        send(userId, emitter, SseEmitter.event().name("ping"));
    }

    private void send(Long userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        // SseEmitter 非线程安全：同一 emitter 的推送（事件/心跳）串行化
        synchronized (emitter) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException deliveryFailure) {
                discard(userId, emitter);
                completeQuietly(emitter);
            }
        }
    }

    /** 摘除连接（protected 仅为单测可模拟发送失败路径）。 */
    protected void discard(Long userId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(userId, emitters);
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception alreadyDead) {
            // 连接已失效时 complete 可能再次抛出，忽略
        }
    }
}
