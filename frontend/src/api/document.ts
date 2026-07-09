import { request } from './http'

export interface DocumentAccessRecord {
  id: string
  traceId: string
  documentId: string
  taskId?: string
  fileName: string
  fileType?: string
  fileSize?: number
  storagePath?: string
  sourceType: string
  sourceSystem?: string
  businessNo?: string
  departmentId?: string
  category?: string
  subCategory?: string
  templateType?: string
  documentType?: string
  priority?: 'HIGH' | 'MEDIUM' | 'LOW' | string
  matchStatus: 'MATCHED' | 'UNMATCHED' | 'MULTIPLE'
  accessStatus: 'PENDING_CONFIRM' | 'CREATED_TASK' | 'REJECTED'
  matchedConfigId?: string
  matchedConfigName?: string
  matchedConfigVersion?: number
  matchMessage?: string
  confirmComment?: string
  confirmedAt?: string
  createdAt: string
  updatedAt: string
}

export interface DocumentAccessPayload {
  configId?: string
  sourceType?: string
  sourceSystem?: string
  businessNo?: string
  departmentId?: string
  category?: string
  subCategory?: string
  templateType?: string
  documentType?: string
  priority?: string
  fileName: string
  fileType?: string
  fileSize?: number
  storagePath?: string
}

export interface DocumentConfirmPayload {
  category?: string
  subCategory?: string
  templateType?: string
  documentType?: string
  configId: string
  priority?: string
  comment?: string
}

function toQuery(params: Record<string, string>) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value) searchParams.set(key, value)
  })
  const query = searchParams.toString()
  return query ? `?${query}` : ''
}

export function listDocumentAccessRecords(params: Record<string, string>) {
  return request<DocumentAccessRecord[]>(`/api/documents/access-records${toQuery(params)}`)
}

export function listPendingDocuments(params: Record<string, string>) {
  return request<DocumentAccessRecord[]>(`/api/documents/pending-confirm${toQuery(params)}`)
}

export function getDocumentAccessRecord(id: string) {
  return request<DocumentAccessRecord>(`/api/documents/access-records/${id}`)
}

export function manualUploadDocument(payload: DocumentAccessPayload) {
  return request<DocumentAccessRecord>('/api/documents/manual-upload', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function pushDocumentByApi(payload: DocumentAccessPayload) {
  return request<DocumentAccessRecord>('/api/documents/api-push', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function rematchDocument(id: string) {
  return request<DocumentAccessRecord>(`/api/documents/access-records/${id}/rematch`, { method: 'POST' })
}

export function confirmDocument(id: string, payload: DocumentConfirmPayload) {
  return request<DocumentAccessRecord>(`/api/documents/access-records/${id}/confirm`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}
