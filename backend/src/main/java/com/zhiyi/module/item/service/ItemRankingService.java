package com.zhiyi.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemFavoriteMapper;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.item.vo.FavoriteRankRow;
import com.zhiyi.module.item.vo.ItemSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemRankingService {

    private final ItemFavoriteMapper favoriteMapper;
    private final ItemMapper itemMapper;
    private final ItemCardAssembler itemCardAssembler;

    public List<ItemSummaryResponse> ranking(Long schoolId, int limit, Long currentUserId) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        List<FavoriteRankRow> rows = favoriteMapper.selectVisibleRanking(
                schoolId, safeLimit, ItemStatus.ON_SALE, ModerationStatus.PASSED);
        List<Long> rankedIds = rows.stream().map(FavoriteRankRow::itemId).toList();
        Map<Long, Long> counts = rows.stream().collect(Collectors.toMap(
                FavoriteRankRow::itemId, FavoriteRankRow::favoriteCount));
        Map<Long, Item> rankedItems = rankedIds.isEmpty()
                ? Map.of()
                : itemMapper.selectByIds(rankedIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        List<Item> items = rankedIds.stream().map(rankedItems::get)
                .filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));

        if (items.size() < safeLimit) {
            // ON_SALE 等值过滤天然排除 RESERVED（交易中），无需 item_reservation 反连接
            LambdaQueryWrapper<Item> filler = new LambdaQueryWrapper<Item>()
                    .eq(Item::getSchoolId, schoolId)
                    .eq(Item::getStatus, ItemStatus.ON_SALE)
                    .eq(Item::getModerationStatus, ModerationStatus.PASSED)
                    .eq(Item::getIsDeleted, false)
                    .orderByDesc(Item::getCreatedAt)
                    .orderByDesc(Item::getId)
                    .last("LIMIT " + (safeLimit - items.size()));
            if (!rankedIds.isEmpty()) filler.notIn(Item::getId, rankedIds);
            items.addAll(itemMapper.selectList(filler));
        }

        List<ItemSnapshot> snapshots = itemCardAssembler.assemble(items, currentUserId);
        snapshots.forEach(snapshot -> {
            Long count = counts.get(snapshot.getId());
            if (count != null) snapshot.setFavoriteCount(count);
        });
        return snapshots.stream().map(ItemSnapshot::toSummary).toList();
    }
}
