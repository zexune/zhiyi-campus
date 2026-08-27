package com.zhiyi.module.admin.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 超管客服收件箱服务 —— 4.6
 *
 * 聚合唯一人工管理员参与的每个会话的最后消息、未读数（只计发给管理员的）及对端用户。
 * 管理员是全部用户消息的对端，此处必须走 SQL 聚合（每会话一行）+ 批量装配，
 * 不得把全部历史消息拉进内存。
 *
 * 单人工管理员产品约束（M8/I16）：selectHumanAdmins() 显式排除 SYSTEM（is_system=0）
 * 并校验恰好一个，禁止 ORDER BY id LIMIT 1 静默选择；管理员非 ACTIVE 时
 * 明确返回"客服暂不可用"并告警（调用方为已登录管理端，此分支通常不可达，防御性保留）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminChatService {

    private final ChatMessageMapper chatMessageMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 管理员客服会话列表
     */
    public List<ConversationVO> getSessions() {
        List<SysUser> admins = sysUserMapper.selectHumanAdmins();
        if (admins.size() != 1) {
            log.error("人工管理员配置异常：期望恰好 1 个，实际 {}", admins.size());
            throw new BusinessException(ResultCode.SERVER_ERROR, "客服账号配置异常，请联系平台管理员");
        }
        SysUser admin = admins.getFirst();
        if (admin.getStatus() != UserStatus.ACTIVE) {
            log.warn("人工管理员当前不可用 adminId={} status={}", admin.getId(), admin.getStatus());
            throw new BusinessException(ResultCode.FORBIDDEN, "人工客服暂不可用，请稍后再试");
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
            vo.setPeer(new ChatUserVO(peer.getId(), peer.getNickname(), peer.getAvatar(),
                    peer.getLevel(), LevelRule.titleOf(peer.getLevel())));
            vo.setLastMessage(latest.getContent());
            vo.setLastMessageTime(latest.getCreatedAt());
            vo.setUnreadCount(aggregate.getUnreadCount());
            result.add(vo);
        }
        return result;
    }
}
