package com.example.extraction.ocr;

import java.util.ArrayList;
import java.util.List;

public class OcrParseResponse {
    private String engineCode;
    private String markdownText;
    private String rawJson;
    private Integer pageCount;
    private long durationMs;
    private final List<OcrImageArtifact> images = new ArrayList<>();

    public String getEngineCode() {
        return engineCode;
    }

    public void setEngineCode(String engineCode) {
        this.engineCode = engineCode;
    }

    public String getMarkdownText() {
        return markdownText;
    }

    public void setMarkdownText(String markdownText) {
        this.markdownText = markdownText;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public List<OcrImageArtifact> getImages() {
        return images;
    }
}
