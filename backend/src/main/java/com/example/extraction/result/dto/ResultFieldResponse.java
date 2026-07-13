package com.example.extraction.result.dto;

import java.math.BigDecimal;

public class ResultFieldResponse {
    private String fieldCode;
    private String fieldName;
    private String extractField;
    private String targetColumn;
    private Object rawValue;
    private Object finalValue;
    private BigDecimal confidence;
    private Boolean reviewRequired;
    private String sourceType;
    private String issue;
    private String sourcePage;

    public String getFieldCode() { return fieldCode; }
    public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getExtractField() { return extractField; }
    public void setExtractField(String extractField) { this.extractField = extractField; }
    public String getTargetColumn() { return targetColumn; }
    public void setTargetColumn(String targetColumn) { this.targetColumn = targetColumn; }
    public Object getRawValue() { return rawValue; }
    public void setRawValue(Object rawValue) { this.rawValue = rawValue; }
    public Object getFinalValue() { return finalValue; }
    public void setFinalValue(Object finalValue) { this.finalValue = finalValue; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public Boolean getReviewRequired() { return reviewRequired; }
    public void setReviewRequired(Boolean reviewRequired) { this.reviewRequired = reviewRequired; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getIssue() { return issue; }
    public void setIssue(String issue) { this.issue = issue; }
    public String getSourcePage() { return sourcePage; }
    public void setSourcePage(String sourcePage) { this.sourcePage = sourcePage; }
}
