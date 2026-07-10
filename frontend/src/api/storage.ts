import { request } from './http'

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
}

export interface StorageExecutePayload {
  storedBy?: string
  duplicateStrategy?: string
}

function toQuery(params: object) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value) searchParams.set(key, String(value))
  })
  const query = searchParams.toString()
  return query ? `?${query}` : ''
}

export function listStorageTables(keyword?: string) {
  return request<StorageTable[]>(`/api/storage/tables${toQuery({ keyword })}`)
}

export function listStorageRecords(params: StorageQuery) {
  return request<StorageRecord[]>(`/api/storage/records${toQuery(params)}`)
}

export function executeStorage(taskId: string, payload: StorageExecutePayload = {}) {
  return request<StorageRecord>(`/api/storage/records/${taskId}/execute`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}
