package com.zhiyi.module.social.support;

import com.zhiyi.module.social.event.ChatMessageSentEvent;
import com.zhiyi.module.social.event.ChatReadAckedEvent;
import com.zhiyi.module.social.vo.ChatEventVO;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 聊天事件广播器：路由、摘除与事件派生的纯单元验证。
 * 发送原语（deliverEvent/deliverComment）以记录式子类替换，
 * 避免单测依赖真实 SSE 容器初始化。
 */
class ChatEventBroadcasterTest {

    /** 记录式替身：覆盖发送原语；refuseDelivery 模拟写入失败（发送方负责摘除死连接）。 */
    static class RecordingBroadcaster extends ChatEventBroadcaster {
        final List<String> deliveries = new ArrayList<>();
        boolean refuseDelivery = false;

        @Override
        protected void deliverEvent(Long userId, SseEmitter emitter, ChatEventVO event) {
            if (refuseDelivery) {
                discard(userId, emitter);
                return;
            }
            deliveries.add("event:" + userId + ":" + event.type() + ":"
                    + event.conversationId() + ":" + event.messageId());
        }

        @Override
        protected void deliverHeartbeat(Long userId, SseEmitter emitter) {
            deliveries.add("ping:" + userId);
        }
    }

    @Test
    void messageEventRoutesOnlyToReceiverEmittersIncludingMultipleTabs() {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        broadcaster.connect(1L);
        broadcaster.connect(1L);
        broadcaster.connect(2L);

        broadcaster.onMessageSent(new ChatMessageSentEvent(1L, 2L, "1_2", 10L));

        assertEquals(List.of("event:1:MESSAGE:1_2:10", "event:1:MESSAGE:1_2:10"),
                broadcaster.deliveries);
    }

    @Test
    void messageEventForUserWithoutConnectionIsSilentlySkipped() {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();

        broadcaster.onMessageSent(new ChatMessageSentEvent(9L, 2L, "2_9", 10L));

        assertTrue(broadcaster.deliveries.isEmpty());
    }

    @Test
    void readEventNotifiesConversationPeerOfReader() {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        broadcaster.connect(1L);
        broadcaster.connect(2L);

        broadcaster.onReadAcked(new ChatReadAckedEvent(2L, "1_2"));

        assertEquals(List.of("event:1:READ:1_2:null"), broadcaster.deliveries);
    }

    @Test
    void readEventWithMalformedConversationIdIsIgnoredSafely() {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        broadcaster.connect(1L);

        broadcaster.onReadAcked(new ChatReadAckedEvent(1L, "abc"));
        broadcaster.onReadAcked(new ChatReadAckedEvent(1L, "1_x"));

        assertTrue(broadcaster.deliveries.isEmpty());
    }

    @Test
    void failedDeliveryDiscardsEmitterSoSubsequentBroadcastsSkipIt() {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        broadcaster.connect(1L);

        broadcaster.refuseDelivery = true;
        broadcaster.onMessageSent(new ChatMessageSentEvent(1L, 2L, "1_2", 10L));

        broadcaster.refuseDelivery = false;
        broadcaster.onMessageSent(new ChatMessageSentEvent(1L, 2L, "1_2", 11L));

        assertTrue(broadcaster.deliveries.isEmpty());
    }

    @Test
    void heartbeatReachesEveryRegisteredUserAsNamedPingEvent() {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        broadcaster.connect(1L);
        broadcaster.connect(2L);

        broadcaster.heartbeat();

        List<String> sorted = broadcaster.deliveries.stream().sorted().toList();
        assertEquals(List.of("ping:1", "ping:2"), sorted);
    }

    @Test
    void realSendFailureOnCompletedEmitterDoesNotPropagate() {
        ChatEventBroadcaster broadcaster = new ChatEventBroadcaster();
        SseEmitter emitter = broadcaster.connect(1L);
        // 容器外 send 仅缓冲不落网络；complete 后再 send 必然抛 IllegalStateException，
        // 用于驱动真实失败路径（摘除 + 二次 complete 不外抛）
        emitter.complete();

        assertDoesNotThrow(() ->
                broadcaster.onMessageSent(new ChatMessageSentEvent(1L, 2L, "1_2", 10L)));
    }
}
