package com.example.extraction.model.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.mapper.LlmModelConfigMapper;
import com.example.extraction.model.domain.LlmModelConfigRecord;
import com.example.extraction.model.dto.LlmInvokeResponse;
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
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;

@Service
public class LlmInvokeService {
    private static final String DEFAULT_SYSTEM_PROMPT = "你是一个严谨的要素提取助手。请只返回 JSON，不要输出解释、Markdown 或额外文本。";

    private final LlmModelConfigMapper llmModelConfigMapper;
    private final ObjectMapper objectMapper;

    public LlmInvokeService(LlmModelConfigMapper llmModelConfigMapper, ObjectMapper objectMapper) {
        this.llmModelConfigMapper = llmModelConfigMapper;
        this.objectMapper = objectMapper;
    }

    public LlmInvokeResponse invoke(String modelCode, String systemPrompt, String userPrompt, boolean requireJson) {
        LlmModelConfigRecord model = resolveModel(modelCode);
        if (!"ENABLED".equals(model.getStatus())) {
            throw new BusinessException("LLM_MODEL_DISABLED", "LLM 模型未启用：" + model.getModelCode());
        }
        if (!StringUtils.hasText(model.getBaseUrl())) {
            throw new BusinessException("LLM_MODEL_400", "LLM 模型接口地址不能为空");
        }
        if (!StringUtils.hasText(userPrompt)) {
            throw new BusinessException("LLM_PROMPT_400", "用户提示词不能为空");
        }

        long begin = System.currentTimeMillis();
        ObjectNode payload = buildPayload(model, systemPrompt, userPrompt);
        LlmInvokeResponse result = new LlmInvokeResponse();
        result.setModel(model);
        result.setRequestPreview(toPrettyJson(payload, 8000));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String apiToken = resolveApiToken(model.getApiKeySecretRef());
            if (StringUtils.hasText(apiToken)) {
                headers.setBearerAuth(apiToken);
            }
            ResponseEntity<String> response = restTemplate(model).postForEntity(model.getBaseUrl(), new HttpEntity<>(payload.toString(), headers), String.class);
            fillSuccessResult(result, response.getBody(), requireJson);
            return result;
        } catch (HttpStatusCodeException e) {
            throw new BusinessException("LLM_HTTP_" + e.getStatusCode().value(),
                    "LLM 调用失败：HTTP " + e.getStatusCode().value() + "，响应内容：" + compactPreview(e.getResponseBodyAsString(), 1000));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("LLM_INVOKE_ERROR", "LLM 调用失败：" + e.getMessage());
        } finally {
            result.setDurationMs(System.currentTimeMillis() - begin);
        }
    }

    public String extractJsonFragment(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String text = content.trim();
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
        int arrayStart = text.indexOf('[');
        int arrayEnd = text.lastIndexOf(']');
        if (objectStart >= 0 && objectEnd > objectStart && (arrayStart < 0 || objectStart < arrayStart)) {
            return text.substring(objectStart, objectEnd + 1);
        }
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return text.substring(arrayStart, arrayEnd + 1);
        }
        return "";
    }

    private LlmModelConfigRecord resolveModel(String modelCode) {
        LlmModelConfigRecord model = StringUtils.hasText(modelCode) ? llmModelConfigMapper.selectByModelCode(modelCode) : null;
        if (model != null) {
            return model;
        }
        return llmModelConfigMapper.selectEnabled().stream().findFirst()
                .orElseThrow(() -> new BusinessException("LLM_MODEL_404", StringUtils.hasText(modelCode)
                        ? "未找到可用 LLM 模型：" + modelCode
                        : "未配置可用 LLM 模型"));
    }

    private ObjectNode buildPayload(LlmModelConfigRecord model, String systemPrompt, String userPrompt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model.getModelIdentifier());
        ArrayNode messages = payload.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", StringUtils.hasText(systemPrompt) ? systemPrompt : DEFAULT_SYSTEM_PROMPT);
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userPrompt);
        payload.put("temperature", model.getTemperature() == null ? new BigDecimal("0.10") : model.getTemperature());
        payload.put("max_tokens", model.getMaxTokens() == null || model.getMaxTokens() <= 0 ? 4096 : model.getMaxTokens());
        return payload;
    }

    private void fillSuccessResult(LlmInvokeResponse result, String responseBody, boolean requireJson) throws JsonProcessingException {
        result.setResponsePreview(prettyJsonOrCompact(responseBody, 12000));
        JsonNode root = objectMapper.readTree(responseBody == null ? "{}" : responseBody);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(content)) {
            content = root.path("choices").path(0).path("text").asText("");
        }
        result.setContent(content);
        JsonNode usage = root.path("usage");
        result.setInputTokens(intOrNull(usage, "prompt_tokens"));
        result.setOutputTokens(intOrNull(usage, "completion_tokens"));
        result.setTotalTokens(intOrNull(usage, "total_tokens"));

        String json = extractJsonFragment(content);
        if (StringUtils.hasText(json)) {
            objectMapper.readTree(json);
            result.setJsonContent(json);
            return;
        }
        if (requireJson) {
            throw new BusinessException("LLM_JSON_INVALID", "LLM 已返回内容，但未返回合法 JSON");
        }
    }

    private Integer intOrNull(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.path(fieldName);
        return value != null && value.isNumber() ? value.asInt() : null;
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

    private RestTemplate restTemplate(LlmModelConfigRecord model) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = model.getTimeoutSeconds() == null || model.getTimeoutSeconds() <= 0 ? 120 : model.getTimeoutSeconds();
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
}
