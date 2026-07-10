package com.example.extraction.result.dto;

public class ReviewFieldCorrectionRequest {
    private String fieldCode;
    private Object finalValue;

    public String getFieldCode() { return fieldCode; }
    public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }
    public Object getFinalValue() { return finalValue; }
    public void setFinalValue(Object finalValue) { this.finalValue = finalValue; }
}
