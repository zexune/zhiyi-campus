package com.zhiyi.module.user.support;

import com.zhiyi.module.user.mapper.LoginAttemptMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录/密保失败限流协调器（数据库固定窗口状态机，替代本地 Caffeine）。
 *
 * 事务边界：所有方法运行在独立短事务（REQUIRES_NEW）中——
 * 失败计数必须在业务层抛出异常前提交，外层业务事务回滚不能把计数一并回滚；
 * 协调器自身从不在外层事务中持有 login_attempt 行锁，避免与 REQUIRES_NEW 自锁。
 *
 * 并发语义：同一 attempt_key 的更新由单条原子 UPSERT 完成；
 * 锁定期间的被拒请求不延长锁定时间、不递增计数。
 */
@Slf4j
@Component
public class LoginAttemptService {

    private final LoginAttemptMapper attemptMapper;
    private final int failLimit;
    private final int windowSeconds;
    private final int lockSeconds;
    private final int purgeRetentionSeconds;

    public LoginAttemptService(LoginAttemptMapper attemptMapper,
                               @Value("${zhiyi.auth.login-fail-limit:5}") int failLimit,
                               @Value("${zhiyi.auth.login-fail-window-seconds:900}") int windowSeconds,
                               @Value("${zhiyi.auth.login-fail-lock-seconds:300}") int lockSeconds,
                               @Value("${zhiyi.auth.login-attempt-purge-retention-seconds:86400}") int purgeRetentionSeconds) {
        this.attemptMapper = attemptMapper;
        this.failLimit = failLimit;
        this.windowSeconds = windowSeconds;
        this.lockSeconds = lockSeconds;
        this.purgeRetentionSeconds = purgeRetentionSeconds;
    }

    /** 是否仍处于锁定期（数据库时间判定）。 */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public boolean isLocked(String attemptKey) {
        return attemptMapper.isLocked(attemptKey);
    }

    /** 记一次失败；达到阈值后按数据库时间锁定。返回是否触发锁定（供日志/指标）。 */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(String attemptKey) {
        attemptMapper.recordFailure(attemptKey, windowSeconds, failLimit, lockSeconds);
        return attemptMapper.isLocked(attemptKey);
    }

    /** 验证成功：清零该主体计数。 */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void reset(String attemptKey) {
        attemptMapper.reset(attemptKey);
    }

    /** 定期清理：只删除窗口与锁均早已失效的记录。 */
    @Scheduled(fixedDelayString = "${zhiyi.auth.login-attempt-purge-interval-ms:3600000}",
            initialDelayString = "600000")
    public void purgeStaleScheduled() {
        try {
            int purged = purgeStale(purgeRetentionSeconds);
            if (purged > 0) {
                log.info("清理过期登录尝试记录 {} 条", purged);
            }
        } catch (Exception purgeFailure) {
            log.warn("登录尝试记录清理失败", purgeFailure);
        }
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public int purgeStale(int retentionSeconds) {
        return attemptMapper.purgeStale(retentionSeconds);
    }
}
