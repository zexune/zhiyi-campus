package com.zhiyi.module.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.Result;
import com.zhiyi.common.annotation.RoleRequired;
import com.zhiyi.module.admin.entity.ViolationLog;
import com.zhiyi.module.admin.mapper.ViolationLogMapper;
import com.zhiyi.module.user.dto.BanUserDTO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.service.BanService;
import com.zhiyi.module.user.vo.UserVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模块一：违规封禁管理接口（管理员，B.4 附录接口清单中属于用户处罚的部分）
 *
 * GET  /api/admin/users           用户检索（学号/昵称，风控工作台用）
 * POST /api/admin/ban-user        封禁/警告用户
 * POST /api/admin/unban-user      提前解封 / 恢复注销账户
 * GET  /api/admin/violation-logs  处罚记录（可追溯）
 */
@Validated
@RoleRequired("ADMIN")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class BanController {

    private final BanService banService;
    private final ViolationLogMapper violationLogMapper;
    private final SysUserMapper userMapper;

    @GetMapping("/users")
    public Result<IPage<UserVO>> searchUsers(@RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        IPage<SysUser> result = userMapper.selectPage(
                new Page<>(page, Math.min(size, 50)),
                Wrappers.<SysUser>lambdaQuery()
                        .select(SysUser::getId, SysUser::getStudentId, SysUser::getNickname,
                                SysUser::getRole, SysUser::getStatus, SysUser::getBanUntilTime,
                                SysUser::getLevel, SysUser::getExp, SysUser::getCreatedAt)
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(SysUser::getStudentId, keyword).or()
                                .like(SysUser::getNickname, keyword))
                        .orderByDesc(SysUser::getId));
        return Result.ok(result.convert(UserVO::from));
    }

    @PostMapping("/ban-user")
    public Result<Void> banUser(@RequestAttribute("userId") Long adminId,
                                @Valid @RequestBody BanUserDTO dto) {
        banService.punish(dto, adminId);
        return Result.ok("处罚已执行", null);
    }

    @PostMapping("/unban-user")
    public Result<Void> unbanUser(@RequestAttribute("userId") Long adminId,
                                  @RequestBody Map<String, Long> body) {
        Long targetId = body.get("userId");
        if (targetId == null) {
            return Result.fail(400, "用户ID不能为空");
        }
        banService.unban(targetId, adminId);
        return Result.ok("已解封", null);
    }

    @GetMapping("/violation-logs")
    public Result<IPage<Map<String, Object>>> violationLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") @NotNull int page,
            @RequestParam(defaultValue = "10") int size) {
        IPage<ViolationLog> result = violationLogMapper.selectPage(
                new Page<>(page, Math.min(size, 50)),
                Wrappers.<ViolationLog>lambdaQuery()
                        .eq(userId != null, ViolationLog::getUserId, userId)
                        .orderByDesc(ViolationLog::getId));

        // 批量补充被处罚用户的学号/昵称（单条 IN 查询，避免 N+1）
        Set<Long> userIds = result.getRecords().stream()
                .map(ViolationLog::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .select(SysUser::getId, SysUser::getStudentId, SysUser::getNickname)
                        .in(SysUser::getId, userIds))
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u));

        IPage<Map<String, Object>> enriched = result.convert(logRow -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", logRow.getId());
            row.put("userId", logRow.getUserId());
            row.put("type", logRow.getType());
            row.put("reason", logRow.getReason());
            row.put("banDays", logRow.getBanDays());
            row.put("createdAt", logRow.getCreatedAt());
            SysUser u = userMap.get(logRow.getUserId());
            row.put("studentId", u != null ? u.getStudentId() : null);
            row.put("nickname", u != null ? u.getNickname() : null);
            return row;
        });
        return Result.ok(enriched);
    }
}
