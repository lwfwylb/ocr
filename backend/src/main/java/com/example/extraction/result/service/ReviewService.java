package com.example.extraction.result.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.mapper.DocumentParseResultMapper;
import com.example.extraction.mapper.ExtractResultMapper;
import com.example.extraction.mapper.ExtractTaskMapper;
import com.example.extraction.mapper.ReviewLogMapper;
import com.example.extraction.result.domain.DocumentParseResultRecord;
import com.example.extraction.result.domain.ExtractResultRecord;
import com.example.extraction.result.domain.ReviewLogRecord;
import com.example.extraction.result.dto.ResultQueryRequest;
import com.example.extraction.result.dto.ResultSummaryResponse;
import com.example.extraction.result.dto.ReviewDetailResponse;
import com.example.extraction.result.dto.ReviewFieldCorrectionRequest;
import com.example.extraction.result.dto.ReviewFieldResponse;
import com.example.extraction.result.dto.ReviewLogResponse;
import com.example.extraction.result.dto.ReviewQueryRequest;
import com.example.extraction.result.dto.ReviewSubmitRequest;
import com.example.extraction.task.domain.ExtractTaskRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {
    private final ExtractResultMapper extractResultMapper;
    private final DocumentParseResultMapper documentParseResultMapper;
    private final ReviewLogMapper reviewLogMapper;
    private final ExtractTaskMapper extractTaskMapper;
    private final ObjectMapper objectMapper;

    public ReviewService(ExtractResultMapper extractResultMapper,
                         DocumentParseResultMapper documentParseResultMapper,
                         ReviewLogMapper reviewLogMapper,
                         ExtractTaskMapper extractTaskMapper,
                         ObjectMapper objectMapper) {
        this.extractResultMapper = extractResultMapper;
        this.documentParseResultMapper = documentParseResultMapper;
        this.reviewLogMapper = reviewLogMapper;
        this.extractTaskMapper = extractTaskMapper;
        this.objectMapper = objectMapper;
    }

    public List<ResultSummaryResponse> list(ReviewQueryRequest query) {
        ResultQueryRequest resultQuery = new ResultQueryRequest();
        resultQuery.setKeyword(query == null ? null : query.getKeyword());
        resultQuery.setDepartmentId(normalizeDepartment(query == null ? null : query.getDepartmentId()));
        resultQuery.setDocumentType(query == null ? null : query.getDocumentType());
        resultQuery.setSourceType(query == null ? null : query.getSourceType());
        resultQuery.setResultStatus("WAIT_REVIEW");
        return extractResultMapper.selectSummaryList(resultQuery);
    }

    public ReviewDetailResponse detail(String taskId) {
        ExtractResultRecord result = requireResult(taskId);
        ResultQueryRequest query = new ResultQueryRequest();
        query.setKeyword(taskId);
        ResultSummaryResponse summary = extractResultMapper.selectSummaryList(query).stream()
                .filter(item -> taskId.equals(item.getTaskId()))
                .findFirst()
                .orElseGet(() -> fallbackSummary(result));
        DocumentParseResultRecord parseResult = documentParseResultMapper.selectByTaskId(taskId);
        ReviewDetailResponse response = new ReviewDetailResponse();
        response.setSummary(summary);
        response.setParseText(parseResult == null ? null : parseResult.getParseText());
        response.setFields(toFields(result));
        response.setLogs(reviewLogMapper.selectByTaskId(taskId).stream().map(this::toLogResponse).toList());
        return response;
    }

    @Transactional
    public ReviewDetailResponse saveDraft(String taskId, ReviewSubmitRequest request) {
        ExtractResultRecord result = requireResult(taskId);
        String beforeJson = result.getResultJson();
        Map<String, Object> updated = mergeFields(readJson(result.getResultJson()), request);
        result.setResultJson(writeJson(updated));
        result.setNeedReview("1");
        result.setStatus("WAIT_REVIEW");
        result.setFieldCount(updated.size());
        result.setUpdatedAt(LocalDateTime.now());
        extractResultMapper.updateReviewState(result);
        writeLog(result, "SAVE_DRAFT", beforeJson, result.getResultJson(), request);
        return detail(taskId);
    }

    @Transactional
    public ReviewDetailResponse approve(String taskId, ReviewSubmitRequest request) {
        ExtractResultRecord result = requireResult(taskId);
        String beforeJson = result.getResultJson();
        Map<String, Object> updated = mergeFields(readJson(result.getResultJson()), request);
        result.setResultJson(writeJson(updated));
        result.setNeedReview("0");
        result.setStatus("STORED");
        result.setFieldCount(updated.size());
        result.setUpdatedAt(LocalDateTime.now());
        extractResultMapper.updateReviewState(result);
        updateTaskState(taskId, "COMPLETED", "复核通过", 100, null, null);
        writeLog(result, "APPROVE", beforeJson, result.getResultJson(), request);
        return detail(taskId);
    }

    @Transactional
    public ReviewDetailResponse reject(String taskId, ReviewSubmitRequest request) {
        ExtractResultRecord result = requireResult(taskId);
        String beforeJson = result.getResultJson();
        Map<String, Object> updated = mergeFields(readJson(result.getResultJson()), request);
        result.setResultJson(writeJson(updated));
        result.setNeedReview("0");
        result.setStatus("FAILED");
        result.setFieldCount(updated.size());
        result.setUpdatedAt(LocalDateTime.now());
        extractResultMapper.updateReviewState(result);
        updateTaskState(taskId, "FAILED", "复核退回", 90, "REVIEW_REJECTED",
                firstText(request == null ? null : request.getComment(), "人工复核退回重提取"));
        writeLog(result, "REJECT", beforeJson, result.getResultJson(), request);
        return detail(taskId);
    }

    private ExtractResultRecord requireResult(String taskId) {
        ExtractResultRecord result = extractResultMapper.selectByTaskId(taskId);
        if (result == null) {
            throw new BusinessException("REVIEW_404", "复核结果不存在");
        }
        return result;
    }

    private List<ReviewFieldResponse> toFields(ExtractResultRecord result) {
        Map<String, Object> values = readJson(result.getResultJson());
        Map<String, Object> confidence = readJson(result.getConfidenceJson());
        List<ReviewFieldResponse> fields = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            BigDecimal fieldConfidence = toBigDecimal(confidence.get(entry.getKey()), result.getOverallConfidence());
            ReviewFieldResponse field = new ReviewFieldResponse();
            field.setFieldCode(entry.getKey());
            field.setFieldName(entry.getKey());
            field.setRawValue(entry.getValue());
            field.setFinalValue(entry.getValue());
            field.setConfidence(fieldConfidence);
            field.setReviewRequired(fieldConfidence.compareTo(new BigDecimal("0.90")) < 0);
            field.setIssue(Boolean.TRUE.equals(field.getReviewRequired()) ? "置信度低于90%" : "自动通过");
            field.setEvidence("来自解析文本的字段命中片段，后续接入真实页码坐标和证据文本。");
            fields.add(field);
        }
        return fields;
    }

    private Map<String, Object> mergeFields(Map<String, Object> original, ReviewSubmitRequest request) {
        Map<String, Object> updated = new LinkedHashMap<>(original);
        if (request != null && request.getFields() != null) {
            for (ReviewFieldCorrectionRequest field : request.getFields()) {
                if (StringUtils.hasText(field.getFieldCode())) {
                    updated.put(field.getFieldCode(), field.getFinalValue());
                }
            }
        }
        return updated;
    }

    private void updateTaskState(String taskId, String status, String stage, Integer progress, String errorCode, String errorMessage) {
        ExtractTaskRecord task = extractTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            return;
        }
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

    private void writeLog(ExtractResultRecord result, String action, String beforeJson, String afterJson, ReviewSubmitRequest request) {
        ReviewLogRecord log = new ReviewLogRecord();
        log.setId(IdGenerator.nextId("RVL"));
        log.setTaskId(result.getTaskId());
        log.setTraceId(result.getTraceId());
        log.setAction(action);
        log.setBeforeJson(beforeJson);
        log.setAfterJson(afterJson);
        log.setComment(request == null ? null : request.getComment());
        log.setReviewer(firstText(request == null ? null : request.getReviewer(), "当前用户"));
        log.setCreatedAt(LocalDateTime.now());
        reviewLogMapper.insert(log);
    }

    private ReviewLogResponse toLogResponse(ReviewLogRecord record) {
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

    private ResultSummaryResponse fallbackSummary(ExtractResultRecord record) {
        ResultSummaryResponse summary = new ResultSummaryResponse();
        summary.setTaskId(record.getTaskId());
        summary.setTraceId(record.getTraceId());
        summary.setDocumentId(record.getDocumentId());
        summary.setResultStatus(record.getStatus());
        summary.setReviewStatus("1".equals(record.getNeedReview()) ? "待复核" : "自动通过");
        summary.setTargetTable(record.getTargetTable());
        summary.setMappingProfile(record.getMappingProfile());
        summary.setFieldCount(record.getFieldCount());
        summary.setOverallConfidence(record.getOverallConfidence());
        summary.setCreatedAt(record.getCreatedAt());
        summary.setUpdatedAt(record.getUpdatedAt());
        return summary;
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("JSON_400", "复核结果无法序列化");
        }
    }

    private BigDecimal toBigDecimal(Object value, BigDecimal fallback) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException ignored) {
                return fallback == null ? BigDecimal.ZERO : fallback;
            }
        }
        return fallback == null ? BigDecimal.ZERO : fallback;
    }

    private String normalizeDepartment(String value) {
        String key = firstText(value);
        if (key == null) {
            return value;
        }
        return switch (key) {
            case "OPS" -> "运营部";
            case "FINANCE" -> "财务部";
            case "PRODUCT" -> "产品部";
            default -> value;
        };
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
