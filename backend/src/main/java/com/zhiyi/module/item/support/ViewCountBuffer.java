package com.zhiyi.module.item.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 商品浏览量有界增量缓冲（单实例）。
 *
 * 详情读取路径只调用 {@link #record}（内存原子操作，无数据库写）；
 * 后台任务周期性原子交换缓冲区并批量持久化。
 * 溢出策略：达到最大键数后新键直接丢弃（浏览量为非权威近似指标，
 * 产品接受崩溃/溢出时损失一个刷新窗口内的增量）。
 */
@Component
public class ViewCountBuffer {

    private final ConcurrentHashMap<Long, LongAdder> pending = new ConcurrentHashMap<>();
    private final int maxKeys;

    public ViewCountBuffer(@Value("${zhiyi.marketplace.view-buffer-max-keys:50000}") int maxKeys) {
        this.maxKeys = Math.max(1, maxKeys);
    }

    /** 记录一次浏览；缓冲已满时丢弃（有界内存优先于准确性）。 */
    public void record(Long itemId) {
        if (pending.size() >= maxKeys) {
            return;
        }
        pending.computeIfAbsent(itemId, key -> new LongAdder()).increment();
    }

    /** 当前某商品尚未持久化的增量（详情展示用：已持久化计数 + 待刷增量）。 */
    public long pendingDelta(Long itemId) {
        LongAdder adder = pending.get(itemId);
        return adder == null ? 0 : adder.sum();
    }

    /** 原子交换出当前快照（可能为空）。 */
    public Map<Long, Long> swap() {
        if (pending.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> snapshot = new HashMap<>(pending.size());
        for (Map.Entry<Long, LongAdder> entry : pending.entrySet()) {
            long delta = entry.getValue().sumThenReset();
            if (delta > 0) {
                snapshot.put(entry.getKey(), delta);
            }
        }
        // 移除已清零的键，保持缓冲有界
        pending.values().removeIf(adder -> adder.sum() == 0);
        return snapshot;
    }

    // 失败策略说明：刷新失败以同一 flush_id 重试（幂等），重试耗尽后丢弃批次
    // 而不 merge 回缓冲——"结果不明时合并回"与"批次实际已提交"无法区分，
    // 丢弃严格限制在一个刷新窗口内（产品已接受的非权威指标损失），
    // 见 ViewCountFlushJob 的重试与日志。

    public int keyCount() {
        return pending.size();
    }
}
