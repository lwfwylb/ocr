package com.example.extraction.model.dto;

public class OcrEngineConfigRequest {
    private String engineCode;
    private String engineName;
    private String engineType;
    private String adapterType;
    private String provider;
    private String baseUrl;
    private String authMode;
    private String apiKeySecretRef;
    private Boolean defaultEngine;
    private Integer priority;
    private Integer timeoutSeconds;
    private Integer retryCount;
    private String supportedFileTypes;
    private String outputFormat;
    private Integer maxPagesPerCall;
    private String engineParamsJson;
    private String status;
    private String description;

    public String getEngineCode() {
        return engineCode;
    }

    public void setEngineCode(String engineCode) {
        this.engineCode = engineCode;
    }

    public String getEngineName() {
        return engineName;
    }

    public void setEngineName(String engineName) {
        this.engineName = engineName;
    }

    public String getEngineType() {
        return engineType;
    }

    public void setEngineType(String engineType) {
        this.engineType = engineType;
    }

    public String getAdapterType() {
        return adapterType;
    }

    public void setAdapterType(String adapterType) {
        this.adapterType = adapterType;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAuthMode() {
        return authMode;
    }

    public void setAuthMode(String authMode) {
        this.authMode = authMode;
    }

    public String getApiKeySecretRef() {
        return apiKeySecretRef;
    }

    public void setApiKeySecretRef(String apiKeySecretRef) {
        this.apiKeySecretRef = apiKeySecretRef;
    }

    public Boolean getDefaultEngine() {
        return defaultEngine;
    }

    public void setDefaultEngine(Boolean defaultEngine) {
        this.defaultEngine = defaultEngine;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getSupportedFileTypes() {
        return supportedFileTypes;
    }

    public void setSupportedFileTypes(String supportedFileTypes) {
        this.supportedFileTypes = supportedFileTypes;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public Integer getMaxPagesPerCall() {
        return maxPagesPerCall;
    }

    public void setMaxPagesPerCall(Integer maxPagesPerCall) {
        this.maxPagesPerCall = maxPagesPerCall;
    }

    public String getEngineParamsJson() {
        return engineParamsJson;
    }

    public void setEngineParamsJson(String engineParamsJson) {
        this.engineParamsJson = engineParamsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
