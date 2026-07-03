package com.example.extraction.configuration.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.configuration.domain.ExtractConfigRecord;
import com.example.extraction.configuration.dto.ConfigDetailResponse;
import com.example.extraction.configuration.dto.ConfigOptionsResponse;
import com.example.extraction.configuration.dto.ConfigQueryRequest;
import com.example.extraction.configuration.dto.ConfigSummaryResponse;
import com.example.extraction.configuration.dto.ConfigWizardPayload;
import com.example.extraction.mapper.ExtractConfigMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConfigWizardService {
    private final ExtractConfigMapper extractConfigMapper;
    private final ObjectMapper objectMapper;

    public ConfigWizardService(ExtractConfigMapper extractConfigMapper, ObjectMapper objectMapper) {
        this.extractConfigMapper = extractConfigMapper;
        this.objectMapper = objectMapper;
    }

    public List<ConfigSummaryResponse> list(ConfigQueryRequest query) {
        return extractConfigMapper.selectList(query).stream().map(this::toSummary).toList();
    }

    public ConfigDetailResponse getDetail(String id) {
        ExtractConfigRecord record = requireRecord(id);
        ConfigDetailResponse response = new ConfigDetailResponse();
        response.setSummary(toSummary(record));
        response.setPayload(readPayloadObject(record));
        return response;
    }

    @Transactional
    public ConfigDetailResponse createDraft(Map<String, Object> payloadBody) {
        ConfigWizardPayload payload = toPayload(payloadBody);
        validateDraft(payload);
        ExtractConfigRecord record = new ExtractConfigRecord();
        record.setId(IdGenerator.nextId("CFG"));
        record.setConfigCode(resolveConfigCode(payload));
        fillRecord(record, payload, payloadBody);
        record.setStatus("DRAFT");
        record.setVersion(1);
        record.setCreatedBy("system");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        extractConfigMapper.insert(record);
        return getDetail(record.getId());
    }

    @Transactional
    public ConfigDetailResponse updateDraft(String id, Map<String, Object> payloadBody) {
        ConfigWizardPayload payload = toPayload(payloadBody);
        validateDraft(payload);
        ExtractConfigRecord existing = requireRecord(id);
        if (!"DRAFT".equals(existing.getStatus()) && !"TESTING".equals(existing.getStatus())) {
            throw new BusinessException("CONFIG_409", "仅草稿或验证中的配置允许直接修改");
        }
        fillRecord(existing, payload, payloadBody);
        existing.setUpdatedAt(LocalDateTime.now());
        int updated = extractConfigMapper.updateDraft(existing);
        if (updated == 0) {
            throw new BusinessException("CONFIG_409", "配置状态已变化，请刷新后重试");
        }
        return getDetail(id);
    }

    @Transactional
    public ConfigDetailResponse copy(String id) {
        ExtractConfigRecord source = requireRecord(id);
        ExtractConfigRecord copied = new ExtractConfigRecord();
        copied.setId(IdGenerator.nextId("CFG"));
        copied.setConfigCode(source.getConfigCode() + "_COPY_" + System.currentTimeMillis());
        copied.setConfigName(source.getConfigName() + "-副本");
        copied.setCategory(source.getCategory());
        copied.setSubCategory(source.getSubCategory());
        copied.setTemplateType(source.getTemplateType());
        copied.setDocumentType(source.getDocumentType());
        copied.setDepartmentId(source.getDepartmentId());
        copied.setOwnerRole(source.getOwnerRole());
        copied.setDefaultPriority(source.getDefaultPriority());
        copied.setStatus("DRAFT");
        copied.setVersion(source.getVersion() == null ? 1 : source.getVersion() + 1);
        copied.setConfigPayload(source.getConfigPayload());
        copied.setCreatedBy("system");
        copied.setCreatedAt(LocalDateTime.now());
        copied.setUpdatedAt(copied.getCreatedAt());
        extractConfigMapper.insert(copied);
        return getDetail(copied.getId());
    }

    @Transactional
    public ConfigDetailResponse publish(String id) {
        ConfigWizardPayload payload = readPayload(requireRecord(id));
        validatePublish(payload);
        int updated = extractConfigMapper.publish(id);
        if (updated == 0) {
            throw new BusinessException("CONFIG_409", "仅草稿或验证中的配置允许发布");
        }
        return getDetail(id);
    }

    @Transactional
    public ConfigDetailResponse disable(String id) {
        requireRecord(id);
        extractConfigMapper.updateStatus(id, "DISABLED");
        return getDetail(id);
    }

    public Map<String, Object> validate(String id) {
        ConfigWizardPayload payload = readPayload(requireRecord(id));
        List<String> errors = collectValidationErrors(payload, true);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", errors.isEmpty());
        result.put("errors", errors);
        result.put("checkedAt", LocalDateTime.now());
        result.put("message", errors.isEmpty() ? "配置验证通过，可发布或进入样本文档验证" : "配置存在缺失项，请补充后再发布");
        return result;
    }

    public ConfigOptionsResponse options() {
        ConfigOptionsResponse response = new ConfigOptionsResponse();
        response.setDepartments(List.of(
                option("运营部", "运营部"),
                option("财务部", "财务部"),
                option("产品部", "产品部")
        ));
        response.setRoles(List.of(
                option("模板配置员", "模板配置员"),
                option("复核人员", "复核人员"),
                option("运营复核岗", "运营复核岗"),
                option("部门管理员", "部门管理员"),
                option("普通业务用户", "普通业务用户")
        ));
        response.setCategories(List.of(
                category("资金业务", List.of(
                        subCategory("划款指令", List.of("通用划款指令模板", "托管行划款指令模板")),
                        subCategory("银行回单", List.of("通用银行回单模板"))
                )),
                category("基金交易", List.of(
                        subCategory("基金申购", List.of("大成基金申购单", "南方基金申购单", "华夏基金申购单")),
                        subCategory("基金赎回", List.of("大成基金赎回单", "南方基金赎回单"))
                )),
                category("客户业务", List.of(
                        subCategory("开户资料", List.of("机构客户开户资料", "产品户开户资料"))
                ))
        ));
        response.setOcrEngines(List.of(
                option("paddleocr_vl", "PaddleOCR-VL-1.6"),
                option("mineru", "MinerU")
        ));
        response.setResultTables(List.of(
                table("ext_fund_business_result", "基金业务要素结果表", "保存基金申购、赎回、划款、回单等业务场景的结构化要素结果"),
                table("ext_payment_instruction", "划款指令结果表", "保存运营划款指令类文档的结构化识别结果"),
                table("ext_bank_receipt", "银行回单结果表", "保存银行回单、流水回执等文档的结构化识别结果")
        ));
        response.setDownstreamServices(List.of(
                service("fund_ops_result_receive", "接收提取结果服务", "fund_ops", "运营业务系统", "HTTP", "结果推送",
                        "https://ops.example.com/api/extract-results", "POST", "httpStatus in [200,202] && body.code == 0", 3, true),
                service("finance_result_receive", "核算结果接收服务", "finance_core", "财务核算系统", "MICROSERVICE", "结果推送",
                        "finance-result-service.receiveExtractResult", "-", "response.accepted == true", 2, true),
                service("dw_extract_result_topic", "结果批量同步 Topic", "data_warehouse", "数据仓库", "MQ", "批量同步",
                        "topic.extract.result", "-", "broker ack", 5, true)
        ));
        return response;
    }

    private ExtractConfigRecord requireRecord(String id) {
        ExtractConfigRecord record = extractConfigMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("CONFIG_404", "配置不存在");
        }
        return record;
    }

    private void fillRecord(ExtractConfigRecord record, ConfigWizardPayload payload, Map<String, Object> payloadBody) {
        ConfigWizardPayload.BaseInfo baseInfo = payload.getBaseInfo();
        record.setConfigName(baseInfo.getConfigName());
        record.setCategory(baseInfo.getCategory());
        record.setSubCategory(baseInfo.getSubCategory());
        record.setTemplateType(baseInfo.getTemplateType());
        record.setDocumentType(baseInfo.getDocumentType());
        record.setDepartmentId(baseInfo.getDepartmentId());
        record.setOwnerRole(baseInfo.getOwnerRole());
        record.setDefaultPriority(baseInfo.getDefaultPriority());
        record.setConfigPayload(writePayload(payloadBody));
    }

    private String resolveConfigCode(ConfigWizardPayload payload) {
        String configCode = payload.getBaseInfo().getConfigCode();
        if (StringUtils.hasText(configCode)) {
            return configCode;
        }
        return "CFG_" + IdGenerator.nextId("");
    }

    private void validateDraft(ConfigWizardPayload payload) {
        if (!StringUtils.hasText(payload.getBaseInfo().getConfigName())) {
            throw new BusinessException("PARAM_400", "配置名称不能为空");
        }
        if (!StringUtils.hasText(payload.getBaseInfo().getDocumentType())) {
            throw new BusinessException("PARAM_400", "文档类型不能为空");
        }
        if (!StringUtils.hasText(payload.getBaseInfo().getDepartmentId())) {
            throw new BusinessException("PARAM_400", "所属部门不能为空");
        }
    }

    private void validatePublish(ConfigWizardPayload payload) {
        List<String> errors = collectValidationErrors(payload, true);
        if (!errors.isEmpty()) {
            throw new BusinessException("CONFIG_400", String.join("；", errors));
        }
    }

    private List<String> collectValidationErrors(ConfigWizardPayload payload, boolean strict) {
        List<String> errors = new ArrayList<>();
        validateDraft(payload);
        if (!StringUtils.hasText(payload.getParseConfig().getEngineCode())) {
            errors.add("解析引擎不能为空");
        }
        if (!StringUtils.hasText(payload.getStorageConfig().getTargetTable())) {
            errors.add("目标表编码不能为空");
        }
        if (!StringUtils.hasText(payload.getStorageConfig().getTargetTableName())) {
            errors.add("目标表名称不能为空");
        }
        if (!StringUtils.hasText(payload.getStorageConfig().getMappingProfileName())) {
            errors.add("映射方案名称不能为空");
        }
        if (payload.getResultTableColumns() == null || payload.getResultTableColumns().isEmpty()) {
            errors.add("至少需要维护一个目标表字段");
        }
        if (payload.getExtractFields() == null || payload.getExtractFields().isEmpty()) {
            errors.add("至少需要维护一个提取字段");
        }
        if (payload.getFieldMappings() == null || payload.getFieldMappings().isEmpty()) {
            errors.add("至少需要维护一条字段映射关系");
        }
        if (strict && (payload.getExtractStrategy() == null || !StringUtils.hasText(payload.getExtractStrategy().getDefaultStrategy()))) {
            errors.add("默认提取策略不能为空");
        }
        return errors;
    }

    private ConfigWizardPayload toPayload(Map<String, Object> payloadBody) {
        return objectMapper.convertValue(payloadBody, ConfigWizardPayload.class);
    }

    private String writePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException("JSON_400", "配置内容无法序列化");
        }
    }

    private Map<String, Object> readPayloadObject(ExtractConfigRecord record) {
        if (!StringUtils.hasText(record.getConfigPayload())) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(record.getConfigPayload(), new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new BusinessException("JSON_400", "配置内容无法解析");
        }
    }

    private ConfigWizardPayload readPayload(ExtractConfigRecord record) {
        if (!StringUtils.hasText(record.getConfigPayload())) {
            return new ConfigWizardPayload();
        }
        try {
            return objectMapper.readValue(record.getConfigPayload(), ConfigWizardPayload.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException("JSON_400", "配置内容无法解析");
        }
    }

    private ConfigSummaryResponse toSummary(ExtractConfigRecord record) {
        ConfigSummaryResponse response = new ConfigSummaryResponse();
        response.setId(record.getId());
        response.setConfigCode(record.getConfigCode());
        response.setConfigName(record.getConfigName());
        response.setCategory(record.getCategory());
        response.setSubCategory(record.getSubCategory());
        response.setTemplateType(record.getTemplateType());
        response.setDocumentType(record.getDocumentType());
        response.setDepartmentId(record.getDepartmentId());
        response.setOwnerRole(record.getOwnerRole());
        response.setDefaultPriority(record.getDefaultPriority());
        response.setStatus(record.getStatus());
        response.setVersion(record.getVersion());
        fillPayloadSummary(record, response);
        response.setCreatedBy(record.getCreatedBy());
        response.setUpdatedBy(record.getCreatedBy());
        response.setPublishedAt(record.getPublishedAt());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }

    private void fillPayloadSummary(ExtractConfigRecord record, ConfigSummaryResponse response) {
        if (!StringUtils.hasText(record.getConfigPayload())) {
            response.setParseEngine("-");
            response.setTargetTable("-");
            response.setMappingProfile("-");
            response.setConfidenceThreshold(0.9);
            return;
        }
        try {
            ConfigWizardPayload payload = objectMapper.readValue(record.getConfigPayload(), ConfigWizardPayload.class);
            response.setParseEngine(payload.getParseConfig() == null ? "-" : payload.getParseConfig().getEngineCode());
            response.setTargetTable(payload.getStorageConfig() == null ? "-" : payload.getStorageConfig().getTargetTable());
            response.setMappingProfile(payload.getStorageConfig() == null ? "-" : payload.getStorageConfig().getMappingProfileName());
            if (payload.getExtractStrategy() != null && payload.getExtractStrategy().getConfidenceThreshold() != null) {
                response.setConfidenceThreshold(payload.getExtractStrategy().getConfidenceThreshold().doubleValue());
            } else if (payload.getReviewPolicy() != null && payload.getReviewPolicy().getConfidenceThreshold() != null) {
                response.setConfidenceThreshold(payload.getReviewPolicy().getConfidenceThreshold().doubleValue());
            } else {
                response.setConfidenceThreshold(0.9);
            }
        } catch (JsonProcessingException e) {
            response.setParseEngine("-");
            response.setTargetTable("-");
            response.setMappingProfile("-");
            response.setConfidenceThreshold(0.9);
        }
    }

    private Map<String, Object> option(String value, String label) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("value", value);
        option.put("label", label);
        return option;
    }

    private Map<String, Object> category(String label, List<Map<String, Object>> children) {
        Map<String, Object> category = option(label, label);
        category.put("children", children);
        return category;
    }

    private Map<String, Object> subCategory(String label, List<String> templates) {
        Map<String, Object> subCategory = option(label, label);
        subCategory.put("templates", templates);
        return subCategory;
    }

    private Map<String, Object> table(String tableCode, String tableName, String comment) {
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("tableCode", tableCode);
        table.put("tableName", tableName);
        table.put("label", tableCode + " - " + tableName);
        table.put("comment", comment);
        return table;
    }

    private Map<String, Object> service(String serviceCode, String serviceName, String systemCode, String systemName,
                                        String serviceType, String purpose, String endpoint, String httpMethod,
                                        String responseSuccessRule, Integer retryCount, Boolean enabled) {
        Map<String, Object> service = new LinkedHashMap<>();
        service.put("serviceCode", serviceCode);
        service.put("serviceName", serviceName);
        service.put("systemCode", systemCode);
        service.put("systemName", systemName);
        service.put("serviceType", serviceType);
        service.put("purpose", purpose);
        service.put("endpoint", endpoint);
        service.put("httpMethod", httpMethod);
        service.put("responseSuccessRule", responseSuccessRule);
        service.put("retryCount", retryCount);
        service.put("enabled", enabled);
        service.put("label", systemName + " / " + serviceName + "（" + serviceType + "）");
        return service;
    }
}
