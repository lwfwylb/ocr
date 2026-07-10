package com.example.extraction.result.dto;

import java.util.ArrayList;
import java.util.List;

public class ReviewDetailResponse {
    private ResultSummaryResponse summary;
    private String parseText;
    private List<ReviewFieldResponse> fields = new ArrayList<>();
    private List<ReviewLogResponse> logs = new ArrayList<>();

    public ResultSummaryResponse getSummary() { return summary; }
    public void setSummary(ResultSummaryResponse summary) { this.summary = summary; }
    public String getParseText() { return parseText; }
    public void setParseText(String parseText) { this.parseText = parseText; }
    public List<ReviewFieldResponse> getFields() { return fields; }
    public void setFields(List<ReviewFieldResponse> fields) { this.fields = fields; }
    public List<ReviewLogResponse> getLogs() { return logs; }
    public void setLogs(List<ReviewLogResponse> logs) { this.logs = logs; }
}
