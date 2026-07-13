package com.example.extraction.system.dto;

import java.util.List;

public class DataPolicyRequest {
    private String policyName;
    private String subjectType;
    private String subjectId;
    private String subjectName;
    private String dataScope;
    private Boolean allowExport;
    private String status;
    private List<String> departments;
    private List<String> documentTypes;
    private List<String> sourceSystems;
    private List<String> configScopes;
    private List<String> fieldMasking;

    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }
    public Boolean getAllowExport() { return allowExport; }
    public void setAllowExport(Boolean allowExport) { this.allowExport = allowExport; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<String> getDepartments() { return departments; }
    public void setDepartments(List<String> departments) { this.departments = departments; }
    public List<String> getDocumentTypes() { return documentTypes; }
    public void setDocumentTypes(List<String> documentTypes) { this.documentTypes = documentTypes; }
    public List<String> getSourceSystems() { return sourceSystems; }
    public void setSourceSystems(List<String> sourceSystems) { this.sourceSystems = sourceSystems; }
    public List<String> getConfigScopes() { return configScopes; }
    public void setConfigScopes(List<String> configScopes) { this.configScopes = configScopes; }
    public List<String> getFieldMasking() { return fieldMasking; }
    public void setFieldMasking(List<String> fieldMasking) { this.fieldMasking = fieldMasking; }
}
