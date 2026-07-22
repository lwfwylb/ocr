package com.example.extraction.configuration.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.configuration.domain.ResultTableColumnRecord;
import com.example.extraction.configuration.domain.ResultTableRecord;
import com.example.extraction.configuration.dto.ConfigWizardPayload;
import com.example.extraction.configuration.dto.ResultTableDetailResponse;
import com.example.extraction.mapper.ResultTableMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ResultTableService {
    private final ResultTableMapper resultTableMapper;

    public ResultTableService(ResultTableMapper resultTableMapper) {
        this.resultTableMapper = resultTableMapper;
    }

    @Transactional
    public List<Map<String, Object>> listOptions(String keyword) {
        return resultTableMapper.selectTables(keyword, "ENABLED").stream()
                .filter(this::isReusableTable)
                .map(this::toOption)
                .toList();
    }

    @Transactional
    public ResultTableDetailResponse detail(String tableCode) {
        ResultTableRecord table = requireReusableTable(tableCode);
        return toDetail(table, resultTableMapper.selectColumnsByTableId(table.getId()));
    }

    @Transactional
    public void syncFromWizard(ConfigWizardPayload payload, String operator) {
        if (payload == null || payload.getStorageConfig() == null) {
            return;
        }
        ConfigWizardPayload.StorageConfig storageConfig = payload.getStorageConfig();
        if (Boolean.FALSE.equals(storageConfig.getStorageEnabled())) {
            return;
        }
        if (!StringUtils.hasText(storageConfig.getTargetTable())) {
            return;
        }
        if ("CREATE".equals(storageConfig.getStorageMode())) {
            upsertCreatedTable(payload, operator);
            return;
        }
        if ("REUSE".equals(storageConfig.getStorageMode())) {
            validateReuseColumns(storageConfig.getTargetTable(), payload.getResultTableColumns(), payload.getExtractFields(), payload.getUniqueConstraints());
        }
    }

    @Transactional
    public void markDdlCreated(ConfigWizardPayload payload, String operator) {
        if (payload == null || payload.getStorageConfig() == null) {
            return;
        }
        ConfigWizardPayload.StorageConfig storageConfig = payload.getStorageConfig();
        if (Boolean.FALSE.equals(storageConfig.getStorageEnabled()) || !"CREATE".equals(storageConfig.getStorageMode())
                || !StringUtils.hasText(storageConfig.getTargetTable())) {
            return;
        }
        ResultTableRecord table = resultTableMapper.selectTableByCode(storageConfig.getTargetTable());
        if (table == null) {
            return;
        }
        table.setAutoCreateTable("1");
        table.setAutoAddColumn("0");
        table.setDdlStatus("CREATED");
        table.setUpdatedAt(LocalDateTime.now());
        resultTableMapper.updateTable(table);
    }

    @Transactional
    public void validateReuseColumns(String tableCode,
                                     List<ConfigWizardPayload.ResultTableColumn> payloadColumns,
                                     List<ConfigWizardPayload.ExtractField> extractFields,
                                     List<ConfigWizardPayload.UniqueConstraint> uniqueConstraints) {
        ResultTableRecord table = requireReusableTable(tableCode);
        List<ResultTableColumnRecord> records = resultTableMapper.selectColumnsByTableId(table.getId());
        Set<String> tableColumns = new HashSet<>();
        for (ResultTableColumnRecord record : records) {
            tableColumns.add(record.getColumnName());
        }
        if (tableColumns.isEmpty()) {
            throw new BusinessException("RESULT_TABLE_400", "复用的结果表未维护字段定义：" + tableCode);
        }
        if (payloadColumns != null) {
            for (ConfigWizardPayload.ResultTableColumn column : payloadColumns) {
                if (StringUtils.hasText(column.getColumnName()) && !tableColumns.contains(column.getColumnName())) {
                    throw new BusinessException("RESULT_TABLE_400", "目标字段不存在于复用结果表：" + column.getColumnName());
                }
            }
        }
        if (extractFields != null) {
            for (ConfigWizardPayload.ExtractField field : extractFields) {
                if (StringUtils.hasText(field.getTargetColumn()) && !tableColumns.contains(field.getTargetColumn())) {
                    throw new BusinessException("RESULT_TABLE_400", "提取字段映射的目标字段不存在于复用结果表：" + field.getTargetColumn());
                }
            }
        }
        if (uniqueConstraints != null) {
            for (ConfigWizardPayload.UniqueConstraint constraint : uniqueConstraints) {
                if (!Boolean.TRUE.equals(constraint.getEnabled()) || constraint.getUniqueColumns() == null) {
                    continue;
                }
                for (String column : constraint.getUniqueColumns()) {
                    if (StringUtils.hasText(column) && !tableColumns.contains(column)) {
                        throw new BusinessException("RESULT_TABLE_400", "唯一约束字段不存在于复用结果表：" + column);
                    }
                }
            }
        }
    }

    private void upsertCreatedTable(ConfigWizardPayload payload, String operator) {
        ConfigWizardPayload.StorageConfig storageConfig = payload.getStorageConfig();
        ResultTableRecord existing = resultTableMapper.selectTableByCode(storageConfig.getTargetTable());
        if (existing != null && !"PREVIEW_ONLY".equals(existing.getDdlStatus())) {
            throw new BusinessException("RESULT_TABLE_409", "结果表编码已存在，请选择“复用已有表”或更换新的结果表编码：" + storageConfig.getTargetTable());
        }
        LocalDateTime now = LocalDateTime.now();
        ResultTableRecord table = existing == null ? new ResultTableRecord() : existing;
        if (existing == null) {
            table.setId(IdGenerator.nextId("RT"));
            table.setTableCode(storageConfig.getTargetTable());
            table.setCreatedBy(operator);
            table.setCreatedAt(now);
        }
        table.setTableName(storageConfig.getTargetTableName());
        table.setTableComment(storageConfig.getTargetTableComment());
        table.setOwnerDepartmentId(payload.getBaseInfo() == null ? null : payload.getBaseInfo().getDepartmentId());
        table.setStorageDatasource("DEFAULT");
        table.setAutoCreateTable("0");
        table.setAutoAddColumn("0");
        table.setDdlStatus("PREVIEW_ONLY");
        table.setStatus("ENABLED");
        table.setUpdatedAt(now);
        if (existing == null) {
            resultTableMapper.insertTable(table);
        } else {
            resultTableMapper.updateTable(table);
        }
        replaceColumns(table.getId(), payload.getResultTableColumns(), now);
    }

    private void replaceColumns(String resultTableId, List<ConfigWizardPayload.ResultTableColumn> columns, LocalDateTime now) {
        resultTableMapper.deleteColumnsByTableId(resultTableId);
        if (columns == null) {
            return;
        }
        int sortNo = 1;
        for (ConfigWizardPayload.ResultTableColumn column : columns) {
            if (!StringUtils.hasText(column.getColumnName()) || !StringUtils.hasText(column.getDbType())) {
                continue;
            }
            ResultTableColumnRecord record = new ResultTableColumnRecord();
            record.setId(IdGenerator.nextId("RTC"));
            record.setResultTableId(resultTableId);
            record.setColumnName(column.getColumnName());
            record.setColumnNameCn(column.getColumnCnName());
            record.setDbType(column.getDbType());
            record.setTypeParams(formatTypeParams(column));
            record.setFieldLength(column.getLength());
            record.setFieldPrecision(column.getPrecision());
            record.setFieldScale(column.getScale());
            record.setRequired(Boolean.TRUE.equals(column.getRequired()) ? "1" : "0");
            record.setDefaultValue(column.getDefaultValue());
            record.setValidationRule(column.getValidationRule());
            record.setSortNo(sortNo++);
            record.setEnabled("1");
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            resultTableMapper.insertColumn(record);
        }
    }

    private ResultTableRecord requireTable(String tableCode) {
        if (!StringUtils.hasText(tableCode)) {
            throw new BusinessException("PARAM_400", "结果表编码不能为空");
        }
        ResultTableRecord table = resultTableMapper.selectTableByCode(tableCode);
        if (table == null || !"ENABLED".equals(table.getStatus())) {
            throw new BusinessException("RESULT_TABLE_404", "结果表不存在或未启用：" + tableCode);
        }
        return table;
    }

    private ResultTableRecord requireReusableTable(String tableCode) {
        ResultTableRecord table = requireTable(tableCode);
        if (!isReusableTable(table)) {
            throw new BusinessException("RESULT_TABLE_400", "结果表尚未完成物理建表，不能作为复用已有表使用：" + tableCode);
        }
        return table;
    }

    private boolean isReusableTable(ResultTableRecord table) {
        return table != null && "1".equals(table.getAutoCreateTable()) && "CREATED".equals(table.getDdlStatus());
    }

    private ResultTableDetailResponse toDetail(ResultTableRecord table, List<ResultTableColumnRecord> records) {
        ResultTableDetailResponse response = new ResultTableDetailResponse();
        response.setId(table.getId());
        response.setTableCode(table.getTableCode());
        response.setTableName(table.getTableName());
        response.setTableComment(table.getTableComment());
        response.setOwnerDepartmentId(table.getOwnerDepartmentId());
        response.setStatus(table.getStatus());
        response.setColumns(records.stream().map(this::toPayloadColumn).toList());
        return response;
    }

    private ConfigWizardPayload.ResultTableColumn toPayloadColumn(ResultTableColumnRecord record) {
        ConfigWizardPayload.ResultTableColumn column = new ConfigWizardPayload.ResultTableColumn();
        column.setColumnName(record.getColumnName());
        column.setColumnCnName(record.getColumnNameCn());
        column.setDbType(record.getDbType());
        column.setLength(record.getFieldLength());
        column.setPrecision(record.getFieldPrecision());
        column.setScale(record.getFieldScale());
        column.setRequired("1".equals(record.getRequired()));
        column.setDefaultValue(record.getDefaultValue());
        column.setValidationRule(record.getValidationRule());
        return column;
    }

    private Map<String, Object> toOption(ResultTableRecord table) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("value", table.getTableCode());
        option.put("label", table.getTableCode() + " - " + table.getTableName());
        option.put("tableCode", table.getTableCode());
        option.put("tableName", table.getTableName());
        option.put("comment", table.getTableComment());
        option.put("ownerDepartmentId", table.getOwnerDepartmentId());
        option.put("ddlStatus", table.getDdlStatus());
        option.put("status", table.getStatus());
        return option;
    }

    private String formatTypeParams(ConfigWizardPayload.ResultTableColumn column) {
        if (List.of("varchar", "char").contains(column.getDbType())) {
            return column.getLength() == null ? null : String.valueOf(column.getLength());
        }
        if (List.of("decimal", "number").contains(column.getDbType())) {
            Integer precision = column.getPrecision() == null ? 18 : column.getPrecision();
            Integer scale = column.getScale() == null ? 2 : column.getScale();
            return precision + "," + scale;
        }
        return null;
    }
}
