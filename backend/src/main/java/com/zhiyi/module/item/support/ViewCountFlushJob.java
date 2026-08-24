package com.zhiyi.module.item.support;

import com.zhiyi.module.item.service.ViewCountFlushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 浏览量缓冲刷新任务：周期性把增量批量持久化到独立统计表。
 *
 * 失败策略：以同一 flush_id 重试（幂等由凭据行保证，不重复累计）；
 * 持续失败则记录错误并放弃该批次（产品接受损失一个刷新窗口的浏览增量，
 * 持久化滞后通过日志与指标可见）。商品业务行全程无浏览写锁。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountFlushJob {

    private static final int MAX_ATTEMPTS = 3;

    private final ViewCountBuffer buffer;
    private final ViewCountFlushService flushService;

    @Scheduled(fixedDelayString = "${zhiyi.marketplace.view-flush-interval-ms:5000}", initialDelayString = "5000")
    public void flush() {
        Map<Long, Long> snapshot = buffer.swap();
        if (snapshot.isEmpty()) {
            return;
        }
        String flushId = UUID.randomUUID().toString();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                flushService.persist(flushId, snapshot);
                return;
            } catch (Exception failure) {
                log.warn("浏览量刷新失败 attempt={}/{} flushId={} items={}",
                        attempt, MAX_ATTEMPTS, flushId, snapshot.size(), failure);
                // 结果不明时以同一 flush_id 重试：凭据行幂等，不会重复累计
            }
        }
        // 重试耗尽：丢弃批次并保留指标可见性（不 mergeBack，避免与已提交批次重复累计的歧义）
        log.error("浏览量刷新重试耗尽，丢弃批次 flushId={} items={}（已接受的单窗口损失）",
                flushId, snapshot.size());
    }
}
