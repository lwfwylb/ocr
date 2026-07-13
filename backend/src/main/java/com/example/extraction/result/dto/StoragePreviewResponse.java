package com.example.extraction.result.dto;

public class StoragePreviewResponse {
    private String targetTable;
    private String targetTableName;
    private String targetColumn;
    private String columnName;
    private String dbType;
    private String typeDescription;
    private Object value;
    private Boolean required;
    private Boolean uniqueKey;
    private Boolean ready;
    private String issue;
    private String transform;

    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    public String getTargetTableName() { return targetTableName; }
    public void setTargetTableName(String targetTableName) { this.targetTableName = targetTableName; }
    public String getTargetColumn() { return targetColumn; }
    public void setTargetColumn(String targetColumn) { this.targetColumn = targetColumn; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }
    public String getTypeDescription() { return typeDescription; }
    public void setTypeDescription(String typeDescription) { this.typeDescription = typeDescription; }
    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
    public Boolean getUniqueKey() { return uniqueKey; }
    public void setUniqueKey(Boolean uniqueKey) { this.uniqueKey = uniqueKey; }
    public Boolean getReady() { return ready; }
    public void setReady(Boolean ready) { this.ready = ready; }
    public String getIssue() { return issue; }
    public void setIssue(String issue) { this.issue = issue; }
    public String getTransform() { return transform; }
    public void setTransform(String transform) { this.transform = transform; }
}
