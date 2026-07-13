package com.example.extraction.result.dto;

import java.util.List;
import java.util.Map;

public class ResultDetailResponse {
    private ResultSummaryResponse summary;
    private String parseText;
    private Integer pageCount;
    private String engineCode;
    private Map<String, Object> result;
    private Map<String, Object> confidence;
    private List<ResultFieldResponse> fields;
    private List<StoragePreviewResponse> storagePreview;

    public ResultSummaryResponse getSummary() { return summary; }
    public void setSummary(ResultSummaryResponse summary) { this.summary = summary; }
    public String getParseText() { return parseText; }
    public void setParseText(String parseText) { this.parseText = parseText; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public String getEngineCode() { return engineCode; }
    public void setEngineCode(String engineCode) { this.engineCode = engineCode; }
    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }
    public Map<String, Object> getConfidence() { return confidence; }
    public void setConfidence(Map<String, Object> confidence) { this.confidence = confidence; }
    public List<ResultFieldResponse> getFields() { return fields; }
    public void setFields(List<ResultFieldResponse> fields) { this.fields = fields; }
    public List<StoragePreviewResponse> getStoragePreview() { return storagePreview; }
    public void setStoragePreview(List<StoragePreviewResponse> storagePreview) { this.storagePreview = storagePreview; }
}
