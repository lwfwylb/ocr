package com.example.extraction.configuration.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.configuration.dto.ConfigDetailResponse;
import com.example.extraction.configuration.dto.ConfigOptionsResponse;
import com.example.extraction.configuration.dto.ConfigQueryRequest;
import com.example.extraction.configuration.dto.ConfigSummaryResponse;
import com.example.extraction.configuration.service.ConfigWizardService;
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
@RequestMapping("/api/config")
public class ConfigWizardController {
    private final ConfigWizardService configWizardService;

    public ConfigWizardController(ConfigWizardService configWizardService) {
        this.configWizardService = configWizardService;
    }

    @GetMapping("/extract-configs")
    public ApiResponse<List<ConfigSummaryResponse>> list(ConfigQueryRequest query) {
        return ApiResponse.success(configWizardService.list(query));
    }

    @GetMapping("/extract-configs/{id}")
    public ApiResponse<ConfigDetailResponse> detail(@PathVariable String id) {
        return ApiResponse.success(configWizardService.getDetail(id));
    }

    @GetMapping("/extract-configs/{id}/versions")
    public ApiResponse<List<ConfigSummaryResponse>> versions(@PathVariable String id) {
        return ApiResponse.success(configWizardService.listVersions(id));
    }

    @PostMapping("/extract-configs/draft")
    public ApiResponse<ConfigDetailResponse> createDraft(@RequestBody Map<String, Object> payload) {
        return ApiResponse.success(configWizardService.createDraft(payload));
    }

    @PutMapping("/extract-configs/{id}/draft")
    public ApiResponse<ConfigDetailResponse> updateDraft(@PathVariable String id, @RequestBody Map<String, Object> payload) {
        return ApiResponse.success(configWizardService.updateDraft(id, payload));
    }

    @PostMapping("/extract-configs/{id}/copy")
    public ApiResponse<ConfigDetailResponse> copy(@PathVariable String id) {
        return ApiResponse.success(configWizardService.copy(id));
    }

    @PostMapping("/extract-configs/{id}/publish")
    public ApiResponse<ConfigDetailResponse> publish(@PathVariable String id) {
        return ApiResponse.success(configWizardService.publish(id));
    }

    @PostMapping("/extract-configs/{id}/disable")
    public ApiResponse<ConfigDetailResponse> disable(@PathVariable String id) {
        return ApiResponse.success(configWizardService.disable(id));
    }

    @PostMapping("/extract-configs/{id}/validate")
    public ApiResponse<Map<String, Object>> validate(@PathVariable String id) {
        return ApiResponse.success(configWizardService.validate(id));
    }

    @GetMapping("/options")
    public ApiResponse<ConfigOptionsResponse> options() {
        return ApiResponse.success(configWizardService.options());
    }
}
