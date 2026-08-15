package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.module.social.dto.ConversationAggregate;
import com.zhiyi.module.social.entity.ChatMessage;
import com.zhiyi.module.social.mapper.ChatMessageMapper;
import com.zhiyi.module.social.vo.ChatUserVO;
import com.zhiyi.module.social.vo.ConversationVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.LevelRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 超管客服收件箱服务 —— 4.6
 *
 * 找到管理员用户，聚合其参与的每个会话的最后消息、未读数（只计发给管理员的）及对端用户。
 * 管理员是全部用户消息的对端，此处必须走 SQL 聚合（每会话一行）+ 批量装配，
 * 不得把全部历史消息拉进内存。
 */
@Service
@RequiredArgsConstructor
public class AdminChatService {

    private final ChatMessageMapper chatMessageMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 管理员客服会话列表
     */
    public List<ConversationVO> getSessions() {
        SysUser admin = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, UserRole.ADMIN)
                .eq(SysUser::getStatus, UserStatus.ACTIVE)
                .orderByAsc(SysUser::getId)
                .last("LIMIT 1"));
        if (admin == null) {
            return List.of();
        }
        Long adminId = admin.getId();

        List<ConversationAggregate> aggregates = chatMessageMapper.aggregateConversations(adminId);
        if (aggregates.isEmpty()) {
            return List.of();
        }

        Map<Long, ChatMessage> lastMessages = chatMessageMapper
                .selectByIds(aggregates.stream().map(ConversationAggregate::getLastMessageId).toList())
                .stream()
                .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));
        Map<Long, SysUser> peers = sysUserMapper
                .selectByIds(aggregates.stream().map(ConversationAggregate::getPeerId).toList())
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        List<ConversationVO> result = new ArrayList<>(aggregates.size());
        for (ConversationAggregate aggregate : aggregates) {
            ChatMessage latest = lastMessages.get(aggregate.getLastMessageId());
            SysUser peer = peers.get(aggregate.getPeerId());
            if (latest == null || peer == null) {
                continue;
            }
            ConversationVO vo = new ConversationVO();
            vo.setConversationId(aggregate.getConversationId());
            vo.setPeer(new ChatUserVO(peer.getId(), peer.getNickname(),
                    peer.getLevel(), LevelRule.titleOf(peer.getLevel())));
            vo.setLastMessage(latest.getContent());
            vo.setLastMessageTime(latest.getCreatedAt());
            vo.setUnreadCount(aggregate.getUnreadCount());
            result.add(vo);
        }
        return result;
    }
}
