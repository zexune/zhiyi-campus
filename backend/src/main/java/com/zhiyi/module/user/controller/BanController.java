package com.zhiyi.module.user.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.ApiSuccess;
import com.zhiyi.common.PageResponse;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.BusinessErrors;
import com.zhiyi.common.annotation.RoleRequired;
import com.zhiyi.module.admin.entity.ViolationLog;
import com.zhiyi.module.admin.mapper.ViolationLogMapper;
import com.zhiyi.module.user.dto.AdminUserSearchQuery;
import com.zhiyi.module.user.dto.BanUserDTO;
import com.zhiyi.module.user.dto.UnbanUserDTO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.service.BanService;
import com.zhiyi.module.user.vo.UserVO;
import com.zhiyi.module.user.vo.ViolationLogRowResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理中的独立账号封禁接口。
 *
 * GET  /api/admin/users           用户列表（学校精确 + 学号/昵称/邮箱/手机号模糊搜索）
 * POST /api/admin/ban-user        限时或永久封禁用户
 * POST /api/admin/unban-user      提前解封 / 恢复注销账户
 * GET  /api/admin/violation-logs  处罚记录（可追溯，命名 DTO，P0-4）
 */
@Validated
@RoleRequired
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class BanController {

    private final BanService banService;
    private final ViolationLogMapper violationLogMapper;
    private final SysUserMapper userMapper;

    @GetMapping("/users")
    @BusinessErrors
    public ApiSuccess<PageResponse<UserVO>> searchUsers(@RequestParam(required = false) Long schoolId,
                                                        @RequestParam(required = false) String studentId,
                                                        @RequestParam(required = false) String nickname,
                                                        @RequestParam(required = false) String email,
                                                        @RequestParam(required = false) String phone,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        return ApiSuccess.ok(PageResponse.from(banService.searchUsers(
                new AdminUserSearchQuery(schoolId, studentId, nickname, email, phone), page, size)));
    }

    /** ORDER_STATUS_ERROR：封禁强制取消在途订单时订单状态已迁移的防御分支。 */
    @PostMapping("/ban-user")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.FORBIDDEN, ResultCode.CONFLICT,
            ResultCode.ORDER_STATUS_ERROR})
    public ApiSuccess<Void> banUser(@RequestAttribute("userId") Long adminId,
                                    @Valid @RequestBody BanUserDTO dto) {
        banService.punish(dto, adminId);
        return ApiSuccess.ok("处罚已执行", null);
    }

    @PostMapping("/unban-user")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.FORBIDDEN, ResultCode.CONFLICT})
    public ApiSuccess<Void> unbanUser(@RequestAttribute("userId") Long adminId,
                                      @Valid @RequestBody UnbanUserDTO dto) {
        banService.unban(dto.getUserId(), adminId);
        return ApiSuccess.ok("已解封", null);
    }

    @GetMapping("/violation-logs")
    @BusinessErrors
    public ApiSuccess<PageResponse<ViolationLogRowResponse>> violationLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ViolationLog> result = violationLogMapper.selectPage(
                new Page<>(page, Math.min(size, 50)),
                Wrappers.<ViolationLog>lambdaQuery()
                        .eq(userId != null, ViolationLog::getUserId, userId)
                        .orderByDesc(ViolationLog::getId));

        // 批量补充被处罚用户的学号/昵称（单条 IN 查询，避免 N+1）；用户已删除时保持 null
        Set<Long> userIds = result.getRecords().stream()
                .map(ViolationLog::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .select(SysUser::getId, SysUser::getStudentId, SysUser::getNickname)
                        .in(SysUser::getId, userIds))
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u));

        return ApiSuccess.ok(PageResponse.from(result.convert(logRow -> {
            ViolationLogRowResponse row = new ViolationLogRowResponse();
            row.setId(logRow.getId());
            row.setUserId(logRow.getUserId());
            row.setType(logRow.getType() == null ? null : logRow.getType().code());
            row.setReason(logRow.getReason());
            row.setBanDays(logRow.getBanDays());
            row.setCreatedAt(logRow.getCreatedAt());
            SysUser u = userMap.get(logRow.getUserId());
            row.setStudentId(u != null ? u.getStudentId() : null);
            row.setNickname(u != null ? u.getNickname() : null);
            return row;
        })));
    }
}
