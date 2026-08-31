package com.zhiyi.module.admin.service;

import com.zhiyi.module.social.entity.ChatMessage;
import com.zhiyi.module.social.mapper.ChatMessageMapper;
import com.zhiyi.module.social.vo.ConversationVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminChatServiceTest {

    @Mock private ChatMessageMapper messageMapper;
    @Mock private SysUserMapper userMapper;
    private AdminChatService service;

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize(ChatMessage.class, ChatMessageMapper.class);
        initialize(SysUser.class, SysUserMapper.class);
    }

    @BeforeEach
    void setUp() {
        service = new AdminChatService(messageMapper, userMapper);
    }

    @Test
    void misconfiguredAdminCountFailsLoudlyInsteadOfSilentPick() {
        // M8/I16：零个或多个非 SYSTEM 管理员是配置错误，禁止 ORDER BY id LIMIT 1 静默选择
        when(userMapper.selectHumanAdmins()).thenReturn(List.of());

        com.zhiyi.common.BusinessException error = org.junit.jupiter.api.Assertions.assertThrows(
                com.zhiyi.common.BusinessException.class, () -> service.getSessions());

        assertEquals(500, error.getCode());
        verifyNoInteractions(messageMapper);
    }

    @Test
    void inactiveAdminMeansCustomerServiceUnavailable() {
        SysUser admin = user(1L, "管理员", 99);
        admin.setStatus(com.zhiyi.common.enums.UserStatus.BANNED_TEMP);
        when(userMapper.selectHumanAdmins()).thenReturn(List.of(admin));

        com.zhiyi.common.BusinessException error = org.junit.jupiter.api.Assertions.assertThrows(
                com.zhiyi.common.BusinessException.class, () -> service.getSessions());

        assertEquals(403, error.getCode());
        verifyNoInteractions(messageMapper);
    }

    @Test
    void activeAdminWithNoMessagesGetsEmptyInbox() {
        SysUser admin = user(1L, "管理员", 99);
        admin.setStatus(com.zhiyi.common.enums.UserStatus.ACTIVE);
        when(userMapper.selectHumanAdmins()).thenReturn(List.of(admin));
        when(messageMapper.aggregateConversations(1L, null, 100)).thenReturn(List.of());

        assertTrue(service.getSessions().isEmpty());
    }

    @Test
    void sessionsAssembleFromPerConversationAggregates() {
        LocalDateTime now = LocalDateTime.now();
        SysUser admin = user(1L, "管理员", 99);
        admin.setStatus(com.zhiyi.common.enums.UserStatus.ACTIVE);
        SysUser alice = user(2L, "小爱", 3);
        SysUser bob = user(3L, "小博", 4);
        when(userMapper.selectHumanAdmins()).thenReturn(List.of(admin));
        when(messageMapper.aggregateConversations(1L, null, 100)).thenReturn(List.of(
                aggregate("3_admin", 5L, 3L, 1L),
                aggregate("2_admin", 4L, 2L, 2L)));
        when(messageMapper.selectByIds(any())).thenReturn(List.of(
                message(5L, "3_admin", 3L, 1L, "最新会话", false, now),
                message(4L, "2_admin", 1L, 2L, "管理员已回复", false, now.minusMinutes(1))));
        when(userMapper.selectByIds(any())).thenReturn(List.of(alice, bob));

        List<ConversationVO> sessions = service.getSessions();

        assertEquals(List.of("3_admin", "2_admin"),
                sessions.stream().map(ConversationVO::getConversationId).toList());
        assertEquals("最新会话", sessions.getFirst().getLastMessage());
        assertEquals("小博", sessions.getFirst().getPeer().getNickname());
        assertEquals(1L, sessions.getFirst().getUnreadCount());
        assertEquals("管理员已回复", sessions.getLast().getLastMessage());
        assertEquals(2L, sessions.getLast().getUnreadCount());
    }

    private com.zhiyi.module.social.dto.ConversationAggregate aggregate(
            String conversationId, Long lastMessageId, Long peerId, Long unreadCount) {
        com.zhiyi.module.social.dto.ConversationAggregate aggregate =
                new com.zhiyi.module.social.dto.ConversationAggregate();
        aggregate.setConversationId(conversationId);
        aggregate.setLastMessageId(lastMessageId);
        aggregate.setPeerId(peerId);
        aggregate.setUnreadCount(unreadCount);
        return aggregate;
    }

    private SysUser user(Long id, String nickname, int level) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setNickname(nickname);
        user.setLevel(level);
        return user;
    }

    private ChatMessage message(Long id, String conversation, Long sender, Long receiver,
                                String content, boolean read, LocalDateTime createdAt) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setConversationId(conversation);
        message.setSenderId(sender);
        message.setReceiverId(receiver);
        message.setContent(content);
        message.setIsRead(read);
        message.setCreatedAt(createdAt);
        return message;
    }
}
