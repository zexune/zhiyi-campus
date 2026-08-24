package com.zhiyi.module.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.module.social.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    /**
     * 领取一条到期 PENDING 事件（数据库时间判定）。FOR UPDATE SKIP LOCKED：
     * 多消费者互不阻塞，未抢到的实例直接进入下一轮。
     */
    @Select("SELECT * FROM outbox_event "
            + "WHERE status = 'PENDING' AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP(6)) "
            + "ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED")
    OutboxEvent pollOnePending();

    /** SENT 提交：必须仍为 PENDING 且影响 1 行，同时记录数据库投递时间。 */
    @Update("UPDATE outbox_event SET status = 'SENT', sent_at = CURRENT_TIMESTAMP(6) "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markSent(@Param("id") Long id);

    /**
     * 失败记录（独立事务）：仅修改仍为 PENDING 的事件，attempts 原子递增，
     * 指数退避计算下一次重试时间；超过上限进入 FAILED 并清除领取资格。
     */
    @Update("""
            UPDATE outbox_event
               SET attempts = attempts + 1,
                   status = IF(attempts + 1 >= #{maxAttempts}, 'FAILED', 'PENDING'),
                   next_retry_at = IF(attempts + 1 >= #{maxAttempts}, NULL,
                                      DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL POWER(2, LEAST(attempts + 1, 6)) SECOND))
             WHERE id = #{id} AND status = 'PENDING'
            """)
    int recordFailure(@Param("id") Long id, @Param("maxAttempts") int maxAttempts);

    /** 清理长期积压监控用：当前 PENDING 数量。 */
    @Select("SELECT COUNT(*) FROM outbox_event WHERE status = 'PENDING'")
    long countPending();
}
