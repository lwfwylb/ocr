package com.example.extraction.configuration.dto;

public class ConfigDetailResponse {
    private ConfigSummaryResponse summary;
    private Object payload;

    public ConfigSummaryResponse getSummary() {
        return summary;
    }

    public void setSummary(ConfigSummaryResponse summary) {
        this.summary = summary;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }
}
