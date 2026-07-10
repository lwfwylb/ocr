package com.example.extraction.trace.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.trace.dto.TraceDetailResponse;
import com.example.extraction.trace.dto.TraceQueryRequest;
import com.example.extraction.trace.dto.TraceSummaryResponse;
import com.example.extraction.trace.service.TraceMonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/traces")
public class TraceMonitorController {
    private final TraceMonitorService traceMonitorService;

    public TraceMonitorController(TraceMonitorService traceMonitorService) {
        this.traceMonitorService = traceMonitorService;
    }

    @GetMapping
    public ApiResponse<List<TraceSummaryResponse>> list(TraceQueryRequest query) {
        return ApiResponse.success(traceMonitorService.list(query));
    }

    @GetMapping("/{traceId}")
    public ApiResponse<TraceDetailResponse> detail(@PathVariable("traceId") String traceId) {
        return ApiResponse.success(traceMonitorService.detail(traceId));
    }
}
