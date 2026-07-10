import { request } from './http'

export type ModelCallType = 'OCR' | 'LLM' | string
export type ModelCallStatus = 'SUCCESS' | 'FAILED' | string

export interface ModelCallLog {
  id: string
  callId: string
  traceId?: string
  taskId?: string
  configId?: string
  callType: ModelCallType
  stageCode?: string
  stageName?: string
  provider?: string
  modelCode?: string
  modelName?: string
  requestSummary?: string
  responseSummary?: string
  promptPreview?: string
  inputTokens?: number
  outputTokens?: number
  durationMs?: number
  status: ModelCallStatus
  errorMessage?: string
  createdAt?: string
}

export interface ModelCallLogQuery {
  keyword?: string
  callType?: string
  status?: string
  stageCode?: string
  modelCode?: string
}

function toQuery(params: ModelCallLogQuery) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value) searchParams.set(key, value)
  })
  const query = searchParams.toString()
  return query ? `?${query}` : ''
}

export function listModelCallLogs(params: ModelCallLogQuery = {}) {
  return request<ModelCallLog[]>(`/api/model-call-logs${toQuery(params)}`)
}

export function getModelCallLogDetail(id: string) {
  return request<ModelCallLog>(`/api/model-call-logs/${id}`)
}
