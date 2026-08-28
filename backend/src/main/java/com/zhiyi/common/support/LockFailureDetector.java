package com.zhiyi.common.support;

import java.sql.SQLException;

/**
 * 数据库锁冲突判定：按 JDBC 厂商错误码识别，不依赖异常文本或 Spring 翻译结果。
 * NOWAIT 锁冲突（ER_LOCK_NOWAIT=3572）必须映射为可重试的业务背压，
 * 不得进入死锁重试切面（避免退避重试重新制造拥塞）。
 */
public final class LockFailureDetector {

    /** MySQL: Statement aborted because lock(s) could not be acquired immediately and NOWAIT is set. */
    public static final int ER_LOCK_NOWAIT = 3572;

    private LockFailureDetector() {
    }

    /** 是否为 NOWAIT 锁冲突（含包装在 Spring DataAccessException 中的场景）。 */
    public static boolean isNowaitConflict(Throwable throwable) {
        return hasErrorCode(throwable, ER_LOCK_NOWAIT);
    }

    private static boolean hasErrorCode(Throwable throwable, int errorCode) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 16) {
            if (current instanceof SQLException sqlException
                    && sqlException.getErrorCode() == errorCode) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }
}
