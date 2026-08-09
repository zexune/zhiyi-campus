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
