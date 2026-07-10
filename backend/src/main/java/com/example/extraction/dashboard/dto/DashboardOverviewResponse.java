package com.example.extraction.dashboard.dto;

import java.util.ArrayList;
import java.util.List;

public class DashboardOverviewResponse {
    private List<DashboardMetricResponse> metrics = new ArrayList<>();
    private List<DashboardTrendResponse> taskTrend = new ArrayList<>();
    private List<DashboardQueueResponse> queueSummary = new ArrayList<>();
    private List<DashboardTaskItemResponse> pendingReviews = new ArrayList<>();
    private List<DashboardTaskItemResponse> failedTasks = new ArrayList<>();

    public List<DashboardMetricResponse> getMetrics() { return metrics; }
    public void setMetrics(List<DashboardMetricResponse> metrics) { this.metrics = metrics; }
    public List<DashboardTrendResponse> getTaskTrend() { return taskTrend; }
    public void setTaskTrend(List<DashboardTrendResponse> taskTrend) { this.taskTrend = taskTrend; }
    public List<DashboardQueueResponse> getQueueSummary() { return queueSummary; }
    public void setQueueSummary(List<DashboardQueueResponse> queueSummary) { this.queueSummary = queueSummary; }
    public List<DashboardTaskItemResponse> getPendingReviews() { return pendingReviews; }
    public void setPendingReviews(List<DashboardTaskItemResponse> pendingReviews) { this.pendingReviews = pendingReviews; }
    public List<DashboardTaskItemResponse> getFailedTasks() { return failedTasks; }
    public void setFailedTasks(List<DashboardTaskItemResponse> failedTasks) { this.failedTasks = failedTasks; }
}
