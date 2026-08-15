package com.zhiyi.module.user.controller;

import com.zhiyi.common.AuthTokenCookieWriter;
import com.zhiyi.common.Result;
import com.zhiyi.module.user.dto.LoginDTO;
import com.zhiyi.module.user.dto.RegisterDTO;
import com.zhiyi.module.user.dto.ResetPasswordDTO;
import com.zhiyi.module.user.service.AuthService;
import com.zhiyi.module.user.vo.LoginVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
 */
@Validated
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthTokenCookieWriter cookieWriter;

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto, HttpServletResponse response) {
        LoginVO vo = authService.register(dto);
        cookieWriter.write(response, vo.getToken());
        return Result.ok("注册成功", vo);
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletResponse response) {
        LoginVO vo = authService.login(dto);
        cookieWriter.write(response, vo.getToken());
        return Result.ok("登录成功", vo);
    }

    /** 登出：清除会话 Cookie，幂等，过期会话也可调用。 */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletResponse response) {
        cookieWriter.clear(response);
        return Result.ok("已退出登录", null);
    }

    @GetMapping("/security-question")
    public Result<Map<String, String>> securityQuestion(
            @RequestParam @NotNull(message = "请选择所属学校") Long schoolId,
            @RequestParam @NotBlank(message = "学号不能为空") String studentId) {
        return Result.ok(Map.of("question", authService.getSecurityQuestion(schoolId, studentId)));
    }

    @GetMapping("/security-questions")
    public Result<List<String>> securityQuestions() {
        return Result.ok(AuthService.SECURITY_QUESTIONS);
    }

    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        authService.resetPassword(dto);
        return Result.ok("密码重置成功，请重新登录", null);
    }

}
