package com.example.extraction.system.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.common.PageQuery;
import com.example.extraction.common.PageResponse;
import com.example.extraction.common.PageSupport;
import com.example.extraction.system.dto.DataPolicyRequest;
import com.example.extraction.system.dto.DataPolicyResponse;
import com.example.extraction.system.dto.PermissionNodeResponse;
import com.example.extraction.system.dto.RolePermissionRequest;
import com.example.extraction.system.dto.RoleRequest;
import com.example.extraction.system.dto.RoleResponse;
import com.example.extraction.system.dto.UserRequest;
import com.example.extraction.system.dto.UserResponse;
import com.example.extraction.system.service.SystemAccessService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/access")
public class SystemAccessController {
    private final SystemAccessService accessService;

    public SystemAccessController(SystemAccessService accessService) {
        this.accessService = accessService;
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<UserResponse>> users(@RequestParam(value = "keyword", required = false) String keyword,
                                                 @RequestParam(value = "departmentId", required = false) String departmentId,
                                                 @RequestParam(value = "status", required = false) String status,
                                                 PageQuery pageQuery) {
        accessService.ensureDefaultsForList();
        return ApiResponse.success(PageSupport.page(pageQuery, () -> accessService.usersWithoutDefaults(keyword, departmentId, status)));
    }

    @PostMapping("/users")
    public ApiResponse<UserResponse> createUser(@RequestBody UserRequest request) {
        return ApiResponse.success(accessService.createUser(request));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<UserResponse> updateUser(@PathVariable("id") String id, @RequestBody UserRequest request) {
        return ApiResponse.success(accessService.updateUser(id, request));
    }

    @PostMapping("/users/{id}/enable")
    public ApiResponse<UserResponse> enableUser(@PathVariable("id") String id) {
        return ApiResponse.success(accessService.updateUserStatus(id, "ENABLED"));
    }

    @PostMapping("/users/{id}/disable")
    public ApiResponse<UserResponse> disableUser(@PathVariable("id") String id) {
        return ApiResponse.success(accessService.updateUserStatus(id, "DISABLED"));
    }

    @GetMapping("/roles")
    public ApiResponse<PageResponse<RoleResponse>> roles(@RequestParam(value = "keyword", required = false) String keyword,
                                                 @RequestParam(value = "status", required = false) String status,
                                                 PageQuery pageQuery) {
        accessService.ensureDefaultsForList();
        return ApiResponse.success(PageSupport.page(pageQuery, () -> accessService.rolesWithoutDefaults(keyword, status)));
    }

    @PostMapping("/roles")
    public ApiResponse<RoleResponse> createRole(@RequestBody RoleRequest request) {
        return ApiResponse.success(accessService.createRole(request));
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<RoleResponse> updateRole(@PathVariable("id") String id, @RequestBody RoleRequest request) {
        return ApiResponse.success(accessService.updateRole(id, request));
    }

    @PostMapping("/roles/{id}/enable")
    public ApiResponse<RoleResponse> enableRole(@PathVariable("id") String id) {
        return ApiResponse.success(accessService.updateRoleStatus(id, "ENABLED"));
    }

    @PostMapping("/roles/{id}/disable")
    public ApiResponse<RoleResponse> disableRole(@PathVariable("id") String id) {
        return ApiResponse.success(accessService.updateRoleStatus(id, "DISABLED"));
    }

    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable("id") String id) {
        accessService.deleteRole(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/permissions/tree")
    public ApiResponse<List<PermissionNodeResponse>> permissionTree() {
        return ApiResponse.success(accessService.permissionTree());
    }

    @GetMapping("/roles/{id}/permissions")
    public ApiResponse<List<String>> rolePermissions(@PathVariable("id") String id) {
        return ApiResponse.success(accessService.rolePermissions(id));
    }

    @PutMapping("/roles/{id}/permissions")
    public ApiResponse<List<String>> saveRolePermissions(@PathVariable("id") String id,
                                                         @RequestBody RolePermissionRequest request) {
        return ApiResponse.success(accessService.saveRolePermissions(id, request));
    }

    @GetMapping("/data-policies")
    public ApiResponse<PageResponse<DataPolicyResponse>> dataPolicies(@RequestParam(value = "keyword", required = false) String keyword,
                                                              @RequestParam(value = "subjectType", required = false) String subjectType,
                                                              @RequestParam(value = "status", required = false) String status,
                                                              PageQuery pageQuery) {
        accessService.ensureDefaultsForList();
        return ApiResponse.success(PageSupport.page(pageQuery, () -> accessService.dataPoliciesWithoutDefaults(keyword, subjectType, status)));
    }

    @PostMapping("/data-policies")
    public ApiResponse<DataPolicyResponse> createDataPolicy(@RequestBody DataPolicyRequest request) {
        return ApiResponse.success(accessService.createDataPolicy(request));
    }

    @PutMapping("/data-policies/{id}")
    public ApiResponse<DataPolicyResponse> updateDataPolicy(@PathVariable("id") String id,
                                                            @RequestBody DataPolicyRequest request) {
        return ApiResponse.success(accessService.updateDataPolicy(id, request));
    }

    @DeleteMapping("/data-policies/{id}")
    public ApiResponse<Void> deleteDataPolicy(@PathVariable("id") String id) {
        accessService.deleteDataPolicy(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/data-permissions/preview")
    public ApiResponse<Map<String, Object>> previewDataPolicy(@RequestBody DataPolicyRequest request) {
        return ApiResponse.success(accessService.previewDataPolicy(request));
    }
}
