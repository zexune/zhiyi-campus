package com.zhiyi.module.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.module.social.dto.ConversationAggregate;
import com.zhiyi.module.social.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 按会话聚合：每个会话一行（最后消息、对端、关联商品、未读数），
     * 结果行数 = 用户会话数，与消息总量解耦；可命中 idx_sender_created / idx_receiver_unread。
     *
     * peerId 推导：会话内每条消息的"另一方"都是同一对端，MAX 即取该常量；
     * relatedItemId 取 MAX（忽略 NULL）：会话内关联商品应唯一（messagesInternal 会拒绝多商品会话）。
     */
    @Select("""
            SELECT conversation_id AS conversationId,
                   MAX(id) AS lastMessageId,
                   MAX(CASE WHEN sender_id = #{userId} THEN receiver_id ELSE sender_id END) AS peerId,
                   MAX(related_item_id) AS relatedItemId,
                   CAST(SUM(CASE WHEN receiver_id = #{userId} AND is_read = 0 THEN 1 ELSE 0 END) AS SIGNED) AS unreadCount
            FROM chat_message
            WHERE sender_id = #{userId} OR receiver_id = #{userId}
            GROUP BY conversation_id
            ORDER BY MAX(id) DESC
            """)
    List<ConversationAggregate> aggregateConversations(@Param("userId") Long userId);

    /** 全局未读数：单条 idx_chat_receiver_unread (receiver_id, is_read, id) 覆盖索引 COUNT，固定成本。 */
    @Select("SELECT COUNT(*) FROM chat_message WHERE receiver_id = #{userId} AND is_read = 0")
    long countUnreadByReceiver(@Param("userId") Long userId);
}
