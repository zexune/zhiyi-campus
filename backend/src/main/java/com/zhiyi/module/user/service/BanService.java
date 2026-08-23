package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.BanActionType;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.module.admin.entity.ViolationLog;
import com.zhiyi.module.admin.mapper.ViolationLogMapper;
import com.zhiyi.module.user.dto.AdminUserSearchQuery;
import com.zhiyi.module.user.dto.BanUserDTO;
import com.zhiyi.module.user.entity.School;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.event.UserPunishedEvent;
import com.zhiyi.module.user.mapper.SchoolMapper;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.UserStateCache;
import com.zhiyi.module.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 用户管理中的独立账号封禁与用户检索服务。内容警告与合规扣分不经过本服务。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BanService {

    private final SysUserMapper userMapper;
    private final SchoolMapper schoolMapper;
    private final ViolationLogMapper violationLogMapper;
    private final UserStateCache userStateCache;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 管理端用户列表：学校精确匹配，学号/昵称/邮箱/手机号模糊搜索。
     * 只返回普通用户（管理员不进入可处罚名单），先分页再按 ID 集合批量补齐学校名称。
     */
    public IPage<UserVO> searchUsers(AdminUserSearchQuery query, int page, int size) {
        IPage<SysUser> result = userMapper.selectPage(
                new Page<>(page, Math.min(size, 50)),
                Wrappers.<SysUser>lambdaQuery()
                        .select(SysUser::getId, SysUser::getStudentId, SysUser::getNickname,
                                SysUser::getSchoolId, SysUser::getSchoolEmail, SysUser::getPhone,
                                SysUser::getRole, SysUser::getStatus, SysUser::getBanUntilTime,
                                SysUser::getLevel, SysUser::getExp, SysUser::getCreatedAt)
                        .eq(SysUser::getRole, UserRole.USER)
                        .eq(query.hasSchoolFilter(), SysUser::getSchoolId, query.schoolId())
                        .like(query.hasStudentIdFilter(), SysUser::getStudentId, query.studentId())
                        .like(query.hasNicknameFilter(), SysUser::getNickname, query.nickname())
                        .like(query.hasEmailFilter(), SysUser::getSchoolEmail, query.email())
                        .like(query.hasPhoneFilter(), SysUser::getPhone, query.phone())
                        .orderByDesc(SysUser::getId));

        Set<Long> schoolIds = result.getRecords().stream()
                .map(SysUser::getSchoolId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> schoolNames = schoolIds.isEmpty() ? Map.of()
                : schoolMapper.selectList(Wrappers.<School>lambdaQuery()
                        .select(School::getId, School::getName)
                        .in(School::getId, schoolIds))
                .stream().collect(Collectors.toMap(School::getId, School::getName));
        return result.convert(user -> UserVO.from(user, schoolNames.get(user.getSchoolId())));
    }

    @Transactional(rollbackFor = Exception.class)
    public void punish(BanUserDTO dto, Long adminId) {
        SysUser target = userMapper.selectById(dto.getUserId());
        if (target == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (target.getRole() == UserRole.ADMIN) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能处罚管理员账户");
        }

        BanActionType action = parseAction(dto.getType());
        LocalDateTime banUntilTime = null;
        switch (action) {
            case BAN_TEMP -> {
                if (dto.getBanDays() == null || dto.getBanDays() < 1 || dto.getBanDays() > 365) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "封禁天数须为 1-365 天");
                }
                SysUser patch = new SysUser();
                patch.setId(target.getId());
                patch.setStatus(UserStatus.BANNED_TEMP);
                banUntilTime = LocalDateTime.now().plusDays(dto.getBanDays());
                patch.setBanUntilTime(banUntilTime);
                userMapper.updateById(patch);
            }
            case BAN_PERM -> {
                SysUser patch = new SysUser();
                patch.setId(target.getId());
                patch.setStatus(UserStatus.BANNED_PERM);
                userMapper.updateById(patch);
            }
        }

        if (userMapper.bumpTokenVersion(dto.getUserId()) == 0) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        ViolationLog logRecord = new ViolationLog();
        logRecord.setUserId(dto.getUserId());
        logRecord.setAdminId(adminId);
        logRecord.setType(action);
        logRecord.setReason(dto.getReason());
        logRecord.setBanDays(action == BanActionType.BAN_TEMP ? dto.getBanDays() : null);
        violationLogMapper.insert(logRecord);

        eventPublisher.publishEvent(new UserPunishedEvent(
                dto.getUserId(), action.code(), dto.getReason(),
                action == BanActionType.BAN_TEMP ? dto.getBanDays() : null, banUntilTime));
        userStateCache.invalidateAfterCommit(dto.getUserId());
        log.info("管理员 {} 对用户 {} 执行处罚 {}：{}", adminId, dto.getUserId(), action, dto.getReason());
    }

    @Transactional(rollbackFor = Exception.class)
    public void unban(Long userId, Long adminId) {
        SysUser target = userMapper.selectById(userId);
        if (target == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (target.getRole() == UserRole.ADMIN) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能操作管理员账户");
        }
        if (target.getStatus() != UserStatus.BANNED_TEMP
                && target.getStatus() != UserStatus.BANNED_PERM
                && target.getStatus() != UserStatus.CANCELLED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该用户当前未被封禁或注销");
        }

        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setStatus(UserStatus.ACTIVE);
        userMapper.update(patch, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, userId)
                .set(SysUser::getBanUntilTime, null));
        userStateCache.invalidateAfterCommit(userId);
        log.info("管理员 {} 解封用户 {}", adminId, userId);
    }

    private BanActionType parseAction(String value) {
        try {
            return BanActionType.valueOf(value);
        } catch (IllegalArgumentException invalidAction) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "封禁类型仅支持 BAN_TEMP 或 BAN_PERM");
        }
    }
}
