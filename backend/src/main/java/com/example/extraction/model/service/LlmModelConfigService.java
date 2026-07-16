package com.example.extraction.model.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.mapper.LlmModelConfigMapper;
import com.example.extraction.model.domain.LlmModelConfigRecord;
import com.example.extraction.model.dto.LlmModelConfigRequest;
import com.example.extraction.model.dto.LlmModelConfigResponse;
import com.example.extraction.model.dto.LlmModelQueryRequest;
import com.example.extraction.model.dto.LlmModelTestRequest;
import com.example.extraction.model.dto.LlmModelTestResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmModelConfigService {
    private static final String DEFAULT_SYSTEM_PROMPT = "你是一个严谨的要素提取助手。请只返回 JSON，不要输出解释、Markdown 或额外文本。";
    private static final String DEFAULT_USER_PROMPT = "请从文本中提取要素：payee_account_name（付款方名称）、business_date（回执日期，转为 yyyyMMdd 格式）。要求无法识别的字段返回 null，严格 JSON 格式输出。文本：付款方：张三，回执日期：2026-07-16";

    private final LlmModelConfigMapper llmModelConfigMapper;
    private final ObjectMapper objectMapper;

    public LlmModelConfigService(LlmModelConfigMapper llmModelConfigMapper, ObjectMapper objectMapper) {
        this.llmModelConfigMapper = llmModelConfigMapper;
        this.objectMapper = objectMapper;
    }

    public List<LlmModelConfigResponse> list(LlmModelQueryRequest query) {
        return llmModelConfigMapper.selectList(query).stream().map(this::toResponse).toList();
    }

    public List<Map<String, Object>> options() {
        return llmModelConfigMapper.selectEnabled().stream().map(record -> {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("value", record.getModelCode());
            option.put("label", record.getModelName() + "（" + record.getProvider() + "）");
            option.put("modelCode", record.getModelCode());
            option.put("modelName", record.getModelName());
            option.put("provider", record.getProvider());
            option.put("defaultModel", "1".equals(record.getDefaultModel()));
            return option;
        }).toList();
    }

    public LlmModelConfigResponse detail(String id) {
        return toResponse(requireRecord(id));
    }

    @Transactional
    public LlmModelConfigResponse create(LlmModelConfigRequest request) {
        validateRequest(request);
        if (llmModelConfigMapper.selectByModelCode(request.getModelCode()) != null) {
            throw new BusinessException("MODEL_409", "模型编码已存在，请使用唯一编码");
        }
        LlmModelConfigRecord record = new LlmModelConfigRecord();
        record.setId(IdGenerator.nextId("LLM"));
        fillRecord(record, request);
        record.setCreatedBy("system");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        if ("1".equals(record.getDefaultModel())) {
            llmModelConfigMapper.clearDefault();
            record.setStatus("ENABLED");
        }
        llmModelConfigMapper.insert(record);
        return detail(record.getId());
    }

    @Transactional
    public LlmModelConfigResponse update(String id, LlmModelConfigRequest request) {
        validateRequest(request);
        LlmModelConfigRecord existing = requireRecord(id);
        LlmModelConfigRecord sameCode = llmModelConfigMapper.selectByModelCode(request.getModelCode());
        if (sameCode != null && !id.equals(sameCode.getId())) {
            throw new BusinessException("MODEL_409", "模型编码已存在，请使用唯一编码");
        }
        fillRecord(existing, request);
        existing.setUpdatedAt(LocalDateTime.now());
        if ("1".equals(existing.getDefaultModel())) {
            llmModelConfigMapper.clearDefault();
            existing.setStatus("ENABLED");
        }
        llmModelConfigMapper.update(existing);
        return detail(id);
    }

    @Transactional
    public LlmModelConfigResponse enable(String id) {
        requireRecord(id);
        llmModelConfigMapper.updateStatus(id, "ENABLED");
        return detail(id);
    }

    @Transactional
    public LlmModelConfigResponse disable(String id) {
        requireRecord(id);
        llmModelConfigMapper.updateStatus(id, "DISABLED");
        return detail(id);
    }

    @Transactional
    public LlmModelConfigResponse setDefault(String id) {
        requireRecord(id);
        llmModelConfigMapper.clearDefault();
        llmModelConfigMapper.setDefault(id);
        return detail(id);
    }

    public LlmModelTestResponse test(String id, LlmModelTestRequest request) {
        LlmModelConfigRecord record = requireRecord(id);
        if (!"ENABLED".equals(record.getStatus())) {
            throw new BusinessException("MODEL_409", "仅启用中的模型允许测试连接");
        }
        if (!StringUtils.hasText(record.getBaseUrl())) {
            throw new BusinessException("MODEL_400", "模型接口地址不能为空");
        }

        long begin = System.currentTimeMillis();
        ObjectNode payload = buildOpenAiCompatiblePayload(record, request);
        LlmModelTestResponse result = baseTestResponse(record);
        result.setRequestPreview(toPrettyJson(payload, 4000));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String apiToken = resolveApiToken(record.getApiKeySecretRef());
            if (StringUtils.hasText(apiToken)) {
                headers.setBearerAuth(apiToken);
            }
            ResponseEntity<String> response = restTemplate(record).postForEntity(record.getBaseUrl(), new HttpEntity<>(payload.toString(), headers), String.class);
            fillSuccessfulTestResult(result, record, response.getBody());
        } catch (HttpStatusCodeException e) {
            result.setPassed(false);
            result.setErrorCode("LLM_HTTP_" + e.getStatusCode().value());
            result.setMessage("LLM 调用失败：HTTP " + e.getStatusCode().value());
            result.setResponsePreview(compactPreview(e.getResponseBodyAsString(), 4000));
        } catch (Exception e) {
            result.setPassed(false);
            result.setErrorCode("LLM_TEST_ERROR");
            result.setMessage("LLM 调用失败：" + e.getMessage());
        }

        result.setDurationMs(System.currentTimeMillis() - begin);
        result.setCheckedAt(LocalDateTime.now());
        return result;
    }

    private LlmModelTestResponse baseTestResponse(LlmModelConfigRecord record) {
        LlmModelTestResponse result = new LlmModelTestResponse();
        result.setPassed(false);
        result.setModelCode(record.getModelCode());
        result.setModelIdentifier(record.getModelIdentifier());
        result.setJsonValid(false);
        return result;
    }

    private ObjectNode buildOpenAiCompatiblePayload(LlmModelConfigRecord record, LlmModelTestRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", record.getModelIdentifier());
        ArrayNode messages = payload.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", StringUtils.hasText(request == null ? null : request.getSystemPrompt()) ? request.getSystemPrompt() : DEFAULT_SYSTEM_PROMPT);
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", StringUtils.hasText(request == null ? null : request.getUserPrompt()) ? request.getUserPrompt() : DEFAULT_USER_PROMPT);
        payload.put("temperature", record.getTemperature() == null ? new BigDecimal("0.10") : record.getTemperature());
        payload.put("max_tokens", record.getMaxTokens() == null || record.getMaxTokens() <= 0 ? 512 : Math.min(record.getMaxTokens(), 4096));
        return payload;
    }

    private void fillSuccessfulTestResult(LlmModelTestResponse result, LlmModelConfigRecord record, String responseBody) throws JsonProcessingException {
        result.setResponsePreview(prettyJsonOrCompact(responseBody, 8000));
        JsonNode root = objectMapper.readTree(responseBody == null ? "{}" : responseBody);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(content)) {
            content = root.path("choices").path(0).path("text").asText("");
        }
        result.setContentPreview(compactPreview(content, 4000));
        fillUsage(result, root.path("usage"));

        boolean jsonValid = isValidJsonContent(content);
        result.setJsonValid(jsonValid);
        boolean requireJson = "1".equals(record.getJsonSchemaRequired());
        if (requireJson && !jsonValid) {
            result.setPassed(false);
            result.setErrorCode("LLM_JSON_INVALID");
            result.setMessage("模型已返回内容，但未通过 JSON 格式校验");
            return;
        }
        result.setPassed(true);
        result.setMessage(jsonValid ? "真实模型调用成功，JSON 校验通过" : "真实模型调用成功");
    }

    private void fillUsage(LlmModelTestResponse result, JsonNode usage) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) {
            return;
        }
        result.setInputTokens(intOrNull(usage, "prompt_tokens"));
        result.setOutputTokens(intOrNull(usage, "completion_tokens"));
        result.setTotalTokens(intOrNull(usage, "total_tokens"));
    }

    private Integer intOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isNumber() ? value.asInt() : null;
    }

    private boolean isValidJsonContent(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        String json = extractJsonFragment(content.trim());
        if (!StringUtils.hasText(json)) {
            return false;
        }
        try {
            objectMapper.readTree(json);
            return true;
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }

    private String extractJsonFragment(String content) {
        String text = content;
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                text = text.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
            return text;
        }
        int objectStart = text.indexOf('{');
        int objectEnd = text.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return text.substring(objectStart, objectEnd + 1);
        }
        int arrayStart = text.indexOf('[');
        int arrayEnd = text.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return text.substring(arrayStart, arrayEnd + 1);
        }
        return "";
    }

    private String resolveApiToken(String secretRef) {
        if (!StringUtils.hasText(secretRef)) {
            return "";
        }
        String value = secretRef.trim();
        if (value.regionMatches(true, 0, "env:", 0, 4)) {
            String envName = value.substring(4).trim();
            String envValue = System.getenv(envName);
            if (!StringUtils.hasText(envValue)) {
                throw new IllegalStateException("环境变量 " + envName + " 未配置或为空");
            }
            return stripBearer(envValue.trim());
        }
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return stripBearer(value);
        }
        if (value.regionMatches(true, 0, "secret://", 0, 9)) {
            return "";
        }
        return value;
    }

    private String stripBearer(String value) {
        String text = value == null ? "" : value.trim();
        return text.regionMatches(true, 0, "Bearer ", 0, 7) ? text.substring(7).trim() : text;
    }

    private RestTemplate restTemplate(LlmModelConfigRecord record) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = record.getTimeoutSeconds() == null || record.getTimeoutSeconds() <= 0 ? 120 : record.getTimeoutSeconds();
        factory.setConnectTimeout(Duration.ofSeconds(Math.min(timeout, 30)));
        factory.setReadTimeout(Duration.ofSeconds(timeout));
        return new RestTemplate(factory);
    }

    private String toPrettyJson(JsonNode node, int maxLength) {
        try {
            return compactPreview(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node), maxLength);
        } catch (JsonProcessingException e) {
            return compactPreview(node.toString(), maxLength);
        }
    }

    private String prettyJsonOrCompact(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            return compactPreview(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(value)), maxLength);
        } catch (JsonProcessingException ignored) {
            return compactPreview(value, maxLength);
        }
    }

    private String compactPreview(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    private LlmModelConfigRecord requireRecord(String id) {
        LlmModelConfigRecord record = llmModelConfigMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("MODEL_404", "LLM 配置不存在");
        }
        return record;
    }

    private void validateRequest(LlmModelConfigRequest request) {
        if (!StringUtils.hasText(request.getModelCode())) {
            throw new BusinessException("PARAM_400", "模型编码不能为空");
        }
        if (!request.getModelCode().matches("^[a-zA-Z][a-zA-Z0-9_\\-]*$")) {
            throw new BusinessException("PARAM_400", "模型编码只能包含字母、数字、下划线、中划线，并以字母开头");
        }
        if (!StringUtils.hasText(request.getModelName())) {
            throw new BusinessException("PARAM_400", "模型名称不能为空");
        }
        if (!StringUtils.hasText(request.getProvider())) {
            throw new BusinessException("PARAM_400", "供应方不能为空");
        }
        if (!StringUtils.hasText(request.getBaseUrl())) {
            throw new BusinessException("PARAM_400", "接口地址不能为空");
        }
        if (!StringUtils.hasText(request.getModelIdentifier())) {
            throw new BusinessException("PARAM_400", "模型调用标识不能为空");
        }
        BigDecimal temperature = request.getTemperature();
        if (temperature != null && (temperature.compareTo(BigDecimal.ZERO) < 0 || temperature.compareTo(BigDecimal.ONE) > 0)) {
            throw new BusinessException("PARAM_400", "temperature 必须在 0 到 1 之间");
        }
        if (request.getMaxTokens() != null && request.getMaxTokens() <= 0) {
            throw new BusinessException("PARAM_400", "maxTokens 必须大于 0");
        }
        if (request.getTimeoutSeconds() != null && request.getTimeoutSeconds() <= 0) {
            throw new BusinessException("PARAM_400", "超时秒数必须大于 0");
        }
        if (request.getRetryCount() != null && request.getRetryCount() < 0) {
            throw new BusinessException("PARAM_400", "重试次数不能小于 0");
        }
    }

    private void fillRecord(LlmModelConfigRecord record, LlmModelConfigRequest request) {
        record.setModelCode(request.getModelCode());
        record.setModelName(request.getModelName());
        record.setProvider(request.getProvider());
        record.setBaseUrl(request.getBaseUrl());
        record.setApiKeySecretRef(request.getApiKeySecretRef());
        record.setModelIdentifier(request.getModelIdentifier());
        record.setTemperature(request.getTemperature() == null ? new BigDecimal("0.10") : request.getTemperature());
        record.setMaxTokens(request.getMaxTokens() == null ? 4096 : request.getMaxTokens());
        record.setTimeoutSeconds(request.getTimeoutSeconds() == null ? 120 : request.getTimeoutSeconds());
        record.setRetryCount(request.getRetryCount() == null ? 1 : request.getRetryCount());
        record.setJsonSchemaRequired(Boolean.FALSE.equals(request.getJsonSchemaRequired()) ? "0" : "1");
        record.setDefaultModel(Boolean.TRUE.equals(request.getDefaultModel()) ? "1" : "0");
        record.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "ENABLED");
        record.setDescription(request.getDescription());
    }

    private LlmModelConfigResponse toResponse(LlmModelConfigRecord record) {
        LlmModelConfigResponse response = new LlmModelConfigResponse();
        response.setId(record.getId());
        response.setModelCode(record.getModelCode());
        response.setModelName(record.getModelName());
        response.setProvider(record.getProvider());
        response.setBaseUrl(record.getBaseUrl());
        response.setApiKeySecretRef(record.getApiKeySecretRef());
        response.setModelIdentifier(record.getModelIdentifier());
        response.setTemperature(record.getTemperature());
        response.setMaxTokens(record.getMaxTokens());
        response.setTimeoutSeconds(record.getTimeoutSeconds());
        response.setRetryCount(record.getRetryCount());
        response.setJsonSchemaRequired("1".equals(record.getJsonSchemaRequired()));
        response.setDefaultModel("1".equals(record.getDefaultModel()));
        response.setStatus(record.getStatus());
        response.setDescription(record.getDescription());
        response.setCreatedBy(record.getCreatedBy());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }
}
