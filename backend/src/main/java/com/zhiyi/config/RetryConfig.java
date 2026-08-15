package com.zhiyi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 开启 @Retryable 声明式重试，用于资金事务在数据库死锁 / 锁等待超时时的自动重试。
 *
 * 切面顺序说明（不可更改的隐含契约）：
 * - spring-retry 的 advisor 默认 order = Ordered.LOWEST_PRECEDENCE - 1，
 *   高于 @Transactional 的默认 order（Ordered.LOWEST_PRECEDENCE），
 *   因此重试切面位于事务切面之外，每次重试都会开启全新事务，
 *   上一轮失败的写入已被完整回滚，不会出现半提交状态。
 * - 若调整任一切面的 order，必须保持"重试在外、事务在内"的嵌套关系。
 */
@Configuration
@EnableRetry
public class RetryConfig {
}
