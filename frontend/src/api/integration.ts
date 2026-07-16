import { pageRecords, request, toQuery, type PageResponse } from './http'

export type IntegrationServiceType = 'HTTP' | 'MICROSERVICE' | 'MQ' | string

export interface DownstreamSystem {
  id: string
  systemCode: string
  systemName: string
  ownerDepartmentId?: string
  defaultAuthMode?: string
  defaultTimeoutSeconds?: number
  defaultRetryCount?: number
  status: string
  enabled: boolean
  serviceCount: number
  enabledServiceCount: number
  successRate: number
  createdAt?: string
  updatedAt?: string
}

export interface DownstreamService {
  id: string
  systemId: string
  systemCode: string
  systemName: string
  ownerDepartmentId?: string
  serviceCode: string
  serviceName: string
  purpose?: string
  serviceType: IntegrationServiceType
  endpoint?: string
  httpMethod?: string
  authMode?: string
  timeoutSeconds?: number
  retryCount?: number
  responseSuccessRule?: string
  enabled: boolean
  boundConfigCount: number
  successRate: number
  createdAt?: string
  updatedAt?: string
}

export interface IntegrationQuery {
  keyword?: string
  ownerDepartmentId?: string
  serviceType?: string
  status?: string
  systemCode?: string
  pageNo?: number | string
  pageSize?: number | string
}

export interface DownstreamSystemPayload {
  systemCode: string
  systemName: string
  ownerDepartmentId?: string
  defaultAuthMode?: string
  defaultTimeoutSeconds?: number
  defaultRetryCount?: number
  status?: string
}

export interface DownstreamServicePayload {
  systemId: string
  serviceCode: string
  serviceName: string
  purpose?: string
  serviceType: string
  endpoint?: string
  httpMethod?: string
  authMode?: string
  timeoutSeconds?: number
  retryCount?: number
  responseSuccessRule?: string
  enabled?: boolean
}

export function pageIntegrationSystems(params: IntegrationQuery = {}) {
  return request<PageResponse<DownstreamSystem>>(`/api/integrations/systems${toQuery(params)}`)
}

export async function listIntegrationSystems(params: IntegrationQuery = {}) {
  return pageRecords(await pageIntegrationSystems({ pageSize: 200, ...params }))
}

export function pageIntegrationServices(params: IntegrationQuery = {}) {
  return request<PageResponse<DownstreamService>>(`/api/integrations/services${toQuery(params)}`)
}

export async function listIntegrationServices(params: IntegrationQuery = {}) {
  return pageRecords(await pageIntegrationServices({ pageSize: 200, ...params }))
}

export function createIntegrationSystem(payload: DownstreamSystemPayload) {
  return request<DownstreamSystem>('/api/integrations/systems', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateIntegrationSystem(id: string, payload: DownstreamSystemPayload) {
  return request<DownstreamSystem>(`/api/integrations/systems/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function createIntegrationService(payload: DownstreamServicePayload) {
  return request<DownstreamService>('/api/integrations/services', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateIntegrationService(id: string, payload: DownstreamServicePayload) {
  return request<DownstreamService>(`/api/integrations/services/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function deleteIntegrationSystem(id: string) {
  return request<void>(`/api/integrations/systems/${id}`, { method: 'DELETE' })
}

export function deleteIntegrationService(id: string) {
  return request<void>(`/api/integrations/services/${id}`, { method: 'DELETE' })
}

export function enableIntegrationSystem(id: string) {
  return request<DownstreamSystem>(`/api/integrations/systems/${id}/enable`, { method: 'POST' })
}

export function disableIntegrationSystem(id: string) {
  return request<DownstreamSystem>(`/api/integrations/systems/${id}/disable`, { method: 'POST' })
}

export function enableIntegrationService(id: string) {
  return request<DownstreamService>(`/api/integrations/services/${id}/enable`, { method: 'POST' })
}

export function disableIntegrationService(id: string) {
  return request<DownstreamService>(`/api/integrations/services/${id}/disable`, { method: 'POST' })
}

export function testIntegrationService(id: string) {
  return request<Record<string, unknown>>(`/api/integrations/services/${id}/test`, { method: 'POST' })
}
