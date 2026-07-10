package com.example.extraction.model.dto;

public class ModelCallLogQueryRequest {
    private String keyword;
    private String callType;
    private String status;
    private String stageCode;
    private String modelCode;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getCallType() { return callType; }
    public void setCallType(String callType) { this.callType = callType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStageCode() { return stageCode; }
    public void setStageCode(String stageCode) { this.stageCode = stageCode; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
}
