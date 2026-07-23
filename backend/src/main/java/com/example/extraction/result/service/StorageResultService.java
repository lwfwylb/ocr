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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class StorageResultService {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    private final ExtractResultMapper extractResultMapper;
    private final StorageResultMapper storageResultMapper;
    private final ExtractConfigMapper extractConfigMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public StorageResultService(ExtractResultMapper extractResultMapper,
                                StorageResultMapper storageResultMapper,
                                ExtractConfigMapper extractConfigMapper,
                                JdbcTemplate jdbcTemplate,
                                ObjectMapper objectMapper) {
        this.extractResultMapper = extractResultMapper;
        this.storageResultMapper = storageResultMapper;
        this.extractConfigMapper = extractConfigMapper;
        this.jdbcTemplate = jdbcTemplate;
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
        StorageResultRecord existing = storageResultMapper.selectByTaskId(taskId);
        if (existing != null && "SUCCESS".equals(existing.getStorageStatus())) {
            return findStoredRecord(taskId);
        }
        ConfigWizardPayload payload = readConfigPayload(result.getConfigId());
        if (!storageEnabled(payload)) {
            throw new BusinessException("STORAGE_409", "当前配置未启用结果落库，不允许执行落库");
        }

        List<Map<String, Object>> physicalRows = buildPhysicalRows(result, payload);
        Map<String, Object> storageData = buildStorageData(result, physicalRows);
        Map<String, Object> uniqueKey = buildUniqueKey(payload, physicalRows);
        writePhysicalRows(payload, physicalRows);

        LocalDateTime now = LocalDateTime.now();
        StorageResultRecord record = existing == null ? new StorageResultRecord() : existing;
        if (existing == null) {
            record.setId(IdGenerator.nextId("STR"));
            record.setTaskId(result.getTaskId());
            record.setTraceId(result.getTraceId());
            record.setDocumentId(result.getDocumentId());
            record.setCreatedAt(now);
        }
        record.setConfigId(result.getConfigId());
        record.setTargetTable(resolveTargetTable(payload, result));
        record.setMappingProfile(resolveMappingProfile(payload, result));
        record.setStorageJson(writeJson(storageData));
        record.setUniqueKeyJson(writeJson(uniqueKey));
        record.setStorageStatus("SUCCESS");
        record.setDuplicateStrategy(firstText(request == null ? null : request.getDuplicateStrategy(), uniqueKey.isEmpty() ? "INSERT_ONLY" : "UPSERT_BY_UNIQUE_KEY"));
        record.setErrorMessage(null);
        record.setStoredBy(firstText(request == null ? null : request.getStoredBy(), "system"));
        record.setStoredAt(now);
        record.setUpdatedAt(now);
        if (existing == null) {
            storageResultMapper.insert(record);
        } else {
            storageResultMapper.update(record);
        }

        result.setStatus("STORED");
        result.setUpdatedAt(now);
        extractResultMapper.updateStorageStatus(result);
        return findStoredRecord(taskId);
    }

    private StorageRecordResponse findStoredRecord(String taskId) {
        StorageQueryRequest query = new StorageQueryRequest();
        query.setKeyword(taskId);
        return records(query).stream()
                .filter(item -> taskId.equals(item.getTaskId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("STORAGE_500", "落库后记录查询失败"));
    }

    private void writePhysicalRows(ConfigWizardPayload payload, List<Map<String, Object>> rows) {
        ConfigWizardPayload.StorageConfig storageConfig = payload.getStorageConfig();
        String tableName = storageConfig.getTargetTable();
        validateIdentifier("目标表编码", tableName);
        if (rows.isEmpty()) {
            throw new BusinessException("STORAGE_400", "没有可写入物理表的字段数据");
        }
        for (Map<String, Object> row : rows) {
            row.keySet().forEach(column -> validateIdentifier("目标字段", column));
            List<String> uniqueColumns = firstUsableUniqueColumns(payload, row);
            if (!uniqueColumns.isEmpty() && physicalRowExists(tableName, uniqueColumns, row)) {
                updatePhysicalRow(tableName, row, uniqueColumns);
            } else {
                insertPhysicalRow(tableName, row);
            }
        }
    }

    private void insertPhysicalRow(String tableName, Map<String, Object> row) {
        List<String> columns = new ArrayList<>(row.keySet());
        String placeholders = String.join(", ", columns.stream().map(column -> "?").toList());
        String sql = "insert into " + tableName + " (" + String.join(", ", columns) + ") values (" + placeholders + ")";
        jdbcTemplate.update(sql, columns.stream().map(row::get).toArray());
    }

    private void updatePhysicalRow(String tableName, Map<String, Object> row, List<String> uniqueColumns) {
        List<String> updateColumns = row.keySet().stream().filter(column -> !uniqueColumns.contains(column)).toList();
        if (updateColumns.isEmpty()) {
            return;
        }
        List<Object> args = new ArrayList<>();
        String setClause = String.join(", ", updateColumns.stream().map(column -> column + " = ?").toList());
        updateColumns.forEach(column -> args.add(row.get(column)));
        String whereClause = String.join(" and ", uniqueColumns.stream().map(column -> column + " = ?").toList());
        uniqueColumns.forEach(column -> args.add(row.get(column)));
        jdbcTemplate.update("update " + tableName + " set " + setClause + " where " + whereClause, args.toArray());
    }

    private boolean physicalRowExists(String tableName, List<String> uniqueColumns, Map<String, Object> row) {
        String whereClause = String.join(" and ", uniqueColumns.stream().map(column -> column + " = ?").toList());
        Object[] args = uniqueColumns.stream().map(row::get).toArray();
        Integer count = jdbcTemplate.queryForObject("select count(1) from " + tableName + " where " + whereClause, Integer.class, args);
        return count != null && count > 0;
    }

    private List<Map<String, Object>> buildPhysicalRows(ExtractResultRecord result, ConfigWizardPayload payload) {
        Map<String, Object> resultJson = readJson(result.getResultJson());
        if ("ARRAY".equals(resultJson.get("_ai_output_mode")) && resultJson.get("items") instanceof List<?> items) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : items) {
                if (item instanceof Map<?, ?> itemMap) {
                    rows.add(buildPhysicalRow(toStringObjectMap(itemMap), payload));
                }
            }
            return rows;
        }
        return List.of(buildPhysicalRow(resultJson, payload));
    }

    private Map<String, Object> buildPhysicalRow(Map<String, Object> resultJson, ConfigWizardPayload payload) {
        Map<String, String> sourceByTarget = sourceFieldByTarget(payload);
        Map<String, ConfigWizardPayload.ResultTableColumn> columnByName = resultColumnByName(payload);
        if (columnByName.isEmpty()) {
            throw new BusinessException("STORAGE_400", "未配置目标表字段，无法写入物理结果表");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        for (ConfigWizardPayload.ResultTableColumn column : columnByName.values()) {
            String columnName = column.getColumnName();
            Object rawValue = firstValue(resultJson, columnName, sourceByTarget.get(columnName));
            Object value = isBlankValue(rawValue) ? column.getDefaultValue() : rawValue;
            if (Boolean.TRUE.equals(column.getRequired()) && isBlankValue(value)) {
                throw new BusinessException("STORAGE_400", "必填字段无入库值：" + columnName);
            }
            row.put(columnName, convertDbValue(value, column));
        }
        return row;
    }

    private Object convertDbValue(Object value, ConfigWizardPayload.ResultTableColumn column) {
        if (isBlankValue(value)) {
            return null;
        }
        String dbType = firstText(column.getDbType(), "varchar").toLowerCase(Locale.ROOT);
        String text = String.valueOf(value).trim();
        return switch (dbType) {
            case "decimal", "number" -> new BigDecimal(text.replace(",", ""));
            case "date" -> Date.valueOf(parseDate(text));
            case "datetime" -> Timestamp.valueOf(parseDateTime(text));
            default -> text;
        };
    }

    private LocalDate parseDate(String text) {
        try {
            if (text.matches("^\\d{8}$")) {
                return LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE);
            }
            return LocalDate.parse(text.substring(0, Math.min(10, text.length())));
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            throw new BusinessException("STORAGE_400", "日期字段格式不正确：" + text);
        }
    }

    private LocalDateTime parseDateTime(String text) {
        try {
            if (text.matches("^\\d{8}$")) {
                return LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay();
            }
            if (text.length() == 10) {
                return LocalDate.parse(text).atStartOfDay();
            }
            return LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (DateTimeParseException e) {
            throw new BusinessException("STORAGE_400", "时间字段格式不正确：" + text);
        }
    }

    private List<String> firstUsableUniqueColumns(ConfigWizardPayload payload, Map<String, Object> row) {
        if (payload.getUniqueConstraints() == null) {
            return List.of();
        }
        for (ConfigWizardPayload.UniqueConstraint constraint : payload.getUniqueConstraints()) {
            if (constraint == null || !Boolean.TRUE.equals(constraint.getEnabled()) || constraint.getUniqueColumns() == null || constraint.getUniqueColumns().isEmpty()) {
                continue;
            }
            List<String> columns = constraint.getUniqueColumns().stream()
                    .filter(StringUtils::hasText)
                    .toList();
            if (!columns.isEmpty() && columns.stream().allMatch(column -> !isBlankValue(row.get(column)))) {
                return columns;
            }
        }
        return List.of();
    }

    private Map<String, Object> buildStorageData(ExtractResultRecord result, List<Map<String, Object>> physicalRows) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (physicalRows.size() == 1) {
            data.putAll(physicalRows.get(0));
        } else {
            data.put("items", physicalRows);
        }
        data.put("_task_id", result.getTaskId());
        data.put("_trace_id", result.getTraceId());
        data.put("_document_id", result.getDocumentId());
        data.put("_config_id", result.getConfigId());
        data.put("_target_table", result.getTargetTable());
        data.put("_mapping_profile", result.getMappingProfile());
        data.put("_stored_at", LocalDateTime.now().toString());
        return data;
    }

    private Map<String, Object> buildUniqueKey(ConfigWizardPayload payload, List<Map<String, Object>> physicalRows) {
        Map<String, Object> uniqueKey = new LinkedHashMap<>();
        if (physicalRows.isEmpty()) {
            return uniqueKey;
        }
        List<String> uniqueColumns = firstUsableUniqueColumns(payload, physicalRows.get(0));
        for (String column : uniqueColumns) {
            uniqueKey.put(column, physicalRows.get(0).get(column));
        }
        return uniqueKey;
    }

    private Map<String, String> sourceFieldByTarget(ConfigWizardPayload payload) {
        Map<String, String> result = new LinkedHashMap<>();
        if (payload == null) {
            return result;
        }
        if (payload.getExtractFields() != null) {
            for (ConfigWizardPayload.ExtractField field : payload.getExtractFields()) {
                if (StringUtils.hasText(field.getTargetColumn()) && StringUtils.hasText(field.getFieldCode())) {
                    result.put(field.getTargetColumn(), field.getFieldCode());
                }
            }
        }
        if (payload.getFieldMappings() != null) {
            for (ConfigWizardPayload.FieldMapping mapping : payload.getFieldMappings()) {
                if (StringUtils.hasText(mapping.getTargetColumn())) {
                    result.put(mapping.getTargetColumn(), firstText(mapping.getResultFieldCode(), mapping.getExtractFieldCode()));
                }
            }
        }
        return result;
    }

    private Map<String, ConfigWizardPayload.ResultTableColumn> resultColumnByName(ConfigWizardPayload payload) {
        Map<String, ConfigWizardPayload.ResultTableColumn> result = new LinkedHashMap<>();
        if (payload == null || payload.getResultTableColumns() == null) {
            return result;
        }
        for (ConfigWizardPayload.ResultTableColumn column : payload.getResultTableColumns()) {
            if (StringUtils.hasText(column.getColumnName())) {
                result.put(column.getColumnName(), column);
            }
        }
        return result;
    }

    private Object firstValue(Map<String, Object> resultJson, String... keys) {
        if (keys == null) {
            return null;
        }
        for (String key : keys) {
            if (StringUtils.hasText(key) && resultJson.containsKey(key)) {
                return resultJson.get(key);
            }
        }
        return null;
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null) {
                result.put(String.valueOf(key), value);
            }
        });
        return result;
    }

    private StorageRecordResponse fillStorageData(StorageRecordResponse response) {
        response.setStorageData(readJson(response.getStorageJson()));
        response.setStorageJson(null);
        return response;
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
        return payload != null && payload.getStorageConfig() != null && !Boolean.FALSE.equals(payload.getStorageConfig().getStorageEnabled());
    }

    private String resolveTargetTable(ConfigWizardPayload payload, ExtractResultRecord result) {
        if (payload != null && payload.getStorageConfig() != null) {
            return firstText(payload.getStorageConfig().getTargetTable(), result.getTargetTable());
        }
        return result.getTargetTable();
    }

    private String resolveMappingProfile(ConfigWizardPayload payload, ExtractResultRecord result) {
        if (payload != null && payload.getStorageConfig() != null) {
            return firstText(payload.getStorageConfig().getMappingProfileName(), result.getMappingProfile(), "默认映射方案");
        }
        return firstText(result.getMappingProfile(), "默认映射方案");
    }

    private void validateIdentifier(String label, String identifier) {
        if (!StringUtils.hasText(identifier) || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new BusinessException("STORAGE_400", label + "只能包含小写字母、数字、下划线，并以小写字母开头：" + identifier);
        }
    }

    private boolean isBlankValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return !StringUtils.hasText(text);
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
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
