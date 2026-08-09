package com.zhiyi.module.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.module.trade.entity.ItemReservation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ItemReservationMapper extends BaseMapper<ItemReservation> {

    @Insert("INSERT IGNORE INTO item_reservation(item_id, buyer_id, created_at) "
            + "VALUES(#{itemId}, #{buyerId}, CURRENT_TIMESTAMP)")
    int tryReserve(@Param("itemId") Long itemId, @Param("buyerId") Long buyerId);
}
