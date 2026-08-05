package com.zhiyi.common;

import lombok.Getter;

/**
 * 统一状态码枚举
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或 Token 过期"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    SERVER_ERROR(500, "服务器内部错误"),

    // 业务错误码（1xxx 用户模块，2xxx 商品模块，3xxx 交易模块，4xxx 管理模块）
    STUDENT_ID_EXISTS(1001, "该学号已注册"),
    PASSWORD_ERROR(1002, "密码错误"),
    USER_BANNED(1003, "账户已被封禁"),
    SECURITY_ANSWER_ERROR(1004, "密保答案错误"),
    LOGIN_LOCKED(1005, "密码错误次数过多，请稍后再试"),
    USER_NOT_FOUND(1006, "用户不存在"),
    SAME_AS_OLD_PASSWORD(1007, "新密码不能与原密码相同"),
    USER_CANCELLED(1008, "该账户已注销"),
    BALANCE_NOT_ENOUGH(3001, "余额不足"),
    ORDER_STATUS_ERROR(3002, "订单状态异常"),
    ORDER_ALREADY_REVIEWED(3003, "该订单已评价"),
    ITEM_NOT_ON_SALE(2001, "商品已下架或已售出"),
    AI_VIOLATION(2002, "内容涉嫌违规，已被拦截"),
    DUPLICATE_FAVORITE(2003, "已收藏过该商品");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
