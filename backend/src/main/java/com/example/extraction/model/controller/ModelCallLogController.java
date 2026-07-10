package com.example.extraction.model.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.model.dto.ModelCallLogQueryRequest;
import com.example.extraction.model.dto.ModelCallLogResponse;
import com.example.extraction.model.service.ModelCallLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/model-call-logs")
public class ModelCallLogController {
    private final ModelCallLogService modelCallLogService;

    public ModelCallLogController(ModelCallLogService modelCallLogService) {
        this.modelCallLogService = modelCallLogService;
    }

    @GetMapping
    public ApiResponse<List<ModelCallLogResponse>> list(ModelCallLogQueryRequest query) {
        return ApiResponse.success(modelCallLogService.list(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<ModelCallLogResponse> detail(@PathVariable("id") String id) {
        return ApiResponse.success(modelCallLogService.detail(id));
    }
}
