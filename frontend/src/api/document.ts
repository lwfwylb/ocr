import { pageRecords, request, toQuery, type PageQuery, type PageResponse } from './http'

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

export function pageDocumentAccessRecords(params: Record<string, unknown> & PageQuery) {
  return request<PageResponse<DocumentAccessRecord>>(`/api/documents/access-records${toQuery(params)}`)
}

export async function listDocumentAccessRecords(params: Record<string, unknown> & PageQuery) {
  return pageRecords(await pageDocumentAccessRecords({ pageSize: 200, ...params }))
}

export function pagePendingDocuments(params: Record<string, unknown> & PageQuery) {
  return request<PageResponse<DocumentAccessRecord>>(`/api/documents/pending-confirm${toQuery(params)}`)
}

export async function listPendingDocuments(params: Record<string, unknown> & PageQuery) {
  return pageRecords(await pagePendingDocuments({ pageSize: 200, ...params }))
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

export function manualUploadDocumentFile(payload: {
  configId: string
  file: File
  businessNo?: string
  priority?: string
}) {
  const formData = new FormData()
  formData.set('configId', payload.configId)
  formData.set('file', payload.file)
  if (payload.businessNo) formData.set('businessNo', payload.businessNo)
  if (payload.priority) formData.set('priority', payload.priority)
  return request<DocumentAccessRecord>('/api/documents/manual-upload', {
    method: 'POST',
    body: formData
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
