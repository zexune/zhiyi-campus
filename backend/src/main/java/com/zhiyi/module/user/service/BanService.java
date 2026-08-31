package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.BanActionType;
import com.zhiyi.common.enums.OrderCancelReason;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.module.admin.entity.ViolationLog;
import com.zhiyi.module.admin.mapper.ViolationLogMapper;
import com.zhiyi.module.social.service.OutboxService;
import com.zhiyi.module.trade.service.ForceCancelService;
import com.zhiyi.module.user.dto.AdminUserSearchQuery;
import com.zhiyi.module.user.dto.BanUserDTO;
import com.zhiyi.module.user.entity.School;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SchoolMapper;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户封禁与检索服务。
 *
 * 事务与锁序（§4.10）：目标用户行 → 其买家进行中订单的商品行（item_id 升序）→ 订单行。
 * 决策：临时与永久封禁均在同一事务内自动取消该用户作为买家的全部 WAITING_MEET
 * 订单并退款（AUTO_CANCEL）；其作为卖家的进行中订单不取消（买家仍可确认/取消，
 * 卖家应得资金可进入被封账户）。临时封禁到期解封后商品保持 OFF_SHELF，由卖家手动上架。
 *
 * 状态机：所有状态迁移都是"锁定后重读 + 明确 expected state + 同 SQL 推进
 * token_version + 数据库时间计算到期值"的条件 UPDATE；CANCELLED 或已达目标状态不可覆盖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BanService {

    private final SysUserMapper userMapper;
    private final SchoolMapper schoolMapper;
    private final ViolationLogMapper violationLogMapper;
    private final ForceCancelService forceCancelService;
    private final OutboxService outboxService;

    /**
     * 管理端用户列表：学校精确匹配，学号/昵称/邮箱/手机号模糊搜索。
     * 只返回普通用户（排除管理员与 SYSTEM 技术主体），先分页再批量补齐学校名称。
     */
    public IPage<UserVO> searchUsers(AdminUserSearchQuery query, int page, int size) {
        IPage<SysUser> result = userMapper.selectPage(
                new Page<>(page, Math.min(size, 50)),
                Wrappers.<SysUser>lambdaQuery()
                        .select(SysUser::getId, SysUser::getStudentId, SysUser::getNickname,
                                SysUser::getAvatar,
                                SysUser::getSchoolId, SysUser::getSchoolEmail, SysUser::getPhone,
                                SysUser::getRole, SysUser::getStatus, SysUser::getBanUntilTime,
                                SysUser::getLevel, SysUser::getExp, SysUser::getCreatedAt)
                        .eq(SysUser::getRole, UserRole.USER)
                        .eq(SysUser::getIsSystem, false)
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
        BanActionType action = parseAction(dto.getType());
        if (action == BanActionType.BAN_TEMP
                && (dto.getBanDays() == null || dto.getBanDays() < 1 || dto.getBanDays() > 365)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "封禁天数须为 1-365 天");
        }

        // 1. 锁定目标用户行，锁后重读（REPEATABLE READ 下普通 SELECT 可能读快照）
        SysUser target = userMapper.selectByIdForUpdate(dto.getUserId());
        if (target == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (target.getRole() == UserRole.ADMIN) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能处罚管理员账户");
        }
        if (Boolean.TRUE.equals(target.getIsSystem())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能处罚 SYSTEM 技术主体");
        }

        // 2. 原子状态迁移（expected state + 数据库时间到期值 + 同 SQL 推进 token_version）
        int updated;
        if (action == BanActionType.BAN_TEMP) {
            updated = userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, dto.getUserId())
                    .in(SysUser::getStatus, UserStatus.ACTIVE, UserStatus.BANNED_TEMP)
                    .set(SysUser::getStatus, UserStatus.BANNED_TEMP)
                    .setSql("ban_until_time = DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL {0} DAY)", dto.getBanDays())
                    .setSql("token_version = token_version + 1"));
        } else {
            updated = userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, dto.getUserId())
                    .in(SysUser::getStatus, UserStatus.ACTIVE, UserStatus.BANNED_TEMP)
                    .set(SysUser::getStatus, UserStatus.BANNED_PERM)
                    .set(SysUser::getBanUntilTime, null)
                    .setSql("token_version = token_version + 1"));
        }
        if (updated == 0) {
            // 已注销或已是目标封禁状态：不覆盖既有状态
            throw new BusinessException(ResultCode.CONFLICT, "用户状态已变更，无法执行处罚");
        }

        // 3. 同一事务自动取消其作为买家的进行中订单并退款（AUTO_CANCEL）
        forceCancelService.cancelActiveOrdersOfBuyer(
                dto.getUserId(), OrderCancelReason.AUTO_CANCEL,
                "因账号处罚，您作为买家的订单已被系统自动取消",
                "因买家账号处罚，相关订单已被系统自动取消");

        // 4. 处罚日志
        ViolationLog logRecord = new ViolationLog();
        logRecord.setUserId(dto.getUserId());
        logRecord.setAdminId(adminId);
        logRecord.setType(action);
        logRecord.setReason(dto.getReason());
        logRecord.setBanDays(action == BanActionType.BAN_TEMP ? dto.getBanDays() : null);
        violationLogMapper.insert(logRecord);

        // 5. 封禁通知（Outbox 同事务写入；已取消订单的双方另有独立 event_id 通知）
        StringBuilder content = new StringBuilder("你的账号收到平台处理：")
                .append(action.code()).append("。原因：").append(dto.getReason());
        if (action == BanActionType.BAN_TEMP) {
            content.append("，封禁 ").append(dto.getBanDays()).append(" 天");
        }
        outboxService.appendNotice("USER:" + dto.getUserId() + ":BANNED:" + logRecord.getId(),
                OutboxService.AGGREGATE_USER, dto.getUserId(), OutboxService.EVENT_USER_PUNISHED,
                dto.getUserId(), content.toString());

        log.info("管理员 {} 对用户 {} 执行处罚 {}：{}（自动撤单完成）",
                adminId, dto.getUserId(), action, dto.getReason());
    }

    @Transactional(rollbackFor = Exception.class)
    public void unban(Long userId, Long adminId) {
        SysUser target = userMapper.selectByIdForUpdate(userId);
        if (target == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (target.getRole() == UserRole.ADMIN) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能操作管理员账户");
        }
        if (Boolean.TRUE.equals(target.getIsSystem())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能操作 SYSTEM 技术主体");
        }
        if (target.getStatus() != UserStatus.BANNED_TEMP
                && target.getStatus() != UserStatus.BANNED_PERM) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该用户当前未被封禁");
        }

        // 仅允许 BANNED_* → ACTIVE：禁止把 CANCELLED 解封复活
        int updated = userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .in(SysUser::getStatus, UserStatus.BANNED_TEMP, UserStatus.BANNED_PERM)
                .set(SysUser::getStatus, UserStatus.ACTIVE)
                .set(SysUser::getBanUntilTime, null)
                .setSql("token_version = token_version + 1"));
        if (updated == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "用户状态已变更或已注销");
        }

        // 确定性 event_id：以被解除的封禁日志主键为键（punish 与封禁同事务写日志，
        // 一轮回封恰好对应一条日志）；异常缺失时放弃通知并显式告警，不做静默兜底。
        ViolationLog banLog = violationLogMapper.selectLatestBanLog(userId);
        if (banLog == null) {
            log.error("解封通知缺失：用户 {} 处于封禁状态但无封禁日志（数据不一致）", userId);
            return;
        }
        outboxService.appendNotice("USER:" + userId + ":UNBANNED:" + banLog.getId(),
                OutboxService.AGGREGATE_USER, userId, OutboxService.EVENT_USER_UNBANNED,
                userId, "你的账号已被管理员解封，可以重新登录使用了。");
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
