package com.example.extraction.trace.dto;

import com.example.extraction.common.PageQuery;

public class TraceQueryRequest extends PageQuery {
    private String keyword;
    private String sourceType;
    private String status;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
