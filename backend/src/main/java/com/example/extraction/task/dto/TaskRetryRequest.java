package com.example.extraction.task.dto;

public class TaskRetryRequest {
    private String retryMode;
    private String priority;
    private String reason;

    public String getRetryMode() { return retryMode; }
    public void setRetryMode(String retryMode) { this.retryMode = retryMode; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
