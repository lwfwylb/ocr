package com.example.extraction.model.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.model.dto.LlmModelConfigRequest;
import com.example.extraction.model.dto.LlmModelConfigResponse;
import com.example.extraction.model.dto.LlmModelQueryRequest;
import com.example.extraction.model.dto.LlmModelTestRequest;
import com.example.extraction.model.dto.LlmModelTestResponse;
import com.example.extraction.model.service.LlmModelConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/model/llm-configs")
public class LlmModelConfigController {
    private final LlmModelConfigService llmModelConfigService;

    public LlmModelConfigController(LlmModelConfigService llmModelConfigService) {
        this.llmModelConfigService = llmModelConfigService;
    }

    @GetMapping
    public ApiResponse<List<LlmModelConfigResponse>> list(LlmModelQueryRequest query) {
        return ApiResponse.success(llmModelConfigService.list(query));
    }

    @GetMapping("/options")
    public ApiResponse<List<Map<String, Object>>> options() {
        return ApiResponse.success(llmModelConfigService.options());
    }

    @GetMapping("/{id}")
    public ApiResponse<LlmModelConfigResponse> detail(@PathVariable("id") String id) {
        return ApiResponse.success(llmModelConfigService.detail(id));
    }

    @PostMapping
    public ApiResponse<LlmModelConfigResponse> create(@RequestBody LlmModelConfigRequest request) {
        return ApiResponse.success(llmModelConfigService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<LlmModelConfigResponse> update(@PathVariable("id") String id, @RequestBody LlmModelConfigRequest request) {
        return ApiResponse.success(llmModelConfigService.update(id, request));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<LlmModelConfigResponse> enable(@PathVariable("id") String id) {
        return ApiResponse.success(llmModelConfigService.enable(id));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<LlmModelConfigResponse> disable(@PathVariable("id") String id) {
        return ApiResponse.success(llmModelConfigService.disable(id));
    }

    @PostMapping("/{id}/default")
    public ApiResponse<LlmModelConfigResponse> setDefault(@PathVariable("id") String id) {
        return ApiResponse.success(llmModelConfigService.setDefault(id));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<LlmModelTestResponse> test(@PathVariable("id") String id, @RequestBody(required = false) LlmModelTestRequest request) {
        return ApiResponse.success(llmModelConfigService.test(id, request));
    }
}
