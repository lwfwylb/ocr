package com.example.extraction.system.dto;

import java.util.ArrayList;
import java.util.List;

public class PermissionNodeResponse {
    private String id;
    private String label;
    private String type;
    private String routePath;
    private List<PermissionNodeResponse> children = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getRoutePath() { return routePath; }
    public void setRoutePath(String routePath) { this.routePath = routePath; }
    public List<PermissionNodeResponse> getChildren() { return children; }
    public void setChildren(List<PermissionNodeResponse> children) { this.children = children; }
}
