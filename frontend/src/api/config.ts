import { request } from './http'

export interface ConfigSummary {
  id: string
  configCode: string
  configName: string
  category: string
  subCategory: string
  templateType: string
  documentType: string
  departmentId: string
  ownerRole: string
  defaultPriority: string
  status: 'DRAFT' | 'TESTING' | 'PUBLISHED' | 'DISABLED'
  version: number
  parseEngine: string
  targetTable: string
  mappingProfile: string
  confidenceThreshold: number
  createdBy: string
  updatedBy: string
  publishedAt?: string
  createdAt: string
  updatedAt: string
}

export interface ConfigDetail {
  summary: ConfigSummary
  payload: any
}

export interface ConfigValidateResult {
  passed: boolean
  errors: string[]
  checkedAt: string
  message: string
}

export interface ConfigOptions {
  departments: Array<Record<string, any>>
  roles: Array<Record<string, any>>
  categories: Array<Record<string, any>>
  ocrEngines: Array<Record<string, any>>
  resultTables: Array<Record<string, any>>
  downstreamServices: Array<Record<string, any>>
}

export function listExtractConfigs(params: Record<string, string>) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value) searchParams.set(key, value)
  })
  const query = searchParams.toString()
  return request<ConfigSummary[]>(`/api/config/extract-configs${query ? `?${query}` : ''}`)
}

export function getExtractConfigDetail(id: string) {
  return request<ConfigDetail>(`/api/config/extract-configs/${id}`)
}

export function listExtractConfigVersions(id: string) {
  return request<ConfigSummary[]>(`/api/config/extract-configs/${id}/versions`)
}

export function createExtractConfigDraft(payload: unknown) {
  return request<ConfigDetail>('/api/config/extract-configs/draft', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateExtractConfigDraft(id: string, payload: unknown) {
  return request<ConfigDetail>(`/api/config/extract-configs/${id}/draft`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function copyExtractConfig(id: string) {
  return request<ConfigDetail>(`/api/config/extract-configs/${id}/copy`, { method: 'POST' })
}

export function publishExtractConfig(id: string) {
  return request<ConfigDetail>(`/api/config/extract-configs/${id}/publish`, { method: 'POST' })
}

export function disableExtractConfig(id: string) {
  return request<ConfigDetail>(`/api/config/extract-configs/${id}/disable`, { method: 'POST' })
}

export function validateExtractConfig(id: string) {
  return request<ConfigValidateResult>(`/api/config/extract-configs/${id}/validate`, { method: 'POST' })
}

export function getConfigOptions() {
  return request<ConfigOptions>('/api/config/options')
}
