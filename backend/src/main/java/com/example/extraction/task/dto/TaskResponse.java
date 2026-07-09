package com.example.extraction.task.dto;

import java.time.LocalDateTime;

public class TaskResponse {
    private String id;
    private String taskId;
    private String traceId;
    private String documentId;
    private String accessRecordId;
    private String configId;
    private String configName;
    private Integer configVersion;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String storagePath;
    private String sourceType;
    private String sourceSystem;
    private String businessNo;
    private String departmentId;
    private String category;
    private String subCategory;
    private String templateType;
    private String documentType;
    private String priority;
    private String status;
    private String currentStage;
    private Integer progress;
    private String queueLevel;
    private String queueName;
    private Integer queueCapacity;
    private Integer queuePosition;
    private Integer waitingMinutes;
    private String estimatedStartAt;
    private Boolean manualAccelerated;
    private String dispatchReason;
    private String errorCode;
    private String errorMessage;
    private String failedStage;
    private Integer retryCount;
    private Integer maxRetry;
    private Boolean retryable;
    private LocalDateTime failedAt;
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
    public String getAccessRecordId() { return accessRecordId; }
    public void setAccessRecordId(String accessRecordId) { this.accessRecordId = accessRecordId; }
    public String getConfigId() { return configId; }
    public void setConfigId(String configId) { this.configId = configId; }
    public String getConfigName() { return configName; }
    public void setConfigName(String configName) { this.configName = configName; }
    public Integer getConfigVersion() { return configVersion; }
    public void setConfigVersion(Integer configVersion) { this.configVersion = configVersion; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getBusinessNo() { return businessNo; }
    public void setBusinessNo(String businessNo) { this.businessNo = businessNo; }
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }
    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getQueueLevel() { return queueLevel; }
    public void setQueueLevel(String queueLevel) { this.queueLevel = queueLevel; }
    public String getQueueName() { return queueName; }
    public void setQueueName(String queueName) { this.queueName = queueName; }
    public Integer getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(Integer queueCapacity) { this.queueCapacity = queueCapacity; }
    public Integer getQueuePosition() { return queuePosition; }
    public void setQueuePosition(Integer queuePosition) { this.queuePosition = queuePosition; }
    public Integer getWaitingMinutes() { return waitingMinutes; }
    public void setWaitingMinutes(Integer waitingMinutes) { this.waitingMinutes = waitingMinutes; }
    public String getEstimatedStartAt() { return estimatedStartAt; }
    public void setEstimatedStartAt(String estimatedStartAt) { this.estimatedStartAt = estimatedStartAt; }
    public Boolean getManualAccelerated() { return manualAccelerated; }
    public void setManualAccelerated(Boolean manualAccelerated) { this.manualAccelerated = manualAccelerated; }
    public String getDispatchReason() { return dispatchReason; }
    public void setDispatchReason(String dispatchReason) { this.dispatchReason = dispatchReason; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getFailedStage() { return failedStage; }
    public void setFailedStage(String failedStage) { this.failedStage = failedStage; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getMaxRetry() { return maxRetry; }
    public void setMaxRetry(Integer maxRetry) { this.maxRetry = maxRetry; }
    public Boolean getRetryable() { return retryable; }
    public void setRetryable(Boolean retryable) { this.retryable = retryable; }
    public LocalDateTime getFailedAt() { return failedAt; }
    public void setFailedAt(LocalDateTime failedAt) { this.failedAt = failedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
