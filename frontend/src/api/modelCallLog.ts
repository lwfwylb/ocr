import { pageRecords, request, toQuery, type PageQuery, type PageResponse } from './http'

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
  pageNo?: number | string
  pageSize?: number | string
}

export function pageModelCallLogs(params: ModelCallLogQuery & PageQuery = {}) {
  return request<PageResponse<ModelCallLog>>(`/api/model-call-logs${toQuery(params)}`)
}

export async function listModelCallLogs(params: ModelCallLogQuery & PageQuery = {}) {
  return pageRecords(await pageModelCallLogs({ pageSize: 200, ...params }))
}

export function getModelCallLogDetail(id: string) {
  return request<ModelCallLog>(`/api/model-call-logs/${id}`)
}
