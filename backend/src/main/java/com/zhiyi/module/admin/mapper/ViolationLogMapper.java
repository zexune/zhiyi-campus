package com.zhiyi.module.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.module.admin.entity.ViolationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ViolationLogMapper extends BaseMapper<ViolationLog> {

    /** 该用户最近一条封禁日志（unban 通知的确定性 event_id 来源：一轮回封对应一条日志）。 */
    @Select("""
            SELECT * FROM violation_log
             WHERE user_id = #{userId} AND type IN ('BAN_TEMP', 'BAN_PERM')
             ORDER BY id DESC
             LIMIT 1
            """)
    ViolationLog selectLatestBanLog(@Param("userId") Long userId);
}
