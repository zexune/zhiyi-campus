package com.zhiyi.module.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.common.enums.OrderStatus;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.vo.DailyTradeStatRow;
import com.zhiyi.module.trade.vo.TradeLocationStatRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {

    /** 当前读锁定订单行；锁定后必须重读状态，不得使用无锁读取时的快照。 */
    @Select("SELECT * FROM trade_order WHERE id = #{id} FOR UPDATE")
    TradeOrder selectByIdForUpdate(@Param("id") Long id);

    /** 查询商品当前进行中的订单（无锁读，仅用于确定锁序；uk 保证至多一条）。 */
    @Select("SELECT * FROM trade_order WHERE item_id = #{itemId} AND status = 'WAITING_MEET' LIMIT 1")
    TradeOrder selectActiveByItemId(@Param("itemId") Long itemId);

    /**
     * 查询商品当前进行中的订单并锁定（uk_order_active_item 保证至多一条）。
     * FOR UPDATE 当前读：REPEATABLE READ 事务内的一致性快照可能落后于
     * 刚提交的下单/确认事务，强制撤单判定必须基于锁定时刻的最新状态。
     */
    @Select("SELECT * FROM trade_order WHERE item_id = #{itemId} AND status = 'WAITING_MEET' LIMIT 1 FOR UPDATE")
    TradeOrder selectActiveByItemIdForUpdate(@Param("itemId") Long itemId);

    /**
     * 封禁自动取消路径：当前读锁定买家全部进行中订单，按 item_id 升序。
     * 本方法为"用户行 → 订单行 → 商品行"锁序的入口（调用方必须已持有买家用户行锁）——
     * 与全局规范 §3 表格的"商品 → 订单"相反；偏差的安全论证见
     * ForceCancelService#cancelActiveOrdersOfBuyer 注释（用户行锁前置串行化，无反向成环路径）。
     * 必须用 FOR UPDATE 当前读：RR 快照可能漏掉刚提交的挂单（I11）。
     */
    @Select("SELECT * FROM trade_order WHERE buyer_id = #{buyerId} AND status = 'WAITING_MEET' "
            + "ORDER BY item_id, id FOR UPDATE")
    List<TradeOrder> selectActiveByBuyerForUpdate(@Param("buyerId") Long buyerId);

    @Select("""
            <script>
            SELECT COALESCE(SUM(o.price), 0)
              FROM trade_order o
              JOIN item i ON i.id = o.item_id
             WHERE o.status = #{status}
               AND o.completed_at &gt;= #{start}
               AND o.completed_at &lt; #{end}
            <if test="schoolId != null">
               AND i.school_id = #{schoolId}
            </if>
            </script>
            """)
    BigDecimal sumCompletedAmount(@Param("status") OrderStatus status,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end,
                                  @Param("schoolId") Long schoolId);

    @Select("""
            <script>
            SELECT DATE(o.completed_at) AS tradeDate,
                   COUNT(*) AS tradeCount,
                   COALESCE(SUM(o.price), 0) AS totalAmount
              FROM trade_order o
              JOIN item i ON i.id = o.item_id
             WHERE o.status = #{status}
               AND o.completed_at &gt;= #{start}
               AND o.completed_at &lt; #{end}
            <if test="schoolId != null">
               AND i.school_id = #{schoolId}
            </if>
             GROUP BY DATE(o.completed_at)
             ORDER BY tradeDate ASC
            </script>
            """)
    List<DailyTradeStatRow> selectDailyStats(@Param("status") OrderStatus status,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             @Param("schoolId") Long schoolId);

    @Select("""
            <script>
            SELECT TRIM(i.trade_location) AS location, COUNT(*) AS tradeCount
              FROM trade_order o
              JOIN item i ON i.id = o.item_id
             WHERE o.status = #{status}
               AND i.trade_location IS NOT NULL
               AND TRIM(i.trade_location) != ''
            <if test="schoolId != null">
               AND i.school_id = #{schoolId}
            </if>
             GROUP BY TRIM(i.trade_location)
             ORDER BY tradeCount DESC, location ASC
            </script>
            """)
    List<TradeLocationStatRow> selectLocationStats(@Param("status") OrderStatus status,
                                                   @Param("schoolId") Long schoolId);
}
