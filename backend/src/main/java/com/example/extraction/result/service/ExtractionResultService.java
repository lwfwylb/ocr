package com.example.extraction.result.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.configuration.domain.ExtractConfigRecord;
import com.example.extraction.configuration.dto.ConfigWizardPayload;
import com.example.extraction.mapper.DocumentParseResultMapper;
import com.example.extraction.mapper.ExtractConfigMapper;
import com.example.extraction.mapper.ExtractResultMapper;
import com.example.extraction.result.domain.DocumentParseResultRecord;
import com.example.extraction.result.domain.ExtractResultRecord;
import com.example.extraction.result.dto.ResultDetailResponse;
import com.example.extraction.result.dto.ResultQueryRequest;
import com.example.extraction.result.dto.ResultSummaryResponse;
import com.example.extraction.task.domain.ExtractTaskRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class ExtractionResultService {
    private final DocumentParseResultMapper documentParseResultMapper;
    private final ExtractResultMapper extractResultMapper;
    private final ExtractConfigMapper extractConfigMapper;
    private final ObjectMapper objectMapper;

    public ExtractionResultService(DocumentParseResultMapper documentParseResultMapper,
                                   ExtractResultMapper extractResultMapper,
                                   ExtractConfigMapper extractConfigMapper,
                                   ObjectMapper objectMapper) {
        this.documentParseResultMapper = documentParseResultMapper;
        this.extractResultMapper = extractResultMapper;
        this.extractConfigMapper = extractConfigMapper;
        this.objectMapper = objectMapper;
    }

    public List<ResultSummaryResponse> list(ResultQueryRequest query) {
        normalizeQuery(query);
        return extractResultMapper.selectSummaryList(query);
    }

    public ResultDetailResponse detail(String taskId) {
        ExtractResultRecord extractResult = extractResultMapper.selectByTaskId(taskId);
        if (extractResult == null) {
            throw new BusinessException("RESULT_404", "\u63d0\u53d6\u7ed3\u679c\u4e0d\u5b58\u5728");
        }
        ResultQueryRequest query = new ResultQueryRequest();
        query.setKeyword(taskId);
        ResultSummaryResponse summary = extractResultMapper.selectSummaryList(query).stream()
                .filter(item -> taskId.equals(item.getTaskId()))
                .findFirst()
                .orElseGet(() -> fallbackSummary(extractResult));
        DocumentParseResultRecord parseResult = documentParseResultMapper.selectByTaskId(taskId);
        ResultDetailResponse response = new ResultDetailResponse();
        response.setSummary(summary);
        response.setParseText(parseResult == null ? null : parseResult.getParseText());
        response.setPageCount(parseResult == null ? null : parseResult.getPageCount());
        response.setEngineCode(parseResult == null ? null : parseResult.getEngineCode());
        response.setResult(readJson(extractResult.getResultJson()));
        response.setConfidence(readJson(extractResult.getConfidenceJson()));
        return response;
    }

    public void saveParseResult(ExtractTaskRecord task) {
        DocumentParseResultRecord record = new DocumentParseResultRecord();
        record.setId(IdGenerator.nextId("DPR"));
        record.setTaskId(task.getTaskId());
        record.setTraceId(task.getTraceId());
        record.setDocumentId(task.getDocumentId());
        record.setEngineCode("SIMULATED_PARSE_ENGINE");
        record.setParseText(buildParseText(task));
        record.setParseMarkdownPath("mock://parse-result/" + task.getTraceId() + ".md");
        record.setPageCount(1);
        record.setStatus("SUCCESS");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        if (documentParseResultMapper.selectByTaskId(task.getTaskId()) == null) {
            documentParseResultMapper.insert(record);
        } else {
            documentParseResultMapper.update(record);
        }
    }

    public void saveExtractResult(ExtractTaskRecord task, BigDecimal confidence, boolean needReview) {
        ConfigWizardPayload payload = readConfigPayload(task.getConfigId());
        DocumentParseResultRecord parseResult = documentParseResultMapper.selectByTaskId(task.getTaskId());
        String parseText = parseResult == null ? buildParseText(task) : parseResult.getParseText();
        SimulatedExtraction simulated = buildConfiguredExtraction(task, payload, confidence, parseText);
        TransformOutcome transformOutcome = applyTransformRules(payload, simulated.result(), simulated.confidence());
        BigDecimal threshold = resolveConfidenceThreshold(payload);
        BigDecimal overallConfidence = overallConfidence(transformOutcome.confidence(), simulated.overallConfidence());
        BigDecimal minConfidence = minConfidence(transformOutcome.confidence(), simulated.minConfidence());
        boolean finalNeedReview = needReview
                || transformOutcome.reviewRequired()
                || minConfidence.compareTo(threshold) < 0
                || hasForceReviewField(payload, transformOutcome.result());

        ExtractResultRecord record = new ExtractResultRecord();
        record.setId(IdGenerator.nextId("ERR"));
        record.setTaskId(task.getTaskId());
        record.setTraceId(task.getTraceId());
        record.setDocumentId(task.getDocumentId());
        record.setConfigId(task.getConfigId());
        record.setResultJson(writeJson(transformOutcome.result()));
        record.setConfidenceJson(writeJson(transformOutcome.confidence()));
        record.setOverallConfidence(overallConfidence);
        record.setNeedReview(finalNeedReview ? "1" : "0");
        record.setStatus(finalNeedReview ? "WAIT_REVIEW" : "STORED");
        record.setFieldCount(transformOutcome.result().size());
        record.setTargetTable(resolveTargetTable(payload));
        record.setMappingProfile(resolveMappingProfile(payload, task));
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        if (extractResultMapper.selectByTaskId(task.getTaskId()) == null) {
            extractResultMapper.insert(record);
        } else {
            extractResultMapper.update(record);
        }
    }

    public void markFailed(ExtractTaskRecord task, String errorCode, String errorMessage) {
        ExtractResultRecord existing = extractResultMapper.selectByTaskId(task.getTaskId());
        if (existing == null) {
            ExtractResultRecord record = new ExtractResultRecord();
            record.setId(IdGenerator.nextId("ERR"));
            record.setTaskId(task.getTaskId());
            record.setTraceId(task.getTraceId());
            record.setDocumentId(task.getDocumentId());
            record.setConfigId(task.getConfigId());
            record.setResultJson(writeJson(Map.of("error_code", errorCode, "error_message", errorMessage)));
            record.setConfidenceJson(writeJson(Map.of()));
            record.setOverallConfidence(BigDecimal.ZERO);
            record.setNeedReview("0");
            record.setStatus("FAILED");
            record.setFieldCount(0);
            record.setTargetTable("SIMULATED_TARGET_TABLE");
            record.setMappingProfile(firstText(task.getConfigName(), "\u9ed8\u8ba4\u6620\u5c04\u65b9\u6848"));
            record.setCreatedAt(LocalDateTime.now());
            record.setUpdatedAt(record.getCreatedAt());
            extractResultMapper.insert(record);
        } else {
            existing.setStatus("FAILED");
            existing.setResultJson(writeJson(Map.of("error_code", errorCode, "error_message", errorMessage)));
            existing.setConfidenceJson(writeJson(Map.of()));
            existing.setOverallConfidence(BigDecimal.ZERO);
            existing.setNeedReview("0");
            existing.setFieldCount(0);
            existing.setUpdatedAt(LocalDateTime.now());
            extractResultMapper.update(existing);
        }
    }

    private ConfigWizardPayload readConfigPayload(String configId) {
        if (!StringUtils.hasText(configId)) {
            return null;
        }
        ExtractConfigRecord config = extractConfigMapper.selectById(configId);
        if (config == null || !StringUtils.hasText(config.getConfigPayload())) {
            return null;
        }
        try {
            return objectMapper.readValue(config.getConfigPayload(), ConfigWizardPayload.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private SimulatedExtraction buildConfiguredExtraction(ExtractTaskRecord task, ConfigWizardPayload payload,
                                                          BigDecimal baseConfidence, String parseText) {
        SimulatedExtraction aiExtraction = Boolean.FALSE.equals(payload == null || payload.getExtractStrategy() == null
                ? null : payload.getExtractStrategy().getAiEnabled())
                ? emptyExtraction()
                : buildSimulatedExtraction(task, payload, baseConfidence);
        SimulatedExtraction regexExtraction = buildRegexExtraction(payload, parseText);
        String strategy = payload == null || payload.getExtractStrategy() == null
                ? "AI_FIRST_RULE_FALLBACK"
                : firstText(payload.getExtractStrategy().getDefaultStrategy(), "AI_FIRST_RULE_FALLBACK");
        boolean aiEnabled = payload == null || payload.getExtractStrategy() == null
                || !Boolean.FALSE.equals(payload.getExtractStrategy().getAiEnabled());

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> confidenceJson = new LinkedHashMap<>();
        if (!aiEnabled) {
            result.putAll(regexExtraction.result());
            confidenceJson.putAll(regexExtraction.confidence());
            fillMissingRequiredFields(payload, result, confidenceJson);
            result.put("_extract_strategy", "REGEX_ONLY");
        } else if ("RULE_FIRST_AI_FALLBACK".equals(strategy)) {
            mergeResult(result, confidenceJson, regexExtraction, true);
            mergeResult(result, confidenceJson, aiExtraction, false);
            result.put("_extract_strategy", "RULE_FIRST_AI_FALLBACK");
        } else {
            mergeResult(result, confidenceJson, aiExtraction, true);
            mergeResult(result, confidenceJson, regexExtraction, false);
            result.put("_extract_strategy", "AI_FIRST_RULE_FALLBACK");
        }

        if (result.isEmpty() || (result.size() == 1 && result.containsKey("_extract_strategy"))) {
            return buildFallbackExtraction(task, baseConfidence);
        }
        BigDecimal overall = overallConfidence(confidenceJson, aiExtraction.overallConfidence());
        BigDecimal min = minConfidence(confidenceJson, aiExtraction.minConfidence());
        return new SimulatedExtraction(result, confidenceJson, overall, min);
    }

    private SimulatedExtraction buildRegexExtraction(ConfigWizardPayload payload, String parseText) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> confidenceJson = new LinkedHashMap<>();
        if (payload == null || payload.getRegexRules() == null || !StringUtils.hasText(parseText)) {
            return emptyExtraction();
        }
        Map<String, String> targetByField = targetColumnByField(payload);
        for (ConfigWizardPayload.RegexRule rule : payload.getRegexRules()) {
            if (rule == null || Boolean.FALSE.equals(rule.getEnabled()) || !StringUtils.hasText(rule.getFieldCode())
                    || !StringUtils.hasText(rule.getRegexPattern())) {
                continue;
            }
            String targetField = firstText(targetByField.get(rule.getFieldCode()), rule.getFieldCode());
            try {
                Pattern pattern = Pattern.compile(rule.getRegexPattern(), regexFlags(rule.getRegexFlags()));
                Matcher matcher = pattern.matcher(parseText);
                if (!matcher.find()) {
                    continue;
                }
                int group = rule.getRegexGroup() == null ? 1 : Math.max(0, rule.getRegexGroup());
                String value = group <= matcher.groupCount() ? matcher.group(group) : matcher.group();
                if (StringUtils.hasText(value)) {
                    result.put(targetField, value.trim());
                    confidenceJson.put(targetField, new BigDecimal("0.98"));
                }
            } catch (PatternSyntaxException ignored) {
                result.put("_regex_error_" + rule.getFieldCode(), "正则表达式不合法：" + ignored.getDescription());
                confidenceJson.put("_regex_error_" + rule.getFieldCode(), BigDecimal.ZERO);
            }
        }
        if (result.isEmpty()) {
            return emptyExtraction();
        }
        BigDecimal overall = overallConfidence(confidenceJson, new BigDecimal("0.98"));
        BigDecimal min = minConfidence(confidenceJson, overall);
        return new SimulatedExtraction(result, confidenceJson, overall, min);
    }

    private int regexFlags(String flags) {
        int result = Pattern.UNICODE_CASE;
        if (!StringUtils.hasText(flags)) {
            return result;
        }
        String normalized = flags.toLowerCase();
        if (normalized.contains("i")) {
            result |= Pattern.CASE_INSENSITIVE;
        }
        if (normalized.contains("m")) {
            result |= Pattern.MULTILINE;
        }
        if (normalized.contains("s")) {
            result |= Pattern.DOTALL;
        }
        return result;
    }

    private void mergeResult(Map<String, Object> target, Map<String, Object> confidenceJson,
                             SimulatedExtraction source, boolean overwrite) {
        source.result().forEach((key, value) -> {
            if (!StringUtils.hasText(key) || key.startsWith("_")) {
                return;
            }
            if (overwrite || isBlankValue(target.get(key))) {
                target.put(key, value);
                Object confidence = source.confidence().get(key);
                if (confidence != null) {
                    confidenceJson.put(key, confidence);
                }
            }
        });
    }

    private void fillMissingRequiredFields(ConfigWizardPayload payload, Map<String, Object> result, Map<String, Object> confidenceJson) {
        for (FieldPlan plan : buildFieldPlans(payload)) {
            if (!Boolean.TRUE.equals(plan.requiredForStorage())) {
                continue;
            }
            String targetField = firstText(plan.targetColumn(), plan.fieldCode());
            if (StringUtils.hasText(targetField) && !result.containsKey(targetField)) {
                result.put(targetField, null);
                confidenceJson.put(targetField, BigDecimal.ZERO);
            }
        }
    }

    private Map<String, String> targetColumnByField(ConfigWizardPayload payload) {
        Map<String, String> result = new LinkedHashMap<>();
        if (payload == null) {
            return result;
        }
        if (payload.getExtractFields() != null) {
            for (ConfigWizardPayload.ExtractField field : payload.getExtractFields()) {
                if (StringUtils.hasText(field.getFieldCode())) {
                    result.put(field.getFieldCode(), firstText(field.getTargetColumn(), field.getFieldCode()));
                }
            }
        }
        if (payload.getFieldMappings() != null) {
            for (ConfigWizardPayload.FieldMapping mapping : payload.getFieldMappings()) {
                if (StringUtils.hasText(mapping.getExtractFieldCode())) {
                    result.put(mapping.getExtractFieldCode(), firstText(mapping.getTargetColumn(), mapping.getExtractFieldCode()));
                }
            }
        }
        return result;
    }

    private SimulatedExtraction emptyExtraction() {
        return new SimulatedExtraction(new LinkedHashMap<>(), new LinkedHashMap<>(), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private SimulatedExtraction buildSimulatedExtraction(ExtractTaskRecord task, ConfigWizardPayload payload, BigDecimal baseConfidence) {
        List<FieldPlan> fieldPlans = buildFieldPlans(payload);
        if (fieldPlans.isEmpty()) {
            return buildFallbackExtraction(task, baseConfidence);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> confidenceJson = new LinkedHashMap<>();
        List<BigDecimal> confidences = new ArrayList<>();
        int index = 0;
        for (FieldPlan fieldPlan : fieldPlans) {
            String resultField = firstText(fieldPlan.targetColumn(), fieldPlan.fieldCode());
            if (!StringUtils.hasText(resultField) || result.containsKey(resultField)) {
                continue;
            }
            BigDecimal fieldConfidence = normalizeConfidence(baseConfidence.subtract(BigDecimal.valueOf(index % 3).multiply(new BigDecimal("0.01"))));
            result.put(resultField, simulatedValue(task, fieldPlan));
            confidenceJson.put(resultField, fieldConfidence);
            confidences.add(fieldConfidence);
            index++;
        }
        if (result.isEmpty()) {
            return buildFallbackExtraction(task, baseConfidence);
        }
        BigDecimal overall = confidences.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(confidences.size()), 6, RoundingMode.HALF_UP);
        return new SimulatedExtraction(result, confidenceJson, overall, min(confidences, overall));
    }

    private SimulatedExtraction buildFallbackExtraction(ExtractTaskRecord task, BigDecimal confidence) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task_id", task.getTaskId());
        result.put("document_id", task.getDocumentId());
        result.put("document_type", nullToDash(task.getDocumentType()));
        result.put("file_name", task.getFileName());
        result.put("business_no", nullToDash(task.getBusinessNo()));
        result.put("amount", "100000.00");
        result.put("business_date", LocalDateTime.now().toLocalDate().toString());
        result.put("counterparty_name", "\u6a21\u62df\u4ea4\u6613\u5bf9\u624b");

        Map<String, Object> confidenceJson = new LinkedHashMap<>();
        confidenceJson.put("document_type", confidence);
        confidenceJson.put("amount", normalizeConfidence(confidence.subtract(new BigDecimal("0.01"))));
        confidenceJson.put("business_date", confidence);
        confidenceJson.put("counterparty_name", normalizeConfidence(confidence.subtract(new BigDecimal("0.02"))));
        return new SimulatedExtraction(result, confidenceJson, confidence, minConfidence(confidenceJson, confidence));
    }

    private List<FieldPlan> buildFieldPlans(ConfigWizardPayload payload) {
        List<FieldPlan> plans = new ArrayList<>();
        if (payload == null) {
            return plans;
        }
        Map<String, ConfigWizardPayload.ExtractField> fieldByCode = new LinkedHashMap<>();
        if (payload.getExtractFields() != null) {
            for (ConfigWizardPayload.ExtractField field : payload.getExtractFields()) {
                if (StringUtils.hasText(field.getFieldCode())) {
                    fieldByCode.put(field.getFieldCode(), field);
                }
            }
        }
        if (payload.getFieldMappings() != null && !payload.getFieldMappings().isEmpty()) {
            for (ConfigWizardPayload.FieldMapping mapping : payload.getFieldMappings()) {
                if (!StringUtils.hasText(mapping.getExtractFieldCode())) {
                    continue;
                }
                ConfigWizardPayload.ExtractField field = fieldByCode.get(mapping.getExtractFieldCode());
                plans.add(new FieldPlan(
                        mapping.getExtractFieldCode(),
                        field == null ? mapping.getExtractFieldCode() : firstText(field.getFieldName(), field.getFieldCode()),
                        field == null ? null : field.getFieldDescription(),
                        firstText(mapping.getTargetColumn(), field == null ? null : field.getTargetColumn(), mapping.getExtractFieldCode()),
                        field == null ? null : field.getMultiple(),
                        mapping.getRequiredForStorage()
                ));
            }
        }
        if (plans.isEmpty() && payload.getExtractFields() != null) {
            for (ConfigWizardPayload.ExtractField field : payload.getExtractFields()) {
                if (!StringUtils.hasText(field.getFieldCode())) {
                    continue;
                }
                plans.add(new FieldPlan(
                        field.getFieldCode(),
                        firstText(field.getFieldName(), field.getFieldCode()),
                        field.getFieldDescription(),
                        firstText(field.getTargetColumn(), field.getFieldCode()),
                        field.getMultiple(),
                        field.getExtractRequired()
                ));
            }
        }
        return plans;
    }

    private Object simulatedValue(ExtractTaskRecord task, FieldPlan fieldPlan) {
        String key = (firstText(fieldPlan.targetColumn(), fieldPlan.fieldCode(), fieldPlan.fieldName()) + " " + nullToDash(fieldPlan.fieldName())).toLowerCase();
        if (Boolean.TRUE.equals(fieldPlan.multiple())) {
            return List.of(simulatedSingleValue(task, key, fieldPlan, 1), simulatedSingleValue(task, key, fieldPlan, 2));
        }
        return simulatedSingleValue(task, key, fieldPlan, 1);
    }

    private Object simulatedSingleValue(ExtractTaskRecord task, String key, FieldPlan fieldPlan, int index) {
        if (key.contains("amount") || key.contains("amt") || key.contains("\u91d1\u989d")) {
            return index == 1 ? "100000.00" : "200000.00";
        }
        if (key.contains("date") || key.contains("\u65e5\u671f") || key.contains("\u65f6\u95f4")) {
            return LocalDateTime.now().toLocalDate().toString();
        }
        if (key.contains("product_code") || key.contains("prod_code") || key.contains("\u4ea7\u54c1\u4ee3\u7801")) {
            return index == 1 ? "PRD001" : "PRD002";
        }
        if (key.contains("product") || key.contains("\u4ea7\u54c1")) {
            return index == 1 ? "\u6a21\u62df\u4ea7\u54c1\u4e00\u53f7" : "\u6a21\u62df\u4ea7\u54c1\u4e8c\u53f7";
        }
        if (key.contains("account") || key.contains("acct") || key.contains("\u8d26\u53f7") || key.contains("\u8d26\u6237")) {
            return index == 1 ? "6222 **** 8910" : "4333 **** 1188";
        }
        if (key.contains("code") || key.contains("no") || key.contains("\u7f16\u53f7") || key.contains("\u5355\u53f7")) {
            return firstText(task.getBusinessNo(), task.getTaskId()) + (index == 1 ? "" : "-" + index);
        }
        if (key.contains("file") || key.contains("\u6587\u4ef6")) {
            return task.getFileName();
        }
        if (key.contains("type") || key.contains("\u7c7b\u578b")) {
            return nullToDash(task.getDocumentType());
        }
        if (key.contains("name") || key.contains("\u540d\u79f0") || key.contains("\u5bf9\u624b") || key.contains("\u4e3b\u4f53")) {
            return "\u6a21\u62df" + firstText(fieldPlan.fieldName(), fieldPlan.fieldCode());
        }
        return "\u6a21\u62df" + firstText(fieldPlan.fieldName(), fieldPlan.fieldCode(), fieldPlan.targetColumn());
    }

    private TransformOutcome applyTransformRules(ConfigWizardPayload payload, Map<String, Object> originalResult,
                                                 Map<String, Object> originalConfidence) {
        Map<String, Object> result = new LinkedHashMap<>(originalResult);
        Map<String, Object> confidenceJson = new LinkedHashMap<>(originalConfidence);
        List<String> warnings = new ArrayList<>();
        boolean reviewRequired = false;
        if (payload == null || payload.getTransformRules() == null) {
            return new TransformOutcome(result, confidenceJson, false);
        }
        for (ConfigWizardPayload.TransformRule rule : payload.getTransformRules()) {
            if (rule == null || Boolean.FALSE.equals(rule.getEnabled()) || !conditionPassed(rule, result)) {
                continue;
            }
            String inputField = firstText(rule.getInputField());
            String outputField = resolveTransformOutputField(rule);
            Object inputValue = StringUtils.hasText(inputField) ? result.get(inputField) : null;
            TransformValue transformValue = transformValue(rule, inputValue);
            if (transformValue.success()) {
                result.put(outputField, transformValue.value());
                confidenceJson.put(outputField, new BigDecimal("0.95"));
                continue;
            }
            String onFail = firstText(rule.getOnFail(), "KEEP_ORIGINAL");
            warnings.add(firstText(rule.getRuleName(), rule.getRuleType(), "加工规则") + "：" + transformValue.message());
            if ("SET_NULL".equals(onFail)) {
                result.put(outputField, null);
                confidenceJson.put(outputField, BigDecimal.ZERO);
            } else if ("REVIEW".equals(onFail) || "BLOCK".equals(onFail)) {
                result.put(outputField, "KEEP_ORIGINAL".equals(onFail) ? inputValue : null);
                confidenceJson.put(outputField, BigDecimal.ZERO);
                reviewRequired = true;
            } else if (StringUtils.hasText(outputField) && inputValue != null) {
                result.put(outputField, inputValue);
                confidenceJson.put(outputField, confidenceJson.getOrDefault(inputField, new BigDecimal("0.60")));
            }
        }
        if (!warnings.isEmpty()) {
            result.put("_transform_warnings", warnings);
        }
        return new TransformOutcome(result, confidenceJson, reviewRequired);
    }

    private TransformValue transformValue(ConfigWizardPayload.TransformRule rule, Object inputValue) {
        if (isBlankValue(inputValue)) {
            return TransformValue.failed("输入字段为空");
        }
        return switch (firstText(rule.getRuleType(), "DICT")) {
            case "API" -> TransformValue.success(simulatedApiValue(rule, inputValue));
            case "SQL" -> TransformValue.success(simulatedSqlValue(rule, inputValue));
            default -> dictTransformValue(rule, inputValue);
        };
    }

    private TransformValue dictTransformValue(ConfigWizardPayload.TransformRule rule, Object inputValue) {
        if (rule.getDictItems() == null || rule.getDictItems().isEmpty()) {
            return TransformValue.failed("未维护字典明细");
        }
        String inputText = String.valueOf(inputValue).trim();
        String matchMode = firstText(rule.getDictMatchMode(), "EQUALS");
        for (Map<String, Object> item : rule.getDictItems()) {
            String source = stringValue(item.get("source"));
            String target = stringValue(item.get("target"));
            if (!StringUtils.hasText(source)) {
                continue;
            }
            boolean matched = switch (matchMode) {
                case "CONTAINS" -> inputText.contains(source);
                case "REGEX" -> regexMatches(source, inputText);
                case "RANGE" -> rangeMatches(source, inputText);
                default -> inputText.equals(source);
            };
            if (matched) {
                return TransformValue.success(StringUtils.hasText(target) ? target : source);
            }
        }
        return TransformValue.failed("未命中字典映射");
    }

    private Object simulatedApiValue(ConfigWizardPayload.TransformRule rule, Object inputValue) {
        String key = (firstText(rule.getOutputField(), rule.getApiResponsePath(), rule.getRuleName()) + " " + inputValue).toLowerCase();
        if (key.contains("account") || key.contains("账户") || key.contains("账号")) {
            return "模拟账户名称-" + tailDigits(String.valueOf(inputValue));
        }
        if (key.contains("product") || key.contains("产品")) {
            return "模拟产品名称-" + inputValue;
        }
        if (key.contains("type") || key.contains("类型")) {
            return "模拟类型-" + inputValue;
        }
        return "模拟API取数-" + inputValue;
    }

    private Object simulatedSqlValue(ConfigWizardPayload.TransformRule rule, Object inputValue) {
        String key = (firstText(rule.getOutputField(), rule.getSqlResultColumn(), rule.getSqlText()) + " " + inputValue).toLowerCase();
        if (key.contains("product_name") || key.contains("产品名称")) {
            return "模拟产品名称-" + inputValue;
        }
        if (key.contains("product_type") || key.contains("产品类型")) {
            return "模拟产品类型-" + inputValue;
        }
        if (key.contains("name") || key.contains("名称")) {
            return "模拟名称-" + inputValue;
        }
        return "模拟SQL取数-" + inputValue;
    }

    private String resolveTransformOutputField(ConfigWizardPayload.TransformRule rule) {
        if ("OVERWRITE_INPUT".equals(rule.getOutputMode())) {
            return firstText(rule.getInputField(), rule.getOutputField(), firstText(rule.getRuleType(), "transform").toLowerCase());
        }
        return firstText(rule.getOutputField(), rule.getInputField(), firstText(rule.getRuleType(), "transform").toLowerCase());
    }

    private boolean conditionPassed(ConfigWizardPayload.TransformRule rule, Map<String, Object> result) {
        if (!Boolean.TRUE.equals(rule.getConditionEnabled())) {
            return true;
        }
        Object value = result.get(firstText(rule.getConditionField(), rule.getInputField()));
        String operator = firstText(rule.getConditionOperator(), "NOT_EMPTY");
        if ("NOT_EMPTY".equals(operator)) {
            return !isBlankValue(value);
        }
        if ("CONTAINS".equals(operator)) {
            return value != null && String.valueOf(value).contains(firstText(rule.getConditionValue(), ""));
        }
        return value != null && String.valueOf(value).equals(firstText(rule.getConditionValue(), ""));
    }

    private boolean regexMatches(String regex, String input) {
        try {
            return Pattern.compile(regex).matcher(input).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private boolean rangeMatches(String range, String input) {
        BigDecimal value = parseDecimal(input);
        if (value == null || !StringUtils.hasText(range) || !range.contains("-")) {
            return false;
        }
        String[] parts = range.split("-", 2);
        BigDecimal min = parseDecimal(parts[0]);
        BigDecimal max = parseDecimal(parts[1]);
        return (min == null || value.compareTo(min) >= 0) && (max == null || value.compareTo(max) <= 0);
    }

    private BigDecimal parseDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String tailDigits(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return digits;
        }
        return digits.substring(digits.length() - 4);
    }

    private BigDecimal resolveConfidenceThreshold(ConfigWizardPayload payload) {
        if (payload != null && payload.getReviewPolicy() != null && payload.getReviewPolicy().getConfidenceThreshold() != null) {
            return payload.getReviewPolicy().getConfidenceThreshold();
        }
        if (payload != null && payload.getExtractStrategy() != null && payload.getExtractStrategy().getConfidenceThreshold() != null) {
            return payload.getExtractStrategy().getConfidenceThreshold();
        }
        return new BigDecimal("0.90");
    }

    private boolean hasForceReviewField(ConfigWizardPayload payload, Map<String, Object> result) {
        if (payload == null || payload.getReviewPolicy() == null || payload.getReviewPolicy().getForceReviewFields() == null) {
            return false;
        }
        return payload.getReviewPolicy().getForceReviewFields().stream()
                .filter(StringUtils::hasText)
                .anyMatch(field -> result.containsKey(field) || buildFieldPlans(payload).stream()
                        .anyMatch(plan -> field.equals(plan.fieldCode()) && result.containsKey(plan.targetColumn())));
    }

    private String resolveTargetTable(ConfigWizardPayload payload) {
        if (payload != null && payload.getStorageConfig() != null) {
            return firstText(payload.getStorageConfig().getTargetTable(), "SIMULATED_TARGET_TABLE");
        }
        return "SIMULATED_TARGET_TABLE";
    }

    private String resolveMappingProfile(ConfigWizardPayload payload, ExtractTaskRecord task) {
        if (payload != null && payload.getStorageConfig() != null) {
            return firstText(payload.getStorageConfig().getMappingProfileName(), task.getConfigName(), "\u9ed8\u8ba4\u6620\u5c04\u65b9\u6848");
        }
        return firstText(task.getConfigName(), "\u9ed8\u8ba4\u6620\u5c04\u65b9\u6848");
    }

    private BigDecimal normalizeConfidence(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private BigDecimal min(List<BigDecimal> values, BigDecimal fallback) {
        return values.stream().min(BigDecimal::compareTo).orElse(fallback);
    }

    private BigDecimal minConfidence(Map<String, Object> confidenceJson, BigDecimal fallback) {
        List<BigDecimal> values = new ArrayList<>();
        for (Object value : confidenceJson.values()) {
            if (value instanceof BigDecimal decimal) {
                values.add(decimal);
            } else if (value instanceof Number number) {
                values.add(BigDecimal.valueOf(number.doubleValue()));
            }
        }
        return min(values, fallback);
    }

    private BigDecimal overallConfidence(Map<String, Object> confidenceJson, BigDecimal fallback) {
        List<BigDecimal> values = new ArrayList<>();
        for (Object value : confidenceJson.values()) {
            if (value instanceof BigDecimal decimal) {
                values.add(decimal);
            } else if (value instanceof Number number) {
                values.add(BigDecimal.valueOf(number.doubleValue()));
            }
        }
        if (values.isEmpty()) {
            return fallback == null ? BigDecimal.ZERO : fallback;
        }
        return values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }

    private void normalizeQuery(ResultQueryRequest query) {
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
            case "OPS" -> "\u8fd0\u8425\u90e8";
            case "FINANCE" -> "\u8d22\u52a1\u90e8";
            case "PRODUCT" -> "\u4ea7\u54c1\u90e8";
            default -> value;
        };
    }

    private String buildParseText(ExtractTaskRecord task) {
        String textContent = readTextFile(task);
        if (StringUtils.hasText(textContent)) {
            return "# 文本文件解析结果\n\n"
                    + "- 任务编号: " + task.getTaskId() + "\n"
                    + "- 文件名: " + task.getFileName() + "\n"
                    + "- 文档类型: " + nullToDash(task.getDocumentType()) + "\n\n"
                    + textContent;
        }
        return "# \u6a21\u62df\u89e3\u6790\u7ed3\u679c\n\n"
                + "- \u4efb\u52a1\u7f16\u53f7: " + task.getTaskId() + "\n"
                + "- \u6587\u4ef6\u540d: " + task.getFileName() + "\n"
                + "- \u6587\u6863\u7c7b\u578b: " + nullToDash(task.getDocumentType()) + "\n\n"
                + "\u8fd9\u662f\u7b2c\u4e00\u7248\u6a21\u62df\u89e3\u6790\u6587\u672c\uff0c\u540e\u7eed\u7531 OCR/MinerU \u771f\u5b9e\u7ed3\u679c\u66ff\u6362\u3002";
    }

    private String readTextFile(ExtractTaskRecord task) {
        if (!isTextFile(task) || !StringUtils.hasText(task.getStoragePath())) {
            return null;
        }
        try {
            Path path = Path.of(task.getStoragePath()).toAbsolutePath().normalize();
            if (!Files.exists(path) || Files.size(path) > 2 * 1024 * 1024) {
                return null;
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isTextFile(ExtractTaskRecord task) {
        String fileType = firstText(task.getFileType(), "");
        String fileName = firstText(task.getFileName(), "").toLowerCase();
        return List.of("txt", "csv", "md", "log").contains(fileType.toLowerCase())
                || fileName.endsWith(".txt")
                || fileName.endsWith(".csv")
                || fileName.endsWith(".md")
                || fileName.endsWith(".log");
    }

    private ResultSummaryResponse fallbackSummary(ExtractResultRecord record) {
        ResultSummaryResponse summary = new ResultSummaryResponse();
        summary.setTaskId(record.getTaskId());
        summary.setTraceId(record.getTraceId());
        summary.setDocumentId(record.getDocumentId());
        summary.setResultStatus(record.getStatus());
        summary.setReviewStatus("1".equals(record.getNeedReview()) ? "\u5f85\u590d\u6838" : "\u81ea\u52a8\u901a\u8fc7");
        summary.setTargetTable(record.getTargetTable());
        summary.setMappingProfile(record.getMappingProfile());
        summary.setFieldCount(record.getFieldCount());
        summary.setOverallConfidence(record.getOverallConfidence());
        summary.setCreatedAt(record.getCreatedAt());
        summary.setUpdatedAt(record.getUpdatedAt());
        return summary;
    }

    private Map<String, Object> readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    private boolean isBlankValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return !StringUtils.hasText(text);
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("JSON_400", "\u7ed3\u679c\u65e0\u6cd5\u5e8f\u5217\u5316");
        }
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record FieldPlan(String fieldCode, String fieldName, String fieldDescription, String targetColumn,
                             Boolean multiple, Boolean requiredForStorage) {
    }

    private record SimulatedExtraction(Map<String, Object> result, Map<String, Object> confidence,
                                       BigDecimal overallConfidence, BigDecimal minConfidence) {
    }

    private record TransformOutcome(Map<String, Object> result, Map<String, Object> confidence,
                                    boolean reviewRequired) {
    }

    private record TransformValue(boolean success, Object value, String message) {
        static TransformValue success(Object value) {
            return new TransformValue(true, value, null);
        }

        static TransformValue failed(String message) {
            return new TransformValue(false, null, message);
        }
    }
}
