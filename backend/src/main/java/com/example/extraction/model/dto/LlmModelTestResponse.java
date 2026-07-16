package com.example.extraction.model.dto;

import java.time.LocalDateTime;

public class LlmModelTestResponse {
    private Boolean passed;
    private String message;
    private String modelCode;
    private String modelIdentifier;
    private Long durationMs;
    private String requestPreview;
    private String responsePreview;
    private String contentPreview;
    private Boolean jsonValid;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private String errorCode;
    private LocalDateTime checkedAt;

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getModelIdentifier() {
        return modelIdentifier;
    }

    public void setModelIdentifier(String modelIdentifier) {
        this.modelIdentifier = modelIdentifier;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getRequestPreview() {
        return requestPreview;
    }

    public void setRequestPreview(String requestPreview) {
        this.requestPreview = requestPreview;
    }

    public String getResponsePreview() {
        return responsePreview;
    }

    public void setResponsePreview(String responsePreview) {
        this.responsePreview = responsePreview;
    }

    public String getContentPreview() {
        return contentPreview;
    }

    public void setContentPreview(String contentPreview) {
        this.contentPreview = contentPreview;
    }

    public Boolean getJsonValid() {
        return jsonValid;
    }

    public void setJsonValid(Boolean jsonValid) {
        this.jsonValid = jsonValid;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }
}
