package com.example.extraction.model.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.model.dto.PromptTemplateDefaultsRequest;
import com.example.extraction.model.dto.PromptTemplateDefaultsResponse;
import com.example.extraction.model.service.PromptTemplateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model/prompt-templates")
public class PromptTemplateController {
    private final PromptTemplateService promptTemplateService;

    public PromptTemplateController(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @GetMapping("/defaults")
    public ApiResponse<PromptTemplateDefaultsResponse> defaults() {
        return ApiResponse.success(promptTemplateService.defaults());
    }

    @PutMapping("/defaults")
    public ApiResponse<PromptTemplateDefaultsResponse> updateDefaults(@RequestBody PromptTemplateDefaultsRequest request) {
        return ApiResponse.success(promptTemplateService.updateDefaults(request));
    }

    @PostMapping("/reset-defaults")
    public ApiResponse<PromptTemplateDefaultsResponse> resetDefaults() {
        return ApiResponse.success(promptTemplateService.resetDefaults());
    }
}
