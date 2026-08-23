package com.zhiyi.module.user.dto;

/**
 * 管理端用户列表查询条件。
 *
 * 学校为精确匹配；学号/昵称/邮箱/手机号为模糊搜索，空值表示不参与筛选。
 */
public record AdminUserSearchQuery(
        Long schoolId,
        String studentId,
        String nickname,
        String email,
        String phone) {

    /** 无任何筛选条件的空查询 */
    public static final AdminUserSearchQuery EMPTY = new AdminUserSearchQuery(null, null, null, null, null);

    public boolean hasSchoolFilter() {
        return schoolId != null;
    }

    public boolean hasStudentIdFilter() {
        return hasText(studentId);
    }

    public boolean hasNicknameFilter() {
        return hasText(nickname);
    }

    public boolean hasEmailFilter() {
        return hasText(email);
    }

    public boolean hasPhoneFilter() {
        return hasText(phone);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
