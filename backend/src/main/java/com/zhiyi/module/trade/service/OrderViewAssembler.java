package com.zhiyi.module.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.enums.OrderStatus;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.entity.TradeReview;
import com.zhiyi.module.trade.mapper.TradeReviewMapper;
import com.zhiyi.module.trade.vo.OrderVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderViewAssembler {

    private final ItemMapper itemMapper;
    private final SysUserMapper userMapper;
    private final TradeReviewMapper reviewMapper;

    public IPage<OrderVO> assemblePage(IPage<TradeOrder> orders, Perspective perspective) {
        List<TradeOrder> records = orders.getRecords();
        Page<OrderVO> result = new Page<>(orders.getCurrent(), orders.getSize(), orders.getTotal());
        if (records.isEmpty()) {
            result.setRecords(List.of());
            return result;
        }

        Set<Long> itemIds = records.stream().map(TradeOrder::getItemId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> peerIds = records.stream()
                .map(order -> perspective == Perspective.BUYER ? order.getSellerId() : order.getBuyerId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Item> items = itemMapper.selectByIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        Map<Long, SysUser> peers = userMapper.selectByIds(peerIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Set<Long> reviewedOrderIds = perspective == Perspective.BUYER
                ? reviewedOrderIds(records)
                : Set.of();

        result.setRecords(records.stream().map(order -> {
            Long peerId = perspective == Perspective.BUYER ? order.getSellerId() : order.getBuyerId();
            SysUser peer = peers.get(peerId);
            OrderVO vo = assemble(order, items.get(order.getItemId()), peer == null ? null : peer.getNickname());
            vo.setReviewed(reviewedOrderIds.contains(order.getId()));
            return vo;
        }).toList());
        return result;
    }

    public OrderVO assemble(TradeOrder order, Item item, String peerNickname) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setItemId(order.getItemId());
        vo.setBuyerId(order.getBuyerId());
        vo.setSellerId(order.getSellerId());
        vo.setPrice(order.getPrice());
        vo.setStatus(order.getStatus().code());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setCompletedAt(order.getCompletedAt());
        vo.setCancelledAt(order.getCancelledAt());
        // 单订单响应（下单/确认/取消）语义上必然"尚未评价"，显式赋默认值而非留 null（P0-3）
        vo.setReviewed(false);
        if (item != null) {
            vo.setItemTitle(item.getTitle());
            List<String> images = item.getImages();
            vo.setItemCover(images == null || images.isEmpty() ? null : images.getFirst());
        }
        vo.setPeerNickname(peerNickname);
        return vo;
    }

    private Set<Long> reviewedOrderIds(List<TradeOrder> orders) {
        Set<Long> completedIds = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.COMPLETED)
                .map(TradeOrder::getId)
                .collect(Collectors.toSet());
        if (completedIds.isEmpty()) return Set.of();
        return reviewMapper.selectList(new LambdaQueryWrapper<TradeReview>()
                        .in(TradeReview::getOrderId, completedIds)
                        .select(TradeReview::getOrderId))
                .stream().map(TradeReview::getOrderId).collect(Collectors.toSet());
    }

    public enum Perspective {
        BUYER, SELLER
    }
}
