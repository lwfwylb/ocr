import { request } from './http'

export interface ResultSummary {
  taskId: string
  traceId: string
  documentId: string
  fileName?: string
  documentType?: string
  departmentId?: string
  sourceType?: string
  resultStatus: 'STORED' | 'WAIT_REVIEW' | 'PUSHED' | 'FAILED' | string
  reviewStatus?: string
  targetTable?: string
  mappingProfile?: string
  fieldCount?: number
  overallConfidence?: number
  createdAt?: string
  updatedAt?: string
}

export interface ResultField {
  fieldCode: string
  fieldName: string
  extractField: string
  targetColumn: string
  rawValue?: unknown
  finalValue?: unknown
  confidence?: number
  reviewRequired?: boolean
  sourceType?: string
  issue?: string
  sourcePage?: string
}

export interface StoragePreview {
  targetTable: string
  targetTableName?: string
  targetColumn: string
  columnName?: string
  dbType?: string
  typeDescription?: string
  value?: unknown
  required?: boolean
  uniqueKey?: boolean
  ready?: boolean
  issue?: string
  transform?: string
}

export interface ResultDetail {
  summary: ResultSummary
  parseText?: string
  pageCount?: number
  engineCode?: string
  result?: Record<string, unknown>
  confidence?: Record<string, unknown>
  fields?: ResultField[]
  storagePreview?: StoragePreview[]
}

export interface ResultQuery {
  keyword?: string
  documentType?: string
  departmentId?: string
  sourceType?: string
  resultStatus?: string
}

function toQuery(params: ResultQuery) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value) searchParams.set(key, value)
  })
  const query = searchParams.toString()
  return query ? `?${query}` : ''
}

export function listResults(params: ResultQuery) {
  return request<ResultSummary[]>(`/api/results${toQuery(params)}`)
}

export function getResultDetail(taskId: string) {
  return request<ResultDetail>(`/api/results/${taskId}`)
}
