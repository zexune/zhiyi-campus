package com.zhiyi.module.admin.controller;

import com.zhiyi.common.Result;
import com.zhiyi.common.annotation.RoleRequired;
import com.zhiyi.module.admin.service.AdminChatService;
import com.zhiyi.module.social.dto.ChatSendDTO;
import com.zhiyi.module.social.service.ChatService;
import com.zhiyi.module.social.vo.ChatMessageVO;
import com.zhiyi.module.social.vo.ChatThreadVO;
import com.zhiyi.module.social.vo.ConversationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 超管控制台 · 客服收件箱（4.6）
 *
 * GET /api/admin/chat/sessions    客服会话列表
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RoleRequired("ADMIN")
public class AdminChatController {

    private final AdminChatService adminChatService;
    private final ChatService chatService;

    @GetMapping("/chat/sessions")
    public Result<List<ConversationVO>> sessions() {
        return Result.ok(adminChatService.getSessions());
    }

    @GetMapping("/chat/messages")
    public Result<ChatThreadVO> messages(@RequestAttribute("userId") Long adminId,
                                         @RequestParam String conversationId,
                                         @RequestParam(required = false) Long peerId,
                                         @RequestParam(required = false) Long relatedItemId) {
        return Result.ok(chatService.messagesAsAdmin(
                adminId, conversationId, peerId, relatedItemId));
    }

    @PostMapping("/chat/send")
    public Result<ChatMessageVO> send(@RequestAttribute("userId") Long adminId,
                                      @Valid @RequestBody ChatSendDTO dto) {
        return Result.ok(chatService.sendAsAdmin(adminId, dto));
    }

    @GetMapping("/chat/unread")
    public Result<List<ChatMessageVO>> unread(@RequestAttribute("userId") Long adminId,
                                              @RequestParam(required = false) String conversationId) {
        return Result.ok(chatService.unreadMessagesAsAdmin(adminId, conversationId));
    }
}
