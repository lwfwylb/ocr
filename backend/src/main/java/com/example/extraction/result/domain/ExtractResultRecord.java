package com.example.extraction.result.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ExtractResultRecord {
    private String id;
    private String taskId;
    private String traceId;
    private String documentId;
    private String configId;
    private String resultJson;
    private String confidenceJson;
    private BigDecimal overallConfidence;
    private String needReview;
    private String status;
    private Integer fieldCount;
    private String targetTable;
    private String mappingProfile;
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
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getConfidenceJson() { return confidenceJson; }
    public void setConfidenceJson(String confidenceJson) { this.confidenceJson = confidenceJson; }
    public BigDecimal getOverallConfidence() { return overallConfidence; }
    public void setOverallConfidence(BigDecimal overallConfidence) { this.overallConfidence = overallConfidence; }
    public String getNeedReview() { return needReview; }
    public void setNeedReview(String needReview) { this.needReview = needReview; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getFieldCount() { return fieldCount; }
    public void setFieldCount(Integer fieldCount) { this.fieldCount = fieldCount; }
    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    public String getMappingProfile() { return mappingProfile; }
    public void setMappingProfile(String mappingProfile) { this.mappingProfile = mappingProfile; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
