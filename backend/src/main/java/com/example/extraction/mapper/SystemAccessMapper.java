package com.example.extraction.mapper;

import com.example.extraction.system.domain.SysDataPolicyRecord;
import com.example.extraction.system.domain.SysDataPolicyScopeRecord;
import com.example.extraction.system.domain.SysPermissionRecord;
import com.example.extraction.system.domain.SysRoleRecord;
import com.example.extraction.system.domain.SysUserDepartmentRoleRecord;
import com.example.extraction.system.domain.SysUserRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SystemAccessMapper {
    int countUsers();
    int countRoles();
    int countPermissions();
    int countDataPolicies();

    List<SysUserRecord> selectUsers(@Param("keyword") String keyword,
                                    @Param("departmentId") String departmentId,
                                    @Param("status") String status);
    SysUserRecord selectUserById(@Param("id") String id);
    SysUserRecord selectUserByAccount(@Param("account") String account);
    void insertUser(SysUserRecord record);
    int updateUser(SysUserRecord record);
    int updateUserStatus(@Param("id") String id, @Param("status") String status);

    List<SysRoleRecord> selectRoles(@Param("keyword") String keyword, @Param("status") String status);
    SysRoleRecord selectRoleById(@Param("id") String id);
    SysRoleRecord selectRoleByCode(@Param("roleCode") String roleCode);
    void insertRole(SysRoleRecord record);
    int updateRole(SysRoleRecord record);
    int updateRoleStatus(@Param("id") String id, @Param("status") String status);
    int deleteRole(@Param("id") String id);

    List<SysUserDepartmentRoleRecord> selectUserRoles(@Param("userId") String userId);
    int countUserRolesByRoleId(@Param("roleId") String roleId);
    int countDataPoliciesByRoleId(@Param("roleId") String roleId);
    void insertUserRole(SysUserDepartmentRoleRecord record);
    int deleteUserRoles(@Param("userId") String userId);

    List<SysPermissionRecord> selectPermissions();
    SysPermissionRecord selectPermissionByCode(@Param("permissionCode") String permissionCode);
    void insertPermission(SysPermissionRecord record);
    List<String> selectRolePermissionCodes(@Param("roleId") String roleId);
    int deleteRolePermissions(@Param("roleId") String roleId);
    void insertRolePermission(@Param("id") String id, @Param("roleId") String roleId, @Param("permissionCode") String permissionCode);

    List<SysDataPolicyRecord> selectDataPolicies(@Param("keyword") String keyword,
                                                 @Param("subjectType") String subjectType,
                                                 @Param("status") String status);
    SysDataPolicyRecord selectDataPolicyById(@Param("id") String id);
    void insertDataPolicy(SysDataPolicyRecord record);
    int updateDataPolicy(SysDataPolicyRecord record);
    int deleteDataPolicy(@Param("id") String id);
    List<SysDataPolicyScopeRecord> selectDataPolicyScopes(@Param("policyId") String policyId);
    void insertDataPolicyScope(SysDataPolicyScopeRecord record);
    int deleteDataPolicyScopes(@Param("policyId") String policyId);
}
