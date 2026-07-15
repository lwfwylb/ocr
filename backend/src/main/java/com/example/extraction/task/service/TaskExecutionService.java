package com.example.extraction.task.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.artifact.service.DocumentArtifactService;
import com.example.extraction.mapper.ExtractTaskMapper;
import com.example.extraction.mapper.TaskStageLogMapper;
import com.example.extraction.model.service.ModelCallLogService;
import com.example.extraction.ocr.OcrParseService;
import com.example.extraction.ocr.OcrTaskParseResult;
import com.example.extraction.result.service.ExtractionResultService;
import com.example.extraction.task.domain.ExtractTaskRecord;
import com.example.extraction.task.domain.TaskStageLogRecord;
import com.example.extraction.task.dto.TaskResponse;
import com.example.extraction.task.dto.TaskStageLogResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskExecutionService {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    private final ExtractTaskMapper extractTaskMapper;
    private final TaskStageLogMapper taskStageLogMapper;
    private final ExtractTaskService extractTaskService;
    private final ExtractionResultService extractionResultService;
    private final ModelCallLogService modelCallLogService;
    private final DocumentArtifactService documentArtifactService;
    private final OcrParseService ocrParseService;

    public TaskExecutionService(ExtractTaskMapper extractTaskMapper,
                                TaskStageLogMapper taskStageLogMapper,
                                ExtractTaskService extractTaskService,
                                ExtractionResultService extractionResultService,
                                ModelCallLogService modelCallLogService,
                                DocumentArtifactService documentArtifactService,
                                OcrParseService ocrParseService) {
        this.extractTaskMapper = extractTaskMapper;
        this.taskStageLogMapper = taskStageLogMapper;
        this.extractTaskService = extractTaskService;
        this.extractionResultService = extractionResultService;
        this.modelCallLogService = modelCallLogService;
        this.documentArtifactService = documentArtifactService;
        this.ocrParseService = ocrParseService;
    }

    public List<TaskStageLogResponse> stageLogs(String taskId) {
        return taskStageLogMapper.selectByTaskId(taskId).stream().map(this::toResponse).toList();
    }

    public TaskResponse executeNext(String departmentId) {
        List<ExtractTaskRecord> queuedTasks = extractTaskMapper.selectNextQueued(normalizeDepartment(departmentId));
        if (queuedTasks.isEmpty()) {
            throw new BusinessException("TASK_404", StringUtils.hasText(departmentId) ? "当前部门暂无可执行的排队任务" : "暂无可执行的排队任务");
        }
        return execute(queuedTasks.get(0).getTaskId());
    }

    private String normalizeDepartment(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return switch (value) {
            case "OPS" -> "运营部";
            case "FINANCE" -> "财务部";
            case "PRODUCT" -> "产品部";
            default -> value;
        };
    }

    public TaskResponse execute(String taskId) {
        ExtractTaskRecord task = extractTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException("TASK_404", "任务不存在");
        }
        if (!List.of("QUEUED", "PARSING", "EXTRACTING").contains(task.getStatus())) {
            throw new BusinessException("TASK_409", "当前状态不允许执行");
        }

        String runningStage = "任务执行";
        try {
            runningStage = "文档解析";
            updateState(task, "PARSING", "文档解析", 20, null, null);
            documentArtifactService.recordPreprocessPlan(task);
            OcrTaskParseResult ocrResult = ocrParseService.parseAndSave(task);
            modelCallLogService.logOcrSuccess(task, ocrResult.getEngine(),
                    "文件：" + task.getFileName() + "，输出格式：Markdown",
                    (ocrResult.isDirectTextParse() ? "文本文件直接解析成功" : "OCR 解析成功") + "，生成 Markdown 文本",
                    ocrResult.getDurationMs());
            logSuccess(task, "PARSE", "文档解析", "读取预处理输入并生成 Markdown", "解析完成，已获得文档文本");

            runningStage = "要素提取";
            updateState(task, "EXTRACTING", "要素提取", 55, null, null);
            boolean needReview = shouldReview(task);
            BigDecimal confidence = needReview ? new BigDecimal("0.86") : new BigDecimal("0.92");
            extractionResultService.saveExtractResult(task, confidence, needReview);
            modelCallLogService.logLlmSuccess(task,
                    "EXTRACT",
                    "要素提取",
                    "按配置字段生成 JSON 提取请求",
                    "模拟 LLM 提取成功，置信度 " + confidence,
                    "请根据已配置的提取字段和目标表映射，输出 JSON 结果。",
                    1200,
                    420,
                    980L);
            logSuccess(task, "EXTRACT", "要素提取", "按生效配置执行 AI/正则提取", "模拟提取完成，置信度 " + confidence);

            runningStage = "加工校验";
            updateState(task, "EXTRACTING", "加工校验", 80, null, null);
            logSuccess(task, "VALIDATE", "加工校验", "执行字典转换、SQL/API 取数和必填校验", "模拟加工校验通过");

            if (shouldFail(task)) {
                modelCallLogService.logFailure(task,
                        "LLM",
                        "EXTRACT",
                        "要素提取",
                        "模拟失败文件触发的提取请求",
                        "模拟执行失败",
                        650L);
                fail(task, "SIMULATED_FAILED", "模拟执行失败，请在失败任务中重试");
            } else if (shouldReview(task)) {
                runningStage = "复核判断";
                updateState(task, "WAIT_REVIEW", "等待人工复核", 90, null, null);
                logSuccess(task, "REVIEW_DECISION", "复核判断", "根据置信度和校验结果判断", "模拟命中复核阈值，进入人工复核");
            } else {
                runningStage = "执行完成";
                updateState(task, "COMPLETED", "执行完成", 100, null, null);
                logSuccess(task, "COMPLETE", "执行完成", "写入模拟结果", "任务模拟执行完成");
            }
            return extractTaskService.detail(taskId);
        } catch (BusinessException e) {
            log.warn("Task execution business failure. taskId={}, stage={}, code={}, message={}",
                    taskId, runningStage, e.getCode(), e.getMessage(), e);
            logParseModelFailure(task, runningStage, e.getMessage());
            fail(task, e.getCode(), firstText(e.getMessage(), runningStage + "失败"));
            return extractTaskService.detail(taskId);
        } catch (RuntimeException e) {
            log.error("Task execution runtime failure. taskId={}, stage={}", taskId, runningStage, e);
            logParseModelFailure(task, runningStage, firstText(e.getMessage(), e.getClass().getSimpleName()));
            fail(task, "EXECUTION_ERROR", runningStage + "异常：" + firstText(e.getMessage(), e.getClass().getSimpleName()));
            return extractTaskService.detail(taskId);
        } catch (LinkageError e) {
            log.error("Task execution dependency failure. taskId={}, stage={}", taskId, runningStage, e);
            logParseModelFailure(task, runningStage, firstText(e.getMessage(), e.getClass().getSimpleName()));
            fail(task, "EXECUTION_LINKAGE_ERROR", runningStage + "依赖异常：" + firstText(e.getMessage(), e.getClass().getSimpleName()));
            return extractTaskService.detail(taskId);
        }
    }

    private boolean shouldReview(ExtractTaskRecord task) {
        String fileName = task.getFileName() == null ? "" : task.getFileName().toLowerCase();
        return "LOW".equals(task.getPriority()) || fileName.contains("low") || fileName.contains("review");
    }

    private boolean shouldFail(ExtractTaskRecord task) {
        String fileName = task.getFileName() == null ? "" : task.getFileName().toLowerCase();
        return fileName.contains("fail") || fileName.contains("error");
    }

    private void logParseModelFailure(ExtractTaskRecord task, String runningStage, String errorMessage) {
        if (!"文档解析".equals(runningStage)) {
            return;
        }
        modelCallLogService.logFailure(task, "OCR", "PARSE", "文档解析",
                "OCR/文档解析请求", firstText(errorMessage, "文档解析失败"), 0L);
    }

    private void fail(ExtractTaskRecord task, String errorCode, String errorMessage) {
        String safeErrorCode = firstText(errorCode, "EXECUTION_ERROR");
        String safeErrorMessage = firstText(errorMessage, "执行失败，未返回具体错误信息");
        updateState(task, "FAILED", "执行失败", task.getProgress() == null ? 0 : task.getProgress(), safeErrorCode, safeErrorMessage);
        extractionResultService.markFailed(task, safeErrorCode, safeErrorMessage);
        logFailure(task, "FAILED", "执行失败", safeErrorCode, safeErrorMessage);
    }

    private void updateState(ExtractTaskRecord task, String status, String stage, Integer progress, String errorCode, String errorMessage) {
        task.setStatus(status);
        task.setCurrentStage(stage);
        task.setProgress(progress);
        task.setErrorCode(errorCode);
        task.setErrorMessage(errorMessage);
        task.setFailedStage("FAILED".equals(status) ? stage : null);
        task.setFailedAt("FAILED".equals(status) ? LocalDateTime.now() : null);
        task.setUpdatedAt(LocalDateTime.now());
        extractTaskMapper.updateExecutionState(task);
    }

    private void logSuccess(ExtractTaskRecord task, String stageCode, String stageName, String inputSummary, String outputSummary) {
        writeLog(task, stageCode, stageName, "SUCCESS", inputSummary, outputSummary, null, null);
    }

    private void logFailure(ExtractTaskRecord task, String stageCode, String stageName, String errorCode, String errorMessage) {
        writeLog(task, stageCode, stageName, "FAILED", "执行阶段异常", null, errorCode, errorMessage);
    }

    private void writeLog(ExtractTaskRecord task, String stageCode, String stageName, String status,
                          String inputSummary, String outputSummary, String errorCode, String errorMessage) {
        LocalDateTime startedAt = LocalDateTime.now().minusSeconds(1);
        LocalDateTime endedAt = LocalDateTime.now();
        TaskStageLogRecord log = new TaskStageLogRecord();
        log.setId(IdGenerator.nextId("TSL"));
        log.setTaskId(task.getTaskId());
        log.setTraceId(firstText(task.getTraceId(), task.getTaskId(), "UNKNOWN_TRACE"));
        log.setStageCode(stageCode);
        log.setStageName(stageName);
        log.setStatus(status);
        log.setInputSummary(inputSummary);
        log.setOutputSummary(outputSummary);
        log.setErrorCode(errorCode);
        log.setErrorMessage(errorMessage);
        log.setStartedAt(startedAt);
        log.setEndedAt(endedAt);
        log.setDurationMs(Duration.between(startedAt, endedAt).toMillis());
        log.setCreatedAt(endedAt);
        taskStageLogMapper.insert(log);
    }

    private TaskStageLogResponse toResponse(TaskStageLogRecord record) {
        TaskStageLogResponse response = new TaskStageLogResponse();
        response.setId(record.getId());
        response.setTaskId(record.getTaskId());
        response.setTraceId(record.getTraceId());
        response.setStageCode(record.getStageCode());
        response.setStageName(record.getStageName());
        response.setStatus(record.getStatus());
        response.setInputSummary(record.getInputSummary());
        response.setOutputSummary(record.getOutputSummary());
        response.setErrorCode(record.getErrorCode());
        response.setErrorMessage(record.getErrorMessage());
        response.setStartedAt(record.getStartedAt());
        response.setEndedAt(record.getEndedAt());
        response.setDurationMs(record.getDurationMs());
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
