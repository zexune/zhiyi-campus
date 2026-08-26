package com.zhiyi.module.item.controller;

import com.zhiyi.common.ApiSuccess;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.BusinessErrors;
import com.zhiyi.common.annotation.RoleRequired;
import com.zhiyi.module.item.dto.EventTopicDTO;
import com.zhiyi.module.item.entity.EventTopic;
import com.zhiyi.module.item.service.EventTopicService;
import com.zhiyi.module.item.vo.EventTopicResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EventTopicController {
    private final EventTopicService topicService;

    /** 大厅活动专题；"当前没有活动专题"是正常结果（data 为 null），非错误。 */
    @GetMapping("/api/item/active-topic")
    @BusinessErrors
    public ApiSuccess<EventTopicResponse> activeTopic() {
        EventTopic active = topicService.activeTopic();
        return ApiSuccess.ok(active == null ? null : EventTopicResponse.from(active));
    }

    @GetMapping("/api/admin/event-topics")
    @RoleRequired
    @BusinessErrors
    public ApiSuccess<List<EventTopicResponse>> list() {
        return ApiSuccess.ok(topicService.listAll().stream()
                .map(EventTopicResponse::from)
                .toList());
    }

    @PostMapping("/api/admin/event-topics")
    @RoleRequired
    @BusinessErrors(ResultCode.NOT_FOUND)
    public ApiSuccess<EventTopicResponse> create(@RequestAttribute("userId") Long adminId, @Valid @RequestBody EventTopicDTO dto) {
        return ApiSuccess.ok("专题已创建", EventTopicResponse.from(topicService.save(null, adminId, dto)));
    }

    @PutMapping("/api/admin/event-topics/{id}")
    @RoleRequired
    @BusinessErrors(ResultCode.NOT_FOUND)
    public ApiSuccess<EventTopicResponse> update(@PathVariable Long id, @RequestAttribute("userId") Long adminId,
                                                 @Valid @RequestBody EventTopicDTO dto) {
        return ApiSuccess.ok("专题已更新", EventTopicResponse.from(topicService.save(id, adminId, dto)));
    }

    @DeleteMapping("/api/admin/event-topics/{id}")
    @RoleRequired
    @BusinessErrors(ResultCode.NOT_FOUND)
    public ApiSuccess<Void> delete(@PathVariable Long id) {
        topicService.delete(id);
        return ApiSuccess.ok("专题已删除", null);
    }
}
