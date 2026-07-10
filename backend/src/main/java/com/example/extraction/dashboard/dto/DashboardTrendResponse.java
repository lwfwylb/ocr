package com.example.extraction.dashboard.dto;

public class DashboardTrendResponse {
    private String date;
    private String label;
    private Integer count;

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
}
