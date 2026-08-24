package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.common.enums.ViolationSource;
import com.zhiyi.common.enums.ViolationStatus;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品审核状态聚合投影（B4 根因修复）。
 *
 * 投影规则（调用方必须已持有该商品行锁作为聚合串行点）：
 * - 存在 CONFIRMED → moderation_status = REJECTED；
 * - 否则存在相关 PENDING（LOCAL_RULE/CORRECTION，USER_REPORT 不影响可见性）→ PENDING；
 * - 两者皆无 → PASSED。
 *
 * 商品 status 永不自动重新上架：仅 REJECTED 时把 ON_SALE/RESERVED 压到 OFF_SHELF；
 * 恢复上架一律由卖家手动 relist（relist 自身有 PASSED 校验）。
 * 投影 UPDATE 影响 0 行代表商品已删除或状态未变，均为合法幂等结果，不按冲突处理。
 */
@Service
@RequiredArgsConstructor
public class ModerationProjectionService {

    private final ViolationReportMapper violationReportMapper;
    private final ItemMapper itemMapper;

    /** REQUIRED 传播：加入调用方（审核/申诉）事务，投影与其决定同生共死。 */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void projectItemModerationStatus(Long itemId) {
        boolean hasConfirmed = violationReportMapper.selectCount(new LambdaQueryWrapper<ViolationReport>()
                .eq(ViolationReport::getItemId, itemId)
                .eq(ViolationReport::getStatus, ViolationStatus.CONFIRMED)) > 0;
        // 仅影响审核状态的来源参与 PENDING 计数；USER_REPORT 不经确认不影响商品可见性
        boolean hasPending = violationReportMapper.selectCount(new LambdaQueryWrapper<ViolationReport>()
                .eq(ViolationReport::getItemId, itemId)
                .eq(ViolationReport::getStatus, ViolationStatus.PENDING)
                .in(ViolationReport::getSource, ViolationSource.LOCAL_RULE, ViolationSource.CORRECTION)) > 0;

        ModerationStatus target = hasConfirmed ? ModerationStatus.REJECTED
                : hasPending ? ModerationStatus.PENDING
                : ModerationStatus.PASSED;

        int updated = itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, itemId)
                .set(Item::getModerationStatus, target)
                .setSql("status = CASE "
                        + "WHEN status IN ('ON_SALE', 'RESERVED') AND '" + target.code() + "' = 'REJECTED' "
                        + "THEN 'OFF_SHELF' ELSE status END"));
        // updated 为 0 表示商品已被删除；状态未变同样是合法幂等结果，均不抛异常
        if (updated == 0) {
            return;
        }
    }
}
