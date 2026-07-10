package com.example.extraction.dashboard.dto;

public class DashboardQueueResponse {
    private String priority;
    private String label;
    private Integer count;

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
}
