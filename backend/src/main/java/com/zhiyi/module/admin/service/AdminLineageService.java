package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.admin.vo.ItemLineageVO;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品传承链服务 —— D3
 *
 * GET /api/admin/item/{id}/lineage
 * 返回从发布者到最后一位买家的完整传承链
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLineageService {

    private final ItemMapper itemMapper;
    private final TradeOrderMapper orderMapper;
    private final SysUserMapper userMapper;

    /**
     * 用户端兼容入口：用户接口已在调用前完成同校可见性校验。
     */
    public ItemLineageVO getLineage(Long itemId) {
        return getLineage(itemId, null);
    }

    public ItemLineageVO getLineage(Long itemId, Long schoolId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }

        // 学校隔离：指定 schoolId 时仅返回该校商品的传承链
        if (schoolId != null && !schoolId.equals(item.getSchoolId())) {
            ItemLineageVO vo = new ItemLineageVO();
            vo.setItemId(itemId);
            vo.setItemTitle("未知商品");
            vo.setChain(List.of());
            return vo;
        }

        ItemLineageVO vo = new ItemLineageVO();
        vo.setItemId(item.getId());
        vo.setItemTitle(item.getTitle());

        List<ItemLineageVO.LineageNode> chain = new ArrayList<>();

        // 收集所有涉及的用户 ID
        List<TradeOrder> completedOrders = orderMapper.selectList(
                new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getItemId, itemId)
                        .eq(TradeOrder::getStatus, "COMPLETED")
                        .orderByAsc(TradeOrder::getCompletedAt));

        // 批量查用户
        List<Long> userIds = new ArrayList<>();
        userIds.add(item.getPublisherId());
        completedOrders.forEach(o -> userIds.add(o.getBuyerId()));
        Map<Long, SysUser> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        // 第一环：发布者
        ItemLineageVO.LineageNode publisher = new ItemLineageVO.LineageNode();
        publisher.setUserId(item.getPublisherId());
        publisher.setNickname(nickOf(userMap, item.getPublisherId()));
        publisher.setRole("PUBLISHER");
        publisher.setPrice(null);
        publisher.setTime(item.getCreatedAt());
        chain.add(publisher);

        // 后续：每位买家
        for (TradeOrder o : completedOrders) {
            ItemLineageVO.LineageNode node = new ItemLineageVO.LineageNode();
            node.setUserId(o.getBuyerId());
            node.setNickname(nickOf(userMap, o.getBuyerId()));
            node.setRole("BUYER");
            node.setPrice(o.getPrice());
            node.setTime(o.getCompletedAt());
            chain.add(node);
        }

        vo.setChain(chain);
        return vo;
    }

    private String nickOf(Map<Long, SysUser> userMap, Long userId) {
        SysUser u = userMap.get(userId);
        return u != null ? u.getNickname() : "未知用户";
    }
}
