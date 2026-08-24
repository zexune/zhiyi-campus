package com.zhiyi.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.module.item.entity.ItemViewStat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ItemViewStatMapper extends BaseMapper<ItemViewStat> {

    /** 批量累加浏览增量（flush 事务内调用，凭据行已在同一事务写入）。 */
    @Insert("INSERT INTO item_view_stat (item_id, view_count) VALUES (#{itemId}, #{delta}) "
            + "ON DUPLICATE KEY UPDATE view_count = view_count + VALUES(view_count)")
    int accumulate(@Param("itemId") Long itemId, @Param("delta") long delta);
}
