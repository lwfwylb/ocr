package com.example.extraction.result.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.result.dto.StorageExecuteRequest;
import com.example.extraction.result.dto.StorageQueryRequest;
import com.example.extraction.result.dto.StorageRecordResponse;
import com.example.extraction.result.dto.StorageTableResponse;
import com.example.extraction.result.service.StorageResultService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/storage")
public class StorageResultController {
    private final StorageResultService storageResultService;

    public StorageResultController(StorageResultService storageResultService) {
        this.storageResultService = storageResultService;
    }

    @GetMapping("/tables")
    public ApiResponse<List<StorageTableResponse>> tables(@RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.success(storageResultService.tables(keyword));
    }

    @GetMapping("/records")
    public ApiResponse<List<StorageRecordResponse>> records(StorageQueryRequest query) {
        return ApiResponse.success(storageResultService.records(query));
    }

    @PostMapping("/records/{taskId}/execute")
    public ApiResponse<StorageRecordResponse> execute(@PathVariable("taskId") String taskId,
                                                      @RequestBody StorageExecuteRequest request) {
        return ApiResponse.success(storageResultService.execute(taskId, request));
    }
}
