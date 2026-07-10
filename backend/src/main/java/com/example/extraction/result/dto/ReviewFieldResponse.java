package com.example.extraction.result.dto;

import java.math.BigDecimal;

public class ReviewFieldResponse {
    private String fieldCode;
    private String fieldName;
    private Object rawValue;
    private Object finalValue;
    private BigDecimal confidence;
    private Boolean reviewRequired;
    private String issue;
    private String evidence;

    public String getFieldCode() { return fieldCode; }
    public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public Object getRawValue() { return rawValue; }
    public void setRawValue(Object rawValue) { this.rawValue = rawValue; }
    public Object getFinalValue() { return finalValue; }
    public void setFinalValue(Object finalValue) { this.finalValue = finalValue; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public Boolean getReviewRequired() { return reviewRequired; }
    public void setReviewRequired(Boolean reviewRequired) { this.reviewRequired = reviewRequired; }
    public String getIssue() { return issue; }
    public void setIssue(String issue) { this.issue = issue; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
}
