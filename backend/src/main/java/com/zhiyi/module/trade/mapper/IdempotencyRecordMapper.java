package com.zhiyi.module.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.module.trade.entity.IdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecord> {

    /** 当前读锁定唯一键记录：判定执行所有权前必须持有行锁重读。 */
    @Select("SELECT * FROM idempotency_record "
            + "WHERE user_id = #{userId} AND operation = #{operation} AND idempotency_key = #{key} "
            + "FOR UPDATE")
    IdempotencyRecord selectByUniqueForUpdate(@Param("userId") Long userId,
                                              @Param("operation") String operation,
                                              @Param("key") String idempotencyKey);

    /** SUCCESS 提交：必须同时匹配唯一键、owner_token 与 PROCESSING 状态，影响行数必须为 1。 */
    @Update("UPDATE idempotency_record "
            + "SET status = 'SUCCESS', result_snapshot = #{snapshot}, result_version = result_version "
            + "WHERE user_id = #{userId} AND operation = #{operation} AND idempotency_key = #{key} "
            + "AND owner_token = #{ownerToken} AND status = 'PROCESSING'")
    int markSuccess(@Param("userId") Long userId,
                    @Param("operation") String operation,
                    @Param("key") String idempotencyKey,
                    @Param("ownerToken") String ownerToken,
                    @Param("snapshot") String resultSnapshot);
}
