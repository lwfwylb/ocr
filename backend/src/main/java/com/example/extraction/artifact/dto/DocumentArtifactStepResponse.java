package com.example.extraction.artifact.dto;

import java.time.LocalDateTime;

public class DocumentArtifactStepResponse {
    private String id;
    private String traceId;
    private String taskId;
    private String stepCode;
    private String stepName;
    private String stepType;
    private String inputArtifactIds;
    private String outputArtifactIds;
    private String configJson;
    private String status;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long durationMs;
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getStepCode() { return stepCode; }
    public void setStepCode(String stepCode) { this.stepCode = stepCode; }
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public String getStepType() { return stepType; }
    public void setStepType(String stepType) { this.stepType = stepType; }
    public String getInputArtifactIds() { return inputArtifactIds; }
    public void setInputArtifactIds(String inputArtifactIds) { this.inputArtifactIds = inputArtifactIds; }
    public String getOutputArtifactIds() { return outputArtifactIds; }
    public void setOutputArtifactIds(String outputArtifactIds) { this.outputArtifactIds = outputArtifactIds; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
