package com.zhiyi.module.social.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事务 Outbox 事件。与业务数据同事务写入；消费者以单事件事务
 * （SKIP LOCKED 领取 → 插入 chat_message → 置 SENT）完成至少一次投递。
 */
@Data
@TableName("outbox_event")
public class OutboxEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 生产者事务中生成的业务唯一事件身份；一条面向一个接收者的消息对应一个 event_id */
    private String eventId;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
    /** JSON 字符串（receiverId / content / relatedItemId 等） */
    private String payload;
    private String status;
    private Integer attempts;
    private LocalDateTime nextRetryAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;
}
