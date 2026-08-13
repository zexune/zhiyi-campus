package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhiyi.module.social.entity.ChatMessage;
import com.zhiyi.module.social.mapper.ChatMessageMapper;
import com.zhiyi.module.social.vo.ConversationVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

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
        initialize("com.zhiyi.module.social.mapper.ChatMessageMapper", ChatMessage.class);
        initialize("com.zhiyi.module.user.mapper.SysUserMapper", SysUser.class);
    }

    private static void initialize(String namespace, Class<?> entity) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace(namespace);
        TableInfoHelper.initTableInfo(assistant, entity);
    }

    @BeforeEach
    void setUp() {
        service = new AdminChatService(messageMapper, userMapper);
    }

    @Test
    void noActiveAdminMeansNoInboxQuery() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertTrue(service.getSessions().isEmpty());

        verifyNoInteractions(messageMapper);
    }

    @Test
    void activeAdminWithNoMessagesGetsEmptyInbox() {
        when(userMapper.selectOne(any())).thenReturn(user(1L, "管理员", 99));
        when(messageMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.getSessions().isEmpty());
    }

    @Test
    void sessionsUseLatestMessageAndCountOnlyUnreadInboundMessages() {
        LocalDateTime now = LocalDateTime.now();
        SysUser admin = user(1L, "管理员", 99);
        SysUser alice = user(2L, "小爱", 3);
        SysUser bob = user(3L, "小博", 4);
        when(userMapper.selectOne(any())).thenReturn(admin);
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message(5L, "3_admin", 3L, 1L, "最新会话", false, now),
                message(4L, "2_admin", 1L, 2L, "管理员已回复", false, now.minusMinutes(1)),
                message(3L, "2_admin", 2L, 1L, "请问还在吗", false, now.minusMinutes(2)),
                message(2L, "2_admin", 2L, 1L, "你好", false, now.minusMinutes(3)),
                message(1L, "2_admin", 2L, 1L, "已读旧消息", true, now.minusMinutes(4))));
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
