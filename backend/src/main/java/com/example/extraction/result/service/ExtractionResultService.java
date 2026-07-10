package com.example.extraction.result.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.mapper.DocumentParseResultMapper;
import com.example.extraction.mapper.ExtractResultMapper;
import com.example.extraction.result.domain.DocumentParseResultRecord;
import com.example.extraction.result.domain.ExtractResultRecord;
import com.example.extraction.result.dto.ResultDetailResponse;
import com.example.extraction.result.dto.ResultQueryRequest;
import com.example.extraction.result.dto.ResultSummaryResponse;
import com.example.extraction.task.domain.ExtractTaskRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExtractionResultService {
    private final DocumentParseResultMapper documentParseResultMapper;
    private final ExtractResultMapper extractResultMapper;
    private final ObjectMapper objectMapper;

    public ExtractionResultService(DocumentParseResultMapper documentParseResultMapper,
                                   ExtractResultMapper extractResultMapper,
                                   ObjectMapper objectMapper) {
        this.documentParseResultMapper = documentParseResultMapper;
        this.extractResultMapper = extractResultMapper;
        this.objectMapper = objectMapper;
    }

    public List<ResultSummaryResponse> list(ResultQueryRequest query) {
        normalizeQuery(query);
        return extractResultMapper.selectSummaryList(query);
    }

    public ResultDetailResponse detail(String taskId) {
        ExtractResultRecord extractResult = extractResultMapper.selectByTaskId(taskId);
        if (extractResult == null) {
            throw new BusinessException("RESULT_404", "\u63d0\u53d6\u7ed3\u679c\u4e0d\u5b58\u5728");
        }
        ResultQueryRequest query = new ResultQueryRequest();
        query.setKeyword(taskId);
        ResultSummaryResponse summary = extractResultMapper.selectSummaryList(query).stream()
                .filter(item -> taskId.equals(item.getTaskId()))
                .findFirst()
                .orElseGet(() -> fallbackSummary(extractResult));
        DocumentParseResultRecord parseResult = documentParseResultMapper.selectByTaskId(taskId);
        ResultDetailResponse response = new ResultDetailResponse();
        response.setSummary(summary);
        response.setParseText(parseResult == null ? null : parseResult.getParseText());
        response.setPageCount(parseResult == null ? null : parseResult.getPageCount());
        response.setEngineCode(parseResult == null ? null : parseResult.getEngineCode());
        response.setResult(readJson(extractResult.getResultJson()));
        response.setConfidence(readJson(extractResult.getConfidenceJson()));
        return response;
    }

    public void saveParseResult(ExtractTaskRecord task) {
        DocumentParseResultRecord record = new DocumentParseResultRecord();
        record.setId(IdGenerator.nextId("DPR"));
        record.setTaskId(task.getTaskId());
        record.setTraceId(task.getTraceId());
        record.setDocumentId(task.getDocumentId());
        record.setEngineCode("SIMULATED_PARSE_ENGINE");
        record.setParseText(buildParseText(task));
        record.setParseMarkdownPath("mock://parse-result/" + task.getTraceId() + ".md");
        record.setPageCount(1);
        record.setStatus("SUCCESS");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        if (documentParseResultMapper.selectByTaskId(task.getTaskId()) == null) {
            documentParseResultMapper.insert(record);
        } else {
            documentParseResultMapper.update(record);
        }
    }

    public void saveExtractResult(ExtractTaskRecord task, BigDecimal confidence, boolean needReview) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task_id", task.getTaskId());
        result.put("document_id", task.getDocumentId());
        result.put("document_type", nullToDash(task.getDocumentType()));
        result.put("file_name", task.getFileName());
        result.put("business_no", nullToDash(task.getBusinessNo()));
        result.put("amount", "100000.00");
        result.put("business_date", LocalDateTime.now().toLocalDate().toString());
        result.put("counterparty_name", "\u6a21\u62df\u4ea4\u6613\u5bf9\u624b");

        Map<String, Object> confidenceJson = new LinkedHashMap<>();
        confidenceJson.put("document_type", confidence);
        confidenceJson.put("amount", confidence.subtract(new BigDecimal("0.01")));
        confidenceJson.put("business_date", confidence);
        confidenceJson.put("counterparty_name", confidence.subtract(new BigDecimal("0.02")));

        ExtractResultRecord record = new ExtractResultRecord();
        record.setId(IdGenerator.nextId("ERR"));
        record.setTaskId(task.getTaskId());
        record.setTraceId(task.getTraceId());
        record.setDocumentId(task.getDocumentId());
        record.setConfigId(task.getConfigId());
        record.setResultJson(writeJson(result));
        record.setConfidenceJson(writeJson(confidenceJson));
        record.setOverallConfidence(confidence);
        record.setNeedReview(needReview ? "1" : "0");
        record.setStatus(needReview ? "WAIT_REVIEW" : "STORED");
        record.setFieldCount(result.size());
        record.setTargetTable("SIMULATED_TARGET_TABLE");
        record.setMappingProfile(firstText(task.getConfigName(), "\u9ed8\u8ba4\u6620\u5c04\u65b9\u6848"));
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        if (extractResultMapper.selectByTaskId(task.getTaskId()) == null) {
            extractResultMapper.insert(record);
        } else {
            extractResultMapper.update(record);
        }
    }

    public void markFailed(ExtractTaskRecord task, String errorCode, String errorMessage) {
        ExtractResultRecord existing = extractResultMapper.selectByTaskId(task.getTaskId());
        if (existing == null) {
            ExtractResultRecord record = new ExtractResultRecord();
            record.setId(IdGenerator.nextId("ERR"));
            record.setTaskId(task.getTaskId());
            record.setTraceId(task.getTraceId());
            record.setDocumentId(task.getDocumentId());
            record.setConfigId(task.getConfigId());
            record.setResultJson(writeJson(Map.of("error_code", errorCode, "error_message", errorMessage)));
            record.setConfidenceJson(writeJson(Map.of()));
            record.setOverallConfidence(BigDecimal.ZERO);
            record.setNeedReview("0");
            record.setStatus("FAILED");
            record.setFieldCount(0);
            record.setTargetTable("SIMULATED_TARGET_TABLE");
            record.setMappingProfile(firstText(task.getConfigName(), "\u9ed8\u8ba4\u6620\u5c04\u65b9\u6848"));
            record.setCreatedAt(LocalDateTime.now());
            record.setUpdatedAt(record.getCreatedAt());
            extractResultMapper.insert(record);
        } else {
            existing.setStatus("FAILED");
            existing.setResultJson(writeJson(Map.of("error_code", errorCode, "error_message", errorMessage)));
            existing.setConfidenceJson(writeJson(Map.of()));
            existing.setOverallConfidence(BigDecimal.ZERO);
            existing.setNeedReview("0");
            existing.setFieldCount(0);
            existing.setUpdatedAt(LocalDateTime.now());
            extractResultMapper.update(existing);
        }
    }

    private void normalizeQuery(ResultQueryRequest query) {
        if (query == null) {
            return;
        }
        query.setDepartmentId(normalizeDepartment(query.getDepartmentId()));
    }

    private String normalizeDepartment(String value) {
        String key = firstText(value);
        if (key == null) {
            return value;
        }
        return switch (key) {
            case "OPS" -> "\u8fd0\u8425\u90e8";
            case "FINANCE" -> "\u8d22\u52a1\u90e8";
            case "PRODUCT" -> "\u4ea7\u54c1\u90e8";
            default -> value;
        };
    }

    private String buildParseText(ExtractTaskRecord task) {
        return "# \u6a21\u62df\u89e3\u6790\u7ed3\u679c\n\n"
                + "- \u4efb\u52a1\u7f16\u53f7: " + task.getTaskId() + "\n"
                + "- \u6587\u4ef6\u540d: " + task.getFileName() + "\n"
                + "- \u6587\u6863\u7c7b\u578b: " + nullToDash(task.getDocumentType()) + "\n\n"
                + "\u8fd9\u662f\u7b2c\u4e00\u7248\u6a21\u62df\u89e3\u6790\u6587\u672c\uff0c\u540e\u7eed\u7531 OCR/MinerU \u771f\u5b9e\u7ed3\u679c\u66ff\u6362\u3002";
    }

    private ResultSummaryResponse fallbackSummary(ExtractResultRecord record) {
        ResultSummaryResponse summary = new ResultSummaryResponse();
        summary.setTaskId(record.getTaskId());
        summary.setTraceId(record.getTraceId());
        summary.setDocumentId(record.getDocumentId());
        summary.setResultStatus(record.getStatus());
        summary.setReviewStatus("1".equals(record.getNeedReview()) ? "\u5f85\u590d\u6838" : "\u81ea\u52a8\u901a\u8fc7");
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
            throw new BusinessException("JSON_400", "\u7ed3\u679c\u65e0\u6cd5\u5e8f\u5217\u5316");
        }
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
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
