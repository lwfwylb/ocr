package com.example.extraction.result.dto;

public class PushExecuteRequest {
    private String targetSystem;
    private String serviceCode;
    private String serviceName;
    private String pushMethod;
    private String triggerType;
    private String operator;

    public String getTargetSystem() { return targetSystem; }
    public void setTargetSystem(String targetSystem) { this.targetSystem = targetSystem; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getPushMethod() { return pushMethod; }
    public void setPushMethod(String pushMethod) { this.pushMethod = pushMethod; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
