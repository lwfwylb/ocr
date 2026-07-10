package com.example.extraction.result.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.result.dto.ResultDetailResponse;
import com.example.extraction.result.dto.ResultQueryRequest;
import com.example.extraction.result.dto.ResultSummaryResponse;
import com.example.extraction.result.service.ExtractionResultService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/results")
public class ExtractionResultController {
    private final ExtractionResultService extractionResultService;

    public ExtractionResultController(ExtractionResultService extractionResultService) {
        this.extractionResultService = extractionResultService;
    }

    @GetMapping
    public ApiResponse<List<ResultSummaryResponse>> list(ResultQueryRequest query) {
        return ApiResponse.success(extractionResultService.list(query));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<ResultDetailResponse> detail(@PathVariable("taskId") String taskId) {
        return ApiResponse.success(extractionResultService.detail(taskId));
    }
}
