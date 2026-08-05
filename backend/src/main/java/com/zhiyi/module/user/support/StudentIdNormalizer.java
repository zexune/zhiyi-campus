package com.zhiyi.module.user.support;

import java.util.Locale;

/** 学号的唯一规范形式：去除首尾空格并按 Locale.ROOT 转为小写。 */
public final class StudentIdNormalizer {

    private StudentIdNormalizer() {
    }

    public static String normalize(String studentId) {
        return studentId == null
                ? null
                : studentId.trim().toLowerCase(Locale.ROOT);
    }
}
