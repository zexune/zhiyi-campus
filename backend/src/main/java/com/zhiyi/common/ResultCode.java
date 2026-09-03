package com.zhiyi.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 统一状态码枚举。
 *
 * 双层契约（业界标准做法）：HTTP 状态码负责粗分类——传输层、网关与监控按
 * 4xx/5xx 正常工作；body 里的 code 负责细粒度业务原因——前端按业务码分支。
 *
 * 错误来源 → HTTP 状态唯一映射表（P0-1 固化，修改需同步认证矩阵测试）：
 * - 401：仅认证层失败（Token 缺失/无效/过期、会话失效、账户注销后的旧 Token）。
 *   由 JwtInterceptor 直写，响应同时清除 httpOnly Cookie；前端以真实 HTTP 401
 *   作为清理登录态的唯一依据；
 * - 403：权限不足与"账户被明确拒绝登录"（封禁 1003、注销 1008——业务层经
 *   GlobalExceptionHandler 返回）。403 不触发前端登出；
 * - 409：状态/并发类失败（已被抢购、乐观冲突、幂等冲突、余额不足）；
 * - 405/406/413/415：MVC 传输协议拒绝（方法、Accept、载荷大小、Content-Type），
 *   保留真实 HTTP 状态并统一输出 ApiFailure；
 * - 429：限流与背压（登录失败锁定、认证与交易准入繁忙），可退避时附 Retry-After；
 * - 400：参数与凭证内容错误（密码/密保答案错误刻意用 400 而非 401：
 *   401 保留给会话失效，不能被"密码输错"触发）。
 *
 * USER_CANCELLED(1008) 只承担业务语义（登录/账户操作被注销状态拒绝 → 403）；
 * 会话失效的认证语义由 SESSION_INVALIDATED(1401) 独立承载（→ 401），
 * 两者不得混用。未登记业务码没有静默兜底：httpStatusOf 直接失败（P1-2）。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, HttpStatus.OK, "操作成功"),
    BAD_REQUEST(400, HttpStatus.BAD_REQUEST, "参数错误"),
    UNAUTHORIZED(401, HttpStatus.UNAUTHORIZED, "未登录或 Token 过期"),
    FORBIDDEN(403, HttpStatus.FORBIDDEN, "权限不足"),
    NOT_FOUND(404, HttpStatus.NOT_FOUND, "资源不存在"),
    METHOD_NOT_ALLOWED(405, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    NOT_ACCEPTABLE(406, HttpStatus.NOT_ACCEPTABLE, "无法生成客户端可接受的响应格式"),
    CONFLICT(409, HttpStatus.CONFLICT, "数据冲突"),
    PAYLOAD_TOO_LARGE(413, HttpStatus.CONTENT_TOO_LARGE, "请求体过大"),
    UNSUPPORTED_MEDIA_TYPE(415, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求体格式不支持"),
    SERVER_ERROR(500, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"),

    // 业务错误码（1xxx 用户模块，2xxx 商品模块，3xxx 交易模块，4xxx 管理模块）
    STUDENT_ID_EXISTS(1001, HttpStatus.CONFLICT, "该学号已注册"),
    PASSWORD_ERROR(1002, HttpStatus.BAD_REQUEST, "密码错误"),
    USER_BANNED(1003, HttpStatus.FORBIDDEN, "账户已被封禁"),
    SECURITY_ANSWER_ERROR(1004, HttpStatus.BAD_REQUEST, "密保答案错误"),
    /** 登录失败锁定：静态默认 300s 仅为兜底策略，运行时由异常实例携带数据库计算的剩余秒数覆盖 */
    LOGIN_LOCKED(1005, HttpStatus.TOO_MANY_REQUESTS, "密码错误次数过多，请稍后再试", 300),
    USER_NOT_FOUND(1006, HttpStatus.NOT_FOUND, "用户不存在"),
    SAME_AS_OLD_PASSWORD(1007, HttpStatus.BAD_REQUEST, "新密码不能与原密码相同"),
    /** 账户已注销：业务层语义（登录/资金/社交等被注销状态明确拒绝）→ 403，不触发前端登出 */
    USER_CANCELLED(1008, HttpStatus.FORBIDDEN, "该账户已注销"),
    /** 用户状态冲突（如对方被封禁/注销导致交易无法进行）；结果明确，幂等键可清除 */
    USER_STATUS_ERROR(1009, HttpStatus.CONFLICT, "账户状态异常，无法完成操作"),
    /** 资料版本冲突（乐观并发）；前端应展示最新资料并要求确认 */
    PROFILE_CONFLICT(1010, HttpStatus.CONFLICT, "资料已被修改，请刷新后重试"),
    /**
     * 认证端点准入背压（登录/注册/密保重置共用）：闸门在触碰任何数据库之前
     * 拒绝，请求确定未执行，按 Retry-After 退避重试即可（无幂等键，REJECTED 语义成立）。
     */
    AUTH_BUSY(1011, HttpStatus.TOO_MANY_REQUESTS, "当前请求较多，请稍后重试", 1),
    /** 会话失效（Token 版本/角色与主库不一致、改密后旧 Token）：认证层语义 → 401 + 清 Cookie */
    SESSION_INVALIDATED(1401, HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录"),
    BALANCE_NOT_ENOUGH(3001, HttpStatus.CONFLICT, "余额不足"),
    ORDER_STATUS_ERROR(3002, HttpStatus.CONFLICT, "订单状态异常"),
    ORDER_ALREADY_REVIEWED(3003, HttpStatus.CONFLICT, "该订单已评价"),
    /** 交易准入/锁繁忙背压：默认结果不明；已知拒绝分支由 BusinessException 实例覆盖 */
    TRADE_BUSY(3004, HttpStatus.TOO_MANY_REQUESTS, "当前交易繁忙，请稍后重试", 2),
    /** 幂等键参数冲突：同键不同参数，明确拒绝 */
    IDEMPOTENCY_CONFLICT(3005, HttpStatus.CONFLICT, "重复请求的参数与原请求不一致"),
    /** 幂等记录处理中：结果不确定，客户端应保留幂等键稍后查询 */
    IDEMPOTENCY_PROCESSING(3006, HttpStatus.CONFLICT, "相同请求正在处理中，请稍后查看结果", 3),
    /** 幂等键格式非法 */
    IDEMPOTENCY_KEY_INVALID(3007, HttpStatus.BAD_REQUEST, "幂等键缺失或格式非法，请刷新页面后重试"),
    ITEM_NOT_ON_SALE(2001, HttpStatus.CONFLICT, "商品已下架或已售出"),
    /** Feed 游标过期/签名不匹配：客户端需从首屏重新开始 */
    FEED_CURSOR_INVALID(2004, HttpStatus.BAD_REQUEST, "列表游标已过期，请重新加载");

    /** 请求结果的幂等处置分类（P1-3）：REJECTED=明确拒绝无副作用；PROCESSING=服务端仍在处理；UNKNOWN=结果不明 */
    public enum RequestOutcome {
        REJECTED, PROCESSING, UNKNOWN
    }

    /** 已确认同一幂等请求仍在服务端处理。 */
    private static final Set<ResultCode> OUTCOME_PROCESSING = Set.of(IDEMPOTENCY_PROCESSING);
    /** 仅凭业务码无法确认最终结果；具体来源可由 BusinessException 实例覆盖。 */
    private static final Set<ResultCode> OUTCOME_UNKNOWN = Set.of(TRADE_BUSY, SERVER_ERROR);

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;
    /** 允许退避重试时的标准 Retry-After 秒数；0 表示不返回该头 */
    private final int retryAfterSeconds;

    ResultCode(int code, HttpStatus httpStatus, String message) {
        this(code, httpStatus, message, 0);
    }

    ResultCode(int code, HttpStatus httpStatus, String message, int retryAfterSeconds) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** 幂等处置默认值：明确拒绝 → 可清键；处理中/结果不明 → 保留原键。 */
    public RequestOutcome requestOutcome() {
        if (OUTCOME_UNKNOWN.contains(this)) {
            return RequestOutcome.UNKNOWN;
        }
        return OUTCOME_PROCESSING.contains(this) ? RequestOutcome.PROCESSING : RequestOutcome.REJECTED;
    }

    /**
     * 未登记业务码没有"默认 400"的静默兜底（P1-2）：登记检查直接失败，
     * 由 {@link ResultCodeContractTest} 在构建期暴露，而非在运行时被错分类。
     */
    public static ResultCode of(int code) {
        for (ResultCode resultCode : values()) {
            if (resultCode.code == code) {
                return resultCode;
            }
        }
        throw new IllegalStateException("未登记的业务码：" + code + "——请在 ResultCode 显式登记并绑定 HTTP 状态");
    }

    /** 契约不变量：业务码唯一、HTTP 状态合法、成功码唯一（供启动级测试调用）。 */
    static void assertContractInvariants() {
        Set<Integer> seen = new HashSet<>();
        for (ResultCode resultCode : values()) {
            if (!seen.add(resultCode.code)) {
                throw new IllegalStateException("业务码重复登记：" + resultCode.code);
            }
            if (resultCode.httpStatus == null) {
                throw new IllegalStateException("业务码 " + resultCode.code + " 未绑定 HTTP 状态");
            }
        }
        long successCount = Arrays.stream(values())
                .filter(code -> code.httpStatus == HttpStatus.OK).count();
        if (successCount != 1) {
            throw new IllegalStateException("必须恰好一个成功码（当前 " + successCount + " 个）");
        }
    }
}
