package com.zhiyi.module.user.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 登录失败限流 —— 同一学号连续密码错误 N 次后锁定一段时间。
 *
 * 高并发/安全设计：
 * - BCrypt 校验本身较慢（~100ms），不加限流时攻击者可用登录接口做撞库 + 拖垮 CPU；
 * - 计数器放 Caffeine（带 TTL 自动过期），按 key 原子更新，不落库。
 */
@Component
public class LoginAttemptService {

    private final int failLimit;
    private final Cache<String, Integer> failCounter;

    @Autowired
    public LoginAttemptService(@Value("${zhiyi.auth.login-fail-limit:5}") int failLimit,
                               @Value("${zhiyi.auth.login-fail-lock-seconds:300}") long lockSeconds) {
        this(failLimit, lockSeconds, Ticker.systemTicker());
    }

    LoginAttemptService(int failLimit, long lockSeconds, Ticker ticker) {
        this.failLimit = failLimit;
        this.failCounter = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(Duration.ofSeconds(lockSeconds))
                .ticker(ticker)
                .build();
    }

    /** 是否已锁定 */
    public boolean isLocked(String studentId) {
        Integer count = failCounter.getIfPresent(StudentIdNormalizer.normalize(studentId));
        return count != null && count >= failLimit;
    }

    /** 记一次失败 */
    public void recordFailure(String studentId) {
        failCounter.asMap().compute(
                StudentIdNormalizer.normalize(studentId),
                (ignored, count) -> count == null ? 1 : count + 1);
    }

    /** 登录成功清零 */
    public void reset(String studentId) {
        failCounter.invalidate(StudentIdNormalizer.normalize(studentId));
    }
}
