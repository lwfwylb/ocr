package com.example.extraction.result.domain;

import java.time.LocalDateTime;

public class DocumentParseResultRecord {
    private String id;
    private String taskId;
    private String traceId;
    private String documentId;
    private String engineCode;
    private String parseText;
    private String parseMarkdownPath;
    private Integer pageCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getEngineCode() { return engineCode; }
    public void setEngineCode(String engineCode) { this.engineCode = engineCode; }
    public String getParseText() { return parseText; }
    public void setParseText(String parseText) { this.parseText = parseText; }
    public String getParseMarkdownPath() { return parseMarkdownPath; }
    public void setParseMarkdownPath(String parseMarkdownPath) { this.parseMarkdownPath = parseMarkdownPath; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
