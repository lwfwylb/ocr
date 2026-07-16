import { pageRecords, request, toQuery, type PageQuery, type PageResponse } from './http'

export type PushStatus = 'SUCCESS' | 'PENDING' | 'FAILED' | 'RETRYING' | string
export type PushMethod = 'HTTP' | 'MICROSERVICE' | 'MQ' | string

export interface PushRecord {
  id: string
  pushId: string
  traceId: string
  taskId: string
  documentId?: string
  configId?: string
  targetSystem?: string
  serviceCode?: string
  serviceName?: string
  pushMethod?: PushMethod
  triggerType?: string
  idempotentKey?: string
  requestPayload?: string
  responsePayload?: string
  status: PushStatus
  retryCount?: number
  maxRetry?: number
  responseCode?: string
  responseMessage?: string
  pushedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface PushQuery {
  keyword?: string
  targetSystem?: string
  serviceCode?: string
  status?: string
  pushMethod?: string
  pageNo?: number | string
  pageSize?: number | string
}

export interface PushExecutePayload {
  targetSystem?: string
  serviceCode?: string
  serviceName?: string
  pushMethod?: string
  triggerType?: string
  operator?: string
}

export function pagePushRecords(params: PushQuery & PageQuery = {}) {
  return request<PageResponse<PushRecord>>(`/api/push-records${toQuery(params)}`)
}

export async function listPushRecords(params: PushQuery & PageQuery = {}) {
  return pageRecords(await pagePushRecords({ pageSize: 200, ...params }))
}

export function pushResultToDownstream(taskId: string, payload: PushExecutePayload = {}) {
  return request<PushRecord>(`/api/push-records/${taskId}/push`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function retryPushRecord(pushId: string) {
  return request<PushRecord>(`/api/push-records/${pushId}/retry`, { method: 'POST' })
}

export function markPushRecordSuccess(pushId: string) {
  return request<PushRecord>(`/api/push-records/${pushId}/mark-success`, { method: 'POST' })
}
