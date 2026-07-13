package com.example.extraction.system.dto;

import java.util.List;

public class RolePermissionRequest {
    private List<String> permissionCodes;

    public List<String> getPermissionCodes() { return permissionCodes; }
    public void setPermissionCodes(List<String> permissionCodes) { this.permissionCodes = permissionCodes; }
}
