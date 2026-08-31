package com.zhiyi.module.social.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ItemType;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.social.dto.ChatSendDTO;
import com.zhiyi.module.social.dto.ChatStartDTO;
import com.zhiyi.module.social.entity.ChatMessage;
import com.zhiyi.module.social.event.ChatMessageSentEvent;
import com.zhiyi.module.social.event.ChatReadAckedEvent;
import com.zhiyi.module.social.mapper.ChatMessageMapper;
import com.zhiyi.module.social.mapper.ChatResponseSampleMapper;
import com.zhiyi.module.social.vo.ChatItemSummaryVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.mapper.UserReputationMetricMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 适配 v3.1 并发重构：新增 ChatResponseSampleMapper/UserReputationMetricMapper 依赖
 * （响应速度样本）；unreadCount 改 countUnreadByReceiver；sendSystemMessage 删除；
 * findAdmin 用 selectHumanAdmins（恰一个）+ 非 ACTIVE 抛 FORBIDDEN；新增 ackRead。
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatMessageMapper chatMessageMapper;
    @Mock
    private ItemMapper itemMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ChatResponseSampleMapper responseSampleMapper;
    @Mock
    private UserReputationMetricMapper reputationMetricMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ChatService service;

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize(ChatMessage.class, ChatMessageMapper.class);
    }

    @BeforeEach
    void setUp() {
        service = new ChatService(chatMessageMapper, itemMapper, userMapper,
                responseSampleMapper, reputationMetricMapper, eventPublisher);
    }

    @Test
    void startsItemConversationForSameSchoolUsers() {
        Item item = item(100L, 2L, 1L);
        when(itemMapper.selectById(100L)).thenReturn(item);
        when(userMapper.selectById(1L)).thenReturn(user(1L, 1L, "USER"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, 1L, "USER"));

        ChatStartDTO dto = new ChatStartDTO();
        dto.setItemId(100L);

        var result = service.startItemConversation(1L, dto);

        assertEquals("1_2", result.getConversationId());
        assertEquals(2L, result.getPeer().getId());
        // P0-2：会话商品摘要必须携带真实类型，前端按 (type, price) 渲染而非用 null 反推
        ChatItemSummaryVO summary = result.getRelatedItem();
        assertEquals("SELL", summary.getType());
        assertEquals(new BigDecimal("19.90"), summary.getPrice());
    }

    /** 交叉字段契约：SWAP => price=null；SELL/BUY/ERRAND => price!=null；无封面 => 显式 null 而非空串。 */
    @Test
    void relatedItemSummaryHonorsTypePriceCrossFieldContract() {
        Item swap = item(101L, 2L, 1L);
        swap.setType(ItemType.SWAP);
        swap.setPrice(null);
        swap.setImages(List.of());
        when(itemMapper.selectById(101L)).thenReturn(swap);
        when(userMapper.selectById(1L)).thenReturn(user(1L, 1L, "USER"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, 1L, "USER"));

        ChatStartDTO dto = new ChatStartDTO();
        dto.setItemId(101L);

        ChatItemSummaryVO summary = service.startItemConversation(1L, dto).getRelatedItem();

        assertEquals("SWAP", summary.getType());
        assertNull(summary.getPrice(), "SWAP 商品价格必须为 null，前端显示「以物换物」而非 ¥0.00");
        assertNull(summary.getCoverImage(), "无封面图必须是显式 null，不允许空字符串");
    }

    @Test
    void rejectsCrossSchoolSellerContact() {
        Item item = item(100L, 2L, 2L);
        when(itemMapper.selectById(100L)).thenReturn(item);
        when(userMapper.selectById(1L)).thenReturn(user(1L, 1L, "USER"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, 2L, "USER"));

        ChatStartDTO dto = new ChatStartDTO();
        dto.setItemId(100L);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.startItemConversation(1L, dto));
        assertEquals(403, error.getCode());
    }

    @Test
    void rejectsOrdinaryCrossSchoolMessage() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, 1L, "USER"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, 2L, "USER"));

        ChatSendDTO dto = messageTo(2L);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.send(1L, dto));
        assertEquals(403, error.getCode());
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
    }

    @Test
    void allowsUserToContactCrossSchoolAdministratorForCustomerService() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, 2L, "USER"));
        when(userMapper.selectById(9L)).thenReturn(user(9L, 1L, "ADMIN"));

        service.send(1L, messageTo(9L));

        verify(chatMessageMapper).insert(any(ChatMessage.class));
    }

    @Test
    void allowsAdministratorEndpointToReplyAcrossSchools() {
        when(userMapper.selectById(9L)).thenReturn(user(9L, 1L, "ADMIN"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, 2L, "USER"));

        service.sendAsAdmin(9L, messageTo(2L));

        verify(chatMessageMapper).insert(any(ChatMessage.class));
    }

    @Test
    void customerServiceConversationUsesSoleHumanAdmin() {
        SysUser admin = user(9L, 1L, "ADMIN");
        admin.setStatus(UserStatus.ACTIVE);
        when(userMapper.selectHumanAdmins()).thenReturn(List.of(admin));

        var vo = service.startCustomerService(1L);

        assertEquals("1_9", vo.getConversationId());
        assertEquals(9L, vo.getPeer().getId());
    }

    @Test
    void customerServiceUnavailableWhenAdminNotActive() {
        SysUser admin = user(9L, 1L, "ADMIN");
        admin.setStatus(UserStatus.BANNED_TEMP);
        when(userMapper.selectHumanAdmins()).thenReturn(List.of(admin));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.startCustomerService(1L));

        assertEquals(ResultCode.FORBIDDEN.getCode(), error.getCode());
        assertEquals("人工客服暂不可用，请稍后再试", error.getMessage());
    }

    @Test
    void customerServiceMisconfiguredWhenAdminCountIsNotOne() {
        when(userMapper.selectHumanAdmins()).thenReturn(List.of());

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.startCustomerService(1L));

        assertEquals(ResultCode.SERVER_ERROR.getCode(), error.getCode());
    }

    @Test
    void sellerReplyToOwnItemConversationRecordsResponseSample() {
        // 买家 1 就商品 100（卖家 2 发布）发起会话，卖家 2 回复：记录唯一响应样本
        when(userMapper.selectById(2L)).thenReturn(user(2L, 1L, "USER"));
        when(userMapper.selectById(1L)).thenReturn(user(1L, 1L, "USER"));
        when(itemMapper.selectById(100L)).thenReturn(item(100L, 2L, 1L));
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setId(10L);
            saved.setCreatedAt(LocalDateTime.now());
            return 1;
        });
        ChatMessage trigger = message(9L, 1L, 2L);
        trigger.setRelatedItemId(100L);
        trigger.setCreatedAt(LocalDateTime.now().minusSeconds(30));
        when(chatMessageMapper.selectOne(any())).thenReturn(trigger);
        when(responseSampleMapper.insertIgnore(eq("1_2:9"), eq(2L), eq(30L))).thenReturn(1);

        ChatSendDTO dto = messageTo(1L);
        dto.setRelatedItemId(100L);
        service.send(2L, dto);

        verify(responseSampleMapper).insertIgnore(eq("1_2:9"), eq(2L), eq(30L));
        verify(reputationMetricMapper).accumulate(2L, 30L);
    }

    @Test
    void buyerMessageDoesNotRecordSellerResponseSample() {
        // 买家发言：触发消息并非"来自对方且关联自己发布商品"的卖家会话
        when(userMapper.selectById(1L)).thenReturn(user(1L, 1L, "USER"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, 1L, "USER"));
        when(itemMapper.selectById(100L)).thenReturn(item(100L, 2L, 1L));
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setId(10L);
            saved.setCreatedAt(LocalDateTime.now());
            return 1;
        });
        ChatMessage trigger = message(9L, 2L, 1L); // 卖家发给买家
        trigger.setRelatedItemId(100L);
        trigger.setCreatedAt(LocalDateTime.now().minusSeconds(30));
        when(chatMessageMapper.selectOne(any())).thenReturn(trigger);

        ChatSendDTO dto = messageTo(2L);
        dto.setRelatedItemId(100L);
        service.send(1L, dto);

        verify(responseSampleMapper, never()).insertIgnore(any(), any(), org.mockito.ArgumentMatchers.anyLong());
        verify(reputationMetricMapper, never()).accumulate(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void rejectsPeerIdThatDoesNotMatchStoredConversation() {
        ChatMessage stored = new ChatMessage();
        stored.setConversationId("1_2");
        stored.setSenderId(1L);
        stored.setReceiverId(2L);
        stored.setContent("历史消息");
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(stored));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.messages(1L, "1_2", 3L, null));

        assertEquals(400, error.getCode());
    }

    @Test
    void readingThreadDoesNotMarkMessagesAsRead() {
        // M1/B10：GET 只读，已读必须由前端显式 ackRead
        ChatMessage stored = message(7L, 2L, 1L);
        stored.setIsRead(false);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(stored));
        when(userMapper.selectById(1L)).thenReturn(user(1L, 1L, "USER"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, 1L, "USER"));

        var result = service.messages(1L, "1_2", 2L, null);

        assertEquals(1, result.getMessages().size());
        verify(chatMessageMapper, never()).update(any(), any());
    }

    @Test
    void sendMessagePublishesPushEventForReceiver() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, 1L, "USER"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, 1L, "USER"));
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setId(10L);
            saved.setCreatedAt(LocalDateTime.now());
            return 1;
        });

        service.send(1L, messageTo(2L));

        verify(eventPublisher).publishEvent(new ChatMessageSentEvent(2L, 1L, "1_2", 10L));
    }

    @Test
    void ackReadMarksOnlyUpToLastSeenMessage() {
        when(chatMessageMapper.exists(any())).thenReturn(true);

        service.ackRead(1L, "1_2", 7L);

        verify(chatMessageMapper).update(any(), any());
    }

    @Test
    void ackReadPublishesReadEventOnlyWhenUnreadStateChanged() {
        when(chatMessageMapper.exists(any())).thenReturn(true);
        when(chatMessageMapper.update(any(), any())).thenReturn(2);

        service.ackRead(1L, "1_2", 7L);
        verify(eventPublisher).publishEvent(new ChatReadAckedEvent(1L, "1_2"));

        // 重复 ACK：没有未读状态变化，不产生推送噪声
        when(chatMessageMapper.update(any(), any())).thenReturn(0);
        service.ackRead(1L, "1_2", 7L);
        verify(eventPublisher, times(1)).publishEvent(any(ChatReadAckedEvent.class));
    }

    @Test
    void rejectedMessageDoesNotPublishPushEvent() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, 1L, "USER"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, 2L, "USER"));

        assertThrows(BusinessException.class, () -> service.send(1L, messageTo(2L)));

        verify(eventPublisher, never()).publishEvent(any(ChatMessageSentEvent.class));
    }

    @Test
    void ackReadRejectsForeignMessageId() {
        when(chatMessageMapper.exists(any())).thenReturn(false);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.ackRead(1L, "1_2", 999L));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), error.getCode());
        verify(chatMessageMapper, never()).update(any(), any());
    }

    @Test
    void unreadCountUsesCoveringIndexCount() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, 1L, "USER"));
        when(chatMessageMapper.countUnreadByReceiver(1L)).thenReturn(7L);

        assertEquals(7L, service.unreadCount(1L));
    }

    @Test
    void ordinaryAdministratorConversationListOnlyContainsSameSchoolPeers() {
        when(chatMessageMapper.aggregateConversations(9L, null, 100)).thenReturn(List.of(
                aggregate("1_9", 1L, 1L, null, 0L),
                aggregate("2_9", 2L, 2L, null, 0L)
        ));
        when(chatMessageMapper.selectByIds(any())).thenReturn(List.of(
                message(1L, 1L, 9L),
                message(2L, 2L, 9L)
        )).thenReturn(List.of());
        when(userMapper.selectById(9L)).thenReturn(user(9L, 1L, "ADMIN"));
        when(userMapper.selectByIds(any())).thenReturn(List.of(
                user(1L, 1L, "USER"),
                user(2L, 2L, "USER")
        ));

        var result = service.conversations(9L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getPeer().getId());
    }

    @Test
    void messageThreadIsKeysetPaginatedWithHasMoreFlag() {
        ChatMessage stored = new ChatMessage();
        stored.setId(7L);
        stored.setConversationId("1_2");
        stored.setSenderId(1L);
        stored.setReceiverId(2L);
        stored.setContent("历史消息");
        stored.setIsRead(true);
        // 一页上限 50，多取 1 条探测 hasMore：返回 51 条应截断为 50 且 hasMore=true
        List<ChatMessage> page = new java.util.ArrayList<>();
        for (long i = 51; i >= 1; i--) {
            ChatMessage m = new ChatMessage();
            m.setId(i);
            m.setConversationId("1_2");
            m.setSenderId(i % 2 == 0 ? 1L : 2L);
            m.setReceiverId(i % 2 == 0 ? 2L : 1L);
            m.setContent("消息" + i);
            m.setIsRead(true);
            page.add(m);
        }
        when(chatMessageMapper.selectList(any())).thenReturn(page);
        when(userMapper.selectById(1L)).thenReturn(user(1L, 1L, "USER"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, 1L, "USER"));

        var result = service.messages(1L, "1_2", 2L, null);

        assertEquals(50, result.getMessages().size());
        assertEquals(Boolean.TRUE, result.getHasMore());
    }

    @Test
    void administratorUnreadEndpointKeepsCrossSchoolMessages() {
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(
                message(1L, 1L, 9L),
                message(2L, 2L, 9L)
        ));

        assertEquals(2, service.unreadMessagesAsAdmin(9L, null).size());
    }

    private ChatSendDTO messageTo(Long receiverId) {
        ChatSendDTO dto = new ChatSendDTO();
        dto.setReceiverId(receiverId);
        dto.setContent("你好");
        return dto;
    }

    private SysUser user(Long id, Long schoolId, String role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setSchoolId(schoolId);
        user.setRole(UserRole.valueOf(role));
        user.setNickname("用户" + id);
        user.setLevel(1);
        return user;
    }

    private ChatMessage message(Long id, Long senderId, Long receiverId) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setConversationId(
                Math.min(senderId, receiverId) + "_" + Math.max(senderId, receiverId));
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent("消息" + id);
        message.setIsRead(false);
        message.setCreatedAt(LocalDateTime.now().minusMinutes(id));
        return message;
    }

    private com.zhiyi.module.social.dto.ConversationAggregate aggregate(
            String conversationId, Long lastMessageId, Long peerId, Long relatedItemId, Long unreadCount) {
        com.zhiyi.module.social.dto.ConversationAggregate aggregate =
                new com.zhiyi.module.social.dto.ConversationAggregate();
        aggregate.setConversationId(conversationId);
        aggregate.setLastMessageId(lastMessageId);
        aggregate.setPeerId(peerId);
        aggregate.setRelatedItemId(relatedItemId);
        aggregate.setUnreadCount(unreadCount);
        return aggregate;
    }

    private Item item(Long id, Long publisherId, Long schoolId) {
        Item item = new Item();
        item.setId(id);
        item.setPublisherId(publisherId);
        item.setSchoolId(schoolId);
        item.setTitle("测试商品");
        item.setType(ItemType.SELL);
        item.setPrice(new BigDecimal("19.90"));
        item.setStatus(ItemStatus.ON_SALE);
        item.setImages(List.of());
        return item;
    }
}
