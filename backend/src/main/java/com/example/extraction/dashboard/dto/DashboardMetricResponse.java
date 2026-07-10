package com.example.extraction.dashboard.dto;

public class DashboardMetricResponse {
    private String label;
    private String value;
    private String trend;

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getTrend() { return trend; }
    public void setTrend(String trend) { this.trend = trend; }
}
