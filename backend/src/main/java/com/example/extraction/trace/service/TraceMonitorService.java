package com.example.extraction.trace.service;

import com.example.extraction.artifact.service.DocumentArtifactService;
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
    private final DocumentArtifactService documentArtifactService;
    private final ObjectMapper objectMapper;

    public TraceMonitorService(DocumentAccessMapper documentAccessMapper,
                               ExtractTaskMapper extractTaskMapper,
                               TaskStageLogMapper taskStageLogMapper,
                               DocumentParseResultMapper documentParseResultMapper,
                               ExtractResultMapper extractResultMapper,
                               ReviewLogMapper reviewLogMapper,
                               StorageResultMapper storageResultMapper,
                               DownstreamPushService downstreamPushService,
                               DocumentArtifactService documentArtifactService,
                               ObjectMapper objectMapper) {
        this.documentAccessMapper = documentAccessMapper;
        this.extractTaskMapper = extractTaskMapper;
        this.taskStageLogMapper = taskStageLogMapper;
        this.documentParseResultMapper = documentParseResultMapper;
        this.extractResultMapper = extractResultMapper;
        this.reviewLogMapper = reviewLogMapper;
        this.storageResultMapper = storageResultMapper;
        this.downstreamPushService = downstreamPushService;
        this.documentArtifactService = documentArtifactService;
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
            throw new BusinessException("TRACE_404", "链路不存在");
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
        response.setArtifacts(documentArtifactService.listByTraceId(traceId));
        response.setArtifactSteps(documentArtifactService.stepsByTraceId(traceId));
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
        stages.add(stage("ACCESS", "文档接入", access == null ? "PENDING" : statusFromAccess(access),
                access == null ? null : access.getSourceType(), access == null ? null : access.getMatchMessage(), null, null,
                null, access == null ? null : access.getCreatedAt(), access == null ? null : access.getUpdatedAt()));
        stages.add(stage("MATCH", "配置匹配", access == null ? "PENDING" : matchStageStatus(access),
                access == null ? null : access.getDocumentType(), access == null ? null : access.getMatchedConfigName(), null, null,
                null, access == null ? null : access.getCreatedAt(), access == null ? null : access.getUpdatedAt()));
        stages.add(stage("QUEUE", "任务排队", task == null ? "PENDING" : "SUCCESS",
                task == null ? null : task.getQueueName(), task == null ? null : task.getCurrentStage(), null, null,
                null, task == null ? null : task.getCreatedAt(), task == null ? null : task.getUpdatedAt()));
        if (task != null) {
            taskStageLogMapper.selectByTaskId(task.getTaskId()).stream().map(this::toStageResponse).forEach(stages::add);
        }
        stages.add(stage("REVIEW", "人工复核", reviewStatus(result, reviewLogs),
                result == null ? null : result.getStatus(), reviewLogs.isEmpty() ? "无复核记录" : "已记录 " + reviewLogs.size() + " 次复核操作",
                null, null, null, firstReviewTime(reviewLogs), firstReviewTime(reviewLogs)));
        stages.add(stage("STORAGE", "结果落库", storage == null ? "PENDING" : storageStatus(storage),
                storage == null ? null : storage.getTargetTable(), storage == null ? null : storage.getStorageStatus(),
                null, storage == null ? null : storage.getErrorMessage(), null,
                storage == null ? null : storage.getStoredAt(), storage == null ? null : storage.getUpdatedAt()));
        PushRecordResponse latestPush = latestPush(pushRecords);
        stages.add(stage("PUSH", "下游推送", pushStatus(latestPush),
                latestPush == null ? null : latestPush.getTargetSystem(),
                latestPush == null ? "尚未发起推送" : latestPush.getResponseMessage(),
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
            suggestions.add("文档未唯一匹配配置，请在待确认文档中手工确认。");
        }
        if (task != null && "FAILED".equals(task.getStatus())) {
            suggestions.add("任务执行失败：" + firstText(task.getErrorMessage(), task.getErrorCode(), "-") + "，可在失败任务中查看并重试。");
        }
        if (result != null && "WAIT_REVIEW".equals(result.getStatus())) {
            suggestions.add("提取结果待复核，请进入人工复核中确认低置信度字段。");
        }
        if (result != null && List.of("STORED", "PUSHED").contains(result.getStatus()) && storage == null) {
            suggestions.add("结果已确认但未落库，可在结果中心执行落库。");
        }
        PushRecordResponse latestPush = latestPush(pushRecords);
        if (storage != null && "SUCCESS".equals(storage.getStorageStatus()) && latestPush == null) {
            suggestions.add("结果已落库，可在结果中心发起下游推送。");
        }
        if (latestPush != null && "FAILED".equals(latestPush.getStatus())) {
            suggestions.add("下游推送失败：" + firstText(latestPush.getResponseMessage(), latestPush.getResponseCode(), "-") + "，可在推送记录中重试。");
        }
        if (reviewLogs != null && !reviewLogs.isEmpty()) {
            suggestions.add("链路包含复核操作，请关注复核备注和字段修正内容。");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("链路暂无异常，可继续执行后续复核、落库或推送动作。");
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
            summary.setReviewStatus("1".equals(result.getNeedReview()) ? "待复核" : "自动通过");
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
            return "结果已落库";
        }
        if (result != null && "WAIT_REVIEW".equals(result.getStatus())) {
            return "等待人工复核";
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
