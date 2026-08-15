package com.zhiyi.config;

import com.zhiyi.common.annotation.RetryOnDeadlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Recover;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 @RetryOnDeadlock 声明式重试在 Spring 上下文中真实生效：
 * 探针 Bean 前两次抛出死锁异常应被自动重试，第三次成功；
 * 持续死锁则重试耗尽后走 @Recover 兜底，不会把 ConcurrencyFailureException 直接抛给调用方。
 */
@SpringJUnitConfig(RetryConfigTest.ProbeConfiguration.class)
class RetryConfigTest {

    @Configuration(proxyBeanMethods = false)
    @Import(RetryConfig.class)
    static class ProbeConfiguration {

        @Bean
        FlakyProbe flakyProbe() {
            return new FlakyProbe();
        }
    }

    static class FlakyProbe {

        private final AtomicInteger flakyAttempts = new AtomicInteger();
        private final AtomicInteger stuckAttempts = new AtomicInteger();

        @RetryOnDeadlock
        public String flaky(Object a, Object b) {
            if (flakyAttempts.incrementAndGet() < 3) {
                // ConcurrencyFailureException 家族（MySQL 1205/1213 翻译产物）均触发重试；
                // 不使用 6.0.3 起废弃的 DeadlockLoserDataAccessException
                throw new CannotAcquireLockException("模拟锁竞争失败后自动回滚");
            }
            return "ok";
        }

        @RetryOnDeadlock
        public String alwaysDeadlocked(Object a, Object b) {
            stuckAttempts.incrementAndGet();
            throw new LockWaitTimeoutProbe("模拟锁等待超时");
        }

        @Recover
        public String recover(ConcurrencyFailureException e, Object a, Object b) {
            return "recovered:" + e.getClass().getSimpleName();
        }

        // 经方法读取计数：CGLIB 代理实例不走构造器，直接读代理字段会得到 null。
        int flakyAttemptCount() {
            return flakyAttempts.get();
        }

        int stuckAttemptCount() {
            return stuckAttempts.get();
        }
    }

    /** ConcurrencyFailureException 家族的另一个分支（对应 MySQL 1205 锁等待超时）。 */
    static class LockWaitTimeoutProbe extends ConcurrencyFailureException {

        LockWaitTimeoutProbe(String msg) {
            super(msg);
        }
    }

    @Autowired
    private FlakyProbe probe;

    @Test
    @DisplayName("死锁异常自动重试，第三次成功且总调用次数为 3")
    void retriesDeadlockUntilSuccess() {
        String result = probe.flaky("a", "b");

        assertEquals("ok", result);
        assertEquals(3, probe.flakyAttemptCount());
    }

    @Test
    @DisplayName("持续死锁重试耗尽后走 @Recover 兜底而非向外抛出")
    void exhaustedRetriesFallBackToRecover() {
        String result = probe.alwaysDeadlocked("a", "b");

        assertEquals("recovered:LockWaitTimeoutProbe", result);
        assertEquals(3, probe.stuckAttemptCount());
    }
}
