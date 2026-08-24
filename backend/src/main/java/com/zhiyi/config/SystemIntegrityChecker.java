package com.zhiyi.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动完整性巡检：
 * - 数据库必须恰好一个 SYSTEM 技术主体（唯一索引只保证"最多一个"，存在性由本检查保证）；
 * - 必须恰好一个 role=ADMIN 且 is_system=0 的人工管理员（禁止运行时任选其一掩盖配置异常）；
 * - 拒绝违反封禁时间约束的历史异常数据（BANNED_TEMP 必须有到期时间，其他状态必须为空），
 *   不把异常数据静默恢复或放行。
 *
 * 任一检查失败都拒绝启动并告警。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemIntegrityChecker implements ApplicationRunner {

    private final SysUserMapper userMapper;

    @Override
    public void run(ApplicationArguments args) {
        checkSystemSingleton();
        checkSoleHumanAdmin();
        checkBanTimeConsistency();
    }

    private void checkSystemSingleton() {
        long systemCount = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getIsSystem, true));
        if (systemCount != 1) {
            throw new IllegalStateException("启动检查失败：SYSTEM 技术主体必须恰好一个，实际 " + systemCount);
        }
        log.info("启动检查通过：SYSTEM 技术主体恰好一个");
    }

    private void checkSoleHumanAdmin() {
        List<SysUser> admins = userMapper.selectHumanAdmins();
        if (admins.size() != 1) {
            throw new IllegalStateException("启动检查失败：人工管理员（role=ADMIN 且非 SYSTEM）必须恰好一个，实际 "
                    + admins.size());
        }
        log.info("启动检查通过：人工管理员唯一 adminId={}", admins.getFirst().getId());
    }

    private void checkBanTimeConsistency() {
        long broken = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRole, UserRole.USER)
                .and(w -> w
                        .and(t -> t.eq(SysUser::getStatus, "BANNED_TEMP").isNull(SysUser::getBanUntilTime))
                        .or(t -> t.ne(SysUser::getStatus, "BANNED_TEMP").isNotNull(SysUser::getBanUntilTime))));
        if (broken > 0) {
            throw new IllegalStateException("启动检查失败：发现 " + broken
                    + " 条封禁时间约束异常数据（BANNED_TEMP 与到期时间必须同生同灭），拒绝启动");
        }
        log.info("启动检查通过：封禁时间约束数据一致");
    }
}
