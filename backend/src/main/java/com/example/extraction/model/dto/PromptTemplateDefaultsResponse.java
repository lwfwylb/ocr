package com.example.extraction.model.dto;

import java.time.LocalDateTime;

public class PromptTemplateDefaultsResponse {
    private String systemTemplate;
    private String userTemplate;
    private LocalDateTime updatedAt;

    public String getSystemTemplate() {
        return systemTemplate;
    }

    public void setSystemTemplate(String systemTemplate) {
        this.systemTemplate = systemTemplate;
    }

    public String getUserTemplate() {
        return userTemplate;
    }

    public void setUserTemplate(String userTemplate) {
        this.userTemplate = userTemplate;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
