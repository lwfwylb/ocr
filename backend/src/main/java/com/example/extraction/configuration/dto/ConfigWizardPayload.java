package com.example.extraction.configuration.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfigWizardPayload {
    @Valid
    private BaseInfo baseInfo = new BaseInfo();
    @Valid
    private ParseConfig parseConfig = new ParseConfig();
    private List<PreprocessStep> preprocessSteps = new ArrayList<>();
    @Valid
    private StorageConfig storageConfig = new StorageConfig();
    private List<ResultTableColumn> resultTableColumns = new ArrayList<>();
    private List<UniqueConstraint> uniqueConstraints = new ArrayList<>();
    private List<ExtractField> extractFields = new ArrayList<>();
    private List<FieldMapping> fieldMappings = new ArrayList<>();
    @Valid
    private ExtractStrategy extractStrategy = new ExtractStrategy();
    private List<RegexRule> regexRules = new ArrayList<>();
    private List<TransformRule> transformRules = new ArrayList<>();
    private List<ValidationRule> validationRules = new ArrayList<>();
    @Valid
    private ReviewPolicy reviewPolicy = new ReviewPolicy();
    private List<PushRule> pushRules = new ArrayList<>();
    private List<String> visibleRoles = new ArrayList<>();
    private Map<String, Object> extension;

    public BaseInfo getBaseInfo() {
        return baseInfo;
    }

    public void setBaseInfo(BaseInfo baseInfo) {
        this.baseInfo = baseInfo;
    }

    public ParseConfig getParseConfig() {
        return parseConfig;
    }

    public void setParseConfig(ParseConfig parseConfig) {
        this.parseConfig = parseConfig;
    }

    public List<PreprocessStep> getPreprocessSteps() {
        return preprocessSteps;
    }

    public void setPreprocessSteps(List<PreprocessStep> preprocessSteps) {
        this.preprocessSteps = preprocessSteps;
    }

    public StorageConfig getStorageConfig() {
        return storageConfig;
    }

    public void setStorageConfig(StorageConfig storageConfig) {
        this.storageConfig = storageConfig;
    }

    public List<ResultTableColumn> getResultTableColumns() {
        return resultTableColumns;
    }

    public void setResultTableColumns(List<ResultTableColumn> resultTableColumns) {
        this.resultTableColumns = resultTableColumns;
    }

    public List<UniqueConstraint> getUniqueConstraints() {
        return uniqueConstraints;
    }

    public void setUniqueConstraints(List<UniqueConstraint> uniqueConstraints) {
        this.uniqueConstraints = uniqueConstraints;
    }

    public List<ExtractField> getExtractFields() {
        return extractFields;
    }

    public void setExtractFields(List<ExtractField> extractFields) {
        this.extractFields = extractFields;
    }

    public List<FieldMapping> getFieldMappings() {
        return fieldMappings;
    }

    public void setFieldMappings(List<FieldMapping> fieldMappings) {
        this.fieldMappings = fieldMappings;
    }

    public ExtractStrategy getExtractStrategy() {
        return extractStrategy;
    }

    public void setExtractStrategy(ExtractStrategy extractStrategy) {
        this.extractStrategy = extractStrategy;
    }

    public List<RegexRule> getRegexRules() {
        return regexRules;
    }

    public void setRegexRules(List<RegexRule> regexRules) {
        this.regexRules = regexRules;
    }

    public List<TransformRule> getTransformRules() {
        return transformRules;
    }

    public void setTransformRules(List<TransformRule> transformRules) {
        this.transformRules = transformRules;
    }

    public List<ValidationRule> getValidationRules() {
        return validationRules;
    }

    public void setValidationRules(List<ValidationRule> validationRules) {
        this.validationRules = validationRules;
    }

    public ReviewPolicy getReviewPolicy() {
        return reviewPolicy;
    }

    public void setReviewPolicy(ReviewPolicy reviewPolicy) {
        this.reviewPolicy = reviewPolicy;
    }

    public List<PushRule> getPushRules() {
        return pushRules;
    }

    public void setPushRules(List<PushRule> pushRules) {
        this.pushRules = pushRules;
    }

    public List<String> getVisibleRoles() {
        return visibleRoles;
    }

    public void setVisibleRoles(List<String> visibleRoles) {
        this.visibleRoles = visibleRoles;
    }

    public Map<String, Object> getExtension() {
        return extension;
    }

    public void setExtension(Map<String, Object> extension) {
        this.extension = extension;
    }

    public static class BaseInfo {
        @NotBlank
        private String configName;
        private String configCode;
        private String category;
        private String subCategory;
        private String templateType;
        private String documentType;
        @NotBlank
        private String departmentId;
        @NotBlank
        private String ownerRole;
        private List<String> tags = new ArrayList<>();
        private String defaultPriority;

        public String getConfigName() {
            return configName;
        }

        public void setConfigName(String configName) {
            this.configName = configName;
        }

        public String getConfigCode() {
            return configCode;
        }

        public void setConfigCode(String configCode) {
            this.configCode = configCode;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getSubCategory() {
            return subCategory;
        }

        public void setSubCategory(String subCategory) {
            this.subCategory = subCategory;
        }

        public String getTemplateType() {
            return templateType;
        }

        public void setTemplateType(String templateType) {
            this.templateType = templateType;
        }

        public String getDocumentType() {
            return documentType;
        }

        public void setDocumentType(String documentType) {
            this.documentType = documentType;
        }

        public String getDepartmentId() {
            return departmentId;
        }

        public void setDepartmentId(String departmentId) {
            this.departmentId = departmentId;
        }

        public String getOwnerRole() {
            return ownerRole;
        }

        public void setOwnerRole(String ownerRole) {
            this.ownerRole = ownerRole;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags == null ? new ArrayList<>() : tags;
        }

        public String getDefaultPriority() {
            return defaultPriority;
        }

        public void setDefaultPriority(String defaultPriority) {
            this.defaultPriority = defaultPriority;
        }
    }

    public static class ParseConfig {
        private String engineCode;
        private String outputFormat;
        private String parseMode;
        private Integer pageBatchSize;
        private Boolean preprocessEnabled;
        private Map<String, Object> engineParams;

        public String getEngineCode() {
            return engineCode;
        }

        public void setEngineCode(String engineCode) {
            this.engineCode = engineCode;
        }

        public String getOutputFormat() {
            return outputFormat;
        }

        public void setOutputFormat(String outputFormat) {
            this.outputFormat = outputFormat;
        }

        public String getParseMode() {
            return parseMode;
        }

        public void setParseMode(String parseMode) {
            this.parseMode = parseMode;
        }

        public Integer getPageBatchSize() {
            return pageBatchSize;
        }

        public void setPageBatchSize(Integer pageBatchSize) {
            this.pageBatchSize = pageBatchSize;
        }

        public Boolean getPreprocessEnabled() {
            return preprocessEnabled;
        }

        public void setPreprocessEnabled(Boolean preprocessEnabled) {
            this.preprocessEnabled = preprocessEnabled;
        }

        public Map<String, Object> getEngineParams() {
            return engineParams;
        }

        public void setEngineParams(Map<String, Object> engineParams) {
            this.engineParams = engineParams;
        }
    }

    public static class PreprocessStep {
        private String id;
        private String stepType;
        private Boolean enabled;
        private String pageRanges;
        private List<String> includeKeywords = new ArrayList<>();
        private List<String> excludeKeywords = new ArrayList<>();
        private String imageQuality;
        private Integer dpi;
        private String imageFormat;
        private Integer splitPageCount;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getStepType() {
            return stepType;
        }

        public void setStepType(String stepType) {
            this.stepType = stepType;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getPageRanges() {
            return pageRanges;
        }

        public void setPageRanges(String pageRanges) {
            this.pageRanges = pageRanges;
        }

        public List<String> getIncludeKeywords() {
            return includeKeywords;
        }

        public void setIncludeKeywords(List<String> includeKeywords) {
            this.includeKeywords = includeKeywords;
        }

        public List<String> getExcludeKeywords() {
            return excludeKeywords;
        }

        public void setExcludeKeywords(List<String> excludeKeywords) {
            this.excludeKeywords = excludeKeywords;
        }

        public String getImageQuality() {
            return imageQuality;
        }

        public void setImageQuality(String imageQuality) {
            this.imageQuality = imageQuality;
        }

        public Integer getDpi() {
            return dpi;
        }

        public void setDpi(Integer dpi) {
            this.dpi = dpi;
        }

        public String getImageFormat() {
            return imageFormat;
        }

        public void setImageFormat(String imageFormat) {
            this.imageFormat = imageFormat;
        }

        public Integer getSplitPageCount() {
            return splitPageCount;
        }

        public void setSplitPageCount(Integer splitPageCount) {
            this.splitPageCount = splitPageCount;
        }
    }

    public static class StorageConfig {
        private String storageMode;
        private String mappingProfileName;
        private String targetTable;
        private String targetTableName;
        private String targetTableComment;
        private String saveMode;

        public String getStorageMode() {
            return storageMode;
        }

        public void setStorageMode(String storageMode) {
            this.storageMode = storageMode;
        }

        public String getMappingProfileName() {
            return mappingProfileName;
        }

        public void setMappingProfileName(String mappingProfileName) {
            this.mappingProfileName = mappingProfileName;
        }

        public String getTargetTable() {
            return targetTable;
        }

        public void setTargetTable(String targetTable) {
            this.targetTable = targetTable;
        }

        public String getTargetTableName() {
            return targetTableName;
        }

        public void setTargetTableName(String targetTableName) {
            this.targetTableName = targetTableName;
        }

        public String getTargetTableComment() {
            return targetTableComment;
        }

        public void setTargetTableComment(String targetTableComment) {
            this.targetTableComment = targetTableComment;
        }

        public String getSaveMode() {
            return saveMode;
        }

        public void setSaveMode(String saveMode) {
            this.saveMode = saveMode;
        }
    }

    public static class ResultTableColumn {
        private String columnName;
        private String columnCnName;
        private String dbType;
        private Integer length;
        private Integer precision;
        private Integer scale;
        private Boolean required;
        private String defaultValue;
        private String validationRule;

        public String getColumnName() {
            return columnName;
        }

        public void setColumnName(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnCnName() {
            return columnCnName;
        }

        public void setColumnCnName(String columnCnName) {
            this.columnCnName = columnCnName;
        }

        public String getDbType() {
            return dbType;
        }

        public void setDbType(String dbType) {
            this.dbType = dbType;
        }

        public Integer getLength() {
            return length;
        }

        public void setLength(Integer length) {
            this.length = length;
        }

        public Integer getPrecision() {
            return precision;
        }

        public void setPrecision(Integer precision) {
            this.precision = precision;
        }

        public Integer getScale() {
            return scale;
        }

        public void setScale(Integer scale) {
            this.scale = scale;
        }

        public Boolean getRequired() {
            return required;
        }

        public void setRequired(Boolean required) {
            this.required = required;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String getValidationRule() {
            return validationRule;
        }

        public void setValidationRule(String validationRule) {
            this.validationRule = validationRule;
        }
    }

    public static class UniqueConstraint {
        private String id;
        private Boolean enabled;
        private String constraintName;
        private List<String> uniqueColumns = new ArrayList<>();
        private String duplicateScope;
        private String duplicateStrategy;
        private Boolean generateDbIndex;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getConstraintName() {
            return constraintName;
        }

        public void setConstraintName(String constraintName) {
            this.constraintName = constraintName;
        }

        public List<String> getUniqueColumns() {
            return uniqueColumns;
        }

        public void setUniqueColumns(List<String> uniqueColumns) {
            this.uniqueColumns = uniqueColumns;
        }

        public String getDuplicateScope() {
            return duplicateScope;
        }

        public void setDuplicateScope(String duplicateScope) {
            this.duplicateScope = duplicateScope;
        }

        public String getDuplicateStrategy() {
            return duplicateStrategy;
        }

        public void setDuplicateStrategy(String duplicateStrategy) {
            this.duplicateStrategy = duplicateStrategy;
        }

        public Boolean getGenerateDbIndex() {
            return generateDbIndex;
        }

        public void setGenerateDbIndex(Boolean generateDbIndex) {
            this.generateDbIndex = generateDbIndex;
        }
    }

    public static class ExtractField {
        private String fieldCode;
        private String fieldName;
        private String fieldDescription;
        private Boolean extractRequired;
        private Boolean multiple;
        private Boolean extractByRegex;
        private String targetColumn;

        public String getFieldCode() {
            return fieldCode;
        }

        public void setFieldCode(String fieldCode) {
            this.fieldCode = fieldCode;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getFieldDescription() {
            return fieldDescription;
        }

        public void setFieldDescription(String fieldDescription) {
            this.fieldDescription = fieldDescription;
        }

        public Boolean getExtractRequired() {
            return extractRequired;
        }

        public void setExtractRequired(Boolean extractRequired) {
            this.extractRequired = extractRequired;
        }

        public Boolean getMultiple() {
            return multiple;
        }

        public void setMultiple(Boolean multiple) {
            this.multiple = multiple;
        }

        public Boolean getExtractByRegex() {
            return extractByRegex;
        }

        public void setExtractByRegex(Boolean extractByRegex) {
            this.extractByRegex = extractByRegex;
        }

        public String getTargetColumn() {
            return targetColumn;
        }

        public void setTargetColumn(String targetColumn) {
            this.targetColumn = targetColumn;
        }
    }

    public static class FieldMapping {
        private String extractFieldCode;
        private String targetColumn;
        private Boolean multiple;
        private Boolean requiredForStorage;

        public String getExtractFieldCode() {
            return extractFieldCode;
        }

        public void setExtractFieldCode(String extractFieldCode) {
            this.extractFieldCode = extractFieldCode;
        }

        public String getTargetColumn() {
            return targetColumn;
        }

        public void setTargetColumn(String targetColumn) {
            this.targetColumn = targetColumn;
        }

        public Boolean getMultiple() {
            return multiple;
        }

        public void setMultiple(Boolean multiple) {
            this.multiple = multiple;
        }

        public Boolean getRequiredForStorage() {
            return requiredForStorage;
        }

        public void setRequiredForStorage(Boolean requiredForStorage) {
            this.requiredForStorage = requiredForStorage;
        }
    }

    public static class ExtractStrategy {
        private Boolean aiEnabled;
        private String outputMode;
        private String llmModelCode;
        private String defaultStrategy;
        private BigDecimal confidenceThreshold;
        private String systemPrompt;
        private String userPrompt;
        private String generatedPromptPreview;
        private String outputJsonSchema;

        public Boolean getAiEnabled() {
            return aiEnabled;
        }

        public void setAiEnabled(Boolean aiEnabled) {
            this.aiEnabled = aiEnabled;
        }

        public String getOutputMode() {
            return outputMode;
        }

        public void setOutputMode(String outputMode) {
            this.outputMode = outputMode;
        }

        public String getLlmModelCode() {
            return llmModelCode;
        }

        public void setLlmModelCode(String llmModelCode) {
            this.llmModelCode = llmModelCode;
        }

        public String getDefaultStrategy() {
            return defaultStrategy;
        }

        public void setDefaultStrategy(String defaultStrategy) {
            this.defaultStrategy = defaultStrategy;
        }

        public BigDecimal getConfidenceThreshold() {
            return confidenceThreshold;
        }

        public void setConfidenceThreshold(BigDecimal confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public String getUserPrompt() {
            return userPrompt;
        }

        public void setUserPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
        }

        public String getGeneratedPromptPreview() {
            return generatedPromptPreview;
        }

        public void setGeneratedPromptPreview(String generatedPromptPreview) {
            this.generatedPromptPreview = generatedPromptPreview;
        }

        public String getOutputJsonSchema() {
            return outputJsonSchema;
        }

        public void setOutputJsonSchema(String outputJsonSchema) {
            this.outputJsonSchema = outputJsonSchema;
        }
    }

    public static class RegexRule {
        private String fieldCode;
        private String ruleName;
        private String regexPattern;
        private Integer regexGroup;
        private String regexFlags;
        private String sampleText;
        private String sampleResult;
        private String validationStatus;
        private Boolean enabled;

        public String getFieldCode() {
            return fieldCode;
        }

        public void setFieldCode(String fieldCode) {
            this.fieldCode = fieldCode;
        }

        public String getRuleName() {
            return ruleName;
        }

        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }

        public String getRegexPattern() {
            return regexPattern;
        }

        public void setRegexPattern(String regexPattern) {
            this.regexPattern = regexPattern;
        }

        public Integer getRegexGroup() {
            return regexGroup;
        }

        public void setRegexGroup(Integer regexGroup) {
            this.regexGroup = regexGroup;
        }

        public String getRegexFlags() {
            return regexFlags;
        }

        public void setRegexFlags(String regexFlags) {
            this.regexFlags = regexFlags;
        }

        public String getSampleText() {
            return sampleText;
        }

        public void setSampleText(String sampleText) {
            this.sampleText = sampleText;
        }

        public String getSampleResult() {
            return sampleResult;
        }

        public void setSampleResult(String sampleResult) {
            this.sampleResult = sampleResult;
        }

        public String getValidationStatus() {
            return validationStatus;
        }

        public void setValidationStatus(String validationStatus) {
            this.validationStatus = validationStatus;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class TransformRule {
        private String id;
        private String ruleName;
        private String ruleType;
        private String inputField;
        private String outputField;
        private String outputMode;
        private Boolean conditionEnabled;
        private String conditionField;
        private String conditionOperator;
        private String conditionValue;
        private Boolean enabled;
        private String onFail;
        private Map<String, Object> ruleConfig;
        private List<Map<String, Object>> dictItems = new ArrayList<>();
        private String dictMatchMode;
        private String apiEndpoint;
        private String apiMethod;
        private String apiParamName;
        private String apiResponsePath;
        private Integer apiTimeout;
        private Integer apiRetryCount;
        private String apiAuthMode;
        private String apiSuccessRule;
        private String sqlDatasource;
        private String sqlText;
        private String sqlResultColumn;
        private Integer sqlMaxRows;
        private Boolean sqlReadonlyChecked;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getRuleName() {
            return ruleName;
        }

        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }

        public String getRuleType() {
            return ruleType;
        }

        public void setRuleType(String ruleType) {
            this.ruleType = ruleType;
        }

        public String getInputField() {
            return inputField;
        }

        public void setInputField(String inputField) {
            this.inputField = inputField;
        }

        public String getOutputField() {
            return outputField;
        }

        public void setOutputField(String outputField) {
            this.outputField = outputField;
        }

        public String getOutputMode() {
            return outputMode;
        }

        public void setOutputMode(String outputMode) {
            this.outputMode = outputMode;
        }

        public Boolean getConditionEnabled() {
            return conditionEnabled;
        }

        public void setConditionEnabled(Boolean conditionEnabled) {
            this.conditionEnabled = conditionEnabled;
        }

        public String getConditionField() {
            return conditionField;
        }

        public void setConditionField(String conditionField) {
            this.conditionField = conditionField;
        }

        public String getConditionOperator() {
            return conditionOperator;
        }

        public void setConditionOperator(String conditionOperator) {
            this.conditionOperator = conditionOperator;
        }

        public String getConditionValue() {
            return conditionValue;
        }

        public void setConditionValue(String conditionValue) {
            this.conditionValue = conditionValue;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getOnFail() {
            return onFail;
        }

        public void setOnFail(String onFail) {
            this.onFail = onFail;
        }

        public Map<String, Object> getRuleConfig() {
            return ruleConfig;
        }

        public void setRuleConfig(Map<String, Object> ruleConfig) {
            this.ruleConfig = ruleConfig;
        }

        public List<Map<String, Object>> getDictItems() {
            return dictItems;
        }

        public void setDictItems(List<Map<String, Object>> dictItems) {
            this.dictItems = dictItems;
        }

        public String getDictMatchMode() {
            return dictMatchMode;
        }

        public void setDictMatchMode(String dictMatchMode) {
            this.dictMatchMode = dictMatchMode;
        }

        public String getApiEndpoint() {
            return apiEndpoint;
        }

        public void setApiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
        }

        public String getApiMethod() {
            return apiMethod;
        }

        public void setApiMethod(String apiMethod) {
            this.apiMethod = apiMethod;
        }

        public String getApiParamName() {
            return apiParamName;
        }

        public void setApiParamName(String apiParamName) {
            this.apiParamName = apiParamName;
        }

        public String getApiResponsePath() {
            return apiResponsePath;
        }

        public void setApiResponsePath(String apiResponsePath) {
            this.apiResponsePath = apiResponsePath;
        }

        public Integer getApiTimeout() {
            return apiTimeout;
        }

        public void setApiTimeout(Integer apiTimeout) {
            this.apiTimeout = apiTimeout;
        }

        public Integer getApiRetryCount() {
            return apiRetryCount;
        }

        public void setApiRetryCount(Integer apiRetryCount) {
            this.apiRetryCount = apiRetryCount;
        }

        public String getApiAuthMode() {
            return apiAuthMode;
        }

        public void setApiAuthMode(String apiAuthMode) {
            this.apiAuthMode = apiAuthMode;
        }

        public String getApiSuccessRule() {
            return apiSuccessRule;
        }

        public void setApiSuccessRule(String apiSuccessRule) {
            this.apiSuccessRule = apiSuccessRule;
        }

        public String getSqlDatasource() {
            return sqlDatasource;
        }

        public void setSqlDatasource(String sqlDatasource) {
            this.sqlDatasource = sqlDatasource;
        }

        public String getSqlText() {
            return sqlText;
        }

        public void setSqlText(String sqlText) {
            this.sqlText = sqlText;
        }

        public String getSqlResultColumn() {
            return sqlResultColumn;
        }

        public void setSqlResultColumn(String sqlResultColumn) {
            this.sqlResultColumn = sqlResultColumn;
        }

        public Integer getSqlMaxRows() {
            return sqlMaxRows;
        }

        public void setSqlMaxRows(Integer sqlMaxRows) {
            this.sqlMaxRows = sqlMaxRows;
        }

        public Boolean getSqlReadonlyChecked() {
            return sqlReadonlyChecked;
        }

        public void setSqlReadonlyChecked(Boolean sqlReadonlyChecked) {
            this.sqlReadonlyChecked = sqlReadonlyChecked;
        }
    }

    public static class ValidationRule {
        private String ruleName;
        private String ruleType;
        private String fieldCode;
        private Boolean enabled;
        private String severity;
        private String expression;
        private String failMessage;

        public String getRuleName() {
            return ruleName;
        }

        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }

        public String getRuleType() {
            return ruleType;
        }

        public void setRuleType(String ruleType) {
            this.ruleType = ruleType;
        }

        public String getFieldCode() {
            return fieldCode;
        }

        public void setFieldCode(String fieldCode) {
            this.fieldCode = fieldCode;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getExpression() {
            return expression;
        }

        public void setExpression(String expression) {
            this.expression = expression;
        }

        public String getFailMessage() {
            return failMessage;
        }

        public void setFailMessage(String failMessage) {
            this.failMessage = failMessage;
        }
    }

    public static class ReviewPolicy {
        private BigDecimal confidenceThreshold;
        private String reviewerRole;
        private List<String> forceReviewFields = new ArrayList<>();

        public BigDecimal getConfidenceThreshold() {
            return confidenceThreshold;
        }

        public void setConfidenceThreshold(BigDecimal confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }

        public String getReviewerRole() {
            return reviewerRole;
        }

        public void setReviewerRole(String reviewerRole) {
            this.reviewerRole = reviewerRole;
        }

        public List<String> getForceReviewFields() {
            return forceReviewFields;
        }

        public void setForceReviewFields(List<String> forceReviewFields) {
            this.forceReviewFields = forceReviewFields;
        }
    }

    public static class PushRule {
        private String serviceCode;
        private Boolean pushEnabled;
        private String pushTrigger;
        private String pushScope;
        private String pushMode;
        private String idempotentKey;
        private String failStrategy;
        private List<Map<String, Object>> fieldMappings = new ArrayList<>();

        public String getServiceCode() {
            return serviceCode;
        }

        public void setServiceCode(String serviceCode) {
            this.serviceCode = serviceCode;
        }

        public Boolean getPushEnabled() {
            return pushEnabled;
        }

        public void setPushEnabled(Boolean pushEnabled) {
            this.pushEnabled = pushEnabled;
        }

        public String getPushTrigger() {
            return pushTrigger;
        }

        public void setPushTrigger(String pushTrigger) {
            this.pushTrigger = pushTrigger;
        }

        public String getPushScope() {
            return pushScope;
        }

        public void setPushScope(String pushScope) {
            this.pushScope = pushScope;
        }

        public String getPushMode() {
            return pushMode;
        }

        public void setPushMode(String pushMode) {
            this.pushMode = pushMode;
        }

        public String getIdempotentKey() {
            return idempotentKey;
        }

        public void setIdempotentKey(String idempotentKey) {
            this.idempotentKey = idempotentKey;
        }

        public String getFailStrategy() {
            return failStrategy;
        }

        public void setFailStrategy(String failStrategy) {
            this.failStrategy = failStrategy;
        }

        public List<Map<String, Object>> getFieldMappings() {
            return fieldMappings;
        }

        public void setFieldMappings(List<Map<String, Object>> fieldMappings) {
            this.fieldMappings = fieldMappings;
        }
    }
}
