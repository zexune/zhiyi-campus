package com.zhiyi.module.social.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.SchoolScopeGuard;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.social.dto.ChatSendDTO;
import com.zhiyi.module.social.dto.ChatStartDTO;
import com.zhiyi.module.social.dto.ConversationAggregate;
import com.zhiyi.module.social.entity.ChatMessage;
import com.zhiyi.module.social.mapper.ChatMessageMapper;
import com.zhiyi.module.social.mapper.ChatResponseSampleMapper;
import com.zhiyi.module.social.vo.ChatItemSummaryVO;
import com.zhiyi.module.social.vo.ChatMessageVO;
import com.zhiyi.module.social.vo.ChatStartVO;
import com.zhiyi.module.social.vo.ChatThreadVO;
import com.zhiyi.module.social.vo.ChatUserVO;
import com.zhiyi.module.social.vo.ConversationVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.mapper.UserReputationMetricMapper;
import com.zhiyi.module.user.support.LevelRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模块三：站内聊天。使用 chat_message 聚合会话，不额外建会话表。
 *
 * 有界查询约定：
 * - 会话列表 / 未读总数：SQL GROUP BY 聚合（每会话一行）+ 固定次数批量装配，不加载消息明细；
 * - 会话消息历史：按 id 倒序 keyset 分页（默认最近 MESSAGE_PAGE_SIZE 条，beforeId 向前翻页）；
 * - 未读明细：单会话查询天然有界；跨会话兜底 LIMIT UNREAD_LIMIT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MESSAGE_PAGE_SIZE = 50;
    private static final int UNREAD_LIMIT = 200;

    private final ChatMessageMapper chatMessageMapper;
    private final ItemMapper itemMapper;
    private final SysUserMapper userMapper;
    private final ChatResponseSampleMapper responseSampleMapper;
    private final UserReputationMetricMapper reputationMetricMapper;

    public ChatStartVO startItemConversation(Long userId, ChatStartDTO dto) {
        Item item = itemMapper.selectById(dto.getItemId());
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        if (Objects.equals(item.getPublisherId(), userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能和自己发起商品会话");
        }
        SysUser requester = requireUser(userId);
        SysUser seller = requireUser(item.getPublisherId());
        SchoolScopeGuard.requireSame(
                requester.getSchoolId(), item.getSchoolId(), "只能联系本校卖家");
        SchoolScopeGuard.requireSame(
                requester.getSchoolId(), seller.getSchoolId(), "只能联系本校卖家");
        return buildStartVO(userId, seller, item);
    }

    public ChatStartVO startCustomerService(Long userId) {
        SysUser admin = findAdmin();
        if (Objects.equals(admin.getId(), userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "管理员可在客服收件箱查看用户消息");
        }
        ChatStartVO vo = new ChatStartVO();
        vo.setConversationId(conversationId(userId, admin.getId()));
        vo.setPeer(toUserVO(admin));
        return vo;
    }

    @Transactional
    public ChatMessageVO send(Long senderId, ChatSendDTO dto) {
        return sendInternal(senderId, dto, false);
    }

    /** 管理后台客服回复：由 /api/admin/** 入口鉴权，可跨学校联系用户。 */
    @Transactional
    public ChatMessageVO sendAsAdmin(Long senderId, ChatSendDTO dto) {
        return sendInternal(senderId, dto, true);
    }

    private ChatMessageVO sendInternal(Long senderId, ChatSendDTO dto, boolean adminScope) {
        Long receiverId = dto.getReceiverId();
        if (Objects.equals(senderId, receiverId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能给自己发送消息");
        }
        SysUser sender = requireUser(senderId);
        SysUser receiver = requireUser(receiverId);
        Long relatedItemId = dto.getRelatedItemId();
        Item relatedItem = null;
        if (relatedItemId != null) {
            relatedItem = itemMapper.selectById(relatedItemId);
            if (relatedItem == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "关联商品不存在");
            }
        }
        validateChatScope(sender, receiver, relatedItem, adminScope);

        String content = dto.getContent().trim();
        ChatMessage message = new ChatMessage();
        message.setConversationId(normalizeConversationId(dto.getConversationId(), senderId, receiverId));
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setRelatedItemId(relatedItemId);
        message.setIsRead(false);
        chatMessageMapper.insert(message);
        recordResponseSample(message, relatedItem, senderId);
        return toMessageVO(message, senderId);
    }

    /**
     * 响应速度唯一贡献样本（B10）：本次发送若是"对上一条来自对方、且关联自己发布商品"
     * 的消息的首个回复，则以 (conversationId, 触发消息ID) 唯一键记录间隔并增量汇总。
     * 固定两次点查 + 一次幂等插入；重复事件依靠唯一贡献键只累计一次。
     */
    private void recordResponseSample(ChatMessage message, Item relatedItem, Long senderId) {
        try {
            ChatMessage trigger = chatMessageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getConversationId, message.getConversationId())
                    .lt(ChatMessage::getId, message.getId())
                    .orderByDesc(ChatMessage::getId)
                    .last("LIMIT 1"));
            if (trigger == null || !trigger.getReceiverId().equals(senderId)
                    || trigger.getRelatedItemId() == null
                    || message.getCreatedAt() == null || trigger.getCreatedAt() == null) {
                return;
            }
            // 只统计卖家在自己商品会话中的响应；触发消息的商品必须由回复者发布
            if (relatedItem == null || !relatedItem.getPublisherId().equals(senderId)) {
                return;
            }
            long gapSeconds = Duration.between(trigger.getCreatedAt(), message.getCreatedAt()).getSeconds();
            if (gapSeconds < 0) {
                return;
            }
            String sampleKey = message.getConversationId() + ":" + trigger.getId();
            if (responseSampleMapper.insertIgnore(sampleKey, senderId, gapSeconds) == 1) {
                reputationMetricMapper.accumulate(senderId, gapSeconds);
            }
        } catch (Exception sampleFailure) {
            // 派生统计失败不阻断消息发送主流程（非权威指标，允许受控延迟）
            log.warn("响应速度样本记录失败 conversation={}", message.getConversationId(), sampleFailure);
        }
    }

    @Transactional
    public ChatThreadVO messages(Long userId, String conversationId, Long peerId, Long relatedItemId) {
        return messagesInternal(userId, conversationId, peerId, relatedItemId, null, false);
    }

    @Transactional
    public ChatThreadVO messages(Long userId, String conversationId, Long peerId,
                                  Long relatedItemId, Long beforeId) {
        return messagesInternal(userId, conversationId, peerId, relatedItemId, beforeId, false);
    }

    /** 管理后台读取客服会话：由 /api/admin/** 入口鉴权，不受学校范围限制。 */
    @Transactional
    public ChatThreadVO messagesAsAdmin(Long userId, String conversationId,
                                        Long peerId, Long relatedItemId) {
        return messagesInternal(userId, conversationId, peerId, relatedItemId, null, true);
    }

    @Transactional
    public ChatThreadVO messagesAsAdmin(Long userId, String conversationId,
                                        Long peerId, Long relatedItemId, Long beforeId) {
        return messagesInternal(userId, conversationId, peerId, relatedItemId, beforeId, true);
    }

    private ChatThreadVO messagesInternal(Long userId, String conversationId,
                                          Long peerId, Long relatedItemId,
                                          Long beforeId, boolean adminScope) {
        if (!StringUtils.hasText(conversationId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "会话ID不能为空");
        }
        // keyset 分页：按 id 倒序取一页（多取 1 条探测 hasMore），再反转为时间正序返回。
        // GET 只读（M1/B10）：读取不再隐式标记已读，已读由前端在消息可见后显式 ackRead。
        List<ChatMessage> page = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .lt(beforeId != null, ChatMessage::getId, beforeId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + (MESSAGE_PAGE_SIZE + 1)));
        boolean hasMore = page.size() > MESSAGE_PAGE_SIZE;
        if (hasMore) {
            page = page.subList(0, MESSAGE_PAGE_SIZE);
        }
        List<ChatMessage> messages = page.reversed();

        Long actualPeerId = peerId;
        Long actualItemId = relatedItemId;
        if (!messages.isEmpty()) {
            ensureParticipant(userId, messages);
            Long derivedPeerId = null;
            Long derivedItemId = null;
            for (ChatMessage message : messages) {
                Long messagePeerId = Objects.equals(message.getSenderId(), userId)
                        ? message.getReceiverId()
                        : message.getSenderId();
                if (derivedPeerId == null) {
                    derivedPeerId = messagePeerId;
                } else if (!Objects.equals(derivedPeerId, messagePeerId)) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "会话参与者数据异常");
                }
                if (message.getRelatedItemId() != null) {
                    if (derivedItemId == null) {
                        derivedItemId = message.getRelatedItemId();
                    } else if (!Objects.equals(derivedItemId, message.getRelatedItemId())) {
                        throw new BusinessException(ResultCode.BAD_REQUEST, "会话关联了多个商品");
                    }
                }
            }
            if (peerId != null && !Objects.equals(peerId, derivedPeerId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "会话对端与消息记录不匹配");
            }
            if (!conversationId(userId, derivedPeerId).equals(conversationId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "会话ID与消息参与者不匹配");
            }
            if (relatedItemId != null && derivedItemId != null
                    && !Objects.equals(relatedItemId, derivedItemId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "会话商品与消息记录不匹配");
            }
            actualPeerId = derivedPeerId;
            if (derivedItemId != null) {
                actualItemId = derivedItemId;
            }
        } else if (actualPeerId == null || !conversationId(userId, actualPeerId).equals(conversationId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }

        validateConversationScope(userId, actualPeerId, actualItemId, adminScope);

        ChatThreadVO vo = new ChatThreadVO();
        vo.setConversationId(conversationId);
        vo.setPeer(toUserVO(requireUser(actualPeerId)));
        vo.setRelatedItem(actualItemId == null ? null : toItemSummary(itemMapper.selectById(actualItemId)));
        vo.setMessages(messages.stream().map(message -> toMessageVO(message, userId)).toList());
        vo.setHasMore(hasMore);
        return vo;
    }

    /**
     * 会话列表：SQL 聚合（每会话一行）+ 按集合批量装配对端与商品，固定次数数据库往返。
     */
    public List<ConversationVO> conversations(Long userId) {
        SysUser currentUser = requireUser(userId);
        SchoolScopeGuard.requireAssigned(currentUser.getSchoolId());

        List<ConversationSnapshot> snapshots = loadAccessibleSnapshots(userId, currentUser);
        if (snapshots.isEmpty()) {
            return List.of();
        }
        return snapshots.stream().map(snapshot -> {
            ConversationVO vo = new ConversationVO();
            vo.setConversationId(snapshot.aggregate().getConversationId());
            vo.setPeer(toUserVO(snapshot.peer()));
            vo.setRelatedItem(toItemSummary(snapshot.relatedItem()));
            vo.setLastMessage(snapshot.lastMessage().getContent());
            vo.setLastMessageTime(snapshot.lastMessage().getCreatedAt());
            vo.setUnreadCount(snapshot.aggregate().getUnreadCount());
            return vo;
        }).toList();
    }

    public Long unreadCount(Long userId) {
        SysUser currentUser = requireUser(userId);
        SchoolScopeGuard.requireAssigned(currentUser.getSchoolId());
        // 全局未读 COUNT：单条 idx_chat_receiver_unread 覆盖索引查询，固定成本
        return chatMessageMapper.countUnreadByReceiver(userId);
    }

    /**
     * 显式已读确认（M1/B10）：前端在消息实际渲染且可见后调用。
     * lastSeenMessageId 必须属于该会话且接收者是当前用户；仅标记
     * id <= lastSeenMessageId 的未读消息（间隙新消息保持未读），
     * ACK 永不越过最后可见的接收消息。
     */
    @Transactional
    public void ackRead(Long userId, String conversationId, Long lastSeenMessageId) {
        if (!StringUtils.hasText(conversationId) || lastSeenMessageId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "会话ID与消息ID不能为空");
        }
        boolean valid = chatMessageMapper.exists(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getId, lastSeenMessageId)
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getReceiverId, userId));
        if (!valid) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "消息 ID 与会话不匹配");
        }
        chatMessageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getReceiverId, userId)
                .eq(ChatMessage::getIsRead, false)
                .le(ChatMessage::getId, lastSeenMessageId)
                .set(ChatMessage::getIsRead, true));
    }

    /** 会话快照：聚合行 + 批量回填的最近消息 / 对端用户 / 关联商品，并已套用普通入口的可见性过滤。 */
    private record ConversationSnapshot(
            ConversationAggregate aggregate,
            ChatMessage lastMessage,
            SysUser peer,
            Item relatedItem) {
    }

    private List<ConversationSnapshot> loadAccessibleSnapshots(Long userId, SysUser currentUser) {
        List<ConversationAggregate> aggregates = chatMessageMapper.aggregateConversations(userId);
        if (aggregates.isEmpty()) {
            return List.of();
        }

        Map<Long, ChatMessage> lastMessages = chatMessageMapper
                .selectByIds(aggregates.stream().map(ConversationAggregate::getLastMessageId).toList())
                .stream()
                .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));

        Set<Long> peerIds = aggregates.stream()
                .map(ConversationAggregate::getPeerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> itemIds = aggregates.stream()
                .map(ConversationAggregate::getRelatedItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SysUser> users = peerIds.isEmpty()
                ? Map.of()
                : userMapper.selectByIds(peerIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, Item> items = itemIds.isEmpty()
                ? Map.of()
                : itemMapper.selectByIds(itemIds).stream()
                        .collect(Collectors.toMap(Item::getId, Function.identity()));

        List<ConversationSnapshot> snapshots = new ArrayList<>(aggregates.size());
        for (ConversationAggregate aggregate : aggregates) {
            ChatMessage lastMessage = lastMessages.get(aggregate.getLastMessageId());
            // lastMessage 是该会话存在的凭证（聚合行由消息聚合而来），防御性跳过理论上的空行。
            if (lastMessage == null) {
                continue;
            }
            SysUser peer = users.get(aggregate.getPeerId());
            Item relatedItem = aggregate.getRelatedItemId() == null
                    ? null
                    : items.get(aggregate.getRelatedItemId());
            if (aggregate.getRelatedItemId() != null && relatedItem == null) {
                continue;
            }
            if (!canAccessOrdinaryConversation(currentUser, peer, relatedItem)) {
                continue;
            }
            snapshots.add(new ConversationSnapshot(aggregate, lastMessage, peer, relatedItem));
        }
        return snapshots;
    }

    public List<ChatMessageVO> unreadMessages(Long userId, String conversationId) {
        return filterOrdinaryMessages(userId, findUnreadMessages(userId, conversationId)).stream()
                .map(message -> toMessageVO(message, userId))
                .toList();
    }

    /** 管理后台轮询客服消息：由 /api/admin/** 入口鉴权，不受学校范围限制。 */
    public List<ChatMessageVO> unreadMessagesAsAdmin(Long userId, String conversationId) {
        return findUnreadMessages(userId, conversationId).stream()
                .map(message -> toMessageVO(message, userId))
                .toList();
    }

    private List<ChatMessage> findUnreadMessages(Long userId, String conversationId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getReceiverId, userId)
                .eq(ChatMessage::getIsRead, false)
                .orderByAsc(ChatMessage::getCreatedAt);
        if (StringUtils.hasText(conversationId)) {
            wrapper.eq(ChatMessage::getConversationId, conversationId);
        }
        // 单会话查询天然有界；不指定会话时按未读积压上限兜底，避免全表加载。
        return chatMessageMapper.selectList(wrapper.last("LIMIT " + UNREAD_LIMIT));
    }

    // 系统消息改由事务 Outbox 投递（OutboxProcessor 以唯一 SYSTEM 主体直接插入
    // chat_message，source_event_id 唯一索引保证幂等），此处不再提供
    // AFTER_COMMIT + REQUIRES_NEW 的 sendSystemMessage 路径。

    private ChatStartVO buildStartVO(Long userId, SysUser seller, Item item) {
        ChatStartVO vo = new ChatStartVO();
        vo.setConversationId(conversationId(userId, seller.getId()));
        vo.setPeer(toUserVO(seller));
        vo.setRelatedItem(toItemSummary(item));
        return vo;
    }

    private void validateChatScope(SysUser sender, SysUser receiver,
                                   Item relatedItem, boolean adminScope) {
        if (adminScope) {
            return;
        }
        if (relatedItem != null) {
            boolean publisherParticipates = Objects.equals(relatedItem.getPublisherId(), sender.getId())
                    || Objects.equals(relatedItem.getPublisherId(), receiver.getId());
            if (!publisherParticipates) {
                throw new BusinessException(ResultCode.FORBIDDEN, "商品会话必须包含商品发布者");
            }
            SchoolScopeGuard.requireSame(
                    sender.getSchoolId(), relatedItem.getSchoolId(), "只能联系本校卖家");
            SchoolScopeGuard.requireSame(
                    receiver.getSchoolId(), relatedItem.getSchoolId(), "只能联系本校卖家");
            return;
        }
        // 用户联系平台客服允许跨校；管理员跨校回复必须走 /api/admin/chat/send。
        if (receiver.getRole() == UserRole.ADMIN) {
            return;
        }
        SchoolScopeGuard.requireSame(
                sender.getSchoolId(), receiver.getSchoolId(), "只能与本校用户联系");
    }

    private void validateConversationScope(Long userId, Long peerId,
                                           Long relatedItemId, boolean adminScope) {
        if (adminScope) {
            return;
        }
        SysUser current = requireUser(userId);
        SysUser peer = requireUser(peerId);
        Item relatedItem = relatedItemId == null ? null : itemMapper.selectById(relatedItemId);
        validateChatScope(current, peer, relatedItem, false);
    }

    private List<ChatMessage> filterOrdinaryMessages(Long userId, List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        SysUser currentUser = requireUser(userId);
        SchoolScopeGuard.requireAssigned(currentUser.getSchoolId());

        Set<Long> peerIds = new LinkedHashSet<>();
        Set<Long> itemIds = new LinkedHashSet<>();
        for (ChatMessage message : messages) {
            peerIds.add(Objects.equals(message.getSenderId(), userId)
                    ? message.getReceiverId()
                    : message.getSenderId());
            if (message.getRelatedItemId() != null) {
                itemIds.add(message.getRelatedItemId());
            }
        }
        Map<Long, SysUser> users = userMapper.selectByIds(peerIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, Item> items = itemIds.isEmpty()
                ? Map.of()
                : itemMapper.selectByIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        return messages.stream()
                .filter(message -> {
                    Long peerId = Objects.equals(message.getSenderId(), userId)
                            ? message.getReceiverId()
                            : message.getSenderId();
                    Item relatedItem = message.getRelatedItemId() == null
                            ? null
                            : items.get(message.getRelatedItemId());
                    if (message.getRelatedItemId() != null && relatedItem == null) {
                        return false;
                    }
                    return canAccessOrdinaryConversation(
                            currentUser, users.get(peerId), relatedItem);
                })
                .toList();
    }

    private boolean canAccessOrdinaryConversation(SysUser currentUser,
                                                   SysUser peer,
                                                   Item relatedItem) {
        if (peer == null) {
            return false;
        }
        if (relatedItem != null) {
            boolean publisherParticipates =
                    Objects.equals(relatedItem.getPublisherId(), currentUser.getId())
                            || Objects.equals(relatedItem.getPublisherId(), peer.getId());
            return publisherParticipates
                    && currentUser.getSchoolId() != null
                    && Objects.equals(currentUser.getSchoolId(), peer.getSchoolId())
                    && Objects.equals(currentUser.getSchoolId(), relatedItem.getSchoolId());
        }
        if (peer.getRole() == UserRole.ADMIN) {
            return true;
        }
        return currentUser.getSchoolId() != null
                && Objects.equals(currentUser.getSchoolId(), peer.getSchoolId());
    }

    private void ensureParticipant(Long userId, List<ChatMessage> messages) {
        boolean allowed = messages.stream()
                .anyMatch(message -> Objects.equals(message.getSenderId(), userId)
                        || Objects.equals(message.getReceiverId(), userId));
        if (!allowed) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该会话");
        }
    }

    private String normalizeConversationId(String provided, Long a, Long b) {
        String expected = conversationId(a, b);
        if (!StringUtils.hasText(provided)) {
            return expected;
        }
        if (!expected.equals(provided.trim())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "会话ID与收发双方不匹配");
        }
        return expected;
    }

    private String conversationId(Long a, Long b) {
        long left = Math.min(a, b);
        long right = Math.max(a, b);
        return left + "_" + right;
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    private SysUser findAdmin() {
        // 单人工管理员产品约束（M8/I16）：显式校验恰好一个 role=ADMIN 且非 SYSTEM 的账户，
        // 禁止 ORDER BY id LIMIT 1 静默选择；非 ACTIVE 时明确"客服暂不可用"并告警。
        List<SysUser> admins = userMapper.selectHumanAdmins();
        if (admins.size() != 1) {
            log.error("人工管理员配置异常：期望恰好 1 个，实际 {}", admins.size());
            throw new BusinessException(ResultCode.SERVER_ERROR, "客服账号配置异常，请联系平台管理员");
        }
        SysUser admin = admins.getFirst();
        if (admin.getStatus() != UserStatus.ACTIVE) {
            log.warn("人工管理员当前不可用 adminId={} status={}", admin.getId(), admin.getStatus());
            throw new BusinessException(ResultCode.FORBIDDEN, "人工客服暂不可用，请稍后再试");
        }
        return admin;
    }

    private ChatUserVO toUserVO(SysUser user) {
        if (user == null) return null;
        return new ChatUserVO(
                user.getId(),
                user.getNickname(),
                user.getAvatar(),
                user.getLevel(),
                LevelRule.titleOf(user.getLevel())
        );
    }

    private ChatItemSummaryVO toItemSummary(Item item) {
        if (item == null) return null;
        ChatItemSummaryVO vo = new ChatItemSummaryVO();
        vo.setId(item.getId());
        vo.setTitle(item.getTitle());
        vo.setType(item.getType().code());
        vo.setPrice(item.getPrice());
        List<String> images = item.getImages();
        vo.setCoverImage(images == null || images.isEmpty() ? null : images.getFirst());
        vo.setStatus(item.getStatus().code());
        return vo;
    }

    private ChatMessageVO toMessageVO(ChatMessage message, Long currentUserId) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setSenderId(message.getSenderId());
        vo.setReceiverId(message.getReceiverId());
        vo.setContent(message.getContent());
        vo.setRelatedItemId(message.getRelatedItemId());
        vo.setIsRead(message.getIsRead());
        vo.setMine(Objects.equals(message.getSenderId(), currentUserId));
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }

}
