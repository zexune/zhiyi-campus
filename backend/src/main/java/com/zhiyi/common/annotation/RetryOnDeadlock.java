package com.zhiyi.common.annotation;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 死锁 / 锁等待超时自动重试 —— 标注在资金事务方法上。
 *
 * 触发条件：MySQL 1213（死锁）或 1205（锁等待超时）经 Spring 异常翻译后的
 * {@link ConcurrencyFailureException} 家族；业务异常（BusinessException）不重试。
 *
 * 最多 3 次尝试，50ms 起步指数退避；配合 RetryConfig 的切面顺序约定，
 * 每次重试都是全新事务，上一轮写入已全部回滚。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Retryable(
        retryFor = ConcurrencyFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 2, maxDelay = 400)
)
public @interface RetryOnDeadlock {
}
