package com.example.extraction.dashboard.service;

import com.example.extraction.dashboard.dto.DashboardMetricResponse;
import com.example.extraction.dashboard.dto.DashboardOverviewResponse;
import com.example.extraction.dashboard.dto.DashboardQueueResponse;
import com.example.extraction.dashboard.dto.DashboardTaskItemResponse;
import com.example.extraction.dashboard.dto.DashboardTrendResponse;
import com.example.extraction.mapper.ExtractTaskMapper;
import com.example.extraction.mapper.ModelCallLogMapper;
import com.example.extraction.model.dto.ModelCallLogQueryRequest;
import com.example.extraction.result.dto.PushQueryRequest;
import com.example.extraction.result.service.DownstreamPushService;
import com.example.extraction.task.domain.ExtractTaskRecord;
import com.example.extraction.task.dto.TaskQueryRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private final ExtractTaskMapper extractTaskMapper;
    private final DownstreamPushService downstreamPushService;
    private final ModelCallLogMapper modelCallLogMapper;

    public DashboardService(ExtractTaskMapper extractTaskMapper,
                            DownstreamPushService downstreamPushService,
                            ModelCallLogMapper modelCallLogMapper) {
        this.extractTaskMapper = extractTaskMapper;
        this.downstreamPushService = downstreamPushService;
        this.modelCallLogMapper = modelCallLogMapper;
    }

    public DashboardOverviewResponse overview() {
        List<ExtractTaskRecord> tasks = extractTaskMapper.selectList(new TaskQueryRequest());
        List<ExtractTaskRecord> failedTasks = extractTaskMapper.selectFailed(new TaskQueryRequest());
        int pushedCount = downstreamPushService.list(new PushQueryRequest()).stream()
                .filter(item -> "SUCCESS".equals(item.getStatus()))
                .toList()
                .size();
        ModelCallLogQueryRequest failedCallQuery = new ModelCallLogQueryRequest();
        failedCallQuery.setStatus("FAILED");
        int failedCallCount = modelCallLogMapper.selectList(failedCallQuery).size();

        DashboardOverviewResponse response = new DashboardOverviewResponse();
        response.setMetrics(buildMetrics(tasks, failedTasks, pushedCount, failedCallCount));
        response.setTaskTrend(buildTrend(tasks));
        response.setQueueSummary(buildQueueSummary(tasks));
        response.setPendingReviews(tasks.stream()
                .filter(task -> "WAIT_REVIEW".equals(task.getStatus()))
                .sorted(Comparator.comparing(ExtractTaskRecord::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .map(this::toTaskItem)
                .toList());
        response.setFailedTasks(failedTasks.stream()
                .limit(8)
                .map(this::toTaskItem)
                .toList());
        return response;
    }

    private List<DashboardMetricResponse> buildMetrics(List<ExtractTaskRecord> tasks, List<ExtractTaskRecord> failedTasks,
                                                       int pushedCount, int failedCallCount) {
        int waitReviewCount = (int) tasks.stream().filter(task -> "WAIT_REVIEW".equals(task.getStatus())).count();
        int runningCount = (int) tasks.stream().filter(task -> List.of("QUEUED", "PARSING", "EXTRACTING").contains(task.getStatus())).count();
        List<DashboardMetricResponse> metrics = new ArrayList<>();
        metrics.add(metric("任务总数", String.valueOf(tasks.size()), "全部接入任务"));
        metrics.add(metric("处理中", String.valueOf(runningCount), "排队/解析/提取"));
        metrics.add(metric("待复核", String.valueOf(waitReviewCount), "需要人工确认"));
        metrics.add(metric("失败任务", String.valueOf(failedTasks.size()), "可进入失败任务重试"));
        metrics.add(metric("已推送", String.valueOf(pushedCount), "下游成功接收"));
        metrics.add(metric("调用失败", String.valueOf(failedCallCount), "OCR/LLM 调用异常"));
        return metrics;
    }

    private List<DashboardTrendResponse> buildTrend(List<ExtractTaskRecord> tasks) {
        Map<LocalDate, Integer> counts = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            counts.put(today.minusDays(i), 0);
        }
        tasks.forEach(task -> {
            if (task.getCreatedAt() == null) {
                return;
            }
            LocalDate date = task.getCreatedAt().toLocalDate();
            if (counts.containsKey(date)) {
                counts.put(date, counts.get(date) + 1);
            }
        });
        return counts.entrySet().stream().map(entry -> {
            DashboardTrendResponse item = new DashboardTrendResponse();
            item.setDate(entry.getKey().toString());
            item.setLabel(entry.getKey().format(DATE_FORMATTER));
            item.setCount(entry.getValue());
            return item;
        }).toList();
    }

    private List<DashboardQueueResponse> buildQueueSummary(List<ExtractTaskRecord> tasks) {
        return List.of(
                queue("HIGH", "高优先级积压", countQueue(tasks, "HIGH")),
                queue("MEDIUM", "中优先级积压", countQueue(tasks, "MEDIUM")),
                queue("LOW", "低优先级积压", countQueue(tasks, "LOW"))
        );
    }

    private int countQueue(List<ExtractTaskRecord> tasks, String priority) {
        return (int) tasks.stream()
                .filter(task -> priority.equals(task.getPriority()))
                .filter(task -> List.of("QUEUED", "PARSING", "EXTRACTING", "WAIT_REVIEW").contains(task.getStatus()))
                .count();
    }

    private DashboardTaskItemResponse toTaskItem(ExtractTaskRecord task) {
        DashboardTaskItemResponse item = new DashboardTaskItemResponse();
        item.setTaskId(task.getTaskId());
        item.setTraceId(task.getTraceId());
        item.setFileName(task.getFileName());
        item.setDocumentType(task.getDocumentType());
        item.setPriority(task.getPriority());
        item.setStatus(task.getStatus());
        item.setCurrentStage(task.getCurrentStage());
        item.setErrorMessage(task.getErrorMessage());
        item.setUpdatedAt(task.getUpdatedAt());
        return item;
    }

    private DashboardMetricResponse metric(String label, String value, String trend) {
        DashboardMetricResponse metric = new DashboardMetricResponse();
        metric.setLabel(label);
        metric.setValue(value);
        metric.setTrend(trend);
        return metric;
    }

    private DashboardQueueResponse queue(String priority, String label, int count) {
        DashboardQueueResponse queue = new DashboardQueueResponse();
        queue.setPriority(priority);
        queue.setLabel(label);
        queue.setCount(count);
        return queue;
    }
}
