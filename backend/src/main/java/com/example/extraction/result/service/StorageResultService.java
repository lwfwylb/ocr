package com.example.extraction.result.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.configuration.domain.ExtractConfigRecord;
import com.example.extraction.configuration.dto.ConfigWizardPayload;
import com.example.extraction.mapper.ExtractConfigMapper;
import com.example.extraction.mapper.ExtractResultMapper;
import com.example.extraction.mapper.StorageResultMapper;
import com.example.extraction.result.domain.ExtractResultRecord;
import com.example.extraction.result.domain.StorageResultRecord;
import com.example.extraction.result.dto.StorageExecuteRequest;
import com.example.extraction.result.dto.StorageQueryRequest;
import com.example.extraction.result.dto.StorageRecordResponse;
import com.example.extraction.result.dto.StorageTableResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StorageResultService {
    private final ExtractResultMapper extractResultMapper;
    private final StorageResultMapper storageResultMapper;
    private final ExtractConfigMapper extractConfigMapper;
    private final ObjectMapper objectMapper;

    public StorageResultService(ExtractResultMapper extractResultMapper,
                                StorageResultMapper storageResultMapper,
                                ExtractConfigMapper extractConfigMapper,
                                ObjectMapper objectMapper) {
        this.extractResultMapper = extractResultMapper;
        this.storageResultMapper = storageResultMapper;
        this.extractConfigMapper = extractConfigMapper;
        this.objectMapper = objectMapper;
    }

    public List<StorageTableResponse> tables(String keyword) {
        return storageResultMapper.selectTables(keyword);
    }

    public List<StorageRecordResponse> records(StorageQueryRequest query) {
        return storageResultMapper.selectRecords(query).stream().map(this::fillStorageData).toList();
    }

    @Transactional
    public StorageRecordResponse execute(String taskId, StorageExecuteRequest request) {
        ExtractResultRecord result = extractResultMapper.selectByTaskId(taskId);
        if (result == null) {
            throw new BusinessException("STORAGE_404", "提取结果不存在");
        }
        if ("WAIT_REVIEW".equals(result.getStatus())) {
            throw new BusinessException("STORAGE_409", "结果尚未复核，不允许落库");
        }
        if ("FAILED".equals(result.getStatus())) {
            throw new BusinessException("STORAGE_409", "结果已失败，不允许落库");
        }
        if (!storageEnabled(readConfigPayload(result.getConfigId()))) {
            throw new BusinessException("STORAGE_409", "当前配置未启用结果落库，不允许执行落库");
        }

        Map<String, Object> storageData = buildStorageData(result);
        StorageResultRecord record = storageResultMapper.selectByTaskId(taskId);
        LocalDateTime now = LocalDateTime.now();
        if (record == null) {
            record = new StorageResultRecord();
            record.setId(IdGenerator.nextId("STR"));
            record.setTaskId(result.getTaskId());
            record.setTraceId(result.getTraceId());
            record.setDocumentId(result.getDocumentId());
            record.setCreatedAt(now);
        }
        record.setConfigId(result.getConfigId());
        record.setTargetTable(firstText(result.getTargetTable(), "SIMULATED_TARGET_TABLE"));
        record.setMappingProfile(firstText(result.getMappingProfile(), "默认映射方案"));
        record.setStorageJson(writeJson(storageData));
        record.setUniqueKeyJson(writeJson(buildUniqueKey(result, storageData)));
        record.setStorageStatus("SUCCESS");
        record.setDuplicateStrategy(firstText(request == null ? null : request.getDuplicateStrategy(), "UPSERT_BY_TASK_ID"));
        record.setErrorMessage(null);
        record.setStoredBy(firstText(request == null ? null : request.getStoredBy(), "当前用户"));
        record.setStoredAt(now);
        record.setUpdatedAt(now);
        if (storageResultMapper.selectByTaskId(taskId) == null) {
            storageResultMapper.insert(record);
        } else {
            storageResultMapper.update(record);
        }

        StorageQueryRequest query = new StorageQueryRequest();
        query.setKeyword(taskId);
        return records(query).stream()
                .filter(item -> taskId.equals(item.getTaskId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("STORAGE_500", "落库后记录查询失败"));
    }

    private StorageRecordResponse fillStorageData(StorageRecordResponse response) {
        response.setStorageData(readJson(response.getStorageJson()));
        response.setStorageJson(null);
        return response;
    }

    private Map<String, Object> buildStorageData(ExtractResultRecord result) {
        Map<String, Object> data = new LinkedHashMap<>(readJson(result.getResultJson()));
        data.put("_task_id", result.getTaskId());
        data.put("_trace_id", result.getTraceId());
        data.put("_document_id", result.getDocumentId());
        data.put("_config_id", result.getConfigId());
        data.put("_target_table", firstText(result.getTargetTable(), "SIMULATED_TARGET_TABLE"));
        data.put("_mapping_profile", firstText(result.getMappingProfile(), "默认映射方案"));
        data.put("_stored_at", LocalDateTime.now().toString());
        return data;
    }

    private Map<String, Object> buildUniqueKey(ExtractResultRecord result, Map<String, Object> storageData) {
        Map<String, Object> uniqueKey = new LinkedHashMap<>();
        uniqueKey.put("task_id", result.getTaskId());
        uniqueKey.put("document_id", result.getDocumentId());
        Object businessNo = storageData.get("business_no");
        if (businessNo != null) {
            uniqueKey.put("business_no", businessNo);
        }
        return uniqueKey;
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
            throw new BusinessException("JSON_400", "落库数据无法序列化");
        }
    }

    private ConfigWizardPayload readConfigPayload(String configId) {
        if (!StringUtils.hasText(configId)) {
            return null;
        }
        ExtractConfigRecord config = extractConfigMapper.selectById(configId);
        if (config == null || !StringUtils.hasText(config.getConfigPayload())) {
            return null;
        }
        try {
            return objectMapper.readValue(config.getConfigPayload(), ConfigWizardPayload.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private boolean storageEnabled(ConfigWizardPayload payload) {
        return payload == null || payload.getStorageConfig() == null || !Boolean.FALSE.equals(payload.getStorageConfig().getStorageEnabled());
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
