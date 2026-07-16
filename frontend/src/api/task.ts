import { pageRecords, request, toQuery, type PageQuery, type PageResponse } from './http'

export interface ExtractTask {
  id: string
  taskId: string
  traceId: string
  documentId: string
  accessRecordId?: string
  configId?: string
  configName?: string
  configVersion?: number
  fileName: string
  fileType?: string
  fileSize?: number
  storagePath?: string
  sourceType?: string
  sourceSystem?: string
  businessNo?: string
  departmentId: string
  category?: string
  subCategory?: string
  templateType?: string
  documentType?: string
  priority: 'HIGH' | 'MEDIUM' | 'LOW' | string
  status: string
  currentStage?: string
  progress?: number
  queueLevel?: 'HIGH' | 'MEDIUM' | 'LOW' | string
  queueName?: string
  queueCapacity?: number
  queuePosition?: number
  waitingMinutes?: number
  estimatedStartAt?: string
  manualAccelerated?: boolean
  dispatchReason?: string
  errorCode?: string
  errorMessage?: string
  failedStage?: string
  retryCount?: number
  maxRetry?: number
  retryable?: boolean
  failedAt?: string
  createdAt: string
  updatedAt: string
}

export interface TaskStageLog {
  id: string
  taskId: string
  traceId: string
  stageCode: string
  stageName: string
  status: string
  inputSummary?: string
  outputSummary?: string
  errorCode?: string
  errorMessage?: string
  startedAt?: string
  endedAt?: string
  durationMs?: number
  createdAt: string
}

export interface TaskQuery {
  keyword?: string
  sourceType?: string
  documentType?: string
  departmentId?: string
  priority?: string
  status?: string
  pageNo?: number | string
  pageSize?: number | string
}

export interface TaskDispatchPayload {
  mode: string
  targetPriority: string
  position?: number
  durationMinutes?: number
  reason: string
}

export interface TaskRetryPayload {
  retryMode: string
  priority?: string
  reason?: string
}

export function pageTasks(params: TaskQuery & PageQuery) {
  return request<PageResponse<ExtractTask>>(`/api/tasks${toQuery(params)}`)
}

export async function listTasks(params: TaskQuery & PageQuery) {
  return pageRecords(await pageTasks({ pageSize: 200, ...params }))
}

export function pageFailedTasks(params: TaskQuery & PageQuery) {
  return request<PageResponse<ExtractTask>>(`/api/tasks/failed${toQuery(params)}`)
}

export async function listFailedTasks(params: TaskQuery & PageQuery) {
  return pageRecords(await pageFailedTasks({ pageSize: 200, ...params }))
}

export function getTaskDetail(taskId: string) {
  return request<ExtractTask>(`/api/tasks/${taskId}`)
}

export function dispatchTask(taskId: string, payload: TaskDispatchPayload) {
  return request<ExtractTask>(`/api/tasks/${taskId}/dispatch`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function retryTask(taskId: string, payload: TaskRetryPayload) {
  return request<ExtractTask>(`/api/tasks/${taskId}/retry`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function executeTask(taskId: string) {
  return request<ExtractTask>(`/api/tasks/${taskId}/execute`, { method: 'POST' })
}

export function executeNextTask(departmentId?: string) {
  const query = departmentId ? `?${new URLSearchParams({ departmentId }).toString()}` : ''
  return request<ExtractTask>(`/api/tasks/execute-next${query}`, { method: 'POST' })
}

export function listTaskStageLogs(taskId: string) {
  return request<TaskStageLog[]>(`/api/tasks/${taskId}/stage-logs`)
}
