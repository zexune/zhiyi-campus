package com.zhiyi.module.admin.controller;

import com.zhiyi.common.ApiSuccess;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.BusinessErrors;
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
@RoleRequired
public class AdminChatController {

    private final AdminChatService adminChatService;
    private final ChatService chatService;

    /** 客服账号缺失/配置异常（500）与客服被跨校限制（403）都是显式契约。 */
    @GetMapping("/chat/sessions")
    @BusinessErrors({ResultCode.SERVER_ERROR, ResultCode.FORBIDDEN})
    public ApiSuccess<List<ConversationVO>> sessions() {
        return ApiSuccess.ok(adminChatService.getSessions());
    }

    @GetMapping("/chat/messages")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.FORBIDDEN, ResultCode.USER_NOT_FOUND})
    public ApiSuccess<ChatThreadVO> messages(@RequestAttribute("userId") Long adminId,
                                         @RequestParam String conversationId,
                                         @RequestParam(required = false) Long peerId,
                                         @RequestParam(required = false) Long relatedItemId,
                                         @RequestParam(required = false) Long beforeId) {
        return ApiSuccess.ok(chatService.messagesAsAdmin(
                adminId, conversationId, peerId, relatedItemId, beforeId));
    }

    @PostMapping("/chat/send")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.FORBIDDEN, ResultCode.USER_NOT_FOUND})
    public ApiSuccess<ChatMessageVO> send(@RequestAttribute("userId") Long adminId,
                                      @Valid @RequestBody ChatSendDTO dto) {
        return ApiSuccess.ok(chatService.sendAsAdmin(adminId, dto));
    }

    /** 管理端同模式显式已读确认（GET messages 只读）。 */
    @PostMapping("/chat/ack")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.FORBIDDEN})
    public ApiSuccess<Void> ack(@RequestAttribute("userId") Long adminId,
                            @RequestParam String conversationId,
                            @RequestParam Long lastSeenMessageId) {
        chatService.ackRead(adminId, conversationId, lastSeenMessageId);
        return ApiSuccess.ok(null);
    }

    @GetMapping("/chat/unread")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.FORBIDDEN})
    public ApiSuccess<List<ChatMessageVO>> unread(@RequestAttribute("userId") Long adminId,
                                              @RequestParam(required = false) String conversationId) {
        return ApiSuccess.ok(chatService.unreadMessagesAsAdmin(adminId, conversationId));
    }
}
