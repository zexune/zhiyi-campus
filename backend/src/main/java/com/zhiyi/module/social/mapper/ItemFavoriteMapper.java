package com.zhiyi.module.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.module.social.entity.ItemFavorite;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.module.item.vo.FavoriteRankRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ItemFavoriteMapper extends BaseMapper<ItemFavorite> {

    @Select("""
            SELECT f.item_id AS itemId, COUNT(*) AS favoriteCount
              FROM item_favorite f
              JOIN item i ON i.id = f.item_id
             WHERE i.school_id = #{schoolId}
               AND i.status = #{itemStatus}
               AND i.moderation_status = #{moderationStatus}
               AND i.is_deleted = 0
             GROUP BY f.item_id
             ORDER BY favoriteCount DESC, f.item_id DESC
             LIMIT #{limit}
            """)
    List<FavoriteRankRow> selectVisibleRanking(@Param("schoolId") Long schoolId,
                                               @Param("limit") int limit,
                                               @Param("itemStatus") ItemStatus itemStatus,
                                               @Param("moderationStatus") ModerationStatus moderationStatus);
}
