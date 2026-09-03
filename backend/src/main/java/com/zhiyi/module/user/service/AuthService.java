package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.module.user.dto.LoginDTO;
import com.zhiyi.module.user.dto.RegisterDTO;
import com.zhiyi.module.user.dto.ResetPasswordDTO;
import com.zhiyi.module.user.entity.School;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.AuthAdmissionGate;
import com.zhiyi.module.user.support.LoginAttemptService;
import com.zhiyi.module.user.support.StudentIdNormalizer;
import com.zhiyi.module.user.vo.LoginVO;
import com.zhiyi.module.user.vo.UserVO;
import com.zhiyi.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 模块一：注册 / 登录 / 密保找回（需求 1.1 / 1.2 / 1.3）
 *
 * 时间权威统一：临时封禁到期判断以数据库 CURRENT_TIMESTAMP(6) 为准；
 * 到期恢复是"条件 UPDATE（status=BANNED_TEMP AND ban_until_time<=now）+ 同 SQL
 * 推进 token_version + FOR UPDATE 重读"的原子迁移，并发登录时恰好完成一次。
 * 请求路径（JwtInterceptor）不比较时间、只认 ACTIVE；到期后必须重新登录。
 *
 * 防枚举：账号不存在时执行同成本等级的 dummy BCrypt 比对，响应文案不区分两种失败。
 *
 * 事务边界：认证流程不以方法级 @Transactional 包裹——BCrypt 校验/哈希（单次
 * 几十毫秒纯 CPU）持有连接执行会在 CPU 饱和时级联耗尽连接池并拖垮全站
 * （且 LoginAttemptService 全部 REQUIRES_NEW，外层事务下还会"持一等一"自锁）。
 * 查询走自动提交即借即还，密码学运算在无连接状态下执行；唯一需要原子性的
 * 多语句小节（临时封禁恢复、改密+版本推进）用 TransactionTemplate 收进短事务
 * ——不能用本类 @Transactional 私有方法替代，self-invocation 不经过代理。
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
    /** 防枚举 dummy 哈希：与真实 BCrypt 同成本等级，明文随机已销毁 */
    private static final String DUMMY_BCRYPT = "$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final LoginAttemptService loginAttemptService;
    private final SchoolService schoolService;
    private final TransactionTemplate transactionTemplate;
    private final AuthAdmissionGate admissionGate;

    /**
     * 注册（需求 1.1）
     * 并发安全：唯一性靠 DB 的 uk_school_student 联合唯一索引兜底 —— 先查后插在并发注册时存在竞态，
     * 捕获 DuplicateKeyException 统一转为业务提示。
     *
     * 事务边界：唯一的写是单条 INSERT（自动提交即原子），无方法级事务；
     * 两次 BCrypt encode 在无连接状态下执行。
     */
    public LoginVO register(RegisterDTO dto) {
        return admissionGate.withAdmission(() -> doRegister(dto));
    }

    private LoginVO doRegister(RegisterDTO dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "两次输入的密码不一致");
        }
        String studentId = StudentIdNormalizer.normalize(dto.getStudentId());
        // 学校必填且必须启用（创新功能 A2：注册即归属学校）；SYSTEM 学校为 DISABLED，
        // 技术主体不存在任何注册/登录入口。
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
            if (exists.getStatus() == UserStatus.CANCELLED) {
                throw new BusinessException(ResultCode.USER_CANCELLED, "该学号在当前学校的账户已注销，注销后不可恢复");
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
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setLevel(1);
        user.setExp(0);
        user.setTokenVersion(0);
        user.setProfileVersion(0L);
        user.setIsSystem(false);
        user.setWalletBalance(BigDecimal.ZERO);
        user.setSecurityQuestion(dto.getSecurityQuestion());
        user.setSecurityAnswer(passwordEncoder.encode(normalizeAnswer(dto.getSecurityAnswer()))); // 密保答案同样加密

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.STUDENT_ID_EXISTS, "该学号已在当前学校注册，请直接登录或找回密码");
        }

        String token = jwtUtils.generateToken(
                user.getId(), user.getRole().code(), user.getTokenVersion());
        return new LoginVO(token, UserVO.from(user, school.getName()));
    }

    /**
     * 登录（需求 1.2）—— 失败限流（数据库状态机）+ 临时封禁到期原子恢复（数据库时间）。
     * 准入闸门在触碰任何数据库之前限流并发认证，超限快速 429（AUTH_BUSY）。
     */
    public LoginVO login(LoginDTO dto) {
        return admissionGate.withAdmission(() -> doLogin(dto));
    }

    private LoginVO doLogin(LoginDTO dto) {
        String studentId = StudentIdNormalizer.normalize(dto.getStudentId());
        School school = requireActiveSchool(dto.getSchoolId());
        String loginKey = accountKey(school.getId(), studentId);
        // 失败限流（REQUIRES_NEW 独立事务）：BCrypt 校验开销大，先挡住暴力尝试
        if (loginAttemptService.isLocked(loginKey)) {
            throw locked(loginKey);
        }

        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getSchoolId, school.getId())
                .eq(SysUser::getStudentId, studentId)
                .eq(SysUser::getRole, UserRole.USER)
                .eq(SysUser::getIsSystem, false));
        if (user == null) {
            // 同成本 dummy 比对：账号不存在与密码错误的响应时间和文案完全一致；
            // BCrypt 在无连接状态下执行（见类注释事务边界）
            passwordEncoder.matches(dto.getPassword(), DUMMY_BCRYPT);
            loginAttemptService.recordFailure(loginKey);
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "学号或密码错误");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailure(loginKey);
            // 不区分「用户不存在」与「密码错误」，防止学号枚举
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "学号或密码错误");
        }

        // 封禁/注销状态检查
        if (user.getStatus() == UserStatus.CANCELLED) {
            throw new BusinessException(ResultCode.USER_CANCELLED, "该账户已注销，注销后不可恢复");
        }
        if (user.getStatus() == UserStatus.BANNED_PERM) {
            throw new BusinessException(ResultCode.USER_BANNED, "该账户已被永久封禁");
        }
        if (user.getStatus() == UserStatus.BANNED_TEMP) {
            // 恢复迁移是唯一需要多语句原子性的小节，收进短事务
            SysUser bannedTemp = user;
            user = transactionTemplate.execute(status -> recoverOrRejectBannedTemp(bannedTemp));
        }

        loginAttemptService.reset(loginKey);
        String token = jwtUtils.generateToken(
                user.getId(), user.getRole().code(), user.getTokenVersion());
        return new LoginVO(token, UserVO.from(user, school.getName()));
    }

    /**
     * 临时封禁到期恢复（须在事务内执行）：
     * 条件 UPDATE（数据库时间判定 + 同 SQL 推进 token_version）恰好一付认成功，
     * 其余并发事务影响 0 行；FOR UPDATE 重读最新状态（REPEATABLE READ 下普通
     * SELECT 可能读快照；本事务自身的修改对当前读可见，fresh 已含最新
     * status/token_version，无需再回读）。
     */
    private SysUser recoverOrRejectBannedTemp(SysUser bannedTemp) {
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, bannedTemp.getId())
                .eq(SysUser::getStatus, UserStatus.BANNED_TEMP)
                .apply("ban_until_time <= CURRENT_TIMESTAMP(6)")
                .set(SysUser::getStatus, UserStatus.ACTIVE)
                .set(SysUser::getBanUntilTime, null)
                .setSql("token_version = token_version + 1"));

        SysUser fresh = userMapper.selectByIdForUpdate(bannedTemp.getId());
        if (fresh == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return switch (fresh.getStatus()) {
            case ACTIVE -> fresh;
            case BANNED_TEMP -> throw new BusinessException(ResultCode.USER_BANNED,
                    "您的账户已被封禁至 " + fresh.getBanUntilTime().format(BAN_TIME_FMT));
            case BANNED_PERM -> throw new BusinessException(ResultCode.USER_BANNED, "该账户已被永久封禁");
            case CANCELLED -> throw new BusinessException(ResultCode.USER_CANCELLED, "该账户已注销");
        };
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
                .eq(SysUser::getRole, UserRole.USER));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "该学号尚未注册");
        }
        if (user.getStatus() == UserStatus.CANCELLED) {
            throw new BusinessException(ResultCode.USER_CANCELLED, "该账户已注销，注销后不可恢复");
        }
        return user.getSecurityQuestion();
    }

    /**
     * 验证密保并重置密码（需求 1.3）
     * 重置成功后推进 tokenVersion，使所有旧 Token 立即失效。
     * 密保答案失败计数在业务异常抛出前由协调器独立事务提交。
     * 事务边界：两次 BCrypt 比对与哈希计算在无连接状态下执行；
     * 「写新密码 + 推进版本」两条语句必须原子，收进短事务。
     */
    public void resetPassword(ResetPasswordDTO dto) {
        admissionGate.withAdmission(() -> {
            doResetPassword(dto);
            return null;
        });
    }

    private void doResetPassword(ResetPasswordDTO dto) {
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "两次输入的密码不一致");
        }
        String studentId = StudentIdNormalizer.normalize(dto.getStudentId());
        School school = requireActiveSchool(dto.getSchoolId());
        // 密保答案验证也走失败限流，防止暴力猜答案
        String lockKey = "reset:" + accountKey(school.getId(), studentId);
        if (loginAttemptService.isLocked(lockKey)) {
            throw locked(lockKey);
        }

        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getSchoolId, school.getId())
                .eq(SysUser::getStudentId, studentId)
                .eq(SysUser::getRole, UserRole.USER));
        if (user == null) {
            passwordEncoder.matches(dto.getSecurityAnswer(), DUMMY_BCRYPT);
            loginAttemptService.recordFailure(lockKey);
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "学号或密保答案错误");
        }
        if (user.getStatus() == UserStatus.CANCELLED) {
            throw new BusinessException(ResultCode.USER_CANCELLED, "该账户已注销，注销后不可恢复");
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

        String newHash = passwordEncoder.encode(dto.getNewPassword());
        transactionTemplate.execute(status -> {
            SysUser patch = new SysUser();
            patch.setId(user.getId());
            patch.setPassword(newHash);
            userMapper.updateById(patch);

            int affected = userMapper.bumpTokenVersion(user.getId());
            if (affected == 0) {
                throw new BusinessException(ResultCode.USER_NOT_FOUND);
            }
            return null;
        });

        loginAttemptService.reset(lockKey);
        log.info("用户 {} 通过密保重置了密码", user.getStudentId());
    }

    /** 登录/密保锁定：附数据库计算的剩余秒数（Retry-After 实例覆盖）。 */
    private BusinessException locked(String attemptKey) {
        return new BusinessException(ResultCode.LOGIN_LOCKED,
                ResultCode.LOGIN_LOCKED.getMessage())
                .withRetryAfterSeconds(loginAttemptService.remainingLockSeconds(attemptKey));
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
