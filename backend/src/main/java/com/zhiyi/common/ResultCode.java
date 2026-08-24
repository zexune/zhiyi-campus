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
    /** 用户状态冲突（如对方被封禁/注销导致交易无法进行）；结果明确，幂等键可清除 */
    USER_STATUS_ERROR(1009, "账户状态异常，无法完成操作"),
    /** 资料版本冲突（乐观并发）；前端应展示最新资料并要求确认 */
    PROFILE_CONFLICT(1010, "资料已被修改，请刷新后重试"),
    BALANCE_NOT_ENOUGH(3001, "余额不足"),
    ORDER_STATUS_ERROR(3002, "订单状态异常"),
    ORDER_ALREADY_REVIEWED(3003, "该订单已评价"),
    /** 交易准入/锁繁忙背压：结果不确定，客户端应保留幂等键退避重试 */
    TRADE_BUSY(3004, "当前交易繁忙，请稍后重试"),
    /** 幂等键参数冲突：同键不同参数，明确拒绝 */
    IDEMPOTENCY_CONFLICT(3005, "重复请求的参数与原请求不一致"),
    /** 幂等记录处理中：结果不确定，客户端应保留幂等键稍后查询 */
    IDEMPOTENCY_PROCESSING(3006, "相同请求正在处理中，请稍后查看结果"),
    /** 幂等键格式非法 */
    IDEMPOTENCY_KEY_INVALID(3007, "幂等键缺失或格式非法，请刷新页面后重试"),
    ITEM_NOT_ON_SALE(2001, "商品已下架或已售出"),
    CONTENT_REVIEW_REQUIRED(2002, "内容涉嫌违规，已转入人工审核"),
    DUPLICATE_FAVORITE(2003, "已收藏过该商品"),
    /** Feed 游标过期/签名不匹配：客户端需从首屏重新开始 */
    FEED_CURSOR_INVALID(2004, "列表游标已过期，请重新加载");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
