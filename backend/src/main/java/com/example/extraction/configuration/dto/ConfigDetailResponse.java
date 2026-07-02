package com.example.extraction.configuration.dto;

public class ConfigDetailResponse {
    private ConfigSummaryResponse summary;
    private ConfigWizardPayload payload;

    public ConfigSummaryResponse getSummary() {
        return summary;
    }

    public void setSummary(ConfigSummaryResponse summary) {
        this.summary = summary;
    }

    public ConfigWizardPayload getPayload() {
        return payload;
    }

    public void setPayload(ConfigWizardPayload payload) {
        this.payload = payload;
    }
}
