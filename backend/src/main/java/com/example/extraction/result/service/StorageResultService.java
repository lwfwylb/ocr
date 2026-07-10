package com.example.extraction.result.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
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
    private final ObjectMapper objectMapper;

    public StorageResultService(ExtractResultMapper extractResultMapper,
                                StorageResultMapper storageResultMapper,
                                ObjectMapper objectMapper) {
        this.extractResultMapper = extractResultMapper;
        this.storageResultMapper = storageResultMapper;
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
            throw new BusinessException("STORAGE_404", "\u63d0\u53d6\u7ed3\u679c\u4e0d\u5b58\u5728");
        }
        if ("WAIT_REVIEW".equals(result.getStatus())) {
            throw new BusinessException("STORAGE_409", "\u7ed3\u679c\u5c1a\u672a\u590d\u6838\uff0c\u4e0d\u5141\u8bb8\u843d\u5e93");
        }
        if ("FAILED".equals(result.getStatus())) {
            throw new BusinessException("STORAGE_409", "\u7ed3\u679c\u5df2\u5931\u8d25\uff0c\u4e0d\u5141\u8bb8\u843d\u5e93");
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
        record.setMappingProfile(firstText(result.getMappingProfile(), "\u9ed8\u8ba4\u6620\u5c04\u65b9\u6848"));
        record.setStorageJson(writeJson(storageData));
        record.setUniqueKeyJson(writeJson(buildUniqueKey(result, storageData)));
        record.setStorageStatus("SUCCESS");
        record.setDuplicateStrategy(firstText(request == null ? null : request.getDuplicateStrategy(), "UPSERT_BY_TASK_ID"));
        record.setErrorMessage(null);
        record.setStoredBy(firstText(request == null ? null : request.getStoredBy(), "\u5f53\u524d\u7528\u6237"));
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
                .orElseThrow(() -> new BusinessException("STORAGE_500", "\u843d\u5e93\u540e\u8bb0\u5f55\u67e5\u8be2\u5931\u8d25"));
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
        data.put("_mapping_profile", firstText(result.getMappingProfile(), "\u9ed8\u8ba4\u6620\u5c04\u65b9\u6848"));
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
            throw new BusinessException("JSON_400", "\u843d\u5e93\u6570\u636e\u65e0\u6cd5\u5e8f\u5217\u5316");
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
