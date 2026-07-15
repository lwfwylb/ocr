package com.example.extraction.configuration.domain;

import java.time.LocalDateTime;

public class ResultTableRecord {
    private String id;
    private String tableCode;
    private String tableName;
    private String tableComment;
    private String ownerDepartmentId;
    private String storageDatasource;
    private String autoCreateTable;
    private String autoAddColumn;
    private String ddlStatus;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTableCode() { return tableCode; }
    public void setTableCode(String tableCode) { this.tableCode = tableCode; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getTableComment() { return tableComment; }
    public void setTableComment(String tableComment) { this.tableComment = tableComment; }
    public String getOwnerDepartmentId() { return ownerDepartmentId; }
    public void setOwnerDepartmentId(String ownerDepartmentId) { this.ownerDepartmentId = ownerDepartmentId; }
    public String getStorageDatasource() { return storageDatasource; }
    public void setStorageDatasource(String storageDatasource) { this.storageDatasource = storageDatasource; }
    public String getAutoCreateTable() { return autoCreateTable; }
    public void setAutoCreateTable(String autoCreateTable) { this.autoCreateTable = autoCreateTable; }
    public String getAutoAddColumn() { return autoAddColumn; }
    public void setAutoAddColumn(String autoAddColumn) { this.autoAddColumn = autoAddColumn; }
    public String getDdlStatus() { return ddlStatus; }
    public void setDdlStatus(String ddlStatus) { this.ddlStatus = ddlStatus; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
