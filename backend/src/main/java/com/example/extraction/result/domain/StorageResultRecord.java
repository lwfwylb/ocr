package com.example.extraction.result.domain;

import java.time.LocalDateTime;

public class StorageResultRecord {
    private String id;
    private String taskId;
    private String traceId;
    private String documentId;
    private String configId;
    private String targetTable;
    private String mappingProfile;
    private String storageJson;
    private String uniqueKeyJson;
    private String storageStatus;
    private String duplicateStrategy;
    private String errorMessage;
    private String storedBy;
    private LocalDateTime storedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getConfigId() { return configId; }
    public void setConfigId(String configId) { this.configId = configId; }
    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    public String getMappingProfile() { return mappingProfile; }
    public void setMappingProfile(String mappingProfile) { this.mappingProfile = mappingProfile; }
    public String getStorageJson() { return storageJson; }
    public void setStorageJson(String storageJson) { this.storageJson = storageJson; }
    public String getUniqueKeyJson() { return uniqueKeyJson; }
    public void setUniqueKeyJson(String uniqueKeyJson) { this.uniqueKeyJson = uniqueKeyJson; }
    public String getStorageStatus() { return storageStatus; }
    public void setStorageStatus(String storageStatus) { this.storageStatus = storageStatus; }
    public String getDuplicateStrategy() { return duplicateStrategy; }
    public void setDuplicateStrategy(String duplicateStrategy) { this.duplicateStrategy = duplicateStrategy; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getStoredBy() { return storedBy; }
    public void setStoredBy(String storedBy) { this.storedBy = storedBy; }
    public LocalDateTime getStoredAt() { return storedAt; }
    public void setStoredAt(LocalDateTime storedAt) { this.storedAt = storedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
