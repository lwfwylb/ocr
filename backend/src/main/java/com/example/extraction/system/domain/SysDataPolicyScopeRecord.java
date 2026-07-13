package com.example.extraction.system.domain;

public class SysDataPolicyScopeRecord {
    private String id;
    private String policyId;
    private String scopeType;
    private String scopeValue;
    private String scopeLabel;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = policyId; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public String getScopeValue() { return scopeValue; }
    public void setScopeValue(String scopeValue) { this.scopeValue = scopeValue; }
    public String getScopeLabel() { return scopeLabel; }
    public void setScopeLabel(String scopeLabel) { this.scopeLabel = scopeLabel; }
}
