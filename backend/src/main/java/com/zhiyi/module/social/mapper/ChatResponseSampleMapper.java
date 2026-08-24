package com.zhiyi.module.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.module.social.entity.ChatResponseSample;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatResponseSampleMapper extends BaseMapper<ChatResponseSample> {

    /** 唯一贡献键防重：返回 1 表示首次贡献（可推进汇总指标），0 表示已累计过。 */
    @Insert("INSERT IGNORE INTO chat_response_sample (sample_key, user_id, gap_seconds) "
            + "VALUES (#{sampleKey}, #{userId}, #{gapSeconds})")
    int insertIgnore(@Param("sampleKey") String sampleKey,
                     @Param("userId") Long userId,
                     @Param("gapSeconds") long gapSeconds);
}
