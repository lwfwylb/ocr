import { request } from './http'

export interface SystemUser {
  id: string
  userCode?: string
  userName: string
  account: string
  departmentId: string
  authMode: 'LOCAL' | 'SSO'
  status: 'ENABLED' | 'DISABLED'
  email?: string
  mobile?: string
  lastLogin?: string
  createdAt?: string
  updatedAt?: string
  roleIds: string[]
  roleNames: string[]
}

export interface SystemUserPayload {
  userCode?: string
  userName: string
  account: string
  departmentId: string
  authMode: 'LOCAL' | 'SSO'
  status: 'ENABLED' | 'DISABLED'
  email?: string
  mobile?: string
  roleIds: string[]
}

export interface SystemRole {
  id: string
  roleCode: string
  roleName: string
  description?: string
  status: 'ENABLED' | 'DISABLED'
  sortNo?: number
  userCount?: number
  createdAt?: string
  updatedAt?: string
}

export interface SystemRolePayload {
  roleCode: string
  roleName: string
  description?: string
  status: 'ENABLED' | 'DISABLED'
  sortNo?: number
}

export interface PermissionNode {
  id: string
  label: string
  type: string
  routePath?: string
  children?: PermissionNode[]
}

export interface DataPolicy {
  id: string
  policyName: string
  subjectType: 'ROLE' | 'USER'
  subjectId?: string
  subjectName?: string
  dataScope: string
  allowExport: boolean
  status: 'ENABLED' | 'DISABLED'
  scopeSummary?: string
  departments: string[]
  documentTypes: string[]
  sourceSystems: string[]
  configScopes: string[]
  fieldMasking: string[]
  createdAt?: string
  updatedAt?: string
}

export type DataPolicyPayload = Omit<DataPolicy, 'id' | 'scopeSummary' | 'createdAt' | 'updatedAt'>

const toQuery = (params: Record<string, any> = {}) => {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  const query = search.toString()
  return query ? `?${query}` : ''
}

export function listSystemUsers(params: Record<string, any> = {}) {
  return request<SystemUser[]>(`/api/system/access/users${toQuery(params)}`)
}

export function createSystemUser(payload: SystemUserPayload) {
  return request<SystemUser>('/api/system/access/users', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateSystemUser(id: string, payload: SystemUserPayload) {
  return request<SystemUser>(`/api/system/access/users/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
}

export function enableSystemUser(id: string) {
  return request<SystemUser>(`/api/system/access/users/${id}/enable`, { method: 'POST' })
}

export function disableSystemUser(id: string) {
  return request<SystemUser>(`/api/system/access/users/${id}/disable`, { method: 'POST' })
}

export function listSystemRoles(params: Record<string, any> = {}) {
  return request<SystemRole[]>(`/api/system/access/roles${toQuery(params)}`)
}

export function createSystemRole(payload: SystemRolePayload) {
  return request<SystemRole>('/api/system/access/roles', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateSystemRole(id: string, payload: SystemRolePayload) {
  return request<SystemRole>(`/api/system/access/roles/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
}

export function enableSystemRole(id: string) {
  return request<SystemRole>(`/api/system/access/roles/${id}/enable`, { method: 'POST' })
}

export function disableSystemRole(id: string) {
  return request<SystemRole>(`/api/system/access/roles/${id}/disable`, { method: 'POST' })
}

export function getPermissionTree() {
  return request<PermissionNode[]>('/api/system/access/permissions/tree')
}

export function getRolePermissions(roleId: string) {
  return request<string[]>(`/api/system/access/roles/${roleId}/permissions`)
}

export function saveRolePermissions(roleId: string, permissionCodes: string[]) {
  return request<string[]>(`/api/system/access/roles/${roleId}/permissions`, {
    method: 'PUT',
    body: JSON.stringify({ permissionCodes })
  })
}

export function listDataPolicies(params: Record<string, any> = {}) {
  return request<DataPolicy[]>(`/api/system/access/data-policies${toQuery(params)}`)
}

export function createDataPolicy(payload: DataPolicyPayload) {
  return request<DataPolicy>('/api/system/access/data-policies', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateDataPolicy(id: string, payload: DataPolicyPayload) {
  return request<DataPolicy>(`/api/system/access/data-policies/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
}

export function deleteDataPolicy(id: string) {
  return request<void>(`/api/system/access/data-policies/${id}`, { method: 'DELETE' })
}

export function previewDataPolicy(payload: DataPolicyPayload) {
  return request<Record<string, any>>('/api/system/access/data-permissions/preview', { method: 'POST', body: JSON.stringify(payload) })
}
