package com.example.extraction.model.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.mapper.LlmModelConfigMapper;
import com.example.extraction.model.domain.LlmModelConfigRecord;
import com.example.extraction.model.dto.LlmModelConfigRequest;
import com.example.extraction.model.dto.LlmModelConfigResponse;
import com.example.extraction.model.dto.LlmModelQueryRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmModelConfigService {
    private final LlmModelConfigMapper llmModelConfigMapper;

    public LlmModelConfigService(LlmModelConfigMapper llmModelConfigMapper) {
        this.llmModelConfigMapper = llmModelConfigMapper;
    }

    public List<LlmModelConfigResponse> list(LlmModelQueryRequest query) {
        return llmModelConfigMapper.selectList(query).stream().map(this::toResponse).toList();
    }

    public List<Map<String, Object>> options() {
        return llmModelConfigMapper.selectEnabled().stream().map(record -> {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("value", record.getModelCode());
            option.put("label", record.getModelName() + "（" + record.getProvider() + "）");
            option.put("modelCode", record.getModelCode());
            option.put("modelName", record.getModelName());
            option.put("provider", record.getProvider());
            option.put("defaultModel", "1".equals(record.getDefaultModel()));
            return option;
        }).toList();
    }

    public LlmModelConfigResponse detail(String id) {
        return toResponse(requireRecord(id));
    }

    @Transactional
    public LlmModelConfigResponse create(LlmModelConfigRequest request) {
        validateRequest(request);
        if (llmModelConfigMapper.selectByModelCode(request.getModelCode()) != null) {
            throw new BusinessException("MODEL_409", "模型编码已存在，请使用唯一编码");
        }
        LlmModelConfigRecord record = new LlmModelConfigRecord();
        record.setId(IdGenerator.nextId("LLM"));
        fillRecord(record, request);
        record.setCreatedBy("system");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        if ("1".equals(record.getDefaultModel())) {
            llmModelConfigMapper.clearDefault();
            record.setStatus("ENABLED");
        }
        llmModelConfigMapper.insert(record);
        return detail(record.getId());
    }

    @Transactional
    public LlmModelConfigResponse update(String id, LlmModelConfigRequest request) {
        validateRequest(request);
        LlmModelConfigRecord existing = requireRecord(id);
        LlmModelConfigRecord sameCode = llmModelConfigMapper.selectByModelCode(request.getModelCode());
        if (sameCode != null && !id.equals(sameCode.getId())) {
            throw new BusinessException("MODEL_409", "模型编码已存在，请使用唯一编码");
        }
        fillRecord(existing, request);
        existing.setUpdatedAt(LocalDateTime.now());
        if ("1".equals(existing.getDefaultModel())) {
            llmModelConfigMapper.clearDefault();
            existing.setStatus("ENABLED");
        }
        llmModelConfigMapper.update(existing);
        return detail(id);
    }

    @Transactional
    public LlmModelConfigResponse enable(String id) {
        requireRecord(id);
        llmModelConfigMapper.updateStatus(id, "ENABLED");
        return detail(id);
    }

    @Transactional
    public LlmModelConfigResponse disable(String id) {
        requireRecord(id);
        llmModelConfigMapper.updateStatus(id, "DISABLED");
        return detail(id);
    }

    @Transactional
    public LlmModelConfigResponse setDefault(String id) {
        requireRecord(id);
        llmModelConfigMapper.clearDefault();
        llmModelConfigMapper.setDefault(id);
        return detail(id);
    }

    public Map<String, Object> test(String id) {
        LlmModelConfigRecord record = requireRecord(id);
        if (!"ENABLED".equals(record.getStatus())) {
            throw new BusinessException("MODEL_409", "仅启用中的模型允许测试连接");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", true);
        result.put("message", "模拟测试通过：配置完整，真实模型调用将在执行链路接入后启用");
        result.put("modelCode", record.getModelCode());
        result.put("modelIdentifier", record.getModelIdentifier());
        result.put("checkedAt", LocalDateTime.now());
        return result;
    }

    private LlmModelConfigRecord requireRecord(String id) {
        LlmModelConfigRecord record = llmModelConfigMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("MODEL_404", "LLM 配置不存在");
        }
        return record;
    }

    private void validateRequest(LlmModelConfigRequest request) {
        if (!StringUtils.hasText(request.getModelCode())) {
            throw new BusinessException("PARAM_400", "模型编码不能为空");
        }
        if (!request.getModelCode().matches("^[a-zA-Z][a-zA-Z0-9_\\-]*$")) {
            throw new BusinessException("PARAM_400", "模型编码只能包含字母、数字、下划线、中划线，并以字母开头");
        }
        if (!StringUtils.hasText(request.getModelName())) {
            throw new BusinessException("PARAM_400", "模型名称不能为空");
        }
        if (!StringUtils.hasText(request.getProvider())) {
            throw new BusinessException("PARAM_400", "供应方不能为空");
        }
        if (!StringUtils.hasText(request.getBaseUrl())) {
            throw new BusinessException("PARAM_400", "接口地址不能为空");
        }
        if (!StringUtils.hasText(request.getModelIdentifier())) {
            throw new BusinessException("PARAM_400", "模型调用标识不能为空");
        }
        BigDecimal temperature = request.getTemperature();
        if (temperature != null && (temperature.compareTo(BigDecimal.ZERO) < 0 || temperature.compareTo(BigDecimal.ONE) > 0)) {
            throw new BusinessException("PARAM_400", "temperature 必须在 0 到 1 之间");
        }
        if (request.getMaxTokens() != null && request.getMaxTokens() <= 0) {
            throw new BusinessException("PARAM_400", "maxTokens 必须大于 0");
        }
        if (request.getTimeoutSeconds() != null && request.getTimeoutSeconds() <= 0) {
            throw new BusinessException("PARAM_400", "超时秒数必须大于 0");
        }
        if (request.getRetryCount() != null && request.getRetryCount() < 0) {
            throw new BusinessException("PARAM_400", "重试次数不能小于 0");
        }
    }

    private void fillRecord(LlmModelConfigRecord record, LlmModelConfigRequest request) {
        record.setModelCode(request.getModelCode());
        record.setModelName(request.getModelName());
        record.setProvider(request.getProvider());
        record.setBaseUrl(request.getBaseUrl());
        record.setApiKeySecretRef(request.getApiKeySecretRef());
        record.setModelIdentifier(request.getModelIdentifier());
        record.setTemperature(request.getTemperature() == null ? new BigDecimal("0.10") : request.getTemperature());
        record.setMaxTokens(request.getMaxTokens() == null ? 4096 : request.getMaxTokens());
        record.setTimeoutSeconds(request.getTimeoutSeconds() == null ? 120 : request.getTimeoutSeconds());
        record.setRetryCount(request.getRetryCount() == null ? 1 : request.getRetryCount());
        record.setJsonSchemaRequired(Boolean.FALSE.equals(request.getJsonSchemaRequired()) ? "0" : "1");
        record.setDefaultModel(Boolean.TRUE.equals(request.getDefaultModel()) ? "1" : "0");
        record.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "ENABLED");
        record.setDescription(request.getDescription());
    }

    private LlmModelConfigResponse toResponse(LlmModelConfigRecord record) {
        LlmModelConfigResponse response = new LlmModelConfigResponse();
        response.setId(record.getId());
        response.setModelCode(record.getModelCode());
        response.setModelName(record.getModelName());
        response.setProvider(record.getProvider());
        response.setBaseUrl(record.getBaseUrl());
        response.setApiKeySecretRef(record.getApiKeySecretRef());
        response.setModelIdentifier(record.getModelIdentifier());
        response.setTemperature(record.getTemperature());
        response.setMaxTokens(record.getMaxTokens());
        response.setTimeoutSeconds(record.getTimeoutSeconds());
        response.setRetryCount(record.getRetryCount());
        response.setJsonSchemaRequired("1".equals(record.getJsonSchemaRequired()));
        response.setDefaultModel("1".equals(record.getDefaultModel()));
        response.setStatus(record.getStatus());
        response.setDescription(record.getDescription());
        response.setCreatedBy(record.getCreatedBy());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }
}
