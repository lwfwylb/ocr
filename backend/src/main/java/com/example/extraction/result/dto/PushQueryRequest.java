package com.example.extraction.result.dto;

import com.example.extraction.common.PageQuery;

public class PushQueryRequest extends PageQuery {
    private String keyword;
    private String targetSystem;
    private String serviceCode;
    private String status;
    private String pushMethod;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getTargetSystem() { return targetSystem; }
    public void setTargetSystem(String targetSystem) { this.targetSystem = targetSystem; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPushMethod() { return pushMethod; }
    public void setPushMethod(String pushMethod) { this.pushMethod = pushMethod; }
}
