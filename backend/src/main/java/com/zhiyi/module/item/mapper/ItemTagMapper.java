package com.zhiyi.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.module.item.entity.ItemTag;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.module.item.vo.TagAggregateRow;
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
}
