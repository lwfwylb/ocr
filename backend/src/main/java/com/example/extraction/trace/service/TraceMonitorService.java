package com.example.extraction.trace.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.document.domain.DocumentAccessRecord;
import com.example.extraction.document.dto.DocumentAccessQueryRequest;
import com.example.extraction.document.dto.DocumentAccessResponse;
import com.example.extraction.mapper.DocumentAccessMapper;
import com.example.extraction.mapper.DocumentParseResultMapper;
import com.example.extraction.mapper.ExtractResultMapper;
import com.example.extraction.mapper.ExtractTaskMapper;
import com.example.extraction.mapper.ReviewLogMapper;
import com.example.extraction.mapper.StorageResultMapper;
import com.example.extraction.mapper.TaskStageLogMapper;
import com.example.extraction.result.domain.DocumentParseResultRecord;
import com.example.extraction.result.domain.ExtractResultRecord;
import com.example.extraction.result.domain.ReviewLogRecord;
import com.example.extraction.result.domain.StorageResultRecord;
import com.example.extraction.result.dto.PushRecordResponse;
import com.example.extraction.result.dto.ResultDetailResponse;
import com.example.extraction.result.dto.ResultSummaryResponse;
import com.example.extraction.result.dto.ReviewLogResponse;
import com.example.extraction.result.dto.StorageRecordResponse;
import com.example.extraction.result.service.DownstreamPushService;
import com.example.extraction.task.domain.ExtractTaskRecord;
import com.example.extraction.task.domain.TaskStageLogRecord;
import com.example.extraction.task.dto.TaskResponse;
import com.example.extraction.trace.dto.TraceDetailResponse;
import com.example.extraction.trace.dto.TraceQueryRequest;
import com.example.extraction.trace.dto.TraceStageResponse;
import com.example.extraction.trace.dto.TraceSummaryResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TraceMonitorService {
    private final DocumentAccessMapper documentAccessMapper;
    private final ExtractTaskMapper extractTaskMapper;
    private final TaskStageLogMapper taskStageLogMapper;
    private final DocumentParseResultMapper documentParseResultMapper;
    private final ExtractResultMapper extractResultMapper;
    private final ReviewLogMapper reviewLogMapper;
    private final StorageResultMapper storageResultMapper;
    private final DownstreamPushService downstreamPushService;
    private final ObjectMapper objectMapper;

    public TraceMonitorService(DocumentAccessMapper documentAccessMapper,
                               ExtractTaskMapper extractTaskMapper,
                               TaskStageLogMapper taskStageLogMapper,
                               DocumentParseResultMapper documentParseResultMapper,
                               ExtractResultMapper extractResultMapper,
                               ReviewLogMapper reviewLogMapper,
                               StorageResultMapper storageResultMapper,
                               DownstreamPushService downstreamPushService,
                               ObjectMapper objectMapper) {
        this.documentAccessMapper = documentAccessMapper;
        this.extractTaskMapper = extractTaskMapper;
        this.taskStageLogMapper = taskStageLogMapper;
        this.documentParseResultMapper = documentParseResultMapper;
        this.extractResultMapper = extractResultMapper;
        this.reviewLogMapper = reviewLogMapper;
        this.storageResultMapper = storageResultMapper;
        this.downstreamPushService = downstreamPushService;
        this.objectMapper = objectMapper;
    }

    public List<TraceSummaryResponse> list(TraceQueryRequest query) {
        DocumentAccessQueryRequest accessQuery = new DocumentAccessQueryRequest();
        accessQuery.setKeyword(query == null ? null : query.getKeyword());
        accessQuery.setSourceType(query == null ? null : normalizeSourceType(query.getSourceType()));
        return documentAccessMapper.selectList(accessQuery).stream()
                .map(this::toSummary)
                .filter(summary -> query == null || !StringUtils.hasText(query.getStatus()) || query.getStatus().equals(summary.getStatus()))
                .toList();
    }

    public TraceDetailResponse detail(String traceId) {
        DocumentAccessRecord access = documentAccessMapper.selectByTraceId(traceId);
        ExtractTaskRecord task = extractTaskMapper.selectByTraceId(traceId);
        if (access == null && task == null) {
            throw new BusinessException("TRACE_404", "\u94fe\u8def\u4e0d\u5b58\u5728");
        }
        String taskId = firstText(task == null ? null : task.getTaskId(), access == null ? null : access.getTaskId());
        ExtractResultRecord result = StringUtils.hasText(taskId) ? extractResultMapper.selectByTaskId(taskId) : null;
        DocumentParseResultRecord parse = StringUtils.hasText(taskId) ? documentParseResultMapper.selectByTaskId(taskId) : null;
        StorageResultRecord storage = StringUtils.hasText(taskId) ? storageResultMapper.selectByTaskId(taskId) : null;
        List<PushRecordResponse> pushRecords = StringUtils.hasText(taskId) ? downstreamPushService.listByTaskId(taskId) : List.of();
        List<ReviewLogRecord> reviewLogs = StringUtils.hasText(taskId) ? reviewLogMapper.selectByTaskId(taskId) : List.of();

        TraceDetailResponse response = new TraceDetailResponse();
        response.setSummary(toSummary(access, task, result, storage));
        response.setAccessRecord(access == null ? null : toAccessResponse(access));
        response.setTask(task == null ? null : toTaskResponse(task));
        response.setResult(toResultDetail(result, parse));
        response.setStorageRecord(toStorageResponse(storage, task));
        response.setPushRecords(pushRecords);
        response.setReviewLogs(reviewLogs.stream().map(this::toReviewLogResponse).toList());
        response.setStages(buildStages(access, task, result, storage, pushRecords, reviewLogs));
        response.setSuggestions(buildSuggestions(access, task, result, storage, pushRecords, reviewLogs));
        return response;
    }

    private TraceSummaryResponse toSummary(DocumentAccessRecord access) {
        ExtractTaskRecord task = StringUtils.hasText(access.getTraceId()) ? extractTaskMapper.selectByTraceId(access.getTraceId()) : null;
        ExtractResultRecord result = task == null ? null : extractResultMapper.selectByTaskId(task.getTaskId());
        StorageResultRecord storage = task == null ? null : storageResultMapper.selectByTaskId(task.getTaskId());
        return toSummary(access, task, result, storage);
    }

    private TraceSummaryResponse toSummary(DocumentAccessRecord access, ExtractTaskRecord task, ExtractResultRecord result, StorageResultRecord storage) {
        TraceSummaryResponse summary = new TraceSummaryResponse();
        summary.setTraceId(firstText(access == null ? null : access.getTraceId(), task == null ? null : task.getTraceId(), result == null ? null : result.getTraceId()));
        summary.setTaskId(firstText(task == null ? null : task.getTaskId(), access == null ? null : access.getTaskId(), result == null ? null : result.getTaskId()));
        summary.setDocumentId(firstText(access == null ? null : access.getDocumentId(), task == null ? null : task.getDocumentId(), result == null ? null : result.getDocumentId()));
        summary.setFileName(firstText(task == null ? null : task.getFileName(), access == null ? null : access.getFileName()));
        summary.setBusinessNo(firstText(task == null ? null : task.getBusinessNo(), access == null ? null : access.getBusinessNo()));
        summary.setSourceType(firstText(task == null ? null : task.getSourceType(), access == null ? null : access.getSourceType()));
        summary.setDocumentType(firstText(task == null ? null : task.getDocumentType(), access == null ? null : access.getDocumentType()));
        summary.setDepartmentId(firstText(task == null ? null : task.getDepartmentId(), access == null ? null : access.getDepartmentId()));
        summary.setStatus(resolveStatus(access, task, result, storage));
        summary.setCurrentStage(resolveStage(access, task, result, storage));
        summary.setProgress(resolveProgress(access, task, result, storage));
        summary.setCreatedAt(access == null ? (task == null ? null : task.getCreatedAt()) : access.getCreatedAt());
        summary.setUpdatedAt(latest(access == null ? null : access.getUpdatedAt(), task == null ? null : task.getUpdatedAt(), result == null ? null : result.getUpdatedAt(), storage == null ? null : storage.getUpdatedAt()));
        return summary;
    }

    private List<TraceStageResponse> buildStages(DocumentAccessRecord access, ExtractTaskRecord task, ExtractResultRecord result,
                                                 StorageResultRecord storage, List<PushRecordResponse> pushRecords, List<ReviewLogRecord> reviewLogs) {
        List<TraceStageResponse> stages = new ArrayList<>();
        stages.add(stage("ACCESS", "\u6587\u6863\u63a5\u5165", access == null ? "PENDING" : statusFromAccess(access),
                access == null ? null : access.getSourceType(), access == null ? null : access.getMatchMessage(), null, null,
                null, access == null ? null : access.getCreatedAt(), access == null ? null : access.getUpdatedAt()));
        stages.add(stage("MATCH", "\u914d\u7f6e\u5339\u914d", access == null ? "PENDING" : matchStageStatus(access),
                access == null ? null : access.getDocumentType(), access == null ? null : access.getMatchedConfigName(), null, null,
                null, access == null ? null : access.getCreatedAt(), access == null ? null : access.getUpdatedAt()));
        stages.add(stage("QUEUE", "\u4efb\u52a1\u6392\u961f", task == null ? "PENDING" : "SUCCESS",
                task == null ? null : task.getQueueName(), task == null ? null : task.getCurrentStage(), null, null,
                null, task == null ? null : task.getCreatedAt(), task == null ? null : task.getUpdatedAt()));
        if (task != null) {
            taskStageLogMapper.selectByTaskId(task.getTaskId()).stream().map(this::toStageResponse).forEach(stages::add);
        }
        stages.add(stage("REVIEW", "\u4eba\u5de5\u590d\u6838", reviewStatus(result, reviewLogs),
                result == null ? null : result.getStatus(), reviewLogs.isEmpty() ? "\u65e0\u590d\u6838\u8bb0\u5f55" : "\u5df2\u8bb0\u5f55 " + reviewLogs.size() + " \u6b21\u590d\u6838\u64cd\u4f5c",
                null, null, null, firstReviewTime(reviewLogs), firstReviewTime(reviewLogs)));
        stages.add(stage("STORAGE", "\u7ed3\u679c\u843d\u5e93", storage == null ? "PENDING" : storageStatus(storage),
                storage == null ? null : storage.getTargetTable(), storage == null ? null : storage.getStorageStatus(),
                null, storage == null ? null : storage.getErrorMessage(), null,
                storage == null ? null : storage.getStoredAt(), storage == null ? null : storage.getUpdatedAt()));
        PushRecordResponse latestPush = latestPush(pushRecords);
        stages.add(stage("PUSH", "\u4e0b\u6e38\u63a8\u9001", pushStatus(latestPush),
                latestPush == null ? null : latestPush.getTargetSystem(),
                latestPush == null ? "\u5c1a\u672a\u53d1\u8d77\u63a8\u9001" : latestPush.getResponseMessage(),
                latestPush == null ? null : latestPush.getResponseCode(),
                latestPush == null ? null : latestPush.getResponseMessage(),
                null,
                latestPush == null ? null : latestPush.getPushedAt(),
                latestPush == null ? null : latestPush.getUpdatedAt()));
        return stages;
    }

    private List<String> buildSuggestions(DocumentAccessRecord access, ExtractTaskRecord task, ExtractResultRecord result,
                                          StorageResultRecord storage, List<PushRecordResponse> pushRecords, List<ReviewLogRecord> reviewLogs) {
        List<String> suggestions = new ArrayList<>();
        if (access != null && !"MATCHED".equals(access.getMatchStatus())) {
            suggestions.add("\u6587\u6863\u672a\u552f\u4e00\u5339\u914d\u914d\u7f6e\uff0c\u8bf7\u5728\u5f85\u786e\u8ba4\u6587\u6863\u4e2d\u624b\u5de5\u786e\u8ba4\u3002");
        }
        if (task != null && "FAILED".equals(task.getStatus())) {
            suggestions.add("\u4efb\u52a1\u6267\u884c\u5931\u8d25\uff1a" + firstText(task.getErrorMessage(), task.getErrorCode(), "-") + "\uff0c\u53ef\u5728\u5931\u8d25\u4efb\u52a1\u4e2d\u67e5\u770b\u5e76\u91cd\u8bd5\u3002");
        }
        if (result != null && "WAIT_REVIEW".equals(result.getStatus())) {
            suggestions.add("\u63d0\u53d6\u7ed3\u679c\u5f85\u590d\u6838\uff0c\u8bf7\u8fdb\u5165\u4eba\u5de5\u590d\u6838\u4e2d\u786e\u8ba4\u4f4e\u7f6e\u4fe1\u5ea6\u5b57\u6bb5\u3002");
        }
        if (result != null && List.of("STORED", "PUSHED").contains(result.getStatus()) && storage == null) {
            suggestions.add("\u7ed3\u679c\u5df2\u786e\u8ba4\u4f46\u672a\u843d\u5e93\uff0c\u53ef\u5728\u7ed3\u679c\u4e2d\u5fc3\u6267\u884c\u843d\u5e93\u3002");
        }
        PushRecordResponse latestPush = latestPush(pushRecords);
        if (storage != null && "SUCCESS".equals(storage.getStorageStatus()) && latestPush == null) {
            suggestions.add("\u7ed3\u679c\u5df2\u843d\u5e93\uff0c\u53ef\u5728\u7ed3\u679c\u4e2d\u5fc3\u53d1\u8d77\u4e0b\u6e38\u63a8\u9001\u3002");
        }
        if (latestPush != null && "FAILED".equals(latestPush.getStatus())) {
            suggestions.add("\u4e0b\u6e38\u63a8\u9001\u5931\u8d25\uff1a" + firstText(latestPush.getResponseMessage(), latestPush.getResponseCode(), "-") + "\uff0c\u53ef\u5728\u63a8\u9001\u8bb0\u5f55\u4e2d\u91cd\u8bd5\u3002");
        }
        if (reviewLogs != null && !reviewLogs.isEmpty()) {
            suggestions.add("\u94fe\u8def\u5305\u542b\u590d\u6838\u64cd\u4f5c\uff0c\u8bf7\u5173\u6ce8\u590d\u6838\u5907\u6ce8\u548c\u5b57\u6bb5\u4fee\u6b63\u5185\u5bb9\u3002");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("\u94fe\u8def\u6682\u65e0\u5f02\u5e38\uff0c\u53ef\u7ee7\u7eed\u6267\u884c\u540e\u7eed\u590d\u6838\u3001\u843d\u5e93\u6216\u63a8\u9001\u52a8\u4f5c\u3002");
        }
        return suggestions;
    }

    private TraceStageResponse toStageResponse(TaskStageLogRecord record) {
        return stage(record.getStageCode(), record.getStageName(), record.getStatus(), record.getInputSummary(), record.getOutputSummary(),
                record.getErrorCode(), record.getErrorMessage(), record.getDurationMs(), record.getStartedAt(), record.getEndedAt());
    }

    private TraceStageResponse stage(String code, String name, String status, String input, String output, String errorCode,
                                     String errorMessage, Long durationMs, LocalDateTime startedAt, LocalDateTime endedAt) {
        TraceStageResponse stage = new TraceStageResponse();
        stage.setStageCode(code);
        stage.setStageName(name);
        stage.setStatus(status);
        stage.setInputSummary(input);
        stage.setOutputSummary(output);
        stage.setErrorCode(errorCode);
        stage.setErrorMessage(errorMessage);
        stage.setDurationMs(durationMs);
        stage.setStartedAt(startedAt);
        stage.setEndedAt(endedAt);
        return stage;
    }

    private ResultDetailResponse toResultDetail(ExtractResultRecord result, DocumentParseResultRecord parse) {
        if (result == null && parse == null) {
            return null;
        }
        ResultDetailResponse response = new ResultDetailResponse();
        if (result != null) {
            ResultSummaryResponse summary = new ResultSummaryResponse();
            summary.setTaskId(result.getTaskId());
            summary.setTraceId(result.getTraceId());
            summary.setDocumentId(result.getDocumentId());
            summary.setResultStatus(result.getStatus());
            summary.setReviewStatus("1".equals(result.getNeedReview()) ? "\u5f85\u590d\u6838" : "\u81ea\u52a8\u901a\u8fc7");
            summary.setTargetTable(result.getTargetTable());
            summary.setMappingProfile(result.getMappingProfile());
            summary.setFieldCount(result.getFieldCount());
            summary.setOverallConfidence(result.getOverallConfidence());
            summary.setCreatedAt(result.getCreatedAt());
            summary.setUpdatedAt(result.getUpdatedAt());
            response.setSummary(summary);
            response.setResult(readJson(result.getResultJson()));
            response.setConfidence(readJson(result.getConfidenceJson()));
        }
        response.setParseText(parse == null ? null : parse.getParseText());
        response.setPageCount(parse == null ? null : parse.getPageCount());
        response.setEngineCode(parse == null ? null : parse.getEngineCode());
        return response;
    }

    private StorageRecordResponse toStorageResponse(StorageResultRecord storage, ExtractTaskRecord task) {
        if (storage == null) {
            return null;
        }
        StorageRecordResponse response = new StorageRecordResponse();
        response.setId(storage.getId());
        response.setTaskId(storage.getTaskId());
        response.setTraceId(storage.getTraceId());
        response.setDocumentId(storage.getDocumentId());
        response.setFileName(task == null ? null : task.getFileName());
        response.setDocumentType(task == null ? null : task.getDocumentType());
        response.setSourceType(task == null ? null : task.getSourceType());
        response.setBusinessNo(task == null ? null : task.getBusinessNo());
        response.setTargetTable(storage.getTargetTable());
        response.setMappingProfile(storage.getMappingProfile());
        response.setStorageData(readJson(storage.getStorageJson()));
        response.setUniqueKeyJson(storage.getUniqueKeyJson());
        response.setStorageStatus(storage.getStorageStatus());
        response.setDuplicateStrategy(storage.getDuplicateStrategy());
        response.setErrorMessage(storage.getErrorMessage());
        response.setStoredBy(storage.getStoredBy());
        response.setStoredAt(storage.getStoredAt());
        response.setCreatedAt(storage.getCreatedAt());
        response.setUpdatedAt(storage.getUpdatedAt());
        return response;
    }

    private ReviewLogResponse toReviewLogResponse(ReviewLogRecord record) {
        ReviewLogResponse response = new ReviewLogResponse();
        response.setId(record.getId());
        response.setTaskId(record.getTaskId());
        response.setTraceId(record.getTraceId());
        response.setAction(record.getAction());
        response.setComment(record.getComment());
        response.setReviewer(record.getReviewer());
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }

    private DocumentAccessResponse toAccessResponse(DocumentAccessRecord record) {
        DocumentAccessResponse response = new DocumentAccessResponse();
        response.setId(record.getId());
        response.setTraceId(record.getTraceId());
        response.setDocumentId(record.getDocumentId());
        response.setTaskId(record.getTaskId());
        response.setFileName(record.getFileName());
        response.setFileType(record.getFileType());
        response.setFileSize(record.getFileSize());
        response.setStoragePath(record.getStoragePath());
        response.setSourceType(record.getSourceType());
        response.setSourceSystem(record.getSourceSystem());
        response.setBusinessNo(record.getBusinessNo());
        response.setDepartmentId(record.getDepartmentId());
        response.setCategory(record.getCategory());
        response.setSubCategory(record.getSubCategory());
        response.setTemplateType(record.getTemplateType());
        response.setDocumentType(record.getDocumentType());
        response.setPriority(record.getPriority());
        response.setMatchStatus(record.getMatchStatus());
        response.setAccessStatus(record.getAccessStatus());
        response.setMatchedConfigId(record.getMatchedConfigId());
        response.setMatchedConfigName(record.getMatchedConfigName());
        response.setMatchedConfigVersion(record.getMatchedConfigVersion());
        response.setMatchMessage(record.getMatchMessage());
        response.setConfirmComment(record.getConfirmComment());
        response.setConfirmedAt(record.getConfirmedAt());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }

    private TaskResponse toTaskResponse(ExtractTaskRecord record) {
        TaskResponse response = new TaskResponse();
        response.setId(record.getId());
        response.setTaskId(record.getTaskId());
        response.setTraceId(record.getTraceId());
        response.setDocumentId(record.getDocumentId());
        response.setAccessRecordId(record.getAccessRecordId());
        response.setConfigId(record.getConfigId());
        response.setConfigName(record.getConfigName());
        response.setConfigVersion(record.getConfigVersion());
        response.setFileName(record.getFileName());
        response.setFileType(record.getFileType());
        response.setFileSize(record.getFileSize());
        response.setStoragePath(record.getStoragePath());
        response.setSourceType(record.getSourceType());
        response.setSourceSystem(record.getSourceSystem());
        response.setBusinessNo(record.getBusinessNo());
        response.setDepartmentId(record.getDepartmentId());
        response.setCategory(record.getCategory());
        response.setSubCategory(record.getSubCategory());
        response.setTemplateType(record.getTemplateType());
        response.setDocumentType(record.getDocumentType());
        response.setPriority(record.getPriority());
        response.setStatus(record.getStatus());
        response.setCurrentStage(record.getCurrentStage());
        response.setProgress(record.getProgress());
        response.setQueueLevel(record.getQueueLevel());
        response.setQueueName(record.getQueueName());
        response.setQueueCapacity(record.getQueueCapacity());
        response.setQueuePosition(record.getQueuePosition());
        response.setWaitingMinutes(record.getWaitingMinutes());
        response.setEstimatedStartAt(record.getEstimatedStartAt());
        response.setManualAccelerated("1".equals(record.getManualAccelerated()));
        response.setDispatchReason(record.getDispatchReason());
        response.setErrorCode(record.getErrorCode());
        response.setErrorMessage(record.getErrorMessage());
        response.setFailedStage(record.getFailedStage());
        response.setRetryCount(record.getRetryCount());
        response.setMaxRetry(record.getMaxRetry());
        response.setRetryable("FAILED".equals(record.getStatus()));
        response.setFailedAt(record.getFailedAt());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }

    private String resolveStatus(DocumentAccessRecord access, ExtractTaskRecord task, ExtractResultRecord result, StorageResultRecord storage) {
        if (storage != null && "SUCCESS".equals(storage.getStorageStatus())) {
            return "STORED";
        }
        if (result != null && StringUtils.hasText(result.getStatus())) {
            return result.getStatus();
        }
        if (task != null && StringUtils.hasText(task.getStatus())) {
            return task.getStatus();
        }
        if (access != null) {
            return access.getAccessStatus();
        }
        return "UNKNOWN";
    }

    private String resolveStage(DocumentAccessRecord access, ExtractTaskRecord task, ExtractResultRecord result, StorageResultRecord storage) {
        if (storage != null && "SUCCESS".equals(storage.getStorageStatus())) {
            return "\u7ed3\u679c\u5df2\u843d\u5e93";
        }
        if (result != null && "WAIT_REVIEW".equals(result.getStatus())) {
            return "\u7b49\u5f85\u4eba\u5de5\u590d\u6838";
        }
        if (task != null && StringUtils.hasText(task.getCurrentStage())) {
            return task.getCurrentStage();
        }
        if (access != null) {
            return access.getAccessStatus();
        }
        return "-";
    }

    private Integer resolveProgress(DocumentAccessRecord access, ExtractTaskRecord task, ExtractResultRecord result, StorageResultRecord storage) {
        if (storage != null && "SUCCESS".equals(storage.getStorageStatus())) {
            return 100;
        }
        if (task != null && task.getProgress() != null) {
            return task.getProgress();
        }
        if (access != null && "CREATED_TASK".equals(access.getAccessStatus())) {
            return 10;
        }
        return 0;
    }

    private String statusFromAccess(DocumentAccessRecord access) {
        return "CREATED_TASK".equals(access.getAccessStatus()) || "PENDING_CONFIRM".equals(access.getAccessStatus()) ? "SUCCESS" : "FAILED";
    }

    private String matchStageStatus(DocumentAccessRecord access) {
        if ("MATCHED".equals(access.getMatchStatus())) {
            return "SUCCESS";
        }
        if ("MULTIPLE".equals(access.getMatchStatus()) || "UNMATCHED".equals(access.getMatchStatus())) {
            return "WARNING";
        }
        return "PENDING";
    }

    private String reviewStatus(ExtractResultRecord result, List<ReviewLogRecord> logs) {
        if (result == null) {
            return "PENDING";
        }
        if ("WAIT_REVIEW".equals(result.getStatus())) {
            return "WAITING";
        }
        if (logs != null && !logs.isEmpty()) {
            return "SUCCESS";
        }
        return "PENDING";
    }

    private String storageStatus(StorageResultRecord storage) {
        return "SUCCESS".equals(storage.getStorageStatus()) ? "SUCCESS" : "FAILED";
    }

    private PushRecordResponse latestPush(List<PushRecordResponse> pushRecords) {
        if (pushRecords == null || pushRecords.isEmpty()) {
            return null;
        }
        return pushRecords.get(0);
    }

    private String pushStatus(PushRecordResponse latestPush) {
        if (latestPush == null) {
            return "PENDING";
        }
        if ("SUCCESS".equals(latestPush.getStatus())) {
            return "SUCCESS";
        }
        if ("FAILED".equals(latestPush.getStatus())) {
            return "FAILED";
        }
        return "WAITING";
    }

    private LocalDateTime firstReviewTime(List<ReviewLogRecord> reviewLogs) {
        if (reviewLogs == null || reviewLogs.isEmpty()) {
            return null;
        }
        return reviewLogs.get(reviewLogs.size() - 1).getCreatedAt();
    }

    private LocalDateTime latest(LocalDateTime... values) {
        LocalDateTime latest = null;
        for (LocalDateTime value : values) {
            if (value != null && (latest == null || value.isAfter(latest))) {
                latest = value;
            }
        }
        return latest;
    }

    private String normalizeSourceType(String value) {
        if ("API".equals(value)) {
            return "BUSINESS_API";
        }
        if ("EMAIL".equals(value)) {
            return "EMAIL_DISPATCH";
        }
        return value;
    }

    private Map<String, Object> readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
