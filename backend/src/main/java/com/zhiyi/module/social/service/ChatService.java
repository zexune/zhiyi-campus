package com.zhiyi.module.social.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.SchoolScopeGuard;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.social.dto.ChatSendDTO;
import com.zhiyi.module.social.dto.ChatStartDTO;
import com.zhiyi.module.social.entity.ChatMessage;
import com.zhiyi.module.social.mapper.ChatMessageMapper;
import com.zhiyi.module.social.vo.ChatItemSummaryVO;
import com.zhiyi.module.social.vo.ChatMessageVO;
import com.zhiyi.module.social.vo.ChatStartVO;
import com.zhiyi.module.social.vo.ChatThreadVO;
import com.zhiyi.module.social.vo.ChatUserVO;
import com.zhiyi.module.social.vo.ConversationVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.LevelRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模块三：站内聊天。使用 chat_message 聚合会话，不额外建会话表。
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageMapper chatMessageMapper;
    private final ItemMapper itemMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

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
        return toMessageVO(message, senderId);
    }

    @Transactional
    public ChatThreadVO messages(Long userId, String conversationId, Long peerId, Long relatedItemId) {
        return messagesInternal(userId, conversationId, peerId, relatedItemId, false);
    }

    /** 管理后台读取客服会话：由 /api/admin/** 入口鉴权，不受学校范围限制。 */
    @Transactional
    public ChatThreadVO messagesAsAdmin(Long userId, String conversationId,
                                        Long peerId, Long relatedItemId) {
        return messagesInternal(userId, conversationId, peerId, relatedItemId, true);
    }

    private ChatThreadVO messagesInternal(Long userId, String conversationId,
                                          Long peerId, Long relatedItemId,
                                          boolean adminScope) {
        if (!StringUtils.hasText(conversationId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "会话ID不能为空");
        }
        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getCreatedAt)
                .orderByAsc(ChatMessage::getId));

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
        markConversationRead(userId, conversationId);

        ChatThreadVO vo = new ChatThreadVO();
        vo.setConversationId(conversationId);
        vo.setPeer(toUserVO(requireUser(actualPeerId)));
        vo.setRelatedItem(actualItemId == null ? null : toItemSummary(itemMapper.selectById(actualItemId)));
        vo.setMessages(messages.stream().map(message -> toMessageVO(message, userId)).toList());
        return vo;
    }

    public List<ConversationVO> conversations(Long userId) {
        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .and(w -> w.eq(ChatMessage::getSenderId, userId).or().eq(ChatMessage::getReceiverId, userId))
                .orderByDesc(ChatMessage::getCreatedAt)
                .orderByDesc(ChatMessage::getId));
        if (messages.isEmpty()) {
            return List.of();
        }
        SysUser currentUser = requireUser(userId);
        SchoolScopeGuard.requireAssigned(currentUser.getSchoolId());

        Map<String, ChatMessage> latestByConversation = new LinkedHashMap<>();
        Map<String, Long> unreadByConversation = new LinkedHashMap<>();
        Map<String, Long> itemByConversation = new LinkedHashMap<>();
        Set<Long> peerIds = new LinkedHashSet<>();
        Set<Long> itemIds = new LinkedHashSet<>();

        for (ChatMessage message : messages) {
            latestByConversation.putIfAbsent(message.getConversationId(), message);
            Long peerId = Objects.equals(message.getSenderId(), userId) ? message.getReceiverId() : message.getSenderId();
            peerIds.add(peerId);
            if (message.getRelatedItemId() != null) {
                itemByConversation.putIfAbsent(message.getConversationId(), message.getRelatedItemId());
                itemIds.add(message.getRelatedItemId());
            }
            if (Objects.equals(message.getReceiverId(), userId) && !Boolean.TRUE.equals(message.getIsRead())) {
                unreadByConversation.merge(message.getConversationId(), 1L, Long::sum);
            }
        }

        Map<Long, SysUser> users = userMapper.selectBatchIds(peerIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, Item> items = itemIds.isEmpty()
                ? Map.of()
                : itemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        List<ConversationVO> result = new ArrayList<>();
        for (ChatMessage latest : latestByConversation.values()) {
            Long peerId = Objects.equals(latest.getSenderId(), userId) ? latest.getReceiverId() : latest.getSenderId();
            SysUser peer = users.get(peerId);
            Long relItemId = itemByConversation.get(latest.getConversationId());
            Item relatedItem = relItemId == null ? null : items.get(relItemId);
            if (relItemId != null && relatedItem == null) {
                continue;
            }
            if (!canAccessOrdinaryConversation(currentUser, peer, relatedItem)) {
                continue;
            }
            ConversationVO vo = new ConversationVO();
            vo.setConversationId(latest.getConversationId());
            vo.setPeer(toUserVO(peer));
            vo.setRelatedItem(toItemSummary(relatedItem));
            vo.setLastMessage(latest.getContent());
            vo.setLastMessageTime(latest.getCreatedAt());
            vo.setUnreadCount(unreadByConversation.getOrDefault(latest.getConversationId(), 0L));
            result.add(vo);
        }
        result.sort(Comparator.comparing(ConversationVO::getLastMessageTime).reversed());
        return result;
    }

    public Long unreadCount(Long userId) {
        return (long) filterOrdinaryMessages(userId, findUnreadMessages(userId, null)).size();
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
        return chatMessageMapper.selectList(wrapper);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendSystemMessage(Long receiverId, String content) {
        SysUser admin = findAdmin();
        if (Objects.equals(admin.getId(), receiverId)) {
            return;
        }
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId(receiverId, admin.getId()));
        message.setSenderId(admin.getId());
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setIsRead(false);
        chatMessageMapper.insert(message);
    }

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
        if ("ADMIN".equals(receiver.getRole())) {
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
        Map<Long, SysUser> users = userMapper.selectBatchIds(peerIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, Item> items = itemIds.isEmpty()
                ? Map.of()
                : itemMapper.selectBatchIds(itemIds).stream()
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
        if ("ADMIN".equals(peer.getRole())) {
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

    private void markConversationRead(Long userId, String conversationId) {
        chatMessageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getReceiverId, userId)
                .eq(ChatMessage::getIsRead, false)
                .set(ChatMessage::getIsRead, true));
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
        SysUser admin = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "ADMIN")
                .eq(SysUser::getStatus, "ACTIVE")
                .orderByAsc(SysUser::getId)
                .last("LIMIT 1"));
        if (admin == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "未找到客服账号");
        }
        return admin;
    }

    private ChatUserVO toUserVO(SysUser user) {
        if (user == null) return null;
        return new ChatUserVO(
                user.getId(),
                user.getNickname(),
                user.getLevel(),
                LevelRule.titleOf(user.getLevel())
        );
    }

    private ChatItemSummaryVO toItemSummary(Item item) {
        if (item == null) return null;
        ChatItemSummaryVO vo = new ChatItemSummaryVO();
        vo.setId(item.getId());
        vo.setTitle(item.getTitle());
        vo.setPrice(item.getPrice());
        vo.setCoverImage(firstImage(item.getImages()));
        vo.setStatus(item.getStatus());
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

    private String firstImage(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        try {
            List<String> images = objectMapper.readValue(raw, new TypeReference<>() {});
            return images == null || images.isEmpty() ? "" : images.get(0);
        } catch (Exception ignored) {
            return "";
        }
    }
}
