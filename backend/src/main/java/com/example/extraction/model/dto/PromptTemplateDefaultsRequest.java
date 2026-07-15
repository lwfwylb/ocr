package com.example.extraction.model.dto;

public class PromptTemplateDefaultsRequest {
    private String systemTemplate;
    private String userTemplate;

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
}
