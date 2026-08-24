package com.zhiyi.module.item.service;

import com.zhiyi.module.item.entity.ViewFlush;
import com.zhiyi.module.item.mapper.ItemViewStatMapper;
import com.zhiyi.module.item.mapper.ViewFlushMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 浏览量刷新事务：flush_id 凭据与全部统计累加在同一事务提交。
 *
 * 幂等：重试复用原 flush_id；凭据行已存在说明该批次已持久化，直接返回成功，
 * 不会重复累计。已持久化计数只增不减。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCountFlushService {

    private final ViewFlushMapper viewFlushMapper;
    private final ItemViewStatMapper statMapper;

    /** 独立事务提交一个刷新批次；返回 true 表示已持久化（含幂等重试命中）。 */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public boolean persist(String flushId, Map<Long, Long> snapshot) {
        if (viewFlushMapper.selectById(flushId) != null) {
            // 同一 flush_id 的重试：批次已提交，幂等返回，不重复累计。
            log.info("浏览量刷新批次幂等命中 flushId={} items={}", flushId, snapshot.size());
            return true;
        }
        ViewFlush flush = new ViewFlush();
        flush.setFlushId(flushId);
        flush.setItemCount(snapshot.size());
        viewFlushMapper.insert(flush);
        snapshot.forEach(statMapper::accumulate);
        return true;
    }
}
