package com.example.extraction.result.dto;

public class StorageExecuteRequest {
    private String storedBy;
    private String duplicateStrategy;

    public String getStoredBy() { return storedBy; }
    public void setStoredBy(String storedBy) { this.storedBy = storedBy; }
    public String getDuplicateStrategy() { return duplicateStrategy; }
    public void setDuplicateStrategy(String duplicateStrategy) { this.duplicateStrategy = duplicateStrategy; }
}
