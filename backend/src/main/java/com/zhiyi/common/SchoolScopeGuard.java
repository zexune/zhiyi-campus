package com.zhiyi.common;

import java.util.Objects;

/**
 * 普通业务的学校边界策略。
 *
 * 管理员访问普通接口时也必须遵守该策略；只有 /api/admin/** 管理接口
 * 可以按管理员权限跨学校处理平台数据。
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
