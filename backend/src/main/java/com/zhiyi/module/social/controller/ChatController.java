package com.zhiyi.module.social.controller;

import com.zhiyi.common.ApiSuccess;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.BusinessErrors;
import com.zhiyi.module.social.dto.ChatSendDTO;
import com.zhiyi.module.social.dto.ChatStartDTO;
import com.zhiyi.module.social.service.ChatService;
import com.zhiyi.module.social.support.ChatEventBroadcaster;
import com.zhiyi.module.social.vo.ChatMessageVO;
import com.zhiyi.module.social.vo.ChatStartVO;
import com.zhiyi.module.social.vo.ChatThreadVO;
import com.zhiyi.module.social.vo.ConversationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatEventBroadcaster chatEventBroadcaster;

    @PostMapping("/start")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.USER_NOT_FOUND, ResultCode.FORBIDDEN})
    public ApiSuccess<ChatStartVO> start(@RequestAttribute("userId") Long userId,
                                     @Valid @RequestBody ChatStartDTO dto) {
        return ApiSuccess.ok(chatService.startItemConversation(userId, dto));
    }

    /** 客服会话：客服账号缺失/配置异常（500）或客服被跨校限制（403）都属于显式契约。 */
    @PostMapping("/customer-service")
    @BusinessErrors({ResultCode.SERVER_ERROR, ResultCode.FORBIDDEN})
    public ApiSuccess<ChatStartVO> customerService(@RequestAttribute("userId") Long userId) {
        return ApiSuccess.ok(chatService.startCustomerService(userId));
    }

    @GetMapping("/conversations")
    @BusinessErrors(ResultCode.USER_NOT_FOUND)
    public ApiSuccess<List<ConversationVO>> conversations(@RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Long beforeMessageId) {
        return ApiSuccess.ok(chatService.conversations(userId, beforeMessageId));
    }

    @GetMapping("/messages")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.FORBIDDEN, ResultCode.USER_NOT_FOUND})
    public ApiSuccess<ChatThreadVO> messages(@RequestAttribute("userId") Long userId,
                                         @RequestParam String conversationId,
                                         @RequestParam(required = false) Long peerId,
                                         @RequestParam(required = false) Long relatedItemId,
                                         @RequestParam(required = false) Long beforeId) {
        return ApiSuccess.ok(chatService.messages(userId, conversationId, peerId, relatedItemId, beforeId));
    }

    @PostMapping("/send")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.FORBIDDEN, ResultCode.USER_NOT_FOUND})
    public ApiSuccess<ChatMessageVO> send(@RequestAttribute("userId") Long userId,
                                      @Valid @RequestBody ChatSendDTO dto) {
        return ApiSuccess.ok(chatService.send(userId, dto));
    }

    /**
     * 显式已读确认（M1）：前端在消息实际渲染且可见后调用；
     * lastSeenMessageId 为当前视口最后一条可见的"接收"消息 ID。
     */
    @PostMapping("/ack")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.FORBIDDEN})
    public ApiSuccess<Void> ack(@RequestAttribute("userId") Long userId,
                            @RequestParam String conversationId,
                            @RequestParam Long lastSeenMessageId) {
        chatService.ackRead(userId, conversationId, lastSeenMessageId);
        return ApiSuccess.ok(null);
    }

    @GetMapping("/unread-count")
    @BusinessErrors(ResultCode.USER_NOT_FOUND)
    public ApiSuccess<Long> unreadCount(@RequestAttribute("userId") Long userId) {
        return ApiSuccess.ok(chatService.unreadCount(userId));
    }

    @GetMapping("/unread")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.FORBIDDEN, ResultCode.USER_NOT_FOUND})
    public ApiSuccess<List<ChatMessageVO>> unread(@RequestAttribute("userId") Long userId,
                                              @RequestParam(required = false) String conversationId) {
        return ApiSuccess.ok(chatService.unreadMessages(userId, conversationId));
    }

    /**
     * SSE 事件流（text/event-stream）：新消息与已读状态变化在事务提交后主动推送，
     * 替代前端定时轮询。事件只做变化信号，明细仍由客户端收到事件后经 REST 重拉。
     */
    @GetMapping("/stream")
    @BusinessErrors
    @Operation(summary = "订阅聊天事件流（SSE）")
    @ApiResponse(responseCode = "200", description = "text/event-stream：event:ready（重连节奏）与 event:chat（MESSAGE/READ 变化信号）",
            content = @Content(mediaType = "text/event-stream", schema = @Schema(type = "object")))
    public SseEmitter stream(@RequestAttribute("userId") Long userId) {
        return chatEventBroadcaster.connect(userId);
    }
}
