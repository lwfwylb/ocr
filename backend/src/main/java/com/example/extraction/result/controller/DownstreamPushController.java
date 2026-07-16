package com.example.extraction.result.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.common.PageResponse;
import com.example.extraction.common.PageSupport;
import com.example.extraction.result.dto.PushExecuteRequest;
import com.example.extraction.result.dto.PushQueryRequest;
import com.example.extraction.result.dto.PushRecordResponse;
import com.example.extraction.result.service.DownstreamPushService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/push-records")
public class DownstreamPushController {
    private final DownstreamPushService downstreamPushService;

    public DownstreamPushController(DownstreamPushService downstreamPushService) {
        this.downstreamPushService = downstreamPushService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PushRecordResponse>> list(PushQueryRequest query) {
        return ApiResponse.success(PageSupport.page(query, () -> downstreamPushService.list(query)));
    }

    @PostMapping("/{taskId}/push")
    public ApiResponse<PushRecordResponse> push(@PathVariable("taskId") String taskId,
                                                @RequestBody(required = false) PushExecuteRequest request) {
        return ApiResponse.success(downstreamPushService.push(taskId, request));
    }

    @PostMapping("/{pushId}/retry")
    public ApiResponse<PushRecordResponse> retry(@PathVariable("pushId") String pushId) {
        return ApiResponse.success(downstreamPushService.retry(pushId));
    }

    @PostMapping("/{pushId}/mark-success")
    public ApiResponse<PushRecordResponse> markSuccess(@PathVariable("pushId") String pushId) {
        return ApiResponse.success(downstreamPushService.markSuccess(pushId));
    }
}
