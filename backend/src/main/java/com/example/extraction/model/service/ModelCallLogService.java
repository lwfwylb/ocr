package com.example.extraction.model.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.mapper.LlmModelConfigMapper;
import com.example.extraction.mapper.ModelCallLogMapper;
import com.example.extraction.mapper.OcrEngineConfigMapper;
import com.example.extraction.model.domain.LlmModelConfigRecord;
import com.example.extraction.model.domain.ModelCallLogRecord;
import com.example.extraction.model.domain.OcrEngineConfigRecord;
import com.example.extraction.model.dto.ModelCallLogQueryRequest;
import com.example.extraction.model.dto.ModelCallLogResponse;
import com.example.extraction.task.domain.ExtractTaskRecord;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ModelCallLogService {
    private final ModelCallLogMapper modelCallLogMapper;
    private final OcrEngineConfigMapper ocrEngineConfigMapper;
    private final LlmModelConfigMapper llmModelConfigMapper;

    public ModelCallLogService(ModelCallLogMapper modelCallLogMapper,
                               OcrEngineConfigMapper ocrEngineConfigMapper,
                               LlmModelConfigMapper llmModelConfigMapper) {
        this.modelCallLogMapper = modelCallLogMapper;
        this.ocrEngineConfigMapper = ocrEngineConfigMapper;
        this.llmModelConfigMapper = llmModelConfigMapper;
    }

    public List<ModelCallLogResponse> list(ModelCallLogQueryRequest query) {
        return modelCallLogMapper.selectList(query).stream().map(this::toResponse).toList();
    }

    public ModelCallLogResponse detail(String id) {
        ModelCallLogRecord record = modelCallLogMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("MODEL_CALL_LOG_404", "调用日志不存在");
        }
        return toResponse(record);
    }

    public void logOcrSuccess(ExtractTaskRecord task, String requestSummary, String responseSummary, long durationMs) {
        logOcrSuccess(task, defaultOcrEngine(), requestSummary, responseSummary, durationMs);
    }

    public void logOcrSuccess(ExtractTaskRecord task, OcrEngineConfigRecord engine, String requestSummary, String responseSummary, long durationMs) {
        ModelCallLogRecord record = baseRecord(task, "OCR", "PARSE", "文档解析", durationMs);
        record.setProvider(engine == null ? "MockOCR" : engine.getProvider());
        record.setModelCode(engine == null ? "mock_ocr" : engine.getEngineCode());
        record.setModelName(engine == null ? "模拟 OCR 引擎" : engine.getEngineName());
        record.setRequestSummary(requestSummary);
        record.setResponseSummary(responseSummary);
        record.setStatus("SUCCESS");
        modelCallLogMapper.insert(record);
    }

    public void logLlmSuccess(ExtractTaskRecord task, String stageCode, String stageName,
                              String requestSummary, String responseSummary, String promptPreview,
                              Integer inputTokens, Integer outputTokens, long durationMs) {
        logLlmSuccess(task, defaultLlmModel(), stageCode, stageName, requestSummary, responseSummary,
                promptPreview, inputTokens, outputTokens, durationMs);
    }

    public void logLlmSuccess(ExtractTaskRecord task, LlmModelConfigRecord model, String stageCode, String stageName,
                              String requestSummary, String responseSummary, String promptPreview,
                              Integer inputTokens, Integer outputTokens, long durationMs) {
        ModelCallLogRecord record = baseRecord(task, "LLM", stageCode, stageName, durationMs);
        fillLlmModel(record, model);
        record.setRequestSummary(requestSummary);
        record.setResponseSummary(responseSummary);
        record.setPromptPreview(promptPreview);
        record.setInputTokens(inputTokens);
        record.setOutputTokens(outputTokens);
        record.setStatus("SUCCESS");
        modelCallLogMapper.insert(record);
    }

    public void logFailure(ExtractTaskRecord task, String callType, String stageCode, String stageName,
                           String requestSummary, String errorMessage, long durationMs) {
        ModelCallLogRecord record = baseRecord(task, callType, stageCode, stageName, durationMs);
        if ("OCR".equals(callType)) {
            OcrEngineConfigRecord engine = defaultOcrEngine();
            record.setProvider(engine == null ? "MockOCR" : engine.getProvider());
            record.setModelCode(engine == null ? "mock_ocr" : engine.getEngineCode());
            record.setModelName(engine == null ? "模拟 OCR 引擎" : engine.getEngineName());
        } else {
            fillLlmModel(record, defaultLlmModel());
        }
        record.setRequestSummary(requestSummary);
        record.setStatus("FAILED");
        record.setErrorMessage(errorMessage);
        modelCallLogMapper.insert(record);
    }

    public void logLlmFailure(ExtractTaskRecord task, LlmModelConfigRecord model, String stageCode, String stageName,
                              String requestSummary, String errorMessage, long durationMs) {
        ModelCallLogRecord record = baseRecord(task, "LLM", stageCode, stageName, durationMs);
        fillLlmModel(record, model);
        record.setRequestSummary(requestSummary);
        record.setStatus("FAILED");
        record.setErrorMessage(errorMessage);
        modelCallLogMapper.insert(record);
    }

    private void fillLlmModel(ModelCallLogRecord record, LlmModelConfigRecord model) {
        record.setProvider(model == null ? "MockLLM" : model.getProvider());
        record.setModelCode(model == null ? "mock_llm" : model.getModelCode());
        record.setModelName(model == null ? "模拟大模型" : model.getModelName());
    }

    private ModelCallLogRecord baseRecord(ExtractTaskRecord task, String callType, String stageCode, String stageName, long durationMs) {
        ModelCallLogRecord record = new ModelCallLogRecord();
        record.setId(IdGenerator.nextId("MCL"));
        record.setCallId(IdGenerator.nextId("CALL"));
        record.setTraceId(task.getTraceId());
        record.setTaskId(task.getTaskId());
        record.setConfigId(task.getConfigId());
        record.setCallType(callType);
        record.setStageCode(stageCode);
        record.setStageName(stageName);
        record.setDurationMs(durationMs);
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }

    private OcrEngineConfigRecord defaultOcrEngine() {
        return ocrEngineConfigMapper.selectEnabled().stream().findFirst().orElse(null);
    }

    private LlmModelConfigRecord defaultLlmModel() {
        return llmModelConfigMapper.selectEnabled().stream().findFirst().orElse(null);
    }

    private ModelCallLogResponse toResponse(ModelCallLogRecord record) {
        ModelCallLogResponse response = new ModelCallLogResponse();
        response.setId(record.getId());
        response.setCallId(record.getCallId());
        response.setTraceId(record.getTraceId());
        response.setTaskId(record.getTaskId());
        response.setConfigId(record.getConfigId());
        response.setCallType(record.getCallType());
        response.setStageCode(record.getStageCode());
        response.setStageName(record.getStageName());
        response.setProvider(record.getProvider());
        response.setModelCode(record.getModelCode());
        response.setModelName(record.getModelName());
        response.setRequestSummary(record.getRequestSummary());
        response.setResponseSummary(record.getResponseSummary());
        response.setPromptPreview(record.getPromptPreview());
        response.setInputTokens(record.getInputTokens());
        response.setOutputTokens(record.getOutputTokens());
        response.setDurationMs(record.getDurationMs());
        response.setStatus(record.getStatus());
        response.setErrorMessage(record.getErrorMessage());
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }
}
