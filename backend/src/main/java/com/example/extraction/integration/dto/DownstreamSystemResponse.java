package com.example.extraction.integration.dto;

import java.time.LocalDateTime;

public class DownstreamSystemResponse {
    private String id;
    private String systemCode;
    private String systemName;
    private String ownerDepartmentId;
    private String defaultAuthMode;
    private Integer defaultTimeoutSeconds;
    private Integer defaultRetryCount;
    private String status;
    private Boolean enabled;
    private Integer serviceCount;
    private Integer enabledServiceCount;
    private Integer successRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSystemCode() { return systemCode; }
    public void setSystemCode(String systemCode) { this.systemCode = systemCode; }
    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }
    public String getOwnerDepartmentId() { return ownerDepartmentId; }
    public void setOwnerDepartmentId(String ownerDepartmentId) { this.ownerDepartmentId = ownerDepartmentId; }
    public String getDefaultAuthMode() { return defaultAuthMode; }
    public void setDefaultAuthMode(String defaultAuthMode) { this.defaultAuthMode = defaultAuthMode; }
    public Integer getDefaultTimeoutSeconds() { return defaultTimeoutSeconds; }
    public void setDefaultTimeoutSeconds(Integer defaultTimeoutSeconds) { this.defaultTimeoutSeconds = defaultTimeoutSeconds; }
    public Integer getDefaultRetryCount() { return defaultRetryCount; }
    public void setDefaultRetryCount(Integer defaultRetryCount) { this.defaultRetryCount = defaultRetryCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getServiceCount() { return serviceCount; }
    public void setServiceCount(Integer serviceCount) { this.serviceCount = serviceCount; }
    public Integer getEnabledServiceCount() { return enabledServiceCount; }
    public void setEnabledServiceCount(Integer enabledServiceCount) { this.enabledServiceCount = enabledServiceCount; }
    public Integer getSuccessRate() { return successRate; }
    public void setSuccessRate(Integer successRate) { this.successRate = successRate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
