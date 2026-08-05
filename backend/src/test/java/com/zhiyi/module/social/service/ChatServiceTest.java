package com.zhiyi.module.social.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.social.dto.ChatSendDTO;
import com.zhiyi.module.social.dto.ChatStartDTO;
import com.zhiyi.module.social.entity.ChatMessage;
import com.zhiyi.module.social.mapper.ChatMessageMapper;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatMessageMapper chatMessageMapper;
    @Mock
    private ItemMapper itemMapper;
    @Mock
    private SysUserMapper userMapper;

    private ChatService service;

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace(
                "com.zhiyi.module.social.mapper.ChatMessageMapper");
        TableInfoHelper.initTableInfo(assistant, ChatMessage.class);
    }

    @BeforeEach
    void setUp() {
        service = new ChatService(
                chatMessageMapper, itemMapper, userMapper, new ObjectMapper());
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
    void ordinaryAdministratorConversationListOnlyContainsSameSchoolPeers() {
        ChatMessage sameSchool = message(1L, 1L, 9L);
        ChatMessage crossSchool = message(2L, 2L, 9L);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(crossSchool, sameSchool));
        when(userMapper.selectById(9L)).thenReturn(user(9L, 1L, "ADMIN"));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(1L, 1L, "USER"),
                user(2L, 2L, "USER")
        ));

        var result = service.conversations(9L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getPeer().getId());
    }

    @Test
    void ordinaryAdministratorUnreadCountOnlyContainsSameSchoolMessages() {
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(
                message(1L, 1L, 9L),
                message(2L, 2L, 9L)
        ));
        when(userMapper.selectById(9L)).thenReturn(user(9L, 1L, "ADMIN"));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(1L, 1L, "USER"),
                user(2L, 2L, "USER")
        ));

        assertEquals(1L, service.unreadCount(9L));
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
        user.setRole(role);
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

    private Item item(Long id, Long publisherId, Long schoolId) {
        Item item = new Item();
        item.setId(id);
        item.setPublisherId(publisherId);
        item.setSchoolId(schoolId);
        item.setTitle("测试商品");
        return item;
    }
}
