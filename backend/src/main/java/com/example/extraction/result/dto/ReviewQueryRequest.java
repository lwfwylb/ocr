package com.example.extraction.result.dto;

import com.example.extraction.common.PageQuery;

public class ReviewQueryRequest extends PageQuery {
    private String keyword;
    private String departmentId;
    private String documentType;
    private String sourceType;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
}
