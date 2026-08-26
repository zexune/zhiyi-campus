package com.zhiyi.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.module.user.entity.LoginAttempt;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 登录/密保失败限流固定窗口状态机。
 * 全部时间判定使用数据库 CURRENT_TIMESTAMP(6)，多实例部署天然一致；
 * 被锁定期间的请求不延长锁定、不递增计数。
 */
@Mapper
public interface LoginAttemptMapper extends BaseMapper<LoginAttempt> {

    /** 是否仍处于锁定期（数据库时间判定；无记录返回 0，被拒请求不续锁）。 */
    @Select("SELECT EXISTS(SELECT 1 FROM login_attempt "
            + "WHERE attempt_key = #{key} AND locked_until IS NOT NULL AND locked_until > CURRENT_TIMESTAMP(6))")
    boolean isLocked(@Param("key") String attemptKey);

    /** 锁定剩余秒数（向上取整，数据库时间判定）；未锁定返回 null。 */
    @Select("SELECT CEIL(TIMESTAMPDIFF(MICROSECOND, CURRENT_TIMESTAMP(6), locked_until) / 1000000) "
            + "FROM login_attempt "
            + "WHERE attempt_key = #{key} AND locked_until IS NOT NULL AND locked_until > CURRENT_TIMESTAMP(6)")
    Integer lockedRemainingSeconds(@Param("key") String attemptKey);

    /**
     * 记一次失败（原子状态机）：
     * - 锁有效：计数与锁定时间均不变（不续锁）；
     * - 失败窗口已过期：重置窗口并把计数归 1；
     * - 否则计数 +1；达到阈值时以数据库时间计算 locked_until。
     */
    @Update("""
            INSERT INTO login_attempt (attempt_key, window_started_at, fail_count, locked_until)
            VALUES (#{key}, CURRENT_TIMESTAMP(6), 1, NULL)
            ON DUPLICATE KEY UPDATE
                fail_count = IF(locked_until IS NOT NULL AND locked_until > CURRENT_TIMESTAMP(6), fail_count,
                            IF(window_started_at < CURRENT_TIMESTAMP(6) - INTERVAL #{windowSeconds} SECOND,
                               1, fail_count + 1)),
                window_started_at = IF(locked_until IS NOT NULL AND locked_until > CURRENT_TIMESTAMP(6), window_started_at,
                            IF(window_started_at < CURRENT_TIMESTAMP(6) - INTERVAL #{windowSeconds} SECOND,
                               CURRENT_TIMESTAMP(6), window_started_at)),
                locked_until = IF(locked_until IS NOT NULL AND locked_until > CURRENT_TIMESTAMP(6), locked_until,
                            IF(IF(window_started_at < CURRENT_TIMESTAMP(6) - INTERVAL #{windowSeconds} SECOND,
                                  1, fail_count + 1) >= #{failLimit},
                               CURRENT_TIMESTAMP(6) + INTERVAL #{lockSeconds} SECOND,
                               NULL))
            """)
    int recordFailure(@Param("key") String attemptKey,
                      @Param("windowSeconds") int windowSeconds,
                      @Param("failLimit") int failLimit,
                      @Param("lockSeconds") int lockSeconds);

    /** 验证成功：清零该主体计数（删除行等价于窗口/计数重置）。 */
    @Delete("DELETE FROM login_attempt WHERE attempt_key = #{key}")
    int reset(@Param("key") String attemptKey);

    /** 定期清理：只删除失败窗口和锁均早已失效的记录。 */
    @Delete("DELETE FROM login_attempt "
            + "WHERE locked_until IS NULL AND window_started_at < CURRENT_TIMESTAMP(6) - INTERVAL #{retentionSeconds} SECOND")
    int purgeStale(@Param("retentionSeconds") int retentionSeconds);
}
