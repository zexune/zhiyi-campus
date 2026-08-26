package com.zhiyi.interceptor;

import com.zhiyi.common.AuthTokenCookieWriter;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.WebResponseUtil;
import com.zhiyi.config.PublicEndpointPolicy;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Objects;

/**
 * JWT 登录拦截器 —— 校验每个请求的 Token，把 userId 和 role 放入 Request 供后续 Controller 使用。
 *
 * 凭证来源（按优先级）：Authorization: Bearer 头（Swagger / 编程客户端）→ httpOnly 会话 Cookie（浏览器）。
 *
 * 鉴权状态主库直读（B7/M5/M6 根因修复）：
 * - UserStateCache 本地缓存已删除——封禁/解封/改密提交后立刻对新请求生效，
 *   不存在 60s 窗口内旧缓存放行已封禁账号的问题，多实例部署也不再有各持一份状态的问题；
 * - 每请求一条按主键的轻量 SELECT（selectAuthState），命中主键索引，成本固定；
 * - 只允许 ACTIVE：任何 BANNED_TEMP 一律拒绝且不在请求路径比较时间；
 *   到期恢复只能由登录事务以数据库时间完成并签发新 Token。
 *
 * 认证错误语义（P0-1 固化，勿再混用）：
 * - Token 无效/过期/缺失、账户已注销的旧 Token → 通用 401 + UNAUTHORIZED(401)，
 *   不返回业务码 1008（业务层 USER_CANCELLED → 403 由 GlobalExceptionHandler 负责）；
 * - Token 版本/角色与主库不一致（改密、改角色、封禁提交后的旧 Token）→ 401 +
 *   SESSION_INVALIDATED(1401) 独立会话失效语义；
 * - 任何 401 都同时清除 httpOnly Cookie；前端以真实 HTTP 401 为清理登录态的唯一依据。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final int MAX_TOKEN_LENGTH = 4096;

    private final JwtUtils jwtUtils;
    private final SysUserMapper userMapper;
    private final AuthTokenCookieWriter cookieWriter;
    private final WebResponseUtil webResponseUtil;

    public JwtInterceptor(JwtUtils jwtUtils, SysUserMapper userMapper,
                          AuthTokenCookieWriter cookieWriter, WebResponseUtil webResponseUtil) {
        this.jwtUtils = jwtUtils;
        this.userMapper = userMapper;
        this.cookieWriter = cookieWriter;
        this.webResponseUtil = webResponseUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // OPTIONS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 动态公开接口（/api/user/{id}/card 等）必须同时匹配 HTTP 方法和完整路径，
        // 避免 PUT/DELETE 商品接口被一并放行；静态公开路由已在 WebMvcConfig 排除。
        if (PublicEndpointPolicy.isPublicRequest(request.getMethod(), requestPath(request))) {
            return true;
        }

        String encodedToken = resolveToken(request);
        if (encodedToken == null) {
            return rejectUnauthorized(response, "未登录");
        }
        if (encodedToken.length() > MAX_TOKEN_LENGTH) {
            return rejectUnauthorized(response, "Token 格式无效");
        }

        Claims claims = jwtUtils.parse(encodedToken); // 单次验签并取得全部 Claims
        if (claims == null) {
            return rejectUnauthorized(response, "Token 无效或已过期");
        }

        Long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException exception) {
            return rejectUnauthorized(response, "Token 格式无效");
        }

        Integer claimVersion = jwtUtils.getTokenVersion(claims);
        if (claimVersion == null) {
            return rejectSessionInvalidated(response);
        }

        // 主库直读鉴权状态（普通读取不加锁；封禁/解封/注销才用锁与条件状态迁移）
        SysUser state = userMapper.selectAuthState(userId);
        if (state == null) {
            return rejectUnauthorized(response, "用户不存在");
        }

        int currentVersion = state.getTokenVersion() == null ? 0 : state.getTokenVersion();
        String issuedRole = claims.get(JwtUtils.ROLE_CLAIM, String.class);
        if (claimVersion != currentVersion || !Objects.equals(issuedRole, state.getRole().code())) {
            return rejectSessionInvalidated(response);
        }

        // 只允许 ACTIVE：封禁（含临时封禁到期未恢复）一律拒绝，到期恢复由登录事务完成
        switch (state.getStatus()) {
            case CANCELLED -> {
                // 注销账户的旧 Token 是认证失效而非业务拒绝：通用 401（非 1008），并清 Cookie
                return rejectUnauthorized(response, "该账户已注销，请重新登录");
            }
            case BANNED_PERM -> {
                webResponseUtil.writeFailure(response, ResultCode.USER_BANNED, "该账户已被永久封禁");
                return false;
            }
            case BANNED_TEMP -> {
                webResponseUtil.writeFailure(response, ResultCode.USER_BANNED, "账户已被封禁，到期后请重新登录");
                return false;
            }
            case ACTIVE -> { /* 放行 */ }
        }

        // 把 userId 和 role 放入 request attribute，Controller 里直接取
        request.setAttribute("userId", userId);
        request.setAttribute("role", state.getRole().code());
        return true;
    }

    /** 通用认证失败：401 + UNAUTHORIZED，并清除 httpOnly Cookie。 */
    private boolean rejectUnauthorized(HttpServletResponse response, String message) throws IOException {
        cookieWriter.clear(response);
        webResponseUtil.writeFailure(response, ResultCode.UNAUTHORIZED, message);
        return false;
    }

    /** 会话失效（改密/改角色/封禁提交后的旧 Token）：401 + SESSION_INVALIDATED，并清除 httpOnly Cookie。 */
    private boolean rejectSessionInvalidated(HttpServletResponse response) throws IOException {
        cookieWriter.clear(response);
        webResponseUtil.writeFailure(response, ResultCode.SESSION_INVALIDATED,
                ResultCode.SESSION_INVALIDATED.getMessage());
        return false;
    }

    /** Bearer 头优先（Swagger / 编程客户端），无头时回退 httpOnly 会话 Cookie（浏览器）。 */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            return token.isBlank() ? null : token;
        }
        return cookieWriter.read(request);
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
    }
}
