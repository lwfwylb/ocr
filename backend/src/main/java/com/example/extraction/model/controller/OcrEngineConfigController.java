package com.example.extraction.model.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.model.dto.OcrEngineConfigRequest;
import com.example.extraction.model.dto.OcrEngineConfigResponse;
import com.example.extraction.model.dto.OcrEngineQueryRequest;
import com.example.extraction.model.service.OcrEngineConfigService;
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
@RequestMapping("/api/model/ocr-engines")
public class OcrEngineConfigController {
    private final OcrEngineConfigService ocrEngineConfigService;

    public OcrEngineConfigController(OcrEngineConfigService ocrEngineConfigService) {
        this.ocrEngineConfigService = ocrEngineConfigService;
    }

    @GetMapping
    public ApiResponse<List<OcrEngineConfigResponse>> list(OcrEngineQueryRequest query) {
        return ApiResponse.success(ocrEngineConfigService.list(query));
    }

    @GetMapping("/options")
    public ApiResponse<List<Map<String, Object>>> options() {
        return ApiResponse.success(ocrEngineConfigService.options());
    }

    @GetMapping("/{id}")
    public ApiResponse<OcrEngineConfigResponse> detail(@PathVariable("id") String id) {
        return ApiResponse.success(ocrEngineConfigService.detail(id));
    }

    @PostMapping
    public ApiResponse<OcrEngineConfigResponse> create(@RequestBody OcrEngineConfigRequest request) {
        return ApiResponse.success(ocrEngineConfigService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<OcrEngineConfigResponse> update(@PathVariable("id") String id, @RequestBody OcrEngineConfigRequest request) {
        return ApiResponse.success(ocrEngineConfigService.update(id, request));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<OcrEngineConfigResponse> enable(@PathVariable("id") String id) {
        return ApiResponse.success(ocrEngineConfigService.enable(id));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<OcrEngineConfigResponse> disable(@PathVariable("id") String id) {
        return ApiResponse.success(ocrEngineConfigService.disable(id));
    }

    @PostMapping("/{id}/default")
    public ApiResponse<OcrEngineConfigResponse> setDefault(@PathVariable("id") String id) {
        return ApiResponse.success(ocrEngineConfigService.setDefault(id));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable("id") String id) {
        return ApiResponse.success(ocrEngineConfigService.test(id));
    }
}
