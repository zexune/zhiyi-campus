package com.zhiyi.module.social.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox 调度器：无业务事务，循环调用单事件处理器。
 *
 * - 事件级失败（{@link OutboxProcessException}）由 {@link OutboxFailureRecorder}
 *   在独立事务中记录后继续处理下一条，毒事件不阻塞队列；
 * - 只有无法识别具体事件的基础设施错误（如数据库不可用）才终止本轮。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxProcessor processor;
    private final OutboxFailureRecorder failureRecorder;

    @Value("${zhiyi.outbox.batch-size:20}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${zhiyi.outbox.poll-interval-ms:1000}", initialDelayString = "3000")
    public void drainPendingEvents() {
        for (int processed = 0; processed < batchSize; processed++) {
            boolean hadEvent;
            try {
                hadEvent = processor.processOne();
            } catch (OutboxProcessException eventFailure) {
                failureRecorder.recordFailure(eventFailure.eventId());
                continue;
            } catch (Exception infrastructureFailure) {
                // 基础设施级错误：终止本轮，等待下一个调度周期。
                log.warn("Outbox 调度轮基础设施错误", infrastructureFailure);
                return;
            }
            if (!hadEvent) {
                return;
            }
        }
    }
}
