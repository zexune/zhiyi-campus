package com.zhiyi.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.module.user.entity.UserReputationMetric;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserReputationMetricMapper extends BaseMapper<UserReputationMetric> {

    /**
     * 增量汇总：仅在唯一贡献样本首次插入成功后调用，重复事件不会重复累计。
     * EWMA 更新（α=0.1）：ewma = ewma*0.9 + gap*0.1，旧样本指数淡出，
     * 分数反映近期表现而非终身平均。ROUND 保持整数秒，避免浮点累计漂移。
     */
    @Insert("INSERT INTO user_reputation_metric (user_id, sample_count, ewma_gap_seconds, last_sample_at) "
            + "VALUES (#{userId}, 1, #{gapSeconds}, CURRENT_TIMESTAMP(6)) "
            + "ON DUPLICATE KEY UPDATE sample_count = sample_count + 1, "
            + "ewma_gap_seconds = ROUND(ewma_gap_seconds * 0.9 + #{gapSeconds} * 0.1), "
            + "last_sample_at = CURRENT_TIMESTAMP(6)")
    int accumulate(@Param("userId") Long userId, @Param("gapSeconds") long gapSeconds);
}
