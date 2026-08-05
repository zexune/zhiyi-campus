package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.module.social.entity.ChatMessage;
import com.zhiyi.module.social.mapper.ChatMessageMapper;
import com.zhiyi.module.social.vo.ChatUserVO;
import com.zhiyi.module.social.vo.ConversationVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.LevelRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 超管客服收件箱服务 —— 4.6
 *
 * 找到管理员用户，查出其参与的所有会话，
 * 聚合每个会话的最后消息、未读数（只计发给管理员的）及对端用户信息。
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
        // 找到管理员
        SysUser admin = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "ADMIN")
                .eq(SysUser::getStatus, "ACTIVE")
                .orderByAsc(SysUser::getId)
                .last("LIMIT 1"));
        if (admin == null) {
            return List.of();
        }
        Long adminId = admin.getId();

        // 查管理员参与的所有消息，按时间倒序
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .and(w -> w.eq(ChatMessage::getSenderId, adminId)
                                 .or()
                                 .eq(ChatMessage::getReceiverId, adminId))
                        .orderByDesc(ChatMessage::getCreatedAt)
                        .orderByDesc(ChatMessage::getId));

        if (messages.isEmpty()) {
            return List.of();
        }

        // 聚合
        Map<String, ChatMessage> latestByConv = new LinkedHashMap<>();
        Map<String, Long> unreadByConv = new LinkedHashMap<>();
        Map<String, Long> peerByConv = new LinkedHashMap<>();
        Set<Long> peerIds = new LinkedHashSet<>();

        for (ChatMessage m : messages) {
            latestByConv.putIfAbsent(m.getConversationId(), m);

            // 对端用户
            Long peerId = Objects.equals(m.getSenderId(), adminId)
                    ? m.getReceiverId() : m.getSenderId();
            peerByConv.putIfAbsent(m.getConversationId(), peerId);
            peerIds.add(peerId);

            // 未读：只计发给管理员的
            if (Objects.equals(m.getReceiverId(), adminId) && !Boolean.TRUE.equals(m.getIsRead())) {
                unreadByConv.merge(m.getConversationId(), 1L, Long::sum);
            }
        }

        // 批量查对端用户
        Map<Long, SysUser> userMap = new HashMap<>();
        if (!peerIds.isEmpty()) {
            userMap = sysUserMapper.selectBatchIds(peerIds).stream()
                    .collect(java.util.stream.Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
        }

        // 组装结果
        List<ConversationVO> result = new ArrayList<>();
        for (Map.Entry<String, ChatMessage> entry : latestByConv.entrySet()) {
            String convId = entry.getKey();
            ChatMessage latest = entry.getValue();
            Long peerId = peerByConv.get(convId);
            SysUser peer = peerId != null ? userMap.get(peerId) : null;

            ConversationVO vo = new ConversationVO();
            vo.setConversationId(convId);
            if (peer != null) {
                vo.setPeer(new ChatUserVO(peer.getId(), peer.getNickname(),
                        peer.getLevel(), LevelRule.titleOf(peer.getLevel())));
            }
            vo.setLastMessage(latest.getContent());
            vo.setLastMessageTime(latest.getCreatedAt());
            vo.setUnreadCount(unreadByConv.getOrDefault(convId, 0L));
            result.add(vo);
        }

        result.sort(Comparator.comparing(ConversationVO::getLastMessageTime).reversed());
        return result;
    }
}
