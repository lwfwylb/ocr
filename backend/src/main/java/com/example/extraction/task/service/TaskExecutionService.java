package com.example.extraction.task.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.mapper.ExtractTaskMapper;
import com.example.extraction.mapper.TaskStageLogMapper;
import com.example.extraction.result.service.ExtractionResultService;
import com.example.extraction.task.domain.ExtractTaskRecord;
import com.example.extraction.task.domain.TaskStageLogRecord;
import com.example.extraction.task.dto.TaskResponse;
import com.example.extraction.task.dto.TaskStageLogResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskExecutionService {
    private final ExtractTaskMapper extractTaskMapper;
    private final TaskStageLogMapper taskStageLogMapper;
    private final ExtractTaskService extractTaskService;
    private final ExtractionResultService extractionResultService;

    public TaskExecutionService(ExtractTaskMapper extractTaskMapper,
                                TaskStageLogMapper taskStageLogMapper,
                                ExtractTaskService extractTaskService,
                                ExtractionResultService extractionResultService) {
        this.extractTaskMapper = extractTaskMapper;
        this.taskStageLogMapper = taskStageLogMapper;
        this.extractTaskService = extractTaskService;
        this.extractionResultService = extractionResultService;
    }

    public List<TaskStageLogResponse> stageLogs(String taskId) {
        return taskStageLogMapper.selectByTaskId(taskId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TaskResponse executeNext() {
        List<ExtractTaskRecord> queuedTasks = extractTaskMapper.selectNextQueued();
        if (queuedTasks.isEmpty()) {
            throw new BusinessException("TASK_404", "\u6682\u65e0\u53ef\u6267\u884c\u7684\u6392\u961f\u4efb\u52a1");
        }
        return execute(queuedTasks.get(0).getTaskId());
    }

    @Transactional
    public TaskResponse execute(String taskId) {
        ExtractTaskRecord task = extractTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException("TASK_404", "\u4efb\u52a1\u4e0d\u5b58\u5728");
        }
        if (!List.of("QUEUED", "PARSING", "EXTRACTING").contains(task.getStatus())) {
            throw new BusinessException("TASK_409", "\u5f53\u524d\u72b6\u6001\u4e0d\u5141\u8bb8\u6267\u884c");
        }

        try {
            updateState(task, "PARSING", "\u6587\u6863\u89e3\u6790", 20, null, null);
            extractionResultService.saveParseResult(task);
            logSuccess(task, "PARSE", "\u6587\u6863\u89e3\u6790", "\u8bfb\u53d6\u539f\u6587\u4ef6\u5e76\u751f\u6210 Markdown", "\u6a21\u62df\u89e3\u6790\u5b8c\u6210\uff0c\u5df2\u83b7\u5f97\u6587\u6863\u6587\u672c");

            updateState(task, "EXTRACTING", "\u8981\u7d20\u63d0\u53d6", 55, null, null);
            boolean needReview = shouldReview(task);
            BigDecimal confidence = needReview ? new BigDecimal("0.86") : new BigDecimal("0.92");
            extractionResultService.saveExtractResult(task, confidence, needReview);
            logSuccess(task, "EXTRACT", "\u8981\u7d20\u63d0\u53d6", "\u6309\u751f\u6548\u914d\u7f6e\u6267\u884c AI/\u6b63\u5219\u63d0\u53d6", "\u6a21\u62df\u63d0\u53d6\u5b8c\u6210\uff0c\u7f6e\u4fe1\u5ea6 " + confidence);

            updateState(task, "EXTRACTING", "\u52a0\u5de5\u6821\u9a8c", 80, null, null);
            logSuccess(task, "VALIDATE", "\u52a0\u5de5\u6821\u9a8c", "\u6267\u884c\u5b57\u5178\u8f6c\u6362\u3001SQL/API \u53d6\u6570\u548c\u5fc5\u586b\u6821\u9a8c", "\u6a21\u62df\u52a0\u5de5\u6821\u9a8c\u901a\u8fc7");

            if (shouldFail(task)) {
                fail(task, "SIMULATED_FAILED", "\u6a21\u62df\u6267\u884c\u5931\u8d25\uff0c\u8bf7\u5728\u5931\u8d25\u4efb\u52a1\u4e2d\u91cd\u8bd5");
            } else if (shouldReview(task)) {
                updateState(task, "WAIT_REVIEW", "\u7b49\u5f85\u4eba\u5de5\u590d\u6838", 90, null, null);
                logSuccess(task, "REVIEW_DECISION", "\u590d\u6838\u5224\u65ad", "\u6839\u636e\u7f6e\u4fe1\u5ea6\u548c\u6821\u9a8c\u7ed3\u679c\u5224\u65ad", "\u6a21\u62df\u547d\u4e2d\u590d\u6838\u9608\u503c\uff0c\u8fdb\u5165\u4eba\u5de5\u590d\u6838");
            } else {
                updateState(task, "COMPLETED", "\u6267\u884c\u5b8c\u6210", 100, null, null);
                logSuccess(task, "COMPLETE", "\u6267\u884c\u5b8c\u6210", "\u5199\u5165\u6a21\u62df\u7ed3\u679c", "\u4efb\u52a1\u6a21\u62df\u6267\u884c\u5b8c\u6210");
            }
            return extractTaskService.detail(taskId);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            fail(task, "EXECUTION_ERROR", e.getMessage());
            return extractTaskService.detail(taskId);
        }
    }

    private boolean shouldReview(ExtractTaskRecord task) {
        String fileName = task.getFileName() == null ? "" : task.getFileName().toLowerCase();
        return "LOW".equals(task.getPriority()) || fileName.contains("low") || fileName.contains("review");
    }

    private boolean shouldFail(ExtractTaskRecord task) {
        String fileName = task.getFileName() == null ? "" : task.getFileName().toLowerCase();
        return fileName.contains("fail") || fileName.contains("error");
    }

    private void fail(ExtractTaskRecord task, String errorCode, String errorMessage) {
        updateState(task, "FAILED", "\u6267\u884c\u5931\u8d25", task.getProgress() == null ? 0 : task.getProgress(), errorCode, errorMessage);
        extractionResultService.markFailed(task, errorCode, errorMessage);
        logFailure(task, "FAILED", "\u6267\u884c\u5931\u8d25", errorCode, errorMessage);
    }

    private void updateState(ExtractTaskRecord task, String status, String stage, Integer progress, String errorCode, String errorMessage) {
        task.setStatus(status);
        task.setCurrentStage(stage);
        task.setProgress(progress);
        task.setErrorCode(errorCode);
        task.setErrorMessage(errorMessage);
        task.setFailedStage("FAILED".equals(status) ? stage : null);
        task.setFailedAt("FAILED".equals(status) ? LocalDateTime.now() : null);
        task.setUpdatedAt(LocalDateTime.now());
        extractTaskMapper.updateExecutionState(task);
    }

    private void logSuccess(ExtractTaskRecord task, String stageCode, String stageName, String inputSummary, String outputSummary) {
        writeLog(task, stageCode, stageName, "SUCCESS", inputSummary, outputSummary, null, null);
    }

    private void logFailure(ExtractTaskRecord task, String stageCode, String stageName, String errorCode, String errorMessage) {
        writeLog(task, stageCode, stageName, "FAILED", "\u6267\u884c\u9636\u6bb5\u5f02\u5e38", null, errorCode, errorMessage);
    }

    private void writeLog(ExtractTaskRecord task, String stageCode, String stageName, String status,
                          String inputSummary, String outputSummary, String errorCode, String errorMessage) {
        LocalDateTime startedAt = LocalDateTime.now().minusSeconds(1);
        LocalDateTime endedAt = LocalDateTime.now();
        TaskStageLogRecord log = new TaskStageLogRecord();
        log.setId(IdGenerator.nextId("TSL"));
        log.setTaskId(task.getTaskId());
        log.setTraceId(task.getTraceId());
        log.setStageCode(stageCode);
        log.setStageName(stageName);
        log.setStatus(status);
        log.setInputSummary(inputSummary);
        log.setOutputSummary(outputSummary);
        log.setErrorCode(errorCode);
        log.setErrorMessage(errorMessage);
        log.setStartedAt(startedAt);
        log.setEndedAt(endedAt);
        log.setDurationMs(Duration.between(startedAt, endedAt).toMillis());
        log.setCreatedAt(endedAt);
        taskStageLogMapper.insert(log);
    }

    private TaskStageLogResponse toResponse(TaskStageLogRecord record) {
        TaskStageLogResponse response = new TaskStageLogResponse();
        response.setId(record.getId());
        response.setTaskId(record.getTaskId());
        response.setTraceId(record.getTraceId());
        response.setStageCode(record.getStageCode());
        response.setStageName(record.getStageName());
        response.setStatus(record.getStatus());
        response.setInputSummary(record.getInputSummary());
        response.setOutputSummary(record.getOutputSummary());
        response.setErrorCode(record.getErrorCode());
        response.setErrorMessage(record.getErrorMessage());
        response.setStartedAt(record.getStartedAt());
        response.setEndedAt(record.getEndedAt());
        response.setDurationMs(record.getDurationMs());
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }
}
