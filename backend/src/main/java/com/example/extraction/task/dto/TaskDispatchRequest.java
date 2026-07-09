package com.example.extraction.task.dto;

public class TaskDispatchRequest {
    private String mode;
    private String targetPriority;
    private Integer position;
    private Integer durationMinutes;
    private String reason;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getTargetPriority() { return targetPriority; }
    public void setTargetPriority(String targetPriority) { this.targetPriority = targetPriority; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
