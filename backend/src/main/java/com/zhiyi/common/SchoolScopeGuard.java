package com.zhiyi.common;

import java.util.Objects;

/**
 * 普通业务的学校边界策略。
 *
 * 普通功能统一按用户所属学校隔离。管理员已由角色拦截器限制为只能访问
 * /api/admin/**，管理接口可按业务需要显式跨学校操作。
 */
public final class SchoolScopeGuard {

    private SchoolScopeGuard() {
    }

    public static Long requireAssigned(Long schoolId) {
        if (schoolId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先设置所属学校");
        }
        return schoolId;
    }

    public static void requireSame(Long actorSchoolId, Long targetSchoolId, String message) {
        requireAssigned(actorSchoolId);
        if (targetSchoolId == null || !Objects.equals(actorSchoolId, targetSchoolId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, message);
        }
    }
}
