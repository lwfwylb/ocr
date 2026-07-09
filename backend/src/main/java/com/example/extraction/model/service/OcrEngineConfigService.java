package com.example.extraction.model.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.mapper.OcrEngineConfigMapper;
import com.example.extraction.model.domain.OcrEngineConfigRecord;
import com.example.extraction.model.dto.OcrEngineConfigRequest;
import com.example.extraction.model.dto.OcrEngineConfigResponse;
import com.example.extraction.model.dto.OcrEngineQueryRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OcrEngineConfigService {
    private final OcrEngineConfigMapper ocrEngineConfigMapper;

    public OcrEngineConfigService(OcrEngineConfigMapper ocrEngineConfigMapper) {
        this.ocrEngineConfigMapper = ocrEngineConfigMapper;
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
            throw new BusinessException("OCR_409", "Only enabled OCR engines can be tested");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", true);
        result.put("message", "Mock test passed. Real OCR call will be connected in the execution pipeline.");
        result.put("engineCode", record.getEngineCode());
        result.put("baseUrl", record.getBaseUrl());
        result.put("outputFormat", record.getOutputFormat());
        result.put("checkedAt", LocalDateTime.now());
        return result;
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
    }

    private void fillRecord(OcrEngineConfigRecord record, OcrEngineConfigRequest request) {
        record.setEngineCode(request.getEngineCode());
        record.setEngineName(request.getEngineName());
        record.setEngineType(request.getEngineType());
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
        record.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "ENABLED");
        record.setDescription(request.getDescription());
    }

    private OcrEngineConfigResponse toResponse(OcrEngineConfigRecord record) {
        OcrEngineConfigResponse response = new OcrEngineConfigResponse();
        response.setId(record.getId());
        response.setEngineCode(record.getEngineCode());
        response.setEngineName(record.getEngineName());
        response.setEngineType(record.getEngineType());
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
        response.setStatus(record.getStatus());
        response.setDescription(record.getDescription());
        response.setCreatedBy(record.getCreatedBy());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }
}
