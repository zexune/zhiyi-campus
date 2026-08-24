package com.zhiyi.module.social.support;

import com.zhiyi.module.social.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 失败记录器：独立失败事务（REQUIRES_NEW），在 Processor 回滚后运行。
 *
 * - 只修改仍为 PENDING 的事件：单条条件 UPDATE 原子递增 attempts，不丢失计数；
 * - 下一次重试基于数据库时间指数退避；超过上限进入 FAILED 并清除后续领取资格；
 * - 事件已变为 SENT/FAILED 时幂等返回，不倒退状态；
 * - 本记录器自身失败单独记录日志（毒事件可见性），不再向上抛出。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxFailureRecorder {

    private final OutboxEventMapper outboxMapper;

    @Value("${zhiyi.outbox.max-attempts:8}")
    private int maxAttempts;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(long eventId) {
        try {
            int updated = outboxMapper.recordFailure(eventId, maxAttempts);
            if (updated == 0) {
                // 已被并发消费者置为 SENT/FAILED：幂等返回，不倒退状态。
                log.debug("Outbox 失败记录未生效（事件已终态）id={}", eventId);
            } else if (updated == 1) {
                log.warn("Outbox 事件处理失败 id={} attempts 上限={}", eventId, maxAttempts);
            }
        } catch (Exception recorderFailure) {
            // 记录器自身失败必须有独立日志，否则毒事件将持续占据队头而不可见。
            log.error("Outbox 失败记录器异常 id={}", eventId, recorderFailure);
        }
    }
}
