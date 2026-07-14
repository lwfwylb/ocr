package com.example.extraction.task.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.document.domain.DocumentAccessRecord;
import com.example.extraction.mapper.ExtractTaskMapper;
import com.example.extraction.task.domain.ExtractTaskRecord;
import com.example.extraction.task.dto.TaskDispatchRequest;
import com.example.extraction.task.dto.TaskQueryRequest;
import com.example.extraction.task.dto.TaskResponse;
import com.example.extraction.task.dto.TaskRetryRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ExtractTaskService {
    private final ExtractTaskMapper extractTaskMapper;

    public ExtractTaskService(ExtractTaskMapper extractTaskMapper) {
        this.extractTaskMapper = extractTaskMapper;
    }

    public List<TaskResponse> list(TaskQueryRequest query) {
        normalizeQuery(query);
        return extractTaskMapper.selectList(query).stream().map(this::toResponse).toList();
    }

    public List<TaskResponse> failed(TaskQueryRequest query) {
        normalizeQuery(query);
        return extractTaskMapper.selectFailed(query).stream().map(this::toResponse).toList();
    }

    public TaskResponse detail(String taskId) {
        return toResponse(requireTask(taskId));
    }

    @Transactional
    public void createFromAccessRecord(DocumentAccessRecord accessRecord) {
        if (accessRecord == null || !"CREATED_TASK".equals(accessRecord.getAccessStatus())
                || !StringUtils.hasText(accessRecord.getTaskId())) {
            return;
        }
        if (extractTaskMapper.selectByTaskId(accessRecord.getTaskId()) != null) {
            return;
        }
        ExtractTaskRecord task = new ExtractTaskRecord();
        task.setId(IdGenerator.nextId("ET"));
        task.setTaskId(accessRecord.getTaskId());
        task.setTraceId(accessRecord.getTraceId());
        task.setDocumentId(accessRecord.getDocumentId());
        task.setAccessRecordId(accessRecord.getId());
        task.setConfigId(accessRecord.getMatchedConfigId());
        task.setConfigName(accessRecord.getMatchedConfigName());
        task.setConfigVersion(accessRecord.getMatchedConfigVersion());
        task.setFileName(accessRecord.getFileName());
        task.setFileType(accessRecord.getFileType());
        task.setFileSize(accessRecord.getFileSize());
        task.setStoragePath(accessRecord.getStoragePath());
        task.setSourceType(accessRecord.getSourceType());
        task.setSourceSystem(accessRecord.getSourceSystem());
        task.setBusinessNo(accessRecord.getBusinessNo());
        task.setDepartmentId(accessRecord.getDepartmentId());
        task.setCategory(accessRecord.getCategory());
        task.setSubCategory(accessRecord.getSubCategory());
        task.setTemplateType(accessRecord.getTemplateType());
        task.setDocumentType(accessRecord.getDocumentType());
        task.setPriority(firstText(accessRecord.getPriority(), "MEDIUM"));
        task.setStatus("QUEUED");
        task.setCurrentStage("等待解析");
        task.setProgress(0);
        fillQueue(task, task.getPriority(), null);
        task.setManualAccelerated("0");
        task.setRetryCount(0);
        task.setMaxRetry(3);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(task.getCreatedAt());
        extractTaskMapper.insert(task);
    }

    @Transactional
    public TaskResponse dispatch(String taskId, TaskDispatchRequest request) {
        ExtractTaskRecord task = requireTask(taskId);
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw new BusinessException("TASK_400", "调度原因不能为空");
        }
        if (!"QUEUED".equals(task.getStatus())) {
            throw new BusinessException("TASK_409", "仅排队中的任务允许插队调度");
        }
        String targetPriority = "PROMOTE_HIGH_TOP".equals(request.getMode()) ? "HIGH" : firstText(request.getTargetPriority(), task.getPriority(), "HIGH");
        Integer targetPosition = "PROMOTE_HIGH_TOP".equals(request.getMode()) ? 1 : request.getPosition();
        fillQueueWithReorder(task, targetPriority, targetPosition);
        task.setPriority(targetPriority);
        task.setWaitingMinutes(0);
        task.setEstimatedStartAt("尽快执行");
        task.setManualAccelerated("1");
        task.setDispatchReason(request.getReason());
        task.setUpdatedAt(LocalDateTime.now());
        extractTaskMapper.updateDispatch(task);
        return detail(taskId);
    }

    @Transactional
    public TaskResponse retry(String taskId, TaskRetryRequest request) {
        ExtractTaskRecord task = requireTask(taskId);
        if (!"FAILED".equals(task.getStatus())) {
            throw new BusinessException("TASK_409", "仅失败任务允许重试");
        }
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxRetry = task.getMaxRetry() == null ? 3 : task.getMaxRetry();
        if (retryCount >= maxRetry) {
            throw new BusinessException("TASK_409", "任务已达到最大重试次数");
        }
        String priority = firstText(request == null ? null : request.getPriority(), task.getPriority(), "MEDIUM");
        task.setStatus("QUEUED");
        task.setCurrentStage(resolveRetryStage(request == null ? null : request.getRetryMode()));
        task.setProgress(0);
        task.setPriority(priority);
        fillQueue(task, priority, null);
        task.setWaitingMinutes(0);
        task.setEstimatedStartAt("等待调度");
        task.setRetryCount(retryCount + 1);
        task.setUpdatedAt(LocalDateTime.now());
        int updated = extractTaskMapper.updateRetry(task);
        if (updated == 0) {
            throw new BusinessException("TASK_409", "任务状态已变化，请刷新后重试");
        }
        return detail(taskId);
    }

    private void fillQueue(ExtractTaskRecord task, String priority, Integer targetPosition) {
        String queueLevel = firstText(priority, "MEDIUM");
        task.setQueueLevel(queueLevel);
        task.setQueueCapacity(capacityOf(task.getDepartmentId()));
        task.setQueueName(task.getDepartmentId() + "-" + priorityLabel(queueLevel) + "队列");
        int queueSize = extractTaskMapper.countQueueTasks(task.getDepartmentId(), queueLevel);
        task.setQueuePosition(normalizeTargetPosition(targetPosition, queueSize + 1));
    }

    private void fillQueueWithReorder(ExtractTaskRecord task, String priority, Integer targetPosition) {
        String sourceDepartment = task.getDepartmentId();
        String sourceLevel = task.getQueueLevel();
        Integer sourcePosition = task.getQueuePosition();
        String targetLevel = firstText(priority, "MEDIUM");
        int targetSize = extractTaskMapper.countQueueTasks(sourceDepartment, targetLevel);
        int maxTargetPosition = targetSize + (sameQueue(sourceDepartment, sourceLevel, sourceDepartment, targetLevel) ? 0 : 1);
        int normalizedTargetPosition = normalizeTargetPosition(targetPosition, Math.max(maxTargetPosition, 1));

        if (sameQueue(sourceDepartment, sourceLevel, sourceDepartment, targetLevel) && sourcePosition != null && sourcePosition > 0) {
            if (normalizedTargetPosition < sourcePosition) {
                extractTaskMapper.incrementQueuePositions(sourceDepartment, targetLevel, normalizedTargetPosition, sourcePosition - 1, task.getTaskId());
            } else if (normalizedTargetPosition > sourcePosition) {
                extractTaskMapper.decrementQueuePositions(sourceDepartment, targetLevel, sourcePosition + 1, normalizedTargetPosition, task.getTaskId());
            }
        } else {
            if (StringUtils.hasText(sourceLevel) && sourcePosition != null && sourcePosition > 0) {
                int sourceSize = extractTaskMapper.countQueueTasks(sourceDepartment, sourceLevel);
                extractTaskMapper.decrementQueuePositions(sourceDepartment, sourceLevel, sourcePosition + 1, sourceSize, task.getTaskId());
            }
            extractTaskMapper.incrementQueuePositions(sourceDepartment, targetLevel, normalizedTargetPosition, targetSize, task.getTaskId());
        }

        task.setQueueLevel(targetLevel);
        task.setQueueCapacity(capacityOf(task.getDepartmentId()));
        task.setQueueName(task.getDepartmentId() + "-" + priorityLabel(targetLevel) + "队列");
        task.setQueuePosition(normalizedTargetPosition);
    }

    private boolean sameQueue(String leftDepartment, String leftLevel, String rightDepartment, String rightLevel) {
        return Objects.equals(leftDepartment, rightDepartment) && Objects.equals(leftLevel, rightLevel);
    }

    private int normalizeTargetPosition(Integer targetPosition, int maxPosition) {
        int position = targetPosition == null || targetPosition < 1 ? maxPosition : targetPosition;
        return Math.min(position, Math.max(maxPosition, 1));
    }

    private ExtractTaskRecord requireTask(String taskId) {
        ExtractTaskRecord task = extractTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException("TASK_404", "任务不存在");
        }
        return task;
    }

    private void normalizeQuery(TaskQueryRequest query) {
        if (query == null) {
            return;
        }
        query.setDepartmentId(normalizeDepartment(query.getDepartmentId()));
    }

    private String normalizeDepartment(String value) {
        String key = firstText(value);
        if (key == null) {
            return value;
        }
        return switch (key) {
            case "OPS" -> "运营部";
            case "FINANCE" -> "财务部";
            case "PRODUCT" -> "产品部";
            default -> value;
        };
    }

    private int capacityOf(String departmentId) {
        if ("运营部".equals(departmentId)) {
            return 60;
        }
        if ("财务部".equals(departmentId)) {
            return 10;
        }
        if ("产品部".equals(departmentId)) {
            return 20;
        }
        return 10;
    }

    private String priorityLabel(String priority) {
        return switch (priority) {
            case "HIGH" -> "高优先级";
            case "LOW" -> "低优先级";
            default -> "中优先级";
        };
    }

    private String resolveRetryStage(String retryMode) {
        if ("REPARSE".equals(retryMode)) {
            return "等待重新解析";
        }
        if ("REEXTRACT".equals(retryMode)) {
            return "等待重新提取";
        }
        if ("FULL_RETRY".equals(retryMode)) {
            return "等待全流程重试";
        }
        return "等待失败阶段重试";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private TaskResponse toResponse(ExtractTaskRecord record) {
        TaskResponse response = new TaskResponse();
        response.setId(record.getId());
        response.setTaskId(record.getTaskId());
        response.setTraceId(record.getTraceId());
        response.setDocumentId(record.getDocumentId());
        response.setAccessRecordId(record.getAccessRecordId());
        response.setConfigId(record.getConfigId());
        response.setConfigName(record.getConfigName());
        response.setConfigVersion(record.getConfigVersion());
        response.setFileName(record.getFileName());
        response.setFileType(record.getFileType());
        response.setFileSize(record.getFileSize());
        response.setStoragePath(record.getStoragePath());
        response.setSourceType(record.getSourceType());
        response.setSourceSystem(record.getSourceSystem());
        response.setBusinessNo(record.getBusinessNo());
        response.setDepartmentId(record.getDepartmentId());
        response.setCategory(record.getCategory());
        response.setSubCategory(record.getSubCategory());
        response.setTemplateType(record.getTemplateType());
        response.setDocumentType(record.getDocumentType());
        response.setPriority(record.getPriority());
        response.setStatus(record.getStatus());
        response.setCurrentStage(record.getCurrentStage());
        response.setProgress(record.getProgress());
        response.setQueueLevel(record.getQueueLevel());
        response.setQueueName(record.getQueueName());
        response.setQueueCapacity(record.getQueueCapacity());
        response.setQueuePosition(record.getQueuePosition());
        response.setWaitingMinutes(resolveWaitingMinutes(record));
        response.setEstimatedStartAt(record.getEstimatedStartAt());
        response.setManualAccelerated("1".equals(record.getManualAccelerated()));
        response.setDispatchReason(record.getDispatchReason());
        response.setErrorCode(record.getErrorCode());
        response.setErrorMessage(record.getErrorMessage());
        response.setFailedStage(record.getFailedStage());
        response.setRetryCount(record.getRetryCount());
        response.setMaxRetry(record.getMaxRetry());
        response.setRetryable("FAILED".equals(record.getStatus())
                && (record.getRetryCount() == null || record.getMaxRetry() == null || record.getRetryCount() < record.getMaxRetry()));
        response.setFailedAt(record.getFailedAt());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }

    private Integer resolveWaitingMinutes(ExtractTaskRecord record) {
        if (record.getCreatedAt() == null || !"QUEUED".equals(record.getStatus())) {
            return record.getWaitingMinutes();
        }
        long minutes = Duration.between(record.getCreatedAt(), LocalDateTime.now()).toMinutes();
        return (int) Math.max(record.getWaitingMinutes() == null ? 0 : record.getWaitingMinutes(), minutes);
    }
}
