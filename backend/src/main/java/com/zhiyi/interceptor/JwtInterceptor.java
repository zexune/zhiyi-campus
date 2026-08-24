package com.zhiyi.interceptor;

import com.zhiyi.common.AuthTokenCookieWriter;
import com.zhiyi.common.WebResponseUtil;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;
import java.util.regex.Pattern;

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
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final Pattern PUBLIC_USER_CARD = Pattern.compile("^/api/user/\\d+/card$");
    private static final Pattern PUBLIC_USER_REPUTATION = Pattern.compile("^/api/user/\\d+/reputation$");
    private static final int MAX_TOKEN_LENGTH = 4096;

    private final JwtUtils jwtUtils;
    private final SysUserMapper userMapper;
    private final AuthTokenCookieWriter cookieWriter;

    public JwtInterceptor(JwtUtils jwtUtils, SysUserMapper userMapper,
                          AuthTokenCookieWriter cookieWriter) {
        this.jwtUtils = jwtUtils;
        this.userMapper = userMapper;
        this.cookieWriter = cookieWriter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // OPTIONS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 动态公开接口必须同时匹配 HTTP 方法和完整路径，避免 PUT/DELETE 商品接口被一并放行。
        if (isDynamicPublicGet(request)) {
            return true;
        }

        String encodedToken = resolveToken(request);
        if (encodedToken == null) {
            WebResponseUtil.writeJson(response, 401, 401, "未登录");
            return false;
        }
        if (encodedToken.length() > MAX_TOKEN_LENGTH) {
            WebResponseUtil.writeJson(response, 401, 401, "Token 格式无效");
            return false;
        }

        Claims claims = jwtUtils.parse(encodedToken); // 单次验签并取得全部 Claims
        if (claims == null) {
            WebResponseUtil.writeJson(response, 401, 401, "Token 无效或已过期");
            return false;
        }

        Long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException exception) {
            WebResponseUtil.writeJson(response, 401, 401, "Token 格式无效");
            return false;
        }

        Integer claimVersion = jwtUtils.getTokenVersion(claims);
        if (claimVersion == null) {
            WebResponseUtil.writeJson(response, 401, 401, "登录状态已失效，请重新登录");
            return false;
        }

        // 主库直读鉴权状态（普通读取不加锁；封禁/解封/注销才用锁与条件状态迁移）
        SysUser state = userMapper.selectAuthState(userId);
        if (state == null) {
            WebResponseUtil.writeJson(response, 401, 401, "用户不存在");
            return false;
        }

        int currentVersion = state.getTokenVersion() == null ? 0 : state.getTokenVersion();
        String issuedRole = claims.get(JwtUtils.ROLE_CLAIM, String.class);
        if (claimVersion != currentVersion || !Objects.equals(issuedRole, state.getRole().code())) {
            WebResponseUtil.writeJson(response, 401, 401, "登录状态已失效，请重新登录");
            return false;
        }

        // 只允许 ACTIVE：封禁（含临时封禁到期未恢复）一律拒绝，到期恢复由登录事务完成
        switch (state.getStatus()) {
            case CANCELLED -> {
                WebResponseUtil.writeJson(response, 401, 1008, "该账户已注销");
                return false;
            }
            case BANNED_PERM -> {
                WebResponseUtil.writeJson(response, 403, 1003, "该账户已被永久封禁");
                return false;
            }
            case BANNED_TEMP -> {
                WebResponseUtil.writeJson(response, 403, 1003, "账户已被封禁，到期后请重新登录");
                return false;
            }
            case ACTIVE -> { /* 放行 */ }
        }

        // 把 userId 和 role 放入 request attribute，Controller 里直接取
        request.setAttribute("userId", userId);
        request.setAttribute("role", state.getRole().code());
        return true;
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

    private boolean isDynamicPublicGet(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
        return PUBLIC_USER_CARD.matcher(path).matches()
                || PUBLIC_USER_REPUTATION.matcher(path).matches();
    }
}
