package com.zhiyi.module.admin.controller;

import com.zhiyi.common.AuthTokenCookieWriter;
import com.zhiyi.common.ApiSuccess;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.BusinessErrors;
import com.zhiyi.common.annotation.RoleRequired;
import com.zhiyi.module.admin.service.AdminAuthService;
import com.zhiyi.module.admin.vo.AdminLoginVO;
import com.zhiyi.module.user.dto.ChangePasswordDTO;
import com.zhiyi.module.user.service.AccountSecurityService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员专用认证入口，不参与普通用户的学校登录与密保流程。登录同时下发 httpOnly 会话 Cookie。 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AccountSecurityService accountSecurityService;
    private final AuthTokenCookieWriter cookieWriter;

    @PostMapping("/login")
    @BusinessErrors({ResultCode.PASSWORD_ERROR, ResultCode.LOGIN_LOCKED, ResultCode.FORBIDDEN})
    public ApiSuccess<AdminLoginVO> login(@Valid @RequestBody AdminLoginRequest request,
                                      HttpServletResponse response) {
        AdminLoginVO vo = adminAuthService.login(request.username(), request.password());
        cookieWriter.write(response, vo.token());
        return ApiSuccess.ok("登录成功", vo);
    }

    /** 管理员登出：清除会话 Cookie，幂等；无特有业务错误。 */
    @PostMapping("/logout")
    @BusinessErrors
    public ApiSuccess<Void> logout(HttpServletResponse response) {
        cookieWriter.clear(response);
        return ApiSuccess.ok("已退出登录", null);
    }

    @PutMapping("/change-password")
    @RoleRequired
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.PASSWORD_ERROR, ResultCode.LOGIN_LOCKED,
            ResultCode.SAME_AS_OLD_PASSWORD})
    public ApiSuccess<Void> changePassword(@RequestAttribute("userId") Long adminId,
                                       @Valid @RequestBody ChangePasswordDTO request) {
        accountSecurityService.changePassword(adminId, request);
        return ApiSuccess.ok("密码修改成功，请重新登录", null);
    }

    public record AdminLoginRequest(
            @NotBlank(message = "管理员账号不能为空")
            @Size(max = 50, message = "管理员账号长度不能超过50个字符")
            String username,
            @NotBlank(message = "密码不能为空")
            @Size(max = 128, message = "密码长度不能超过128个字符")
            String password) {
    }
}
