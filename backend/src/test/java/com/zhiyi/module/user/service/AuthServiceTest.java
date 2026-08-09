package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.user.dto.LoginDTO;
import com.zhiyi.module.user.dto.RegisterDTO;
import com.zhiyi.module.user.dto.ResetPasswordDTO;
import com.zhiyi.module.user.entity.School;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.LoginAttemptService;
import com.zhiyi.module.user.support.RecordingUserStateCache;
import com.zhiyi.module.user.vo.LoginVO;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private com.zhiyi.module.user.mapper.SchoolMapper schoolMapper;

    private LoginAttemptService loginAttemptService;
    private RecordingUserStateCache userStateCache;
    private SchoolService schoolService;
    private JwtUtils jwtUtils;
    private AuthService service;

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.zhiyi.module.user.mapper.SysUserMapper");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService(1, 300);
        userStateCache = new RecordingUserStateCache(userMapper);
        schoolService = new SchoolService(schoolMapper);
        jwtUtils = new JwtUtils(
                "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
                Duration.ofMinutes(1));
        service = new AuthService(
                userMapper,
                passwordEncoder,
                jwtUtils,
                loginAttemptService,
                userStateCache,
                schoolService);
        lenient().when(schoolMapper.selectById(1L)).thenReturn(shu());
    }

    /** 上海大学，测试用固定学校 */
    private School shu() {
        School s = new School();
        s.setId(1L);
        s.setName("上海大学");
        s.setCode("SHU");
        s.setEmailDomain("@shu.edu.cn");
        s.setStatus("ACTIVE");
        return s;
    }

    @Test
    @SuppressWarnings("unchecked")
    void loginUsesSchoolAndCanonicalIdForThrottleAndQuery() {
        LoginDTO dto = new LoginDTO();
        dto.setSchoolId(1L);
        dto.setStudentId("  AdMiN  ");
        dto.setPassword("wrong");
        when(userMapper.selectOne(any())).thenReturn(null);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.login(dto));

        assertEquals(ResultCode.PASSWORD_ERROR.getCode(), exception.getCode());
        ArgumentCaptor<Wrapper<SysUser>> query =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).selectOne(query.capture());
        LambdaQueryWrapper<SysUser> wrapper =
                (LambdaQueryWrapper<SysUser>) query.getValue();
        String sql = wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue("admin"),
                () -> "sql=" + sql + ", params=" + wrapper.getParamNameValuePairs());
        assertTrue(wrapper.getParamNameValuePairs().containsValue(1L),
                () -> "sql=" + sql + ", params=" + wrapper.getParamNameValuePairs());
        assertTrue(loginAttemptService.isLocked("1:admin"));
        assertTrue(loginAttemptService.isLocked("  1:ADMIN  "));
    }

    @Test
    void registerStoresCanonicalIdAndBuildsNicknameFromIt() {
        RegisterDTO dto = new RegisterDTO();
        dto.setStudentId("  ABcd1234  ");
        dto.setSchoolId(1L);
        dto.setPassword("secret1");
        dto.setConfirmPassword("secret1");
        dto.setSecurityQuestion("你的小学名称是？");
        dto.setSecurityAnswer("Answer");
        when(schoolMapper.selectById(1L)).thenReturn(shu());
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode(anyString()))
                .thenAnswer(invocation -> "encoded:" + invocation.getArgument(0));
        doAnswer(invocation -> {
            SysUser inserted = invocation.getArgument(0);
            inserted.setId(7L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

        LoginVO result = service.register(dto);

        ArgumentCaptor<Wrapper<SysUser>> duplicateQuery =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).selectOne(duplicateQuery.capture());
        LambdaQueryWrapper<SysUser> duplicateWrapper =
                (LambdaQueryWrapper<SysUser>) duplicateQuery.getValue();
        String duplicateSql = duplicateWrapper.getSqlSegment();
        assertTrue(duplicateWrapper.getParamNameValuePairs().containsValue(1L),
                () -> "sql=" + duplicateSql + ", params=" + duplicateWrapper.getParamNameValuePairs());
        assertTrue(duplicateWrapper.getParamNameValuePairs().containsValue("abcd1234"),
                () -> "sql=" + duplicateSql + ", params=" + duplicateWrapper.getParamNameValuePairs());

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("abcd1234", captor.getValue().getStudentId());
        assertEquals("同学_1234", captor.getValue().getNickname());
        assertEquals(1L, captor.getValue().getSchoolId());
        assertEquals(1, captor.getValue().getLevel());
        assertEquals(0, captor.getValue().getExp());
        assertEquals(0, captor.getValue().getTokenVersion());
        assertEquals(0, jwtUtils.getTokenVersion(jwtUtils.parse(result.getToken())));
        assertNotNull(result.getToken());
        // 未填学校邮箱也可注册，VO 带学校名称
        assertEquals(null, captor.getValue().getSchoolEmail());
        assertEquals("上海大学", result.getUser().getSchoolName());
    }

    @Test
    void registerRejectsUnknownSchool() {
        RegisterDTO dto = new RegisterDTO();
        dto.setStudentId("abcd1234");
        dto.setSchoolId(999L);
        dto.setPassword("secret1");
        dto.setConfirmPassword("secret1");
        dto.setSecurityQuestion("你的小学名称是？");
        dto.setSecurityAnswer("Answer");
        when(schoolMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(
                BusinessException.class, () -> service.register(dto));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void registerRejectsEmailWithWrongDomain() {
        RegisterDTO dto = new RegisterDTO();
        dto.setStudentId("abcd1234");
        dto.setSchoolId(1L);
        dto.setSchoolEmail("student@dhu.edu.cn"); // 上海大学要求 @shu.edu.cn
        dto.setPassword("secret1");
        dto.setConfirmPassword("secret1");
        dto.setSecurityQuestion("你的小学名称是？");
        dto.setSecurityAnswer("Answer");
        when(schoolMapper.selectById(1L)).thenReturn(shu());

        BusinessException ex = assertThrows(
                BusinessException.class, () -> service.register(dto));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void registerStoresMatchingSchoolEmailWithoutVerificationCode() {
        RegisterDTO dto = new RegisterDTO();
        dto.setStudentId("abcd1234");
        dto.setSchoolId(1L);
        dto.setSchoolEmail("  20240101@SHU.EDU.CN ");
        dto.setPassword("secret1");
        dto.setConfirmPassword("secret1");
        dto.setSecurityQuestion("你的小学名称是？");
        dto.setSecurityAnswer("Answer");
        when(schoolMapper.selectById(1L)).thenReturn(shu());
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode(anyString()))
                .thenAnswer(invocation -> "encoded:" + invocation.getArgument(0));
        doAnswer(invocation -> {
            ((SysUser) invocation.getArgument(0)).setId(7L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

        LoginVO result = service.register(dto);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("20240101@shu.edu.cn", captor.getValue().getSchoolEmail());
        assertEquals("20240101@shu.edu.cn", result.getUser().getSchoolEmail());
    }

    @Test
    @SuppressWarnings("unchecked")
    void securityQuestionUsesSchoolAndCanonicalIdForQuery() {
        SysUser user = new SysUser();
        user.setId(3L);
        user.setStatus("ACTIVE");
        user.setSecurityQuestion("你的出生地是哪个城市？");
        when(userMapper.selectOne(any())).thenReturn(user);

        assertEquals("你的出生地是哪个城市？",
                service.getSecurityQuestion(1L, " USER01 "));

        ArgumentCaptor<Wrapper<SysUser>> query =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).selectOne(query.capture());
        LambdaQueryWrapper<SysUser> wrapper =
                (LambdaQueryWrapper<SysUser>) query.getValue();
        String sql = wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue("user01"),
                () -> "sql=" + sql + ", params=" + wrapper.getParamNameValuePairs());
        assertTrue(wrapper.getParamNameValuePairs().containsValue(1L),
                () -> "sql=" + sql + ", params=" + wrapper.getParamNameValuePairs());
    }

    @Test
    void resetPasswordBuildsLimiterKeyFromCanonicalId() {
        loginAttemptService.recordFailure("reset:1:user01");
        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setSchoolId(1L);
        dto.setStudentId(" USER01 ");
        dto.setSecurityAnswer("answer");
        dto.setNewPassword("newpass");
        dto.setConfirmPassword("newpass");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.resetPassword(dto));

        assertEquals(ResultCode.LOGIN_LOCKED.getCode(), exception.getCode());
    }

    @Test
    void expiredTemporaryBanInvalidatesStateAfterCommit() {
        SysUser user = activeUser(3L, "user01");
        user.setStatus("BANNED_TEMP");
        user.setBanUntilTime(LocalDateTime.now().minusMinutes(1));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("secret", "old-hash")).thenReturn(true);
        LoginDTO dto = new LoginDTO();
        dto.setSchoolId(1L);
        dto.setStudentId("user01");
        dto.setPassword("secret");

        service.login(dto);

        assertEquals(List.of(3L), userStateCache.afterCommitInvalidations());
        assertTrue(userStateCache.immediateInvalidations().isEmpty());
    }

    @Test
    void successfulResetInvalidatesStateAfterCommit() {
        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setSchoolId(1L);
        dto.setStudentId("user01");
        dto.setSecurityAnswer(" Answer ");
        dto.setNewPassword("newpass");
        dto.setConfirmPassword("newpass");
        SysUser user = activeUser(3L, "user01");
        user.setSecurityAnswer("answer-hash");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("answer", "answer-hash")).thenReturn(true);
        when(passwordEncoder.matches("newpass", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("newpass")).thenReturn("new-hash");
        when(userMapper.bumpTokenVersion(3L)).thenReturn(1);

        service.resetPassword(dto);

        ArgumentCaptor<SysUser> patch = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(patch.capture());
        assertEquals("new-hash", patch.getValue().getPassword());
        verify(userMapper).bumpTokenVersion(3L);
        assertEquals(List.of(3L), userStateCache.afterCommitInvalidations());
        assertTrue(userStateCache.immediateInvalidations().isEmpty());
    }

    @Test
    void loginReturnsTokenWithCurrentVersion() {
        SysUser user = activeUser(3L, "user01");
        user.setTokenVersion(4);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("secret", "old-hash")).thenReturn(true);
        LoginDTO dto = new LoginDTO();
        dto.setSchoolId(1L);
        dto.setStudentId("user01");
        dto.setPassword("secret");

        LoginVO result = service.login(dto);

        assertEquals(4, jwtUtils.getTokenVersion(jwtUtils.parse(result.getToken())));
    }

    private SysUser activeUser(Long id, String studentId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setStudentId(studentId);
        user.setPassword("old-hash");
        user.setNickname("测试用户");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setLevel(1);
        user.setExp(0);
        user.setWalletBalance(BigDecimal.ZERO);
        return user;
    }
}
