import { pageRecords, request, toQuery, type PageQuery, type PageResponse } from './http'

export interface StorageTable {
  tableName: string
  tableCnName?: string
  description?: string
  rowCount: number
  relatedConfigs?: string
  lastStoredAt?: string
}

export interface StorageRecord {
  id: string
  taskId: string
  traceId: string
  documentId: string
  fileName?: string
  documentType?: string
  sourceType?: string
  businessNo?: string
  targetTable: string
  mappingProfile?: string
  storageData: Record<string, unknown>
  uniqueKeyJson?: string
  storageStatus: string
  duplicateStrategy?: string
  errorMessage?: string
  storedBy?: string
  storedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface StorageQuery {
  keyword?: string
  targetTable?: string
  documentType?: string
  sourceType?: string
  storageStatus?: string
  pageNo?: number | string
  pageSize?: number | string
}

export interface StorageExecutePayload {
  storedBy?: string
  duplicateStrategy?: string
}

export function listStorageTables(keyword?: string) {
  return request<StorageTable[]>(`/api/storage/tables${toQuery({ keyword })}`)
}

export function pageStorageRecords(params: StorageQuery & PageQuery) {
  return request<PageResponse<StorageRecord>>(`/api/storage/records${toQuery(params)}`)
}

export async function listStorageRecords(params: StorageQuery & PageQuery) {
  return pageRecords(await pageStorageRecords({ pageSize: 200, ...params }))
}

export function executeStorage(taskId: string, payload: StorageExecutePayload = {}) {
  return request<StorageRecord>(`/api/storage/records/${taskId}/execute`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}
