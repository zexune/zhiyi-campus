package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.module.admin.vo.AdminLoginVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.LoginAttemptService;
import com.zhiyi.module.user.support.StudentIdNormalizer;
import com.zhiyi.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 管理员认证服务，与面向学生的注册、登录和密保流程完全分离。 */
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final LoginAttemptService loginAttemptService;

    public AdminLoginVO login(String username, String password) {
        String canonicalUsername = StudentIdNormalizer.normalize(username);
        String loginKey = "admin:" + canonicalUsername;
        if (loginAttemptService.isLocked(loginKey)) {
            throw new BusinessException(ResultCode.LOGIN_LOCKED,
                    ResultCode.LOGIN_LOCKED.getMessage())
                    .withRetryAfterSeconds(loginAttemptService.remainingLockSeconds(loginKey));
        }

        SysUser admin = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getStudentId, canonicalUsername)
                .eq(SysUser::getRole, UserRole.ADMIN));
        if (admin == null || !passwordEncoder.matches(password, admin.getPassword())) {
            loginAttemptService.recordFailure(loginKey);
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "管理员账号或密码错误");
        }
        if (admin.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ResultCode.FORBIDDEN, "管理员账号不可用");
        }

        loginAttemptService.reset(loginKey);
        String token = jwtUtils.generateToken(admin.getId(), UserRole.ADMIN.code(), admin.getTokenVersion());
        return AdminLoginVO.of(token, admin);
    }
}
