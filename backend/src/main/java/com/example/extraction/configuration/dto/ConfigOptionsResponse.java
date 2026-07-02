package com.example.extraction.configuration.dto;

import java.util.List;
import java.util.Map;

public class ConfigOptionsResponse {
    private List<Map<String, Object>> departments;
    private List<Map<String, Object>> roles;
    private List<Map<String, Object>> categories;
    private List<Map<String, Object>> ocrEngines;
    private List<Map<String, Object>> resultTables;
    private List<Map<String, Object>> downstreamServices;

    public List<Map<String, Object>> getDepartments() {
        return departments;
    }

    public void setDepartments(List<Map<String, Object>> departments) {
        this.departments = departments;
    }

    public List<Map<String, Object>> getRoles() {
        return roles;
    }

    public void setRoles(List<Map<String, Object>> roles) {
        this.roles = roles;
    }

    public List<Map<String, Object>> getCategories() {
        return categories;
    }

    public void setCategories(List<Map<String, Object>> categories) {
        this.categories = categories;
    }

    public List<Map<String, Object>> getOcrEngines() {
        return ocrEngines;
    }

    public void setOcrEngines(List<Map<String, Object>> ocrEngines) {
        this.ocrEngines = ocrEngines;
    }

    public List<Map<String, Object>> getResultTables() {
        return resultTables;
    }

    public void setResultTables(List<Map<String, Object>> resultTables) {
        this.resultTables = resultTables;
    }

    public List<Map<String, Object>> getDownstreamServices() {
        return downstreamServices;
    }

    public void setDownstreamServices(List<Map<String, Object>> downstreamServices) {
        this.downstreamServices = downstreamServices;
    }
}
