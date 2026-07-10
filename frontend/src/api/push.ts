import { request } from './http'

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
}

export interface PushExecutePayload {
  targetSystem?: string
  serviceCode?: string
  serviceName?: string
  pushMethod?: string
  triggerType?: string
  operator?: string
}

function toQuery(params: PushQuery) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value) searchParams.set(key, value)
  })
  const query = searchParams.toString()
  return query ? `?${query}` : ''
}

export function listPushRecords(params: PushQuery = {}) {
  return request<PushRecord[]>(`/api/push-records${toQuery(params)}`)
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
