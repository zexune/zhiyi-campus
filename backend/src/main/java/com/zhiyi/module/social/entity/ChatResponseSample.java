package com.zhiyi.module.social.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 响应速度唯一贡献样本：同一"会话+触发消息"只累计一次。 */
@Data
@TableName("chat_response_sample")
public class ChatResponseSample {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sampleKey;
    private Long userId;
    private Long gapSeconds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
