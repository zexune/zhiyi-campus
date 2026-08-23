package com.zhiyi.module.admin.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.module.item.entity.Category;
import com.zhiyi.module.item.mapper.CategoryMapper;
import com.zhiyi.module.item.service.LocalContentAnalyzer;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.UserStateCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 超管账号管理服务 —— 4.7
 *
 * 强制重置密码：任意用户密码 → 123456 + Token 失效。
 * 商品下架与内容处罚统一由内容审核工作台执行，本服务不再承担商品侧操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminManageService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserStateCache userStateCache;
    private final CategoryMapper categoryMapper;
    private final LocalContentAnalyzer contentAnalyzer;

    /**
     * 标签建议（管理端）：按专题名称生成候选标签，仅供专题配置选择，不落库。
     */
    public List<String> suggestTags(String title, Long categoryId) {
        Category category = categoryId == null ? null : categoryMapper.selectById(categoryId);
        return contentAnalyzer.suggestTags(title, category);
    }

    /**
     * 强制重置用户密码
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, Long adminId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getRole() == UserRole.ADMIN) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能重置管理员密码");
        }

        // 1. 重置密码
        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setPassword(passwordEncoder.encode("123456"));
        sysUserMapper.updateById(patch);

        // 2. Token 失效
        if (sysUserMapper.bumpTokenVersion(userId) == 0) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 3. 缓存失效
        userStateCache.invalidateAfterCommit(userId);

        log.info("管理员 {} 强制重置用户 {} ({}) 的密码", adminId, userId, user.getNickname());
    }
}
