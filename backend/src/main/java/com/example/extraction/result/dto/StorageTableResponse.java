package com.example.extraction.result.dto;

import java.time.LocalDateTime;

public class StorageTableResponse {
    private String tableName;
    private String tableCnName;
    private String description;
    private Long rowCount;
    private String relatedConfigs;
    private LocalDateTime lastStoredAt;

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getTableCnName() { return tableCnName; }
    public void setTableCnName(String tableCnName) { this.tableCnName = tableCnName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
    public String getRelatedConfigs() { return relatedConfigs; }
    public void setRelatedConfigs(String relatedConfigs) { this.relatedConfigs = relatedConfigs; }
    public LocalDateTime getLastStoredAt() { return lastStoredAt; }
    public void setLastStoredAt(LocalDateTime lastStoredAt) { this.lastStoredAt = lastStoredAt; }
}
