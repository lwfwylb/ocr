package com.example.extraction.configuration.domain;

import java.time.LocalDateTime;

public class ResultTableColumnRecord {
    private String id;
    private String resultTableId;
    private String columnName;
    private String columnNameCn;
    private String dbType;
    private String typeParams;
    private Integer fieldLength;
    private Integer fieldPrecision;
    private Integer fieldScale;
    private String required;
    private String defaultValue;
    private String validationRule;
    private Integer sortNo;
    private String enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getResultTableId() { return resultTableId; }
    public void setResultTableId(String resultTableId) { this.resultTableId = resultTableId; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getColumnNameCn() { return columnNameCn; }
    public void setColumnNameCn(String columnNameCn) { this.columnNameCn = columnNameCn; }
    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }
    public String getTypeParams() { return typeParams; }
    public void setTypeParams(String typeParams) { this.typeParams = typeParams; }
    public Integer getFieldLength() { return fieldLength; }
    public void setFieldLength(Integer fieldLength) { this.fieldLength = fieldLength; }
    public Integer getFieldPrecision() { return fieldPrecision; }
    public void setFieldPrecision(Integer fieldPrecision) { this.fieldPrecision = fieldPrecision; }
    public Integer getFieldScale() { return fieldScale; }
    public void setFieldScale(Integer fieldScale) { this.fieldScale = fieldScale; }
    public String getRequired() { return required; }
    public void setRequired(String required) { this.required = required; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    public String getValidationRule() { return validationRule; }
    public void setValidationRule(String validationRule) { this.validationRule = validationRule; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getEnabled() { return enabled; }
    public void setEnabled(String enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
