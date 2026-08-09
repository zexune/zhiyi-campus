package com.zhiyi.common.annotation;

import com.zhiyi.common.enums.UserRole;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色权限注解 —— 标注在 Controller 类或方法上，由 RoleInterceptor 校验。
 * 用法：@RoleRequired（默认要求管理员）
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RoleRequired {
    UserRole value() default UserRole.ADMIN;
}
