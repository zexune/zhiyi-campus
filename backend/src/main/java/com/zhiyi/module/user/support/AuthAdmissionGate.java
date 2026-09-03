package com.zhiyi.module.user.support;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 认证端点事务外有界准入闸门（负载保护，不承担正确性职责）。
 *
 * 登录/密保重置/注册都含 BCrypt 校验或哈希（cost 10，单次约几十毫秒纯 CPU）。
 * 压测证明：当并发密码学运算把 CPU 打满时，持连接的请求排队变慢，会级联
 * 耗尽 Hikari 池并拖垮全站。闸门在触碰任何数据库之前限流并发认证数，
 * 超限请求快速返回 AUTH_BUSY（429 + Retry-After）而非排队超时 500：
 *
 * - 容量：slots 配置为 0/负数时按 2×CPU 核数自动推导（认证吞吐受 CPU 限制，
 *   闸门只防 CPU 被打穿，不追求精确调度）；显式正数则固定生效；
 * - 等待预算：按一次完整认证（BCrypt + 数据库往返）的 P99 时延放大若干倍
 *   取值——许可覆盖整个认证动作，数据库抖动同样会触发背压，属设计内保护；
 * - 准入失败发生在任何数据库访问之前，请求确定未执行（REJECTED 语义），
 *   认证端点无幂等键，客户端按 Retry-After 退避重试即可；
 * - 未来多实例部署时每实例闸门保护本节点 CPU 与连接池。
 */
@Slf4j
@Component
public class AuthAdmissionGate {

    private final Semaphore slots;
    private final int capacity;
    private final long acquireTimeoutMillis;
    private final AtomicInteger busyRejects = new AtomicInteger();

    public AuthAdmissionGate(
            @Value("${zhiyi.auth.admission-slots:0}") int slots,
            @Value("${zhiyi.auth.admission-wait-millis:1000}") long acquireTimeoutMillis) {
        this.capacity = slots > 0 ? slots : 2 * Runtime.getRuntime().availableProcessors();
        this.slots = new Semaphore(capacity);
        this.acquireTimeoutMillis = Math.max(0, acquireTimeoutMillis);
    }

    /** 闸门容量（自动推导或显式配置后的实际值），供日志与测试观测。 */
    int capacity() {
        return capacity;
    }

    /** 全局容量准入：包裹完整认证动作，进入前不占用任何数据库连接。 */
    public <T> T withAdmission(Supplier<T> action) {
        boolean acquired = false;
        try {
            try {
                acquired = slots.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw busy();
            }
            if (!acquired) {
                // 节流告警：第 1 次与此后每第 100 次拒绝记一条，洪峰期不打爆日志
                if (busyRejects.incrementAndGet() % 100 == 1) {
                    log.warn("认证准入背压：并发认证已满 {} 槽位，累计拒绝 {} 次",
                            capacity, busyRejects.get());
                }
                throw busy();
            }
            return action.get();
        } finally {
            if (acquired) {
                slots.release();
            }
        }
    }

    private BusinessException busy() {
        return new BusinessException(ResultCode.AUTH_BUSY);
    }
}
