package com.example.extraction.result.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.mapper.DownstreamPushRecordMapper;
import com.example.extraction.mapper.ExtractResultMapper;
import com.example.extraction.mapper.StorageResultMapper;
import com.example.extraction.result.domain.DownstreamPushRecord;
import com.example.extraction.result.domain.ExtractResultRecord;
import com.example.extraction.result.domain.StorageResultRecord;
import com.example.extraction.result.dto.PushExecuteRequest;
import com.example.extraction.result.dto.PushQueryRequest;
import com.example.extraction.result.dto.PushRecordResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DownstreamPushService {
    private final DownstreamPushRecordMapper pushRecordMapper;
    private final StorageResultMapper storageResultMapper;
    private final ExtractResultMapper extractResultMapper;
    private final ObjectMapper objectMapper;

    public DownstreamPushService(DownstreamPushRecordMapper pushRecordMapper,
                                 StorageResultMapper storageResultMapper,
                                 ExtractResultMapper extractResultMapper,
                                 ObjectMapper objectMapper) {
        this.pushRecordMapper = pushRecordMapper;
        this.storageResultMapper = storageResultMapper;
        this.extractResultMapper = extractResultMapper;
        this.objectMapper = objectMapper;
    }

    public List<PushRecordResponse> list(PushQueryRequest query) {
        return pushRecordMapper.selectList(query).stream().map(this::toResponse).toList();
    }

    public List<PushRecordResponse> listByTaskId(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return List.of();
        }
        return pushRecordMapper.selectByTaskId(taskId).stream().map(this::toResponse).toList();
    }

    public DownstreamPushRecord latestByTaskId(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return null;
        }
        return pushRecordMapper.selectByTaskId(taskId).stream().findFirst().orElse(null);
    }

    @Transactional
    public PushRecordResponse push(String taskId, PushExecuteRequest request) {
        StorageResultRecord storage = storageResultMapper.selectByTaskId(taskId);
        if (storage == null) {
            throw new BusinessException("PUSH_404", "落库记录不存在，请先完成结果落库");
        }
        if (!"SUCCESS".equals(storage.getStorageStatus())) {
            throw new BusinessException("PUSH_409", "仅成功落库的数据允许推送下游");
        }

        LocalDateTime now = LocalDateTime.now();
        DownstreamPushRecord record = new DownstreamPushRecord();
        record.setId(IdGenerator.nextId("DPR"));
        record.setPushId(IdGenerator.nextId("PUSH"));
        record.setTraceId(storage.getTraceId());
        record.setTaskId(storage.getTaskId());
        record.setDocumentId(storage.getDocumentId());
        record.setConfigId(storage.getConfigId());
        record.setTargetSystem(firstText(request == null ? null : request.getTargetSystem(), "模拟业务系统"));
        record.setServiceCode(firstText(request == null ? null : request.getServiceCode(), "mock_result_receive"));
        record.setServiceName(firstText(request == null ? null : request.getServiceName(), "模拟结果接收服务"));
        record.setPushMethod(firstText(request == null ? null : request.getPushMethod(), "HTTP"));
        record.setTriggerType(firstText(request == null ? null : request.getTriggerType(), "MANUAL"));
        record.setIdempotentKey(buildIdempotentKey(storage, record.getServiceCode()));
        record.setRequestPayload(buildRequestPayload(storage, request));
        record.setRetryCount(0);
        record.setMaxRetry(3);
        record.setPushedAt(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        applySimulatedCallResult(record, false);
        pushRecordMapper.insert(record);
        updateResultPushed(record);
        return toResponse(record);
    }

    @Transactional
    public PushRecordResponse retry(String pushId) {
        DownstreamPushRecord record = requireRecord(pushId);
        if ("SUCCESS".equals(record.getStatus())) {
            throw new BusinessException("PUSH_409", "成功记录无需重试");
        }
        int retryCount = record.getRetryCount() == null ? 0 : record.getRetryCount();
        int maxRetry = record.getMaxRetry() == null ? 3 : record.getMaxRetry();
        if (retryCount >= maxRetry) {
            throw new BusinessException("PUSH_409", "已达到最大重试次数");
        }
        record.setRetryCount(retryCount + 1);
        record.setPushedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        applySimulatedCallResult(record, true);
        pushRecordMapper.update(record);
        updateResultPushed(record);
        return toResponse(record);
    }

    @Transactional
    public PushRecordResponse markSuccess(String pushId) {
        DownstreamPushRecord record = requireRecord(pushId);
        record.setStatus("SUCCESS");
        record.setResponseCode("MANUAL_CONFIRMED");
        record.setResponseMessage("人工确认为下游已接收");
        record.setResponsePayload(writeJson(Map.of("confirmed", true, "message", record.getResponseMessage())));
        record.setPushedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        pushRecordMapper.update(record);
        updateResultPushed(record);
        return toResponse(record);
    }

    private DownstreamPushRecord requireRecord(String pushId) {
        DownstreamPushRecord record = pushRecordMapper.selectByPushId(pushId);
        if (record == null) {
            throw new BusinessException("PUSH_404", "推送记录不存在");
        }
        return record;
    }

    private void applySimulatedCallResult(DownstreamPushRecord record, boolean retry) {
        boolean failed = !retry && shouldSimulateFailure(record);
        if (failed) {
            record.setStatus("FAILED");
            record.setResponseCode("MOCK_500");
            record.setResponseMessage("模拟下游返回失败，可在推送记录中重试");
            record.setResponsePayload(writeJson(Map.of("success", false, "code", record.getResponseCode(), "message", record.getResponseMessage())));
            return;
        }
        record.setStatus("SUCCESS");
        record.setResponseCode("MOCK_200");
        record.setResponseMessage(retry ? "重试推送成功" : "模拟推送成功，下游已接收");
        record.setResponsePayload(writeJson(Map.of("success", true, "code", record.getResponseCode(), "message", record.getResponseMessage())));
    }

    private boolean shouldSimulateFailure(DownstreamPushRecord record) {
        return containsIgnoreCase(record.getServiceCode(), "fail") || containsIgnoreCase(record.getTargetSystem(), "fail");
    }

    private void updateResultPushed(DownstreamPushRecord record) {
        if (!"SUCCESS".equals(record.getStatus())) {
            return;
        }
        ExtractResultRecord result = extractResultMapper.selectByTaskId(record.getTaskId());
        if (result == null) {
            return;
        }
        result.setStatus("PUSHED");
        result.setUpdatedAt(LocalDateTime.now());
        extractResultMapper.update(result);
    }

    private String buildRequestPayload(StorageResultRecord storage, PushExecuteRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", storage.getTraceId());
        payload.put("taskId", storage.getTaskId());
        payload.put("documentId", storage.getDocumentId());
        payload.put("configId", storage.getConfigId());
        payload.put("targetTable", storage.getTargetTable());
        payload.put("mappingProfile", storage.getMappingProfile());
        payload.put("storageData", storage.getStorageJson());
        payload.put("operator", request == null ? null : request.getOperator());
        return writeJson(payload);
    }

    private String buildIdempotentKey(StorageResultRecord storage, String serviceCode) {
        return storage.getTaskId() + ":" + firstText(serviceCode, "default");
    }

    private PushRecordResponse toResponse(DownstreamPushRecord record) {
        PushRecordResponse response = new PushRecordResponse();
        response.setId(record.getId());
        response.setPushId(record.getPushId());
        response.setTraceId(record.getTraceId());
        response.setTaskId(record.getTaskId());
        response.setDocumentId(record.getDocumentId());
        response.setConfigId(record.getConfigId());
        response.setTargetSystem(record.getTargetSystem());
        response.setServiceCode(record.getServiceCode());
        response.setServiceName(record.getServiceName());
        response.setPushMethod(record.getPushMethod());
        response.setTriggerType(record.getTriggerType());
        response.setIdempotentKey(record.getIdempotentKey());
        response.setRequestPayload(record.getRequestPayload());
        response.setResponsePayload(record.getResponsePayload());
        response.setStatus(record.getStatus());
        response.setRetryCount(record.getRetryCount());
        response.setMaxRetry(record.getMaxRetry());
        response.setResponseCode(record.getResponseCode());
        response.setResponseMessage(record.getResponseMessage());
        response.setPushedAt(record.getPushedAt());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("JSON_400", "推送报文无法序列化");
        }
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return StringUtils.hasText(value) && value.toLowerCase().contains(keyword);
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
