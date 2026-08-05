package com.zhiyi.module.social.controller;

import com.zhiyi.common.Result;
import com.zhiyi.module.social.dto.ChatSendDTO;
import com.zhiyi.module.social.dto.ChatStartDTO;
import com.zhiyi.module.social.service.ChatService;
import com.zhiyi.module.social.vo.ChatMessageVO;
import com.zhiyi.module.social.vo.ChatStartVO;
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

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/start")
    public Result<ChatStartVO> start(@RequestAttribute("userId") Long userId,
                                     @Valid @RequestBody ChatStartDTO dto) {
        return Result.ok(chatService.startItemConversation(userId, dto));
    }

    @PostMapping("/customer-service")
    public Result<ChatStartVO> customerService(@RequestAttribute("userId") Long userId) {
        return Result.ok(chatService.startCustomerService(userId));
    }

    @GetMapping("/conversations")
    public Result<List<ConversationVO>> conversations(@RequestAttribute("userId") Long userId) {
        return Result.ok(chatService.conversations(userId));
    }

    @GetMapping("/messages")
    public Result<ChatThreadVO> messages(@RequestAttribute("userId") Long userId,
                                         @RequestParam String conversationId,
                                         @RequestParam(required = false) Long peerId,
                                         @RequestParam(required = false) Long relatedItemId) {
        return Result.ok(chatService.messages(userId, conversationId, peerId, relatedItemId));
    }

    @PostMapping("/send")
    public Result<ChatMessageVO> send(@RequestAttribute("userId") Long userId,
                                      @Valid @RequestBody ChatSendDTO dto) {
        return Result.ok(chatService.send(userId, dto));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount(@RequestAttribute("userId") Long userId) {
        return Result.ok(chatService.unreadCount(userId));
    }

    @GetMapping("/unread")
    public Result<List<ChatMessageVO>> unread(@RequestAttribute("userId") Long userId,
                                              @RequestParam(required = false) String conversationId) {
        return Result.ok(chatService.unreadMessages(userId, conversationId));
    }
}
