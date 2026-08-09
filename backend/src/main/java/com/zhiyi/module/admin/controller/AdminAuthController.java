package com.zhiyi.module.admin.controller;

import com.zhiyi.common.Result;
import com.zhiyi.common.annotation.RoleRequired;
import com.zhiyi.module.admin.service.AdminAuthService;
import com.zhiyi.module.admin.vo.AdminLoginVO;
import com.zhiyi.module.user.dto.ChangePasswordDTO;
import com.zhiyi.module.user.service.AccountSecurityService;
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

/** 管理员专用认证入口，不参与普通用户的学校登录与密保流程。 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AccountSecurityService accountSecurityService;

    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.ok("登录成功", adminAuthService.login(request.username(), request.password()));
    }

    @PutMapping("/change-password")
    @RoleRequired("ADMIN")
    public Result<Void> changePassword(@RequestAttribute("userId") Long adminId,
                                       @Valid @RequestBody ChangePasswordDTO request) {
        accountSecurityService.changePassword(adminId, request);
        return Result.ok("密码修改成功，请重新登录", null);
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
