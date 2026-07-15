package com.example.extraction.model.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.mapper.OcrEngineConfigMapper;
import com.example.extraction.model.domain.OcrEngineConfigRecord;
import com.example.extraction.model.dto.OcrEngineConfigRequest;
import com.example.extraction.model.dto.OcrEngineConfigResponse;
import com.example.extraction.model.dto.OcrEngineQueryRequest;
import com.example.extraction.ocr.OcrParseResponse;
import com.example.extraction.ocr.OcrParseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OcrEngineConfigService {
    private final OcrEngineConfigMapper ocrEngineConfigMapper;
    private final OcrParseService ocrParseService;
    private final ObjectMapper objectMapper;

    public OcrEngineConfigService(OcrEngineConfigMapper ocrEngineConfigMapper,
                                  OcrParseService ocrParseService,
                                  ObjectMapper objectMapper) {
        this.ocrEngineConfigMapper = ocrEngineConfigMapper;
        this.ocrParseService = ocrParseService;
        this.objectMapper = objectMapper;
    }

    public List<OcrEngineConfigResponse> list(OcrEngineQueryRequest query) {
        return ocrEngineConfigMapper.selectList(query).stream().map(this::toResponse).toList();
    }

    public List<Map<String, Object>> options() {
        return ocrEngineConfigMapper.selectEnabled().stream().map(record -> {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("value", record.getEngineCode());
            option.put("label", record.getEngineName() + " (" + record.getProvider() + ")");
            option.put("engineCode", record.getEngineCode());
            option.put("engineName", record.getEngineName());
            option.put("engineType", record.getEngineType());
            option.put("adapterType", resolveAdapterType(record));
            option.put("provider", record.getProvider());
            option.put("defaultEngine", "1".equals(record.getDefaultEngine()));
            option.put("outputFormat", record.getOutputFormat());
            return option;
        }).toList();
    }

    public OcrEngineConfigResponse detail(String id) {
        return toResponse(requireRecord(id));
    }

    @Transactional
    public OcrEngineConfigResponse create(OcrEngineConfigRequest request) {
        validateRequest(request);
        if (ocrEngineConfigMapper.selectByEngineCode(request.getEngineCode()) != null) {
            throw new BusinessException("OCR_409", "OCR engine code already exists");
        }
        OcrEngineConfigRecord record = new OcrEngineConfigRecord();
        record.setId(IdGenerator.nextId("OCR"));
        fillRecord(record, request);
        record.setCreatedBy("system");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        if ("1".equals(record.getDefaultEngine())) {
            ocrEngineConfigMapper.clearDefault();
            record.setStatus("ENABLED");
        }
        ocrEngineConfigMapper.insert(record);
        return detail(record.getId());
    }

    @Transactional
    public OcrEngineConfigResponse update(String id, OcrEngineConfigRequest request) {
        validateRequest(request);
        OcrEngineConfigRecord existing = requireRecord(id);
        OcrEngineConfigRecord sameCode = ocrEngineConfigMapper.selectByEngineCode(request.getEngineCode());
        if (sameCode != null && !id.equals(sameCode.getId())) {
            throw new BusinessException("OCR_409", "OCR engine code already exists");
        }
        fillRecord(existing, request);
        existing.setUpdatedAt(LocalDateTime.now());
        if ("1".equals(existing.getDefaultEngine())) {
            ocrEngineConfigMapper.clearDefault();
            existing.setStatus("ENABLED");
        }
        ocrEngineConfigMapper.update(existing);
        return detail(id);
    }

    @Transactional
    public OcrEngineConfigResponse enable(String id) {
        requireRecord(id);
        ocrEngineConfigMapper.updateStatus(id, "ENABLED");
        return detail(id);
    }

    @Transactional
    public OcrEngineConfigResponse disable(String id) {
        requireRecord(id);
        ocrEngineConfigMapper.updateStatus(id, "DISABLED");
        return detail(id);
    }

    @Transactional
    public OcrEngineConfigResponse setDefault(String id) {
        requireRecord(id);
        ocrEngineConfigMapper.clearDefault();
        ocrEngineConfigMapper.setDefault(id);
        return detail(id);
    }

    public Map<String, Object> test(String id) {
        OcrEngineConfigRecord record = requireRecord(id);
        if (!"ENABLED".equals(record.getStatus())) {
            throw new BusinessException("OCR_409", "只有启用中的 OCR 引擎可以测试");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", true);
        result.put("message", "配置检查通过。可继续上传样本文档进行真实 OCR 试识别。");
        result.put("engineCode", record.getEngineCode());
        result.put("engineName", record.getEngineName());
        result.put("adapterType", resolveAdapterType(record));
        result.put("baseUrl", record.getBaseUrl());
        result.put("outputFormat", record.getOutputFormat());
        result.put("checkedAt", LocalDateTime.now());
        return result;
    }

    public Map<String, Object> testParse(String id, MultipartFile file) {
        OcrEngineConfigRecord record = requireRecord(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("engineCode", record.getEngineCode());
        result.put("engineName", record.getEngineName());
        result.put("adapterType", resolveAdapterType(record));
        result.put("baseUrl", record.getBaseUrl());
        result.put("fileName", file == null ? null : file.getOriginalFilename());
        result.put("checkedAt", LocalDateTime.now());
        if (!"ENABLED".equals(record.getStatus())) {
            result.put("passed", false);
            result.put("message", "只有启用中的 OCR 引擎可以测试");
            return result;
        }
        try {
            OcrParseResponse response = ocrParseService.parsePreview(record, file);
            result.put("passed", true);
            result.put("message", StringUtils.hasText(response.getMarkdownText()) ? "OCR 解析成功，已获得 Markdown 文本" : "OCR 调用成功，但未返回 Markdown 文本");
            result.put("durationMs", response.getDurationMs());
            result.put("pageCount", response.getPageCount());
            result.put("imageCount", response.getImages().size());
            result.put("markdownText", response.getMarkdownText());
            result.put("markdownPreview", preview(response.getMarkdownText(), 8000));
            result.put("rawResponsePreview", preview(response.getRawJson(), 4000));
            return result;
        } catch (BusinessException e) {
            result.put("passed", false);
            result.put("errorCode", e.getCode());
            result.put("message", e.getMessage());
            return result;
        } catch (RuntimeException e) {
            result.put("passed", false);
            result.put("errorCode", "OCR_TEST_ERROR");
            result.put("message", StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName());
            return result;
        }
    }

    private OcrEngineConfigRecord requireRecord(String id) {
        OcrEngineConfigRecord record = ocrEngineConfigMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("OCR_404", "OCR engine config not found");
        }
        return record;
    }

    private void validateRequest(OcrEngineConfigRequest request) {
        if (!StringUtils.hasText(request.getEngineCode())) {
            throw new BusinessException("PARAM_400", "engineCode is required");
        }
        if (!request.getEngineCode().matches("^[a-zA-Z][a-zA-Z0-9_\\-]*$")) {
            throw new BusinessException("PARAM_400", "engineCode must start with a letter and contain only letters, digits, underscore or hyphen");
        }
        if (!StringUtils.hasText(request.getEngineName())) {
            throw new BusinessException("PARAM_400", "engineName is required");
        }
        if (!StringUtils.hasText(request.getEngineType())) {
            throw new BusinessException("PARAM_400", "engineType is required");
        }
        if (StringUtils.hasText(request.getAdapterType()) && !List.of("PADDLE_OCR_VL", "MINERU").contains(request.getAdapterType())) {
            throw new BusinessException("PARAM_400", "adapterType must be PADDLE_OCR_VL or MINERU");
        }
        if (!StringUtils.hasText(request.getProvider())) {
            throw new BusinessException("PARAM_400", "provider is required");
        }
        if (!StringUtils.hasText(request.getBaseUrl())) {
            throw new BusinessException("PARAM_400", "baseUrl is required");
        }
        if (request.getPriority() != null && request.getPriority() < 0) {
            throw new BusinessException("PARAM_400", "priority cannot be negative");
        }
        if (request.getTimeoutSeconds() != null && request.getTimeoutSeconds() <= 0) {
            throw new BusinessException("PARAM_400", "timeoutSeconds must be greater than 0");
        }
        if (request.getRetryCount() != null && request.getRetryCount() < 0) {
            throw new BusinessException("PARAM_400", "retryCount cannot be negative");
        }
        if (request.getMaxPagesPerCall() != null && request.getMaxPagesPerCall() <= 0) {
            throw new BusinessException("PARAM_400", "maxPagesPerCall must be greater than 0");
        }
        if (StringUtils.hasText(request.getEngineParamsJson())) {
            try {
                objectMapper.readTree(request.getEngineParamsJson());
            } catch (JsonProcessingException e) {
                throw new BusinessException("PARAM_400", "engineParamsJson must be valid JSON");
            }
        }
    }

    private void fillRecord(OcrEngineConfigRecord record, OcrEngineConfigRequest request) {
        record.setEngineCode(request.getEngineCode());
        record.setEngineName(request.getEngineName());
        record.setEngineType(request.getEngineType());
        record.setAdapterType(StringUtils.hasText(request.getAdapterType()) ? request.getAdapterType() : inferAdapterType(request.getEngineType(), request.getProvider(), request.getEngineCode()));
        record.setProvider(request.getProvider());
        record.setBaseUrl(request.getBaseUrl());
        record.setAuthMode(StringUtils.hasText(request.getAuthMode()) ? request.getAuthMode() : "NONE");
        record.setApiKeySecretRef(request.getApiKeySecretRef());
        record.setDefaultEngine(Boolean.TRUE.equals(request.getDefaultEngine()) ? "1" : "0");
        record.setPriority(request.getPriority() == null ? 100 : request.getPriority());
        record.setTimeoutSeconds(request.getTimeoutSeconds() == null ? 120 : request.getTimeoutSeconds());
        record.setRetryCount(request.getRetryCount() == null ? 2 : request.getRetryCount());
        record.setSupportedFileTypes(StringUtils.hasText(request.getSupportedFileTypes()) ? request.getSupportedFileTypes() : "pdf,png,jpg,jpeg,tif,tiff");
        record.setOutputFormat("Markdown");
        record.setMaxPagesPerCall(request.getMaxPagesPerCall());
        record.setEngineParamsJson(request.getEngineParamsJson());
        record.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "ENABLED");
        record.setDescription(request.getDescription());
    }

    private OcrEngineConfigResponse toResponse(OcrEngineConfigRecord record) {
        OcrEngineConfigResponse response = new OcrEngineConfigResponse();
        response.setId(record.getId());
        response.setEngineCode(record.getEngineCode());
        response.setEngineName(record.getEngineName());
        response.setEngineType(record.getEngineType());
        response.setAdapterType(resolveAdapterType(record));
        response.setProvider(record.getProvider());
        response.setBaseUrl(record.getBaseUrl());
        response.setAuthMode(record.getAuthMode());
        response.setApiKeySecretRef(record.getApiKeySecretRef());
        response.setDefaultEngine("1".equals(record.getDefaultEngine()));
        response.setPriority(record.getPriority());
        response.setTimeoutSeconds(record.getTimeoutSeconds());
        response.setRetryCount(record.getRetryCount());
        response.setSupportedFileTypes(record.getSupportedFileTypes());
        response.setOutputFormat(record.getOutputFormat());
        response.setMaxPagesPerCall(record.getMaxPagesPerCall());
        response.setEngineParamsJson(record.getEngineParamsJson());
        response.setStatus(record.getStatus());
        response.setDescription(record.getDescription());
        response.setCreatedBy(record.getCreatedBy());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }

    private String resolveAdapterType(OcrEngineConfigRecord record) {
        if (StringUtils.hasText(record.getAdapterType())) {
            return record.getAdapterType();
        }
        return inferAdapterType(record.getEngineType(), record.getProvider(), record.getEngineCode());
    }

    private String inferAdapterType(String engineType, String provider, String engineCode) {
        String joined = (safe(engineType) + " " + safe(provider) + " " + safe(engineCode)).toLowerCase();
        if (joined.contains("mineru") || joined.contains("miner_u")) {
            return "MINERU";
        }
        if (joined.contains("paddle") || joined.contains("paddleocr") || joined.contains("paddle_ocr_vl")) {
            return "PADDLE_OCR_VL";
        }
        return null;
    }

    private String preview(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "\n...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
