package com.zhiyi.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.module.item.entity.ItemTag;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.module.item.vo.TagAggregateRow;
import com.zhiyi.module.item.vo.TagTrendVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ItemTagMapper extends BaseMapper<ItemTag> {

    @Select("""
            SELECT i.category_id AS categoryId,
                   c.name AS categoryName,
                   c.sort_order AS categorySort,
                   t.name AS tagName,
                   COUNT(*) AS itemCount
              FROM item i
              JOIN item_tag it ON it.item_id = i.id
              JOIN tag t ON t.id = it.tag_id
              JOIN category c ON c.id = i.category_id
             WHERE i.school_id = #{schoolId}
               AND i.status = #{itemStatus}
               AND i.moderation_status = #{moderationStatus}
               AND i.is_deleted = 0
             GROUP BY i.category_id, c.name, c.sort_order, t.id, t.name
             ORDER BY c.sort_order ASC, itemCount DESC, t.name ASC
            """)
    List<TagAggregateRow> selectVisibleTagAggregates(@Param("schoolId") Long schoolId,
                                                     @Param("itemStatus") ItemStatus itemStatus,
                                                     @Param("moderationStatus") ModerationStatus moderationStatus);

    /**
     * 标签趋势榜：只统计近 windowDays 天新发布商品的标签（当前在售、审核通过），
     * 与 selectVisibleTagAggregates 的存量口径不同——趋势必须有发布时间维度。
     * 时间基准用数据库 CURRENT_TIMESTAMP(6)，与应用时钟无关。
     */
    @Select("""
            SELECT t.name AS tag,
                   COUNT(*) AS itemCount
              FROM item i
              JOIN item_tag it ON it.item_id = i.id
              JOIN tag t ON t.id = it.tag_id
             WHERE i.school_id = #{schoolId}
               AND i.status = #{itemStatus}
               AND i.moderation_status = #{moderationStatus}
               AND i.is_deleted = 0
               AND i.created_at >= DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL #{windowDays} DAY)
             GROUP BY t.id, t.name
             ORDER BY itemCount DESC, t.name ASC
             LIMIT #{limit}
            """)
    List<TagTrendVO> selectRecentTagTrends(@Param("schoolId") Long schoolId,
                                           @Param("itemStatus") ItemStatus itemStatus,
                                           @Param("moderationStatus") ModerationStatus moderationStatus,
                                           @Param("windowDays") int windowDays,
                                           @Param("limit") int limit);
}
