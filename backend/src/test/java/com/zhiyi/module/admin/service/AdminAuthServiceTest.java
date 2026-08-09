package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.module.admin.vo.AdminLoginVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.LoginAttemptService;
import com.zhiyi.utils.JwtUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    private JwtUtils jwtUtils;
    private AdminAuthService service;

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "admin-auth-test");
        assistant.setCurrentNamespace("com.zhiyi.module.user.mapper.SysUserMapper");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils(
                "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
                Duration.ofMinutes(5));
        service = new AdminAuthService(
                userMapper,
                passwordEncoder,
                jwtUtils,
                new LoginAttemptService(3, 300));
    }

    @Test
    @SuppressWarnings("unchecked")
    void loginUsesCanonicalAdminIdentityAndIssuesAdminToken() {
        SysUser admin = adminUser();
        when(userMapper.selectOne(any())).thenReturn(admin);
        when(passwordEncoder.matches("secret", "admin-hash")).thenReturn(true);

        AdminLoginVO result = service.login("  AdMiN  ", "secret");

        assertEquals("admin", result.user().username());
        assertEquals("ADMIN", result.user().role());
        assertEquals("ADMIN", jwtUtils.getRole(result.token()));

        ArgumentCaptor<Wrapper<SysUser>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).selectOne(query.capture());
        LambdaQueryWrapper<SysUser> wrapper = (LambdaQueryWrapper<SysUser>) query.getValue();
        String sql = wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue("admin"), () -> "sql=" + sql);
        assertTrue(wrapper.getParamNameValuePairs().containsValue(UserRole.ADMIN), () -> "sql=" + sql);
    }

    @Test
    void rejectsNonAdminEvenIfMapperReturnsIt() {
        SysUser user = adminUser();
        user.setRole(com.zhiyi.common.enums.UserRole.USER);
        when(userMapper.selectOne(any())).thenReturn(user);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.login("admin", "secret"));

        assertEquals(ResultCode.PASSWORD_ERROR.getCode(), exception.getCode());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    private SysUser adminUser() {
        SysUser admin = new SysUser();
        admin.setId(1L);
        admin.setStudentId("admin");
        admin.setPassword("admin-hash");
        admin.setNickname("系统管理员");
        admin.setRole(com.zhiyi.common.enums.UserRole.ADMIN);
        admin.setStatus(com.zhiyi.common.enums.UserStatus.ACTIVE);
        admin.setTokenVersion(2);
        return admin;
    }
}
