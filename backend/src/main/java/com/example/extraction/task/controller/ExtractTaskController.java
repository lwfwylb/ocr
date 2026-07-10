package com.example.extraction.task.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.task.dto.TaskDispatchRequest;
import com.example.extraction.task.dto.TaskQueryRequest;
import com.example.extraction.task.dto.TaskResponse;
import com.example.extraction.task.dto.TaskRetryRequest;
import com.example.extraction.task.dto.TaskStageLogResponse;
import com.example.extraction.task.service.TaskExecutionService;
import com.example.extraction.task.service.ExtractTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class ExtractTaskController {
    private final ExtractTaskService extractTaskService;
    private final TaskExecutionService taskExecutionService;

    public ExtractTaskController(ExtractTaskService extractTaskService,
                                 TaskExecutionService taskExecutionService) {
        this.extractTaskService = extractTaskService;
        this.taskExecutionService = taskExecutionService;
    }

    @GetMapping
    public ApiResponse<List<TaskResponse>> list(TaskQueryRequest query) {
        return ApiResponse.success(extractTaskService.list(query));
    }

    @GetMapping("/failed")
    public ApiResponse<List<TaskResponse>> failed(TaskQueryRequest query) {
        return ApiResponse.success(extractTaskService.failed(query));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TaskResponse> detail(@PathVariable("taskId") String taskId) {
        return ApiResponse.success(extractTaskService.detail(taskId));
    }

    @PostMapping("/{taskId}/dispatch")
    public ApiResponse<TaskResponse> dispatch(@PathVariable("taskId") String taskId, @RequestBody TaskDispatchRequest request) {
        return ApiResponse.success(extractTaskService.dispatch(taskId, request));
    }

    @PostMapping("/{taskId}/retry")
    public ApiResponse<TaskResponse> retry(@PathVariable("taskId") String taskId, @RequestBody TaskRetryRequest request) {
        return ApiResponse.success(extractTaskService.retry(taskId, request));
    }

    @PostMapping("/{taskId}/execute")
    public ApiResponse<TaskResponse> execute(@PathVariable("taskId") String taskId) {
        return ApiResponse.success(taskExecutionService.execute(taskId));
    }

    @PostMapping("/execute-next")
    public ApiResponse<TaskResponse> executeNext() {
        return ApiResponse.success(taskExecutionService.executeNext());
    }

    @GetMapping("/{taskId}/stage-logs")
    public ApiResponse<List<TaskStageLogResponse>> stageLogs(@PathVariable("taskId") String taskId) {
        return ApiResponse.success(taskExecutionService.stageLogs(taskId));
    }
}
