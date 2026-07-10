import { request } from './http'

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
}

function toQuery(params: IntegrationQuery) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value) searchParams.set(key, value)
  })
  const query = searchParams.toString()
  return query ? `?${query}` : ''
}

export function listIntegrationSystems(params: IntegrationQuery = {}) {
  return request<DownstreamSystem[]>(`/api/integrations/systems${toQuery(params)}`)
}

export function listIntegrationServices(params: IntegrationQuery = {}) {
  return request<DownstreamService[]>(`/api/integrations/services${toQuery(params)}`)
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
