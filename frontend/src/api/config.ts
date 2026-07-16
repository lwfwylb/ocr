import { pageRecords, request, type PageQuery, type PageResponse } from './http'

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
  currentEffective?: boolean
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
  warnings?: string[]
  sections?: Array<{
    code: string
    title: string
    status: 'PASSED' | 'WARNING' | 'FAILED'
    items: Array<{
      level: 'INFO' | 'WARN' | 'ERROR'
      message: string
    }>
  }>
  ddlPreview?: string
  checkedAt: string
  message: string
}

export interface ConfigOptions {
  departments: Array<Record<string, any>>
  roles: Array<Record<string, any>>
  categories: Array<Record<string, any>>
  documentTypes: Array<Record<string, any>>
  ocrEngines: Array<Record<string, any>>
  resultTables: Array<Record<string, any>>
  downstreamServices: Array<Record<string, any>>
}

export interface ResultTableColumnOption {
  columnName: string
  columnCnName: string
  dbType: string
  length?: number
  precision?: number
  scale?: number
  required?: boolean
  defaultValue?: string
  validationRule?: string
}

export interface ResultTableDetail {
  id: string
  tableCode: string
  tableName: string
  tableComment?: string
  ownerDepartmentId?: string
  status: string
  columns: ResultTableColumnOption[]
}

export function pageExtractConfigs(params: Record<string, unknown> & PageQuery) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value) searchParams.set(key, String(value))
  })
  const query = searchParams.toString()
  return request<PageResponse<ConfigSummary>>(`/api/config/extract-configs${query ? `?${query}` : ''}`)
}

export async function listExtractConfigs(params: Record<string, unknown> & PageQuery) {
  return pageRecords(await pageExtractConfigs({ pageSize: 200, ...params }))
}

export function getExtractConfigDetail(id: string) {
  return request<ConfigDetail>(`/api/config/extract-configs/${id}`)
}

export function getEffectiveExtractConfig(configName: string) {
  const searchParams = new URLSearchParams({ configName })
  return request<ConfigDetail>(`/api/config/extract-configs/effective?${searchParams.toString()}`)
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

export function deleteExtractConfigDraft(id: string) {
  return request<void>(`/api/config/extract-configs/${id}`, { method: 'DELETE' })
}

export function validateExtractConfig(id: string) {
  return request<ConfigValidateResult>(`/api/config/extract-configs/${id}/validate`, { method: 'POST' })
}

export function listResultTables(keyword?: string) {
  const searchParams = new URLSearchParams()
  if (keyword) searchParams.set('keyword', keyword)
  const query = searchParams.toString()
  return request<Array<Record<string, any>>>(`/api/config/result-tables${query ? `?${query}` : ''}`)
}

export function getResultTableDetail(tableCode: string) {
  return request<ResultTableDetail>(`/api/config/result-tables/${encodeURIComponent(tableCode)}`)
}

export function getConfigOptions() {
  return request<ConfigOptions>('/api/config/options')
}
