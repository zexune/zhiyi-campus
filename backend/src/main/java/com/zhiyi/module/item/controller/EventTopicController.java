package com.zhiyi.module.item.controller;

import com.zhiyi.common.Result;
import com.zhiyi.common.annotation.RoleRequired;
import com.zhiyi.module.item.dto.EventTopicDTO;
import com.zhiyi.module.item.entity.EventTopic;
import com.zhiyi.module.item.service.EventTopicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class EventTopicController {
    private final EventTopicService topicService;

    @GetMapping("/api/item/active-topic")
    public Result<EventTopic> activeTopic() { return Result.ok(topicService.activeTopic()); }

    @GetMapping("/api/admin/event-topics") @RoleRequired("ADMIN")
    public Result<List<EventTopic>> list() { return Result.ok(topicService.listAll()); }

    @PostMapping("/api/admin/event-topics") @RoleRequired("ADMIN")
    public Result<EventTopic> create(@RequestAttribute("userId") Long adminId, @Valid @RequestBody EventTopicDTO dto) {
        return Result.ok("专题已创建", topicService.save(null, adminId, dto));
    }

    @PutMapping("/api/admin/event-topics/{id}") @RoleRequired("ADMIN")
    public Result<EventTopic> update(@PathVariable Long id, @RequestAttribute("userId") Long adminId,
                                     @Valid @RequestBody EventTopicDTO dto) {
        return Result.ok("专题已更新", topicService.save(id, adminId, dto));
    }

    @DeleteMapping("/api/admin/event-topics/{id}") @RoleRequired("ADMIN")
    public Result<Void> delete(@PathVariable Long id) {
        topicService.delete(id);
        return Result.ok("专题已删除", null);
    }
}
