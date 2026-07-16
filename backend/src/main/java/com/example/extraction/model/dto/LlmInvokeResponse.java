package com.example.extraction.model.dto;

import com.example.extraction.model.domain.LlmModelConfigRecord;

public class LlmInvokeResponse {
    private LlmModelConfigRecord model;
    private String requestPreview;
    private String responsePreview;
    private String content;
    private String jsonContent;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private long durationMs;

    public LlmModelConfigRecord getModel() {
        return model;
    }

    public void setModel(LlmModelConfigRecord model) {
        this.model = model;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getJsonContent() {
        return jsonContent;
    }

    public void setJsonContent(String jsonContent) {
        this.jsonContent = jsonContent;
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

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
