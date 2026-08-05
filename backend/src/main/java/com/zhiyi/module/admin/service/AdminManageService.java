package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.admin.entity.ViolationLog;
import com.zhiyi.module.admin.mapper.ViolationLogMapper;
import com.zhiyi.module.admin.vo.AdminItemVO;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.service.UserGrowthService;
import com.zhiyi.module.user.support.UserStateCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 超管内容强制管理服务 —— 4.7
 *
 * 强制下架：任意商品 → OFF_SHELF + 扣卖家经验 + 记录处罚日志
 * 强制重置密码：任意用户密码 → 123456 + Token 失效
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminManageService {

    private final ItemMapper itemMapper;
    private final SysUserMapper sysUserMapper;
    private final ViolationLogMapper violationLogMapper;
    private final UserGrowthService growthService;
    private final PasswordEncoder passwordEncoder;
    private final UserStateCache userStateCache;

    /**
     * 管理员商品检索 —— 4.7 强制下架前选择商品用
     * 按标题/ID 搜索，支持状态筛选，分页，返回发布者昵称
     */
    public IPage<AdminItemVO> searchItems(String keyword, String status, int page, int size) {
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<Item>();
        if (StringUtils.hasText(keyword)) {
            // 纯数字 → 按 ID 精确匹配 + 标题模糊搜索
            if (keyword.matches("\\d+")) {
                try {
                    Long itemId = Long.parseLong(keyword);
                    wrapper.and(w -> w.like(Item::getTitle, keyword).or().eq(Item::getId, itemId));
                } catch (NumberFormatException e) {
                    wrapper.like(Item::getTitle, keyword);
                }
            } else {
                wrapper.like(Item::getTitle, keyword);
            }
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Item::getStatus, status);
        }
        wrapper.orderByDesc(Item::getId);

        IPage<Item> itemPage = itemMapper.selectPage(
                new Page<>(page, Math.min(size, 50)), wrapper);

        // 批量取发布者昵称
        List<Long> publisherIds = itemPage.getRecords().stream()
                .map(Item::getPublisherId).distinct().collect(Collectors.toList());
        Map<Long, SysUser> userMap = publisherIds.isEmpty()
                ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(publisherIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        return itemPage.convert(item -> toAdminItemVO(item, userMap));
    }

    private AdminItemVO toAdminItemVO(Item item, Map<Long, SysUser> userMap) {
        AdminItemVO vo = new AdminItemVO();
        vo.setId(item.getId());
        vo.setTitle(item.getTitle());
        vo.setType(item.getType());
        vo.setPrice(item.getPrice());
        vo.setStatus(item.getStatus());
        vo.setPublisherId(item.getPublisherId());
        SysUser publisher = userMap.get(item.getPublisherId());
        vo.setPublisherNickname(publisher == null ? null : publisher.getNickname());
        vo.setCreatedAt(item.getCreatedAt());
        return vo;
    }

    /**
     * 强制下架商品
     */
    @Transactional(rollbackFor = Exception.class)
    public void forceOffShelf(Long itemId, Long adminId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        if ("OFF_SHELF".equals(item.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该商品已处于下架状态");
        }

        // 1. 下架商品
        item.setStatus("OFF_SHELF");
        itemMapper.updateById(item);

        // 2. 扣卖家经验
        growthService.addExp(item.getPublisherId(),
                UserGrowthService.EXP_FORCED_OFF_SHELF, "商品被管理员强制下架");

        // 3. 记录处罚日志
        ViolationLog vlog = new ViolationLog();
        vlog.setUserId(item.getPublisherId());
        vlog.setAdminId(adminId);
        vlog.setType("WARNING");
        vlog.setReason("商品「" + item.getTitle() + "」被管理员强制下架");
        violationLogMapper.insert(vlog);

        log.info("管理员 {} 强制下架商品 itemId={} title={} publisherId={}",
                adminId, itemId, item.getTitle(), item.getPublisherId());
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
        if ("ADMIN".equals(user.getRole())) {
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
