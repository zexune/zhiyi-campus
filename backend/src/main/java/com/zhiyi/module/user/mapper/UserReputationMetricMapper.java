package com.zhiyi.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.module.user.entity.UserReputationMetric;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserReputationMetricMapper extends BaseMapper<UserReputationMetric> {

    /** 增量汇总：仅在唯一贡献样本首次插入成功后调用，重复事件不会重复累计。 */
    @Insert("INSERT INTO user_reputation_metric (user_id, sample_count, total_gap_seconds) "
            + "VALUES (#{userId}, 1, #{gapSeconds}) "
            + "ON DUPLICATE KEY UPDATE sample_count = sample_count + 1, "
            + "total_gap_seconds = total_gap_seconds + #{gapSeconds}")
    int accumulate(@Param("userId") Long userId, @Param("gapSeconds") long gapSeconds);
}
