package com.zhiyi.module.user.controller;

import com.zhiyi.common.ApiSuccess;
import com.zhiyi.common.AuthTokenCookieWriter;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.BusinessErrors;
import com.zhiyi.module.user.dto.LoginDTO;
import com.zhiyi.module.user.dto.RegisterDTO;
import com.zhiyi.module.user.dto.ResetPasswordDTO;
import com.zhiyi.module.user.service.AuthService;
import com.zhiyi.module.user.vo.LoginVO;
import com.zhiyi.module.user.vo.SecurityQuestionVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模块一：认证接口（B.1 附录接口清单）
 *
 * POST /api/auth/register           用户注册
 * POST /api/auth/login              用户登录
 * POST /api/auth/logout             登出（清除会话 Cookie）
 * GET  /api/auth/security-question  获取密保问题（?schoolId=1&studentId=xxx）
 * GET  /api/auth/security-questions 预设密保问题列表（注册页下拉用）
 * POST /api/auth/reset-password     验证密保并重置密码
 *
 * 登录/注册除在响应体返回 token（Swagger / 编程客户端用 Bearer）外，
 * 同时下发 httpOnly 会话 Cookie 供浏览器使用。
 *
 * 认证矩阵（P0-1）：密码/密保错误是业务失败（400/429），不触发前端登出；
 * 注销账户登录 → 403 + 1008；只有 HTTP 401（拦截器直写）才清理登录态。
 */
@Validated
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthTokenCookieWriter cookieWriter;

    @PostMapping("/register")
    @BusinessErrors({ResultCode.STUDENT_ID_EXISTS, ResultCode.USER_CANCELLED, ResultCode.AUTH_BUSY})
    public ApiSuccess<LoginVO> register(@Valid @RequestBody RegisterDTO dto, HttpServletResponse response) {
        LoginVO vo = authService.register(dto);
        cookieWriter.write(response, vo.getToken());
        return ApiSuccess.ok("注册成功", vo);
    }

    /** USER_NOT_FOUND：登录锁定窗口内账户被删除后重读为空的防御分支；AUTH_BUSY：认证准入背压。 */
    @PostMapping("/login")
    @BusinessErrors({ResultCode.PASSWORD_ERROR, ResultCode.LOGIN_LOCKED,
            ResultCode.USER_BANNED, ResultCode.USER_CANCELLED, ResultCode.USER_NOT_FOUND,
            ResultCode.AUTH_BUSY})
    public ApiSuccess<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletResponse response) {
        LoginVO vo = authService.login(dto);
        cookieWriter.write(response, vo.getToken());
        return ApiSuccess.ok("登录成功", vo);
    }

    /** 登出：清除会话 Cookie，幂等，过期会话也可调用；无特有业务错误。 */
    @PostMapping("/logout")
    @BusinessErrors
    public ApiSuccess<Void> logout(HttpServletResponse response) {
        cookieWriter.clear(response);
        return ApiSuccess.ok("已退出登录", null);
    }

    @GetMapping("/security-question")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.USER_CANCELLED})
    public ApiSuccess<SecurityQuestionVO> securityQuestion(
            @RequestParam @NotNull(message = "请选择所属学校") Long schoolId,
            @RequestParam @NotBlank(message = "学号不能为空") String studentId) {
        return ApiSuccess.ok(new SecurityQuestionVO(authService.getSecurityQuestion(schoolId, studentId)));
    }

    @GetMapping("/security-questions")
    @BusinessErrors
    public ApiSuccess<List<String>> securityQuestions() {
        return ApiSuccess.ok(AuthService.SECURITY_QUESTIONS);
    }

    @PostMapping("/reset-password")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.USER_CANCELLED,
            ResultCode.PASSWORD_ERROR, ResultCode.SECURITY_ANSWER_ERROR, ResultCode.LOGIN_LOCKED,
            ResultCode.SAME_AS_OLD_PASSWORD, ResultCode.AUTH_BUSY})
    public ApiSuccess<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        authService.resetPassword(dto);
        return ApiSuccess.ok("密码重置成功，请重新登录", null);
    }

}
