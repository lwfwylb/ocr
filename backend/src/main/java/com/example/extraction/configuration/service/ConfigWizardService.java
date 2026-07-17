package com.example.extraction.configuration.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.configuration.domain.ExtractConfigRecord;
import com.example.extraction.configuration.dto.ConfigDetailResponse;
import com.example.extraction.configuration.dto.ConfigOptionsResponse;
import com.example.extraction.configuration.dto.ConfigQueryRequest;
import com.example.extraction.configuration.dto.ConfigSummaryResponse;
import com.example.extraction.configuration.dto.ConfigWizardPayload;
import com.example.extraction.integration.service.DownstreamIntegrationService;
import com.example.extraction.mapper.ExtractConfigMapper;
import com.example.extraction.mapper.LlmModelConfigMapper;
import com.example.extraction.mapper.OcrEngineConfigMapper;
import com.example.extraction.model.domain.LlmModelConfigRecord;
import com.example.extraction.model.domain.OcrEngineConfigRecord;
import com.example.extraction.system.service.SystemDictionaryService;
import com.example.extraction.system.service.SystemAccessService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class ConfigWizardService {
    private final ExtractConfigMapper extractConfigMapper;
    private final OcrEngineConfigMapper ocrEngineConfigMapper;
    private final LlmModelConfigMapper llmModelConfigMapper;
    private final DownstreamIntegrationService downstreamIntegrationService;
    private final SystemDictionaryService dictionaryService;
    private final SystemAccessService systemAccessService;
    private final ResultTableService resultTableService;
    private final ObjectMapper objectMapper;

    public ConfigWizardService(ExtractConfigMapper extractConfigMapper,
                               OcrEngineConfigMapper ocrEngineConfigMapper,
                               LlmModelConfigMapper llmModelConfigMapper,
                               DownstreamIntegrationService downstreamIntegrationService,
                               SystemDictionaryService dictionaryService,
                               SystemAccessService systemAccessService,
                               ResultTableService resultTableService,
                               ObjectMapper objectMapper) {
        this.extractConfigMapper = extractConfigMapper;
        this.ocrEngineConfigMapper = ocrEngineConfigMapper;
        this.llmModelConfigMapper = llmModelConfigMapper;
        this.downstreamIntegrationService = downstreamIntegrationService;
        this.dictionaryService = dictionaryService;
        this.systemAccessService = systemAccessService;
        this.resultTableService = resultTableService;
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

    public ConfigDetailResponse getEffectiveByConfigName(String configName) {
        if (!StringUtils.hasText(configName)) {
            throw new BusinessException("PARAM_400", "配置名称不能为空");
        }
        List<ExtractConfigRecord> records = extractConfigMapper.selectPublishedByConfigName(configName);
        if (records.isEmpty()) {
            throw new BusinessException("CONFIG_404", "未找到已发布的生效配置");
        }
        if (records.size() > 1) {
            throw new BusinessException("CONFIG_409", "存在多个同名已发布配置，请先处理配置名称唯一性");
        }
        ExtractConfigRecord record = records.get(0);
        ConfigDetailResponse response = new ConfigDetailResponse();
        response.setSummary(toSummary(record));
        response.setPayload(readPayloadObject(record));
        return response;
    }

    public List<ConfigSummaryResponse> listVersions(String id) {
        ExtractConfigRecord record = requireRecord(id);
        return extractConfigMapper.selectByConfigCode(record.getConfigCode()).stream().map(this::toSummary).toList();
    }

    @Transactional
    public ConfigDetailResponse createDraft(Map<String, Object> payloadBody) {
        ConfigWizardPayload payload = toPayload(payloadBody);
        validateDraft(payload);
        ExtractConfigRecord record = new ExtractConfigRecord();
        record.setId(IdGenerator.nextId("CFG"));
        record.setConfigCode(resolveConfigCode(payload));
        validateConfigNameUnique(payload.getBaseInfo().getConfigName(), record.getConfigCode());
        fillRecord(record, payload, payloadBody);
        record.setStatus("DRAFT");
        record.setVersion(1);
        record.setCreatedBy("system");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        resultTableService.syncFromWizard(payload, record.getCreatedBy());
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
        validateConfigNameUnique(payload.getBaseInfo().getConfigName(), existing.getConfigCode());
        fillRecord(existing, payload, payloadBody);
        existing.setUpdatedAt(LocalDateTime.now());
        resultTableService.syncFromWizard(payload, existing.getCreatedBy());
        int updated = extractConfigMapper.updateDraft(existing);
        if (updated == 0) {
            throw new BusinessException("CONFIG_409", "配置状态已变化，请刷新后重试");
        }
        return getDetail(id);
    }

    @Transactional
    public ConfigDetailResponse copy(String id) {
        ExtractConfigRecord source = requireRecord(id);
        List<ExtractConfigRecord> editableVersions = extractConfigMapper.selectEditableVersions(source.getConfigCode());
        if (!editableVersions.isEmpty()) {
            ExtractConfigRecord editable = editableVersions.get(0);
            throw new BusinessException(
                    "CONFIG_409",
                    "当前配置已存在 V" + editable.getVersion() + " " + statusLabel(editable.getStatus()) + "版本，请继续编辑该版本，或删除草稿后再复制新版本"
            );
        }
        ExtractConfigRecord copied = new ExtractConfigRecord();
        Integer maxVersion = extractConfigMapper.selectMaxVersion(source.getConfigCode());
        int nextVersion = maxVersion == null ? 1 : maxVersion + 1;
        copied.setId(IdGenerator.nextId("CFG"));
        copied.setConfigCode(source.getConfigCode());
        copied.setConfigName(source.getConfigName());
        copied.setCategory(source.getCategory());
        copied.setSubCategory(source.getSubCategory());
        copied.setTemplateType(source.getTemplateType());
        copied.setDocumentType(source.getDocumentType());
        copied.setDepartmentId(source.getDepartmentId());
        copied.setOwnerRole(source.getOwnerRole());
        copied.setDefaultPriority(source.getDefaultPriority());
        copied.setStatus("DRAFT");
        copied.setVersion(nextVersion);
        copied.setConfigPayload(source.getConfigPayload());
        copied.setCreatedBy("system");
        copied.setCreatedAt(LocalDateTime.now());
        copied.setUpdatedAt(copied.getCreatedAt());
        extractConfigMapper.insert(copied);
        return getDetail(copied.getId());
    }

    @Transactional
    public ConfigDetailResponse publish(String id) {
        ExtractConfigRecord record = requireRecord(id);
        ConfigWizardPayload payload = readPayload(record);
        validatePublish(payload);
        resultTableService.syncFromWizard(payload, record.getCreatedBy());
        extractConfigMapper.disablePublishedByConfigCode(record.getConfigCode(), id);
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

    @Transactional
    public void deleteDraft(String id) {
        ExtractConfigRecord record = requireRecord(id);
        if (!"DRAFT".equals(record.getStatus())) {
            throw new BusinessException("CONFIG_409", "仅草稿版本允许删除");
        }
        int updated = extractConfigMapper.updateStatus(id, "DELETED");
        if (updated == 0) {
            throw new BusinessException("CONFIG_409", "草稿状态已变化，请刷新后重试");
        }
    }

    public Map<String, Object> validate(String id) {
        ConfigWizardPayload payload = readPayload(requireRecord(id));
        List<Map<String, Object>> sections = buildValidationSections(payload);
        List<String> errors = flattenValidationMessages(sections, "ERROR");
        List<String> warnings = flattenValidationMessages(sections, "WARN");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", errors.isEmpty());
        result.put("errors", errors);
        result.put("warnings", warnings);
        result.put("sections", sections);
        result.put("ddlPreview", buildDdlPreview(payload));
        result.put("checkedAt", LocalDateTime.now());
        result.put("message", errors.isEmpty() ? "配置验证通过，可发布配置" : "配置存在阻断项，请补充后再发布");
        return result;
    }

    public ConfigOptionsResponse options() {
        ConfigOptionsResponse response = new ConfigOptionsResponse();
        response.setDepartments(dictionaryService.departments());
        response.setRoles(systemAccessService.roles(null, "ENABLED").stream().map(role -> {
            Map<String, Object> option = option(role.getRoleCode(), role.getRoleName());
            option.put("roleId", role.getId());
            option.put("roleCode", role.getRoleCode());
            option.put("roleName", role.getRoleName());
            option.put("status", role.getStatus());
            return option;
        }).toList());
        response.setCategories(dictionaryService.businessCategoryTree());
        response.setDocumentTypes(dictionaryService.options("DOCUMENT_TYPE"));
        response.setOcrEngines(ocrEngineConfigMapper.selectEnabled().stream().map(ocrEngine -> {
            Map<String, Object> option = option(ocrEngine.getEngineCode(), ocrEngine.getEngineName());
            option.put("engineCode", ocrEngine.getEngineCode());
            option.put("engineName", ocrEngine.getEngineName());
            option.put("engineType", ocrEngine.getEngineType());
            option.put("provider", ocrEngine.getProvider());
            option.put("defaultEngine", "1".equals(ocrEngine.getDefaultEngine()));
            return option;
        }).toList());
        response.setResultTables(resultTableService.listOptions(null));
        response.setDownstreamServices(downstreamIntegrationService.serviceOptions());
        return response;
    }

    private ExtractConfigRecord requireRecord(String id) {
        ExtractConfigRecord record = extractConfigMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("CONFIG_404", "配置不存在");
        }
        return record;
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "TESTING" -> "验证中";
            case "PUBLISHED" -> "已发布";
            case "DISABLED" -> "已停用";
            default -> status;
        };
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
        if (!StringUtils.hasText(payload.getBaseInfo().getDepartmentId())) {
            throw new BusinessException("PARAM_400", "所属部门不能为空");
        }
        if (!StringUtils.hasText(payload.getBaseInfo().getOwnerRole())) {
            throw new BusinessException("PARAM_400", "配置负责人角色不能为空");
        }
        if (payload.getVisibleRoles() == null || payload.getVisibleRoles().isEmpty()) {
            throw new BusinessException("PARAM_400", "可见角色至少选择一个");
        }
    }

    private void validateConfigNameUnique(String configName, String currentConfigCode) {
        List<ExtractConfigRecord> records = extractConfigMapper.selectByConfigName(configName);
        boolean occupiedByOtherConfig = records.stream()
                .anyMatch(record -> !currentConfigCode.equals(record.getConfigCode()));
        if (occupiedByOtherConfig) {
            throw new BusinessException("CONFIG_409", "配置名称已存在，请使用唯一的配置名称");
        }
    }

    private void validatePublish(ConfigWizardPayload payload) {
        List<String> errors = collectValidationErrors(payload, true);
        if (!errors.isEmpty()) {
            throw new BusinessException("CONFIG_400", String.join("；", errors));
        }
    }

    private List<String> collectValidationErrors(ConfigWizardPayload payload, boolean strict) {
        return flattenValidationMessages(buildValidationSections(payload), "ERROR");
    }

    private List<Map<String, Object>> buildValidationSections(ConfigWizardPayload payload) {
        List<Map<String, Object>> sections = new ArrayList<>();
        sections.add(section("BASIC", "配置完整性验证", validateBasicConfig(payload)));
        sections.add(section("DDL", "DDL 预检查", validateStorageConfig(payload)));
        sections.add(section("TRADITIONAL_RULE", "传统规则验证", validateRegexRules(payload)));
        sections.add(section("RULE", "加工校验规则验证", validateTransformAndValidationRules(payload)));
        sections.add(section("PUSH", "下游推送配置验证", validatePushRules(payload)));
        return sections;
    }

    private List<Map<String, Object>> validateBasicConfig(ConfigWizardPayload payload) {
        List<Map<String, Object>> issues = new ArrayList<>();
        try {
            validateDraft(payload);
        } catch (BusinessException e) {
            issues.add(issue("ERROR", e.getMessage()));
        }
        if (!StringUtils.hasText(payload.getParseConfig().getEngineCode())) {
            issues.add(issue("ERROR", "解析引擎不能为空"));
        }
        if (StringUtils.hasText(payload.getParseConfig().getEngineCode())) {
            OcrEngineConfigRecord ocrEngine = ocrEngineConfigMapper.selectByEngineCode(payload.getParseConfig().getEngineCode());
            if (ocrEngine == null) {
                issues.add(issue("ERROR", "OCR engine does not exist: " + payload.getParseConfig().getEngineCode()));
            } else if (!"ENABLED".equals(ocrEngine.getStatus())) {
                issues.add(issue("ERROR", "OCR engine is disabled: " + payload.getParseConfig().getEngineCode()));
            }
        }
        if (!StringUtils.hasText(payload.getParseConfig().getParseMode())) {
            issues.add(issue("ERROR", "解析模式不能为空"));
        }
        if ("PAGE_BATCH_MERGE".equals(payload.getParseConfig().getParseMode())
                && (payload.getParseConfig().getPageBatchSize() == null || payload.getParseConfig().getPageBatchSize() <= 0)) {
            issues.add(issue("ERROR", "每批页数必须是大于 0 的整数"));
        }
        if (payload.getExtractStrategy() == null || !StringUtils.hasText(payload.getExtractStrategy().getDefaultStrategy())) {
            issues.add(issue("ERROR", "默认提取策略不能为空"));
        }
        if (payload.getExtractStrategy() != null
                && Boolean.TRUE.equals(payload.getExtractStrategy().getAiEnabled())
                && !StringUtils.hasText(payload.getExtractStrategy().getLlmModelCode())) {
            issues.add(issue("ERROR", "启用 AI 提取时必须选择 LLM 模型"));
        }
        if (payload.getExtractStrategy() != null
                && Boolean.TRUE.equals(payload.getExtractStrategy().getAiEnabled())
                && StringUtils.hasText(payload.getExtractStrategy().getLlmModelCode())) {
            LlmModelConfigRecord llmModel = llmModelConfigMapper.selectByModelCode(payload.getExtractStrategy().getLlmModelCode());
            if (llmModel == null) {
                issues.add(issue("ERROR", "LLM model does not exist: " + payload.getExtractStrategy().getLlmModelCode()));
            } else if (!"ENABLED".equals(llmModel.getStatus())) {
                issues.add(issue("ERROR", "LLM model is disabled: " + payload.getExtractStrategy().getLlmModelCode()));
            }
        }
        if (payload.getExtractStrategy() != null && payload.getExtractStrategy().getConfidenceThreshold() != null
                && (payload.getExtractStrategy().getConfidenceThreshold().doubleValue() < 0
                || payload.getExtractStrategy().getConfidenceThreshold().doubleValue() > 1)) {
            issues.add(issue("ERROR", "置信度阈值必须在 0 到 1 之间"));
        }
        if (issues.isEmpty()) {
            issues.add(issue("INFO", "基础信息、解析配置和提取策略完整"));
        }
        return issues;
    }

    private List<Map<String, Object>> validateStorageConfig(ConfigWizardPayload payload) {
        List<Map<String, Object>> issues = new ArrayList<>();
        ConfigWizardPayload.StorageConfig storageConfig = payload.getStorageConfig() == null ? new ConfigWizardPayload.StorageConfig() : payload.getStorageConfig();
        if (!storageEnabled(payload)) {
            validateExtractFieldsOnly(payload, issues);
            if (issues.isEmpty()) {
                issues.add(issue("INFO", "未启用结果落库，仅校验提取字段配置；不会生成 DDL，也不会同步结果表元数据"));
            }
            return issues;
        }
        if (!StringUtils.hasText(storageConfig.getStorageMode())) {
            issues.add(issue("ERROR", "落库模式不能为空"));
        }
        if (!StringUtils.hasText(storageConfig.getTargetTable())) {
            issues.add(issue("ERROR", "目标表编码不能为空"));
        } else if (!storageConfig.getTargetTable().matches("^[a-z][a-z0-9_]*$")) {
            issues.add(issue("ERROR", "目标表编码只能包含小写字母、数字、下划线，并以小写字母开头"));
        }
        if (!StringUtils.hasText(storageConfig.getTargetTableName())) {
            issues.add(issue("ERROR", "目标表名称不能为空"));
        }
        if (!StringUtils.hasText(storageConfig.getMappingProfileName())) {
            issues.add(issue("ERROR", "映射方案名称不能为空"));
        }
        List<ConfigWizardPayload.ResultTableColumn> columns = payload.getResultTableColumns();
        if (columns == null || columns.isEmpty()) {
            issues.add(issue("ERROR", "至少需要维护一个目标表字段"));
        }
        Set<String> columnNames = new HashSet<>();
        if (columns != null) {
            for (ConfigWizardPayload.ResultTableColumn column : columns) {
                if (!StringUtils.hasText(column.getColumnName())) {
                    issues.add(issue("ERROR", "目标表字段名不能为空"));
                    continue;
                }
                if (!columnNames.add(column.getColumnName())) {
                    issues.add(issue("ERROR", "目标表字段重复：" + column.getColumnName()));
                }
                validateColumnType(column, issues);
            }
        }
        if (payload.getExtractFields() == null || payload.getExtractFields().isEmpty()) {
            issues.add(issue("ERROR", "至少需要维护一个提取字段"));
        }
        if (payload.getFieldMappings() == null || payload.getFieldMappings().isEmpty()) {
            issues.add(issue("ERROR", "至少需要维护一条字段映射关系"));
        } else {
            for (ConfigWizardPayload.FieldMapping mapping : payload.getFieldMappings()) {
                if (!StringUtils.hasText(mapping.getExtractFieldCode())) {
                    issues.add(issue("ERROR", "字段映射中的提取字段不能为空"));
                }
                if (!StringUtils.hasText(mapping.getTargetColumn())) {
                    issues.add(issue("ERROR", "字段映射中的目标字段不能为空"));
                } else if (!columnNames.contains(mapping.getTargetColumn())) {
                    issues.add(issue("ERROR", "字段映射目标字段不存在：" + mapping.getTargetColumn()));
                }
            }
        }
        if (payload.getUniqueConstraints() != null) {
            Set<String> constraintNames = new HashSet<>();
            for (ConfigWizardPayload.UniqueConstraint constraint : payload.getUniqueConstraints()) {
                if (!Boolean.TRUE.equals(constraint.getEnabled())) {
                    continue;
                }
                if (!StringUtils.hasText(constraint.getConstraintName())) {
                    issues.add(issue("ERROR", "启用的唯一约束名称不能为空"));
                } else if (!constraintNames.add(constraint.getConstraintName())) {
                    issues.add(issue("ERROR", "唯一约束名称重复：" + constraint.getConstraintName()));
                }
                if (constraint.getUniqueColumns() == null || constraint.getUniqueColumns().isEmpty()) {
                    issues.add(issue("ERROR", "唯一约束至少需要选择一个字段：" + constraint.getConstraintName()));
                } else {
                    for (String column : constraint.getUniqueColumns()) {
                        if (!columnNames.contains(column)) {
                            issues.add(issue("ERROR", "唯一约束字段不存在：" + column));
                        }
                    }
                }
            }
        }
        if ("CREATE".equals(storageConfig.getStorageMode())) {
            issues.add(issue("WARN", "验证阶段只预览 DDL，不会真正创建物理结果表；保存草稿时会登记结果表元数据台账"));
        } else if ("REUSE".equals(storageConfig.getStorageMode()) && StringUtils.hasText(storageConfig.getTargetTable())) {
            try {
                resultTableService.validateReuseColumns(storageConfig.getTargetTable(), columns, payload.getExtractFields(), payload.getUniqueConstraints());
            } catch (BusinessException e) {
                issues.add(issue("ERROR", e.getMessage()));
            }
        }
        if (issues.isEmpty()) {
            issues.add(issue("INFO", "落库配置、字段映射和唯一约束检查通过"));
        }
        return issues;
    }

    private void validateExtractFieldsOnly(ConfigWizardPayload payload, List<Map<String, Object>> issues) {
        if (payload.getExtractFields() == null || payload.getExtractFields().isEmpty()) {
            issues.add(issue("ERROR", "至少需要维护一个提取字段"));
            return;
        }
        Set<String> fieldCodes = new HashSet<>();
        for (ConfigWizardPayload.ExtractField field : payload.getExtractFields()) {
            if (!StringUtils.hasText(field.getFieldCode())) {
                issues.add(issue("ERROR", "提取字段编码不能为空"));
                continue;
            }
            if (!fieldCodes.add(field.getFieldCode())) {
                issues.add(issue("ERROR", "提取字段编码重复：" + field.getFieldCode()));
            }
            if (!StringUtils.hasText(field.getFieldName())) {
                issues.add(issue("ERROR", "提取字段名称不能为空：" + field.getFieldCode()));
            }
        }
    }

    private void validateColumnType(ConfigWizardPayload.ResultTableColumn column, List<Map<String, Object>> issues) {
        if (!StringUtils.hasText(column.getDbType())) {
            issues.add(issue("ERROR", "目标字段 " + column.getColumnName() + " 数据库类型不能为空"));
            return;
        }
        if (List.of("varchar", "char").contains(column.getDbType())
                && (column.getLength() == null || column.getLength() <= 0)) {
            issues.add(issue("ERROR", "字符型字段长度必须大于 0：" + column.getColumnName()));
        }
        if (List.of("decimal", "number").contains(column.getDbType())) {
            if (column.getPrecision() == null || column.getPrecision() <= 0) {
                issues.add(issue("ERROR", "数值型字段精度必须大于 0：" + column.getColumnName()));
            }
            if (column.getScale() == null || column.getScale() < 0) {
                issues.add(issue("ERROR", "数值型字段小数位不能小于 0：" + column.getColumnName()));
            }
            if (column.getPrecision() != null && column.getScale() != null && column.getScale() > column.getPrecision()) {
                issues.add(issue("ERROR", "数值型字段小数位不能大于精度：" + column.getColumnName()));
            }
        }
    }

    private List<Map<String, Object>> validateRegexRules(ConfigWizardPayload payload) {
        List<Map<String, Object>> issues = new ArrayList<>();
        if (payload.getRegexRules() == null || payload.getRegexRules().isEmpty()) {
            issues.add(issue("WARN", "未配置字段级传统规则，仅依赖 AI 提取"));
            return issues;
        }
        for (ConfigWizardPayload.RegexRule rule : payload.getRegexRules()) {
            if (!Boolean.TRUE.equals(rule.getEnabled())) {
                continue;
            }
            if (!StringUtils.hasText(rule.getFieldCode())) {
                issues.add(issue("ERROR", "传统规则字段编码不能为空"));
            }
            String ruleType = StringUtils.hasText(rule.getRuleType()) ? rule.getRuleType() : "REGEX";
            if (!"REGEX".equals(ruleType)) {
                issues.add(issue("INFO", "传统规则类型待接入执行器：" + rule.getFieldCode() + "，" + ruleType));
                continue;
            }
            if (!StringUtils.hasText(rule.getRegexPattern())) {
                issues.add(issue("ERROR", "正则表达式不能为空：" + rule.getFieldCode()));
                continue;
            }
            try {
                Pattern.compile(rule.getRegexPattern(), regexFlags(rule.getRegexFlags()));
            } catch (PatternSyntaxException e) {
                issues.add(issue("ERROR", "正则表达式语法错误：" + rule.getFieldCode() + "，" + e.getDescription()));
            }
            if (rule.getRegexGroup() != null && rule.getRegexGroup() < 0) {
                issues.add(issue("ERROR", "正则分组不能小于 0：" + rule.getFieldCode()));
            }
        }
        if (issues.isEmpty()) {
            issues.add(issue("INFO", "已启用文本正则规则语法检查通过"));
        }
        return issues;
    }

    private int regexFlags(String flags) {
        int value = 0;
        if (!StringUtils.hasText(flags)) {
            return value;
        }
        if (flags.contains("i")) {
            value |= Pattern.CASE_INSENSITIVE;
        }
        if (flags.contains("m")) {
            value |= Pattern.MULTILINE;
        }
        if (flags.contains("s")) {
            value |= Pattern.DOTALL;
        }
        return value;
    }

    private List<Map<String, Object>> validateTransformAndValidationRules(ConfigWizardPayload payload) {
        List<Map<String, Object>> issues = new ArrayList<>();
        boolean transformEnabled = transformProcessingEnabled(payload);
        boolean validationEnabled = validationProcessingEnabled(payload);
        if (!transformEnabled && !validationEnabled) {
            issues.add(issue("INFO", "未启用加工规则和校验规则，任务执行将跳过该环节"));
            return issues;
        }
        Set<String> fieldCodes = collectFieldCodes(payload);
        if (transformEnabled) {
            if (!hasEnabledTransformRules(payload)) {
                issues.add(issue("WARN", "已启用加工规则，但尚未新增启用的加工规则"));
            }
            if (payload.getTransformRules() != null) {
                for (ConfigWizardPayload.TransformRule rule : payload.getTransformRules()) {
                    if (!Boolean.TRUE.equals(rule.getEnabled())) {
                        continue;
                    }
                    if (!StringUtils.hasText(rule.getRuleName())) {
                        issues.add(issue("ERROR", "启用的加工规则名称不能为空"));
                    }
                    if (!StringUtils.hasText(rule.getRuleType())) {
                        issues.add(issue("ERROR", "加工规则类型不能为空：" + rule.getRuleName()));
                    }
                    if (StringUtils.hasText(rule.getInputField()) && !fieldCodes.contains(rule.getInputField())) {
                        issues.add(issue("WARN", "加工规则输入字段未在提取字段或目标字段中找到：" + rule.getInputField()));
                    }
                    if (!"OVERWRITE_INPUT".equals(rule.getOutputMode()) && !StringUtils.hasText(rule.getOutputField())) {
                        issues.add(issue("WARN", "加工规则未维护输出字段：" + rule.getRuleName()));
                    }
                }
            }
        } else {
            issues.add(issue("INFO", "未启用加工规则，已跳过加工规则检查"));
        }
        if (validationEnabled) {
            if (!hasEnabledValidationRules(payload)) {
                issues.add(issue("WARN", "已启用校验规则，但尚未新增启用的校验规则"));
            }
            if (payload.getValidationRules() != null) {
                for (ConfigWizardPayload.ValidationRule rule : payload.getValidationRules()) {
                    if (!Boolean.TRUE.equals(rule.getEnabled())) {
                        continue;
                    }
                    if (!StringUtils.hasText(rule.getRuleName())) {
                        issues.add(issue("ERROR", "启用的校验规则名称不能为空"));
                    }
                    if (!StringUtils.hasText(rule.getRuleType())) {
                        issues.add(issue("ERROR", "校验规则类型不能为空：" + rule.getRuleName()));
                    }
                    if (StringUtils.hasText(rule.getFieldCode()) && !fieldCodes.contains(rule.getFieldCode())) {
                        issues.add(issue("ERROR", "校验规则字段不存在：" + rule.getFieldCode()));
                    }
                    if (!StringUtils.hasText(rule.getExpression())) {
                        issues.add(issue("WARN", "校验规则表达式为空：" + rule.getRuleName()));
                    }
                }
            }
        } else {
            issues.add(issue("INFO", "未启用校验规则，已跳过校验规则检查"));
        }
        if (issues.isEmpty()) {
            issues.add(issue("INFO", "加工规则和校验规则基础检查通过"));
        }
        return issues;
    }

    private boolean transformProcessingEnabled(ConfigWizardPayload payload) {
        if (payload == null) {
            return false;
        }
        if (payload.getProcessConfig() != null && payload.getProcessConfig().getTransformEnabled() != null) {
            return Boolean.TRUE.equals(payload.getProcessConfig().getTransformEnabled());
        }
        return hasEnabledTransformRules(payload);
    }

    private boolean validationProcessingEnabled(ConfigWizardPayload payload) {
        if (payload == null) {
            return false;
        }
        if (payload.getProcessConfig() != null && payload.getProcessConfig().getValidationEnabled() != null) {
            return Boolean.TRUE.equals(payload.getProcessConfig().getValidationEnabled());
        }
        return hasEnabledValidationRules(payload);
    }

    private boolean hasEnabledTransformRules(ConfigWizardPayload payload) {
        return payload != null && payload.getTransformRules() != null
                && payload.getTransformRules().stream().anyMatch(rule -> rule != null && Boolean.TRUE.equals(rule.getEnabled()));
    }

    private boolean hasEnabledValidationRules(ConfigWizardPayload payload) {
        return payload != null && payload.getValidationRules() != null
                && payload.getValidationRules().stream().anyMatch(rule -> rule != null && Boolean.TRUE.equals(rule.getEnabled()));
    }

    private Set<String> collectFieldCodes(ConfigWizardPayload payload) {
        Set<String> fieldCodes = new HashSet<>();
        if (payload.getExtractFields() != null) {
            payload.getExtractFields().forEach(field -> {
                if (StringUtils.hasText(field.getFieldCode())) {
                    fieldCodes.add(field.getFieldCode());
                }
                if (StringUtils.hasText(field.getTargetColumn())) {
                    fieldCodes.add(field.getTargetColumn());
                }
            });
        }
        if (payload.getResultTableColumns() != null) {
            payload.getResultTableColumns().forEach(column -> {
                if (StringUtils.hasText(column.getColumnName())) {
                    fieldCodes.add(column.getColumnName());
                }
            });
        }
        return fieldCodes;
    }

    private List<Map<String, Object>> validatePushRules(ConfigWizardPayload payload) {
        List<Map<String, Object>> issues = new ArrayList<>();
        if (payload.getPushRules() == null || payload.getPushRules().isEmpty()) {
            issues.add(issue("INFO", "未启用下游推送规则"));
            return issues;
        }
        boolean storageDisabled = !storageEnabled(payload);
        Map<String, Map<String, Object>> downstreamServices = downstreamServicesByCode();
        for (ConfigWizardPayload.PushRule rule : payload.getPushRules()) {
            if (!Boolean.TRUE.equals(rule.getPushEnabled())) {
                continue;
            }
            if (!StringUtils.hasText(rule.getServiceCode())) {
                issues.add(issue("ERROR", "启用的推送规则必须选择目标接口服务"));
            }
            if (StringUtils.hasText(rule.getServiceCode())) {
                Map<String, Object> service = downstreamServices.get(rule.getServiceCode());
                if (service == null) {
                    issues.add(issue("ERROR", "Downstream service does not exist: " + rule.getServiceCode()));
                } else if (!Boolean.TRUE.equals(service.get("enabled"))) {
                    issues.add(issue("ERROR", "Downstream service is disabled: " + rule.getServiceCode()));
                }
            }
            if (!StringUtils.hasText(rule.getPushTrigger())) {
                issues.add(issue("ERROR", "启用的推送规则必须维护触发时机：" + rule.getServiceCode()));
            }
            if (storageDisabled && "STORED".equals(rule.getPushTrigger())) {
                issues.add(issue("ERROR", "未启用结果落库时，下游推送触发时机不能选择落库成功后"));
            }
            if (!StringUtils.hasText(rule.getPushMode())) {
                issues.add(issue("ERROR", "启用的推送规则必须维护推送模式：" + rule.getServiceCode()));
            }
        }
        if (issues.isEmpty()) {
            issues.add(issue("INFO", "下游推送配置基础检查通过"));
        }
        return issues;
    }

    private Map<String, Map<String, Object>> downstreamServicesByCode() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        ConfigOptionsResponse configOptions = options();
        if (configOptions.getDownstreamServices() == null) {
            return result;
        }
        for (Map<String, Object> service : configOptions.getDownstreamServices()) {
            Object serviceCode = service.get("serviceCode");
            if (serviceCode != null) {
                result.put(String.valueOf(serviceCode), service);
            }
        }
        return result;
    }

    private Map<String, Object> section(String code, String title, List<Map<String, Object>> items) {
        Map<String, Object> section = new LinkedHashMap<>();
        boolean failed = items.stream().anyMatch(item -> "ERROR".equals(item.get("level")));
        boolean warning = items.stream().anyMatch(item -> "WARN".equals(item.get("level")));
        section.put("code", code);
        section.put("title", title);
        section.put("status", failed ? "FAILED" : warning ? "WARNING" : "PASSED");
        section.put("items", items);
        return section;
    }

    private Map<String, Object> issue(String level, String message) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("level", level);
        issue.put("message", message);
        return issue;
    }

    @SuppressWarnings("unchecked")
    private List<String> flattenValidationMessages(List<Map<String, Object>> sections, String level) {
        List<String> messages = new ArrayList<>();
        for (Map<String, Object> section : sections) {
            Object itemsValue = section.get("items");
            if (!(itemsValue instanceof List<?> items)) {
                continue;
            }
            for (Object itemValue : items) {
                if (!(itemValue instanceof Map<?, ?> item)) {
                    continue;
                }
                if (level.equals(item.get("level"))) {
                    messages.add(String.valueOf(item.get("message")));
                }
            }
        }
        return messages;
    }

    private String buildDdlPreview(ConfigWizardPayload payload) {
        ConfigWizardPayload.StorageConfig storageConfig = payload.getStorageConfig() == null ? new ConfigWizardPayload.StorageConfig() : payload.getStorageConfig();
        if (!storageEnabled(payload)) {
            return "-- 当前配置未启用结果落库\n-- 不生成 DDL，不写入结果表，仅保留提取、加工、复核和下游推送能力。";
        }
        if (!"CREATE".equals(storageConfig.getStorageMode())) {
            return "-- 复用已有表: " + nullToDash(storageConfig.getTargetTable()) + "\n-- 验证阶段不执行 DDL，仅校验目标字段映射。";
        }
        StringBuilder ddl = new StringBuilder();
        ddl.append("-- 验证阶段仅预览 DDL，不会真正建表\n");
        ddl.append("CREATE TABLE ").append(nullToDash(storageConfig.getTargetTable())).append(" (\n");
        List<String> lines = new ArrayList<>();
        if (payload.getResultTableColumns() != null) {
            for (ConfigWizardPayload.ResultTableColumn column : payload.getResultTableColumns()) {
                lines.add("  " + column.getColumnName() + " " + formatDbColumnType(column) + (Boolean.TRUE.equals(column.getRequired()) ? " NOT NULL" : ""));
            }
        }
        if (payload.getUniqueConstraints() != null) {
            for (ConfigWizardPayload.UniqueConstraint constraint : payload.getUniqueConstraints()) {
                if (Boolean.TRUE.equals(constraint.getEnabled()) && Boolean.TRUE.equals(constraint.getGenerateDbIndex())
                        && constraint.getUniqueColumns() != null && !constraint.getUniqueColumns().isEmpty()) {
                    lines.add("  UNIQUE KEY " + constraint.getConstraintName() + " (" + String.join(", ", constraint.getUniqueColumns()) + ")");
                }
            }
        }
        ddl.append(String.join(",\n", lines));
        ddl.append("\n);");
        return ddl.toString();
    }

    private String formatDbColumnType(ConfigWizardPayload.ResultTableColumn column) {
        if (List.of("varchar", "char").contains(column.getDbType())) {
            return column.getDbType() + "(" + (column.getLength() == null ? 100 : column.getLength()) + ")";
        }
        if (List.of("decimal", "number").contains(column.getDbType())) {
            return column.getDbType() + "(" + (column.getPrecision() == null ? 18 : column.getPrecision()) + "," + (column.getScale() == null ? 2 : column.getScale()) + ")";
        }
        return StringUtils.hasText(column.getDbType()) ? column.getDbType() : "varchar(100)";
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private boolean storageEnabled(ConfigWizardPayload payload) {
        return payload == null || payload.getStorageConfig() == null || !Boolean.FALSE.equals(payload.getStorageConfig().getStorageEnabled());
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
        response.setCurrentEffective("PUBLISHED".equals(record.getStatus()));
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
            response.setTargetTable(storageEnabled(payload) && payload.getStorageConfig() != null ? payload.getStorageConfig().getTargetTable() : "未启用落库");
            response.setMappingProfile(storageEnabled(payload) && payload.getStorageConfig() != null ? payload.getStorageConfig().getMappingProfileName() : "-");
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
