package com.example.extraction.system.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.mapper.SystemAccessMapper;
import com.example.extraction.system.domain.SysDataPolicyRecord;
import com.example.extraction.system.domain.SysDataPolicyScopeRecord;
import com.example.extraction.system.domain.SysPermissionRecord;
import com.example.extraction.system.domain.SysRoleRecord;
import com.example.extraction.system.domain.SysUserDepartmentRoleRecord;
import com.example.extraction.system.domain.SysUserRecord;
import com.example.extraction.system.dto.DataPolicyRequest;
import com.example.extraction.system.dto.DataPolicyResponse;
import com.example.extraction.system.dto.PermissionNodeResponse;
import com.example.extraction.system.dto.RolePermissionRequest;
import com.example.extraction.system.dto.RoleRequest;
import com.example.extraction.system.dto.RoleResponse;
import com.example.extraction.system.dto.UserRequest;
import com.example.extraction.system.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class SystemAccessService {
    private static final Set<String> BUILTIN_ROLE_CODES = Set.of(
            "biz_user", "reviewer", "template_admin", "dept_admin", "auditor", "system_account"
    );

    private final SystemAccessMapper accessMapper;

    public SystemAccessService(SystemAccessMapper accessMapper) {
        this.accessMapper = accessMapper;
    }

    @Transactional
    public List<UserResponse> users(String keyword, String departmentId, String status) {
        ensureDefaults();
        return accessMapper.selectUsers(keyword, departmentId, status).stream().map(this::toUserResponse).toList();
    }

    @Transactional
    public void ensureDefaultsForList() {
        ensureDefaults();
    }

    public List<UserResponse> usersWithoutDefaults(String keyword, String departmentId, String status) {
        return accessMapper.selectUsers(keyword, departmentId, status).stream().map(this::toUserResponse).toList();
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        ensureDefaults();
        validateUser(request);
        if (accessMapper.selectUserByAccount(request.getAccount()) != null) {
            throw new BusinessException("USER_409", "用户账号已存在");
        }
        SysUserRecord record = new SysUserRecord();
        record.setId(IdGenerator.nextId("USER"));
        fillUser(record, request);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        accessMapper.insertUser(record);
        saveUserRoles(record.getId(), record.getDepartmentId(), request.getRoleIds());
        return toUserResponse(requireUser(record.getId()));
    }

    @Transactional
    public UserResponse updateUser(String id, UserRequest request) {
        ensureDefaults();
        validateUser(request);
        SysUserRecord record = requireUser(id);
        SysUserRecord sameAccount = accessMapper.selectUserByAccount(request.getAccount());
        if (sameAccount != null && !id.equals(sameAccount.getId())) {
            throw new BusinessException("USER_409", "用户账号已存在");
        }
        fillUser(record, request);
        record.setUpdatedAt(LocalDateTime.now());
        accessMapper.updateUser(record);
        saveUserRoles(id, record.getDepartmentId(), request.getRoleIds());
        return toUserResponse(requireUser(id));
    }

    @Transactional
    public UserResponse updateUserStatus(String id, String status) {
        requireUser(id);
        accessMapper.updateUserStatus(id, status);
        return toUserResponse(requireUser(id));
    }

    @Transactional
    public List<RoleResponse> roles(String keyword, String status) {
        ensureDefaults();
        return accessMapper.selectRoles(keyword, status).stream().map(this::toRoleResponse).toList();
    }

    public List<RoleResponse> rolesWithoutDefaults(String keyword, String status) {
        return accessMapper.selectRoles(keyword, status).stream().map(this::toRoleResponse).toList();
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        ensureDefaults();
        validateRole(request);
        if (accessMapper.selectRoleByCode(request.getRoleCode()) != null) {
            throw new BusinessException("ROLE_409", "角色编码已存在");
        }
        SysRoleRecord record = new SysRoleRecord();
        record.setId(IdGenerator.nextId("ROLE"));
        fillRole(record, request);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        accessMapper.insertRole(record);
        return toRoleResponse(requireRole(record.getId()));
    }

    @Transactional
    public RoleResponse updateRole(String id, RoleRequest request) {
        ensureDefaults();
        validateRole(request);
        SysRoleRecord record = requireRole(id);
        SysRoleRecord sameCode = accessMapper.selectRoleByCode(request.getRoleCode());
        if (sameCode != null && !id.equals(sameCode.getId())) {
            throw new BusinessException("ROLE_409", "角色编码已存在");
        }
        fillRole(record, request);
        record.setUpdatedAt(LocalDateTime.now());
        accessMapper.updateRole(record);
        return toRoleResponse(requireRole(id));
    }

    @Transactional
    public RoleResponse updateRoleStatus(String id, String status) {
        requireRole(id);
        accessMapper.updateRoleStatus(id, status);
        return toRoleResponse(requireRole(id));
    }

    @Transactional
    public void deleteRole(String id) {
        SysRoleRecord role = requireRole(id);
        if (BUILTIN_ROLE_CODES.contains(role.getRoleCode())) {
            throw new BusinessException("ROLE_409", "系统内置角色不允许删除，可改为停用");
        }
        int userRefs = accessMapper.countUserRolesByRoleId(id);
        if (userRefs > 0) {
            throw new BusinessException("ROLE_409", "该角色已绑定用户，请先解除用户角色关系，或改为停用角色");
        }
        int policyRefs = accessMapper.countDataPoliciesByRoleId(id);
        if (policyRefs > 0) {
            throw new BusinessException("ROLE_409", "该角色已被数据权限策略引用，请先调整策略，或改为停用角色");
        }
        accessMapper.deleteRolePermissions(id);
        accessMapper.deleteRole(id);
    }

    @Transactional
    public List<PermissionNodeResponse> permissionTree() {
        ensureDefaults();
        List<SysPermissionRecord> records = accessMapper.selectPermissions();
        Map<String, PermissionNodeResponse> nodeMap = new LinkedHashMap<>();
        for (SysPermissionRecord record : records) {
            PermissionNodeResponse node = new PermissionNodeResponse();
            node.setId(record.getPermissionCode());
            node.setLabel(record.getPermissionName());
            node.setType(record.getPermissionType());
            node.setRoutePath(record.getRoutePath());
            nodeMap.put(record.getPermissionCode(), node);
        }
        List<PermissionNodeResponse> roots = new ArrayList<>();
        for (SysPermissionRecord record : records) {
            PermissionNodeResponse node = nodeMap.get(record.getPermissionCode());
            if (StringUtils.hasText(record.getParentCode()) && nodeMap.containsKey(record.getParentCode())) {
                nodeMap.get(record.getParentCode()).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    @Transactional
    public List<String> rolePermissions(String roleId) {
        ensureDefaults();
        requireRole(roleId);
        return accessMapper.selectRolePermissionCodes(roleId);
    }

    @Transactional
    public List<String> saveRolePermissions(String roleId, RolePermissionRequest request) {
        ensureDefaults();
        requireRole(roleId);
        accessMapper.deleteRolePermissions(roleId);
        for (String permissionCode : safeList(request.getPermissionCodes())) {
            if (accessMapper.selectPermissionByCode(permissionCode) != null) {
                accessMapper.insertRolePermission(IdGenerator.nextId("RPERM"), roleId, permissionCode);
            }
        }
        return accessMapper.selectRolePermissionCodes(roleId);
    }

    @Transactional
    public List<DataPolicyResponse> dataPolicies(String keyword, String subjectType, String status) {
        ensureDefaults();
        return accessMapper.selectDataPolicies(keyword, subjectType, status).stream().map(this::toDataPolicyResponse).toList();
    }

    public List<DataPolicyResponse> dataPoliciesWithoutDefaults(String keyword, String subjectType, String status) {
        return accessMapper.selectDataPolicies(keyword, subjectType, status).stream().map(this::toDataPolicyResponse).toList();
    }

    @Transactional
    public DataPolicyResponse createDataPolicy(DataPolicyRequest request) {
        ensureDefaults();
        validateDataPolicy(request);
        SysDataPolicyRecord record = new SysDataPolicyRecord();
        record.setId(IdGenerator.nextId("DPOL"));
        fillDataPolicy(record, request);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        accessMapper.insertDataPolicy(record);
        savePolicyScopes(record.getId(), request);
        return toDataPolicyResponse(requireDataPolicy(record.getId()));
    }

    @Transactional
    public DataPolicyResponse updateDataPolicy(String id, DataPolicyRequest request) {
        ensureDefaults();
        validateDataPolicy(request);
        SysDataPolicyRecord record = requireDataPolicy(id);
        fillDataPolicy(record, request);
        record.setUpdatedAt(LocalDateTime.now());
        accessMapper.updateDataPolicy(record);
        savePolicyScopes(id, request);
        return toDataPolicyResponse(requireDataPolicy(id));
    }

    @Transactional
    public void deleteDataPolicy(String id) {
        requireDataPolicy(id);
        accessMapper.deleteDataPolicyScopes(id);
        accessMapper.deleteDataPolicy(id);
    }

    public Map<String, Object> previewDataPolicy(DataPolicyRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subject", request.getSubjectName());
        result.put("scope", request.getDataScope());
        result.put("allowExport", Boolean.TRUE.equals(request.getAllowExport()));
        result.put("effects", List.of(
                "任务中心、结果中心、落库数据查询后续接入后，将按该策略过滤数据",
                "当前版本仅维护策略和预览效果，不影响其他业务页面查询",
                "脱敏字段：" + String.join("、", safeList(request.getFieldMasking()))
        ));
        return result;
    }

    private void ensureDefaults() {
        if (accessMapper.countRoles() == 0) {
            LocalDateTime now = LocalDateTime.now();
            insertRole(role("biz_user", "普通业务用户", "上传文档、查看本人或本部门任务", 10, now));
            insertRole(role("reviewer", "复核人员", "处理低置信度和高风险字段复核", 20, now));
            insertRole(role("template_admin", "模板配置员", "维护文档类型、解析配置和提取配置", 30, now));
            insertRole(role("dept_admin", "部门管理员", "管理本部门用户、配置和任务", 40, now));
            insertRole(role("auditor", "审计人员", "查看审计日志和处理链路", 50, now));
            insertRole(role("system_account", "系统账号", "系统间接口接入账号", 60, now));
        }
        if (accessMapper.countPermissions() == 0) {
            defaultPermissions().forEach(accessMapper::insertPermission);
        }
        if (accessMapper.countUsers() == 0) {
            createDefaultUser("U001", "王老师", "wang.ops", "运营部", "SSO", List.of("reviewer"));
            createDefaultUser("U002", "李老师", "li.finance", "财务部", "SSO", List.of("biz_user"));
            createDefaultUser("U003", "赵老师", "zhao.config", "运营部", "LOCAL", List.of("template_admin"));
            createDefaultUser("U004", "陈老师", "chen.product", "产品部", "SSO", List.of("dept_admin"));
            createDefaultUser("U005", "系统接入账号", "api.dispatch", "平台", "LOCAL", List.of("system_account"));
        }
        if (accessMapper.countDataPolicies() == 0) {
            DataPolicyRequest request = new DataPolicyRequest();
            request.setPolicyName("运营复核岗数据权限");
            request.setSubjectType("ROLE");
            SysRoleRecord role = accessMapper.selectRoleByCode("reviewer");
            request.setSubjectId(role == null ? "" : role.getId());
            request.setSubjectName("复核人员");
            request.setDataScope("DEPARTMENT_AND_TYPES");
            request.setAllowExport(false);
            request.setStatus("ENABLED");
            request.setDepartments(List.of("运营部"));
            request.setDocumentTypes(List.of("划款指令", "开户资料"));
            request.setSourceSystems(List.of("MANUAL_UPLOAD", "EMAIL"));
            request.setConfigScopes(List.of("划款指令-运营部-提取配置"));
            request.setFieldMasking(List.of("certificate_no", "counterparty_account"));
            createDataPolicyWithoutEnsure(request);
        }
    }

    private void createDataPolicyWithoutEnsure(DataPolicyRequest request) {
        SysDataPolicyRecord record = new SysDataPolicyRecord();
        record.setId(IdGenerator.nextId("DPOL"));
        fillDataPolicy(record, request);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        accessMapper.insertDataPolicy(record);
        savePolicyScopes(record.getId(), request);
    }

    private void createDefaultUser(String userCode, String userName, String account, String department, String authMode, List<String> roleCodes) {
        SysUserRecord user = new SysUserRecord();
        user.setId(IdGenerator.nextId("USER"));
        user.setUserCode(userCode);
        user.setUserName(userName);
        user.setAccount(account);
        user.setDepartmentId(department);
        user.setAuthMode(authMode);
        user.setStatus("ENABLED");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(user.getCreatedAt());
        accessMapper.insertUser(user);
        List<String> roleIds = roleCodes.stream()
                .map(accessMapper::selectRoleByCode)
                .filter(Objects::nonNull)
                .map(SysRoleRecord::getId)
                .toList();
        saveUserRoles(user.getId(), department, roleIds);
    }

    private SysRoleRecord role(String code, String name, String description, int sortNo, LocalDateTime now) {
        SysRoleRecord role = new SysRoleRecord();
        role.setId(IdGenerator.nextId("ROLE"));
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setDescription(description);
        role.setStatus("ENABLED");
        role.setSortNo(sortNo);
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        return role;
    }

    private void insertRole(SysRoleRecord role) {
        accessMapper.insertRole(role);
    }

    private List<SysPermissionRecord> defaultPermissions() {
        List<SysPermissionRecord> records = new ArrayList<>();
        addPermission(records, "dashboard", "工作台", "MENU", null, "/dashboard", 10);
        addPermission(records, "documents", "文档接入", "MENU", null, null, 20);
        addPermission(records, "documents:upload", "手工上传", "MENU", "documents", "/documents/upload", 21);
        addPermission(records, "documents:records", "接入记录", "MENU", "documents", "/documents/records", 22);
        addPermission(records, "tasks", "任务中心", "MENU", null, "/tasks", 30);
        addPermission(records, "configs", "配置中心", "MENU", null, "/configs", 40);
        addPermission(records, "configs:view", "查看配置", "BUTTON", "configs", null, 41);
        addPermission(records, "configs:create", "新建配置", "BUTTON", "configs", null, 42);
        addPermission(records, "configs:publish", "发布配置", "BUTTON", "configs", null, 43);
        addPermission(records, "results", "结果中心", "MENU", null, "/results", 50);
        addPermission(records, "review:view", "查看复核任务", "BUTTON", "results", null, 51);
        addPermission(records, "review:submit", "提交复核", "BUTTON", "results", null, 52);
        addPermission(records, "system", "系统管理", "MENU", null, null, 90);
        addPermission(records, "system:users", "用户管理", "MENU", "system", "/system/users", 91);
        addPermission(records, "system:roles", "角色权限", "MENU", "system", "/system/roles", 92);
        addPermission(records, "system:data", "数据权限", "MENU", "system", "/system/data-permissions", 93);
        return records;
    }

    private void addPermission(List<SysPermissionRecord> records, String code, String name, String type, String parentCode, String routePath, int sortNo) {
        SysPermissionRecord record = new SysPermissionRecord();
        record.setId(IdGenerator.nextId("PERM"));
        record.setPermissionCode(code);
        record.setPermissionName(name);
        record.setPermissionType(type);
        record.setParentCode(parentCode);
        record.setRoutePath(routePath);
        record.setSortNo(sortNo);
        record.setStatus("ENABLED");
        records.add(record);
    }

    private void saveUserRoles(String userId, String departmentId, List<String> roleIds) {
        accessMapper.deleteUserRoles(userId);
        for (String roleId : safeList(roleIds)) {
            SysRoleRecord role = accessMapper.selectRoleById(roleId);
            if (role == null) {
                continue;
            }
            SysUserDepartmentRoleRecord relation = new SysUserDepartmentRoleRecord();
            relation.setId(IdGenerator.nextId("UROLE"));
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            relation.setDepartmentId(departmentId);
            accessMapper.insertUserRole(relation);
        }
    }

    private void savePolicyScopes(String policyId, DataPolicyRequest request) {
        accessMapper.deleteDataPolicyScopes(policyId);
        insertScopes(policyId, "DEPARTMENT", request.getDepartments());
        insertScopes(policyId, "DOCUMENT_TYPE", request.getDocumentTypes());
        insertScopes(policyId, "SOURCE_SYSTEM", request.getSourceSystems());
        insertScopes(policyId, "CONFIG", request.getConfigScopes());
        insertScopes(policyId, "FIELD_MASK", request.getFieldMasking());
    }

    private void insertScopes(String policyId, String scopeType, List<String> values) {
        for (String value : safeList(values)) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            SysDataPolicyScopeRecord scope = new SysDataPolicyScopeRecord();
            scope.setId(IdGenerator.nextId("DPSC"));
            scope.setPolicyId(policyId);
            scope.setScopeType(scopeType);
            scope.setScopeValue(value);
            scope.setScopeLabel(value);
            accessMapper.insertDataPolicyScope(scope);
        }
    }

    private void validateUser(UserRequest request) {
        if (!StringUtils.hasText(request.getUserName())) {
            throw new BusinessException("PARAM_400", "用户姓名不能为空");
        }
        if (!StringUtils.hasText(request.getAccount())) {
            throw new BusinessException("PARAM_400", "用户账号不能为空");
        }
        if (!StringUtils.hasText(request.getDepartmentId())) {
            throw new BusinessException("PARAM_400", "所属部门不能为空");
        }
    }

    private void validateRole(RoleRequest request) {
        if (!StringUtils.hasText(request.getRoleCode())) {
            throw new BusinessException("PARAM_400", "角色编码不能为空");
        }
        if (!request.getRoleCode().matches("^[a-zA-Z][a-zA-Z0-9_\\-]*$")) {
            throw new BusinessException("PARAM_400", "角色编码必须以字母开头，仅支持字母、数字、下划线和中划线");
        }
        if (!StringUtils.hasText(request.getRoleName())) {
            throw new BusinessException("PARAM_400", "角色名称不能为空");
        }
    }

    private void validateDataPolicy(DataPolicyRequest request) {
        if (!StringUtils.hasText(request.getPolicyName())) {
            throw new BusinessException("PARAM_400", "策略名称不能为空");
        }
        if (!StringUtils.hasText(request.getSubjectType())) {
            throw new BusinessException("PARAM_400", "授权对象类型不能为空");
        }
        if (!StringUtils.hasText(request.getDataScope())) {
            throw new BusinessException("PARAM_400", "数据范围不能为空");
        }
    }

    private void fillUser(SysUserRecord record, UserRequest request) {
        record.setUserCode(StringUtils.hasText(request.getUserCode()) ? request.getUserCode() : request.getAccount());
        record.setUserName(request.getUserName());
        record.setAccount(request.getAccount());
        record.setDepartmentId(request.getDepartmentId());
        record.setAuthMode(StringUtils.hasText(request.getAuthMode()) ? request.getAuthMode() : "LOCAL");
        record.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "ENABLED");
        record.setEmail(request.getEmail());
        record.setMobile(request.getMobile());
    }

    private void fillRole(SysRoleRecord record, RoleRequest request) {
        record.setRoleCode(request.getRoleCode());
        record.setRoleName(request.getRoleName());
        record.setDescription(request.getDescription());
        record.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "ENABLED");
        record.setSortNo(request.getSortNo() == null ? 100 : request.getSortNo());
    }

    private void fillDataPolicy(SysDataPolicyRecord record, DataPolicyRequest request) {
        record.setPolicyName(request.getPolicyName());
        record.setSubjectType(request.getSubjectType());
        record.setSubjectId(request.getSubjectId());
        record.setSubjectName(request.getSubjectName());
        record.setDataScope(request.getDataScope());
        record.setAllowExport(Boolean.TRUE.equals(request.getAllowExport()) ? "1" : "0");
        record.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "ENABLED");
    }

    private SysUserRecord requireUser(String id) {
        SysUserRecord record = accessMapper.selectUserById(id);
        if (record == null) {
            throw new BusinessException("USER_404", "用户不存在");
        }
        return record;
    }

    private SysRoleRecord requireRole(String id) {
        SysRoleRecord record = accessMapper.selectRoleById(id);
        if (record == null) {
            throw new BusinessException("ROLE_404", "角色不存在");
        }
        return record;
    }

    private SysDataPolicyRecord requireDataPolicy(String id) {
        SysDataPolicyRecord record = accessMapper.selectDataPolicyById(id);
        if (record == null) {
            throw new BusinessException("POLICY_404", "数据权限策略不存在");
        }
        return record;
    }

    private UserResponse toUserResponse(SysUserRecord record) {
        List<SysUserDepartmentRoleRecord> roles = accessMapper.selectUserRoles(record.getId());
        UserResponse response = new UserResponse();
        response.setId(record.getId());
        response.setUserCode(record.getUserCode());
        response.setUserName(record.getUserName());
        response.setAccount(record.getAccount());
        response.setDepartmentId(record.getDepartmentId());
        response.setAuthMode(record.getAuthMode());
        response.setStatus(record.getStatus());
        response.setEmail(record.getEmail());
        response.setMobile(record.getMobile());
        response.setLastLogin(record.getLastLogin());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        response.setRoleIds(roles.stream().map(SysUserDepartmentRoleRecord::getRoleId).toList());
        response.setRoleNames(roles.stream().map(SysUserDepartmentRoleRecord::getRoleName).toList());
        return response;
    }

    private RoleResponse toRoleResponse(SysRoleRecord record) {
        RoleResponse response = new RoleResponse();
        response.setId(record.getId());
        response.setRoleCode(record.getRoleCode());
        response.setRoleName(record.getRoleName());
        response.setDescription(record.getDescription());
        response.setStatus(record.getStatus());
        response.setSortNo(record.getSortNo());
        response.setUserCount(record.getUserCount() == null ? 0 : record.getUserCount());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }

    private DataPolicyResponse toDataPolicyResponse(SysDataPolicyRecord record) {
        List<SysDataPolicyScopeRecord> scopes = accessMapper.selectDataPolicyScopes(record.getId());
        DataPolicyResponse response = new DataPolicyResponse();
        response.setId(record.getId());
        response.setPolicyName(record.getPolicyName());
        response.setSubjectType(record.getSubjectType());
        response.setSubjectId(record.getSubjectId());
        response.setSubjectName(record.getSubjectName());
        response.setDataScope(record.getDataScope());
        response.setAllowExport("1".equals(record.getAllowExport()));
        response.setStatus(record.getStatus());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        response.setDepartments(scopeValues(scopes, "DEPARTMENT"));
        response.setDocumentTypes(scopeValues(scopes, "DOCUMENT_TYPE"));
        response.setSourceSystems(scopeValues(scopes, "SOURCE_SYSTEM"));
        response.setConfigScopes(scopeValues(scopes, "CONFIG"));
        response.setFieldMasking(scopeValues(scopes, "FIELD_MASK"));
        response.setScopeSummary(summary(response));
        return response;
    }

    private List<String> scopeValues(List<SysDataPolicyScopeRecord> scopes, String type) {
        return scopes.stream()
                .filter(scope -> type.equals(scope.getScopeType()))
                .map(SysDataPolicyScopeRecord::getScopeLabel)
                .toList();
    }

    private String summary(DataPolicyResponse response) {
        if ("ALL_READONLY".equals(response.getDataScope())) {
            return "全部只读";
        }
        List<String> parts = new ArrayList<>();
        if (!safeList(response.getDepartments()).isEmpty()) {
            parts.add("部门 " + safeList(response.getDepartments()).size() + " 个");
        }
        if (!safeList(response.getDocumentTypes()).isEmpty()) {
            parts.add("文档类型 " + safeList(response.getDocumentTypes()).size() + " 个");
        }
        if (!safeList(response.getConfigScopes()).isEmpty()) {
            parts.add("配置 " + safeList(response.getConfigScopes()).size() + " 个");
        }
        return parts.isEmpty() ? "未限制明细范围" : String.join("，", parts);
    }

    private <T> List<T> safeList(List<T> source) {
        return source == null ? List.of() : source;
    }
}
