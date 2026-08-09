package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.user.dto.LoginDTO;
import com.zhiyi.module.user.dto.RegisterDTO;
import com.zhiyi.module.user.dto.ResetPasswordDTO;
import com.zhiyi.module.user.entity.School;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.LoginAttemptService;
import com.zhiyi.module.user.support.StudentIdNormalizer;
import com.zhiyi.module.user.support.UserStateCache;
import com.zhiyi.module.user.vo.LoginVO;
import com.zhiyi.module.user.vo.UserVO;
import com.zhiyi.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 模块一：注册 / 登录 / 密保找回（需求 1.1 / 1.2 / 1.3）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 预设密保问题（注册时前端从中选择） */
    public static final List<String> SECURITY_QUESTIONS = List.of(
            "你的小学名称是？",
            "你最喜欢的老师姓什么？",
            "你的出生地是哪个城市？",
            "你第一只宠物叫什么？",
            "你母亲的姓名是？"
    );

    private static final DateTimeFormatter BAN_TIME_FMT = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final LoginAttemptService loginAttemptService;
    private final UserStateCache userStateCache;
    private final SchoolService schoolService;

    /**
     * 注册（需求 1.1）
     * 并发安全：唯一性靠 DB 的 uk_school_student 联合唯一索引兜底 —— 先查后插在并发注册时存在竞态，
     * 捕获 DuplicateKeyException 统一转为业务提示。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "两次输入的密码不一致");
        }
        String studentId = StudentIdNormalizer.normalize(dto.getStudentId());
        // 学校必填且必须启用（创新功能 A2：注册即归属学校）
        School school = requireActiveSchool(dto.getSchoolId());
        // 学校邮箱可选：无需验证码，填入时只校验邮箱后缀与学校是否匹配。
        String schoolEmail = schoolService.normalizeAndValidateEmail(dto.getSchoolEmail(), school);

        // 密保问题支持预设列表之外的自定义问题（长度由 DTO @Size 约束）
        // 先查提示更友好（非并发场景直接命中）；并发窗口由唯一索引兜底
        SysUser exists = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId, SysUser::getStatus)
                .eq(SysUser::getSchoolId, school.getId())
                .eq(SysUser::getStudentId, studentId));
        if (exists != null) {
            if ("CANCELLED".equals(exists.getStatus())) {
                throw new BusinessException(ResultCode.USER_CANCELLED, "该学号在当前学校的账户已注销，如需恢复请联系管理员");
            }
            throw new BusinessException(ResultCode.STUDENT_ID_EXISTS, "该学号已在当前学校注册，请直接登录或找回密码");
        }

        SysUser user = new SysUser();
        user.setStudentId(studentId);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));                    // BCrypt，不存明文
        user.setNickname(defaultNickname(dto.getNickname(), studentId));
        user.setPhone(dto.getPhone());
        user.setSchoolId(school.getId());
        user.setSchoolEmail(schoolEmail);
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setLevel(1);
        user.setExp(0);
        user.setTokenVersion(0);
        user.setWalletBalance(BigDecimal.ZERO);
        user.setSecurityQuestion(dto.getSecurityQuestion());
        user.setSecurityAnswer(passwordEncoder.encode(normalizeAnswer(dto.getSecurityAnswer()))); // 密保答案同样加密

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.STUDENT_ID_EXISTS, "该学号已在当前学校注册，请直接登录或找回密码");
        }

        String token = jwtUtils.generateToken(
                user.getId(), user.getRole(), user.getTokenVersion());
        return new LoginVO(token, UserVO.from(user, school.getName()));
    }

    /**
     * 登录（需求 1.2）—— 状态检查 + 临时封禁到期自动恢复 + 失败限流
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginDTO dto) {
        String studentId = StudentIdNormalizer.normalize(dto.getStudentId());
        School school = requireActiveSchool(dto.getSchoolId());
        String loginKey = accountKey(school.getId(), studentId);
        // 失败限流：BCrypt 校验开销大，先挡住暴力尝试
        if (loginAttemptService.isLocked(loginKey)) {
            throw new BusinessException(ResultCode.LOGIN_LOCKED);
        }

        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getSchoolId, school.getId())
                .eq(SysUser::getStudentId, studentId)
                .eq(SysUser::getRole, "USER"));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailure(loginKey);
            // 不区分「用户不存在」与「密码错误」，防止学号枚举
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "学号或密码错误");
        }

        // 封禁/注销状态检查
        if ("CANCELLED".equals(user.getStatus())) {
            throw new BusinessException(ResultCode.USER_CANCELLED, "该账户已注销，如需恢复请联系管理员");
        }
        if ("BANNED_PERM".equals(user.getStatus())) {
            throw new BusinessException(ResultCode.USER_BANNED, "该账户已被永久封禁");
        }
        if ("BANNED_TEMP".equals(user.getStatus())) {
            LocalDateTime until = user.getBanUntilTime();
            if (until != null && until.isAfter(LocalDateTime.now())) {
                throw new BusinessException(ResultCode.USER_BANNED,
                        "您的账户已被封禁至 " + until.format(BAN_TIME_FMT));
            }
            // 到期自动恢复（需求 1.6）
            SysUser patch = new SysUser();
            patch.setId(user.getId());
            patch.setStatus("ACTIVE");
            userMapper.update(patch, Wrappers.<SysUser>lambdaUpdate()
                    .eq(SysUser::getId, user.getId())
                    .set(SysUser::getBanUntilTime, null));
            user.setStatus("ACTIVE");
            user.setBanUntilTime(null);
            userStateCache.invalidateAfterCommit(user.getId());
        }

        loginAttemptService.reset(loginKey);
        String token = jwtUtils.generateToken(
                user.getId(), user.getRole(), user.getTokenVersion());
        return new LoginVO(token, UserVO.from(user, school.getName()));
    }

    /**
     * 获取密保问题（需求 1.3 步骤 2）
     */
    public String getSecurityQuestion(Long schoolId, String studentId) {
        studentId = StudentIdNormalizer.normalize(studentId);
        School school = requireActiveSchool(schoolId);
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId, SysUser::getSecurityQuestion, SysUser::getStatus)
                .eq(SysUser::getSchoolId, school.getId())
                .eq(SysUser::getStudentId, studentId)
                .eq(SysUser::getRole, "USER"));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "该学号尚未注册");
        }
        if ("CANCELLED".equals(user.getStatus())) {
            throw new BusinessException(ResultCode.USER_CANCELLED, "该账户已注销，如需恢复请联系管理员");
        }
        return user.getSecurityQuestion();
    }

    /**
     * 验证密保并重置密码（需求 1.3）
     * 重置成功后推进 tokenVersion，使所有旧 Token 立即失效。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordDTO dto) {
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "两次输入的密码不一致");
        }
        String studentId = StudentIdNormalizer.normalize(dto.getStudentId());
        School school = requireActiveSchool(dto.getSchoolId());
        // 密保答案验证也走失败限流，防止暴力猜答案
        String lockKey = "reset:" + accountKey(school.getId(), studentId);
        if (loginAttemptService.isLocked(lockKey)) {
            throw new BusinessException(ResultCode.LOGIN_LOCKED, "尝试次数过多，请稍后再试");
        }

        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getSchoolId, school.getId())
                .eq(SysUser::getStudentId, studentId)
                .eq(SysUser::getRole, "USER"));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "该学号尚未注册");
        }
        if ("CANCELLED".equals(user.getStatus())) {
            throw new BusinessException(ResultCode.USER_CANCELLED, "该账户已注销，如需恢复请联系管理员");
        }
        // 比对忽略首尾空格、不区分大小写（需求 1.3）
        if (!passwordEncoder.matches(normalizeAnswer(dto.getSecurityAnswer()), user.getSecurityAnswer())) {
            loginAttemptService.recordFailure(lockKey);
            throw new BusinessException(ResultCode.SECURITY_ANSWER_ERROR, "密保答案错误，请再想想");
        }
        // 新密码不得与原密码相同
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.SAME_AS_OLD_PASSWORD);
        }

        SysUser patch = new SysUser();
        patch.setId(user.getId());
        patch.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(patch);

        int affected = userMapper.bumpTokenVersion(user.getId());
        if (affected == 0) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        loginAttemptService.reset(lockKey);
        userStateCache.invalidateAfterCommit(user.getId());
        log.info("用户 {} 通过密保重置了密码", user.getStudentId());
    }

    /** 默认昵称：同学_学号后4位 */
    private String defaultNickname(String nickname, String studentId) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname.trim();
        }
        String tail = studentId.length() >= 4 ? studentId.substring(studentId.length() - 4) : studentId;
        return "同学_" + tail;
    }

    /** 密保答案归一化：去首尾空格 + 转小写 */
    private String normalizeAnswer(String answer) {
        return answer == null ? "" : answer.trim().toLowerCase();
    }

    private School requireActiveSchool(Long schoolId) {
        School school = schoolService.getActiveSchool(schoolId);
        if (school == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择有效的学校");
        }
        return school;
    }

    private String accountKey(Long schoolId, String studentId) {
        return schoolId + ":" + studentId;
    }
}
