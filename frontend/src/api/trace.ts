import { request } from './http'
import type { ResultDetail } from './result'
import type { ReviewLog } from './review'
import type { StorageRecord } from './storage'
import type { ExtractTask } from './task'
import type { PushRecord } from './push'

export interface TraceSummary {
  traceId: string
  taskId?: string
  documentId?: string
  fileName?: string
  businessNo?: string
  sourceType?: string
  documentType?: string
  departmentId?: string
  currentStage?: string
  status?: string
  progress?: number
  createdAt?: string
  updatedAt?: string
}

export interface TraceStage {
  stageCode: string
  stageName: string
  status: string
  inputSummary?: string
  outputSummary?: string
  errorCode?: string
  errorMessage?: string
  durationMs?: number
  startedAt?: string
  endedAt?: string
}

export interface DocumentArtifact {
  id: string
  traceId: string
  taskId?: string
  documentId?: string
  parentId?: string
  artifactType: string
  stageCode: string
  fileName?: string
  fileExt?: string
  mimeType?: string
  fileSize?: number
  checksum?: string
  pageNo?: number
  pageRange?: string
  sortNo?: number
  status?: string
  metadataJson?: string
  createdAt?: string
  updatedAt?: string
}

export interface DocumentArtifactStep {
  id: string
  traceId: string
  taskId?: string
  stepCode?: string
  stepName?: string
  stepType?: string
  inputArtifactIds?: string
  outputArtifactIds?: string
  configJson?: string
  status?: string
  errorMessage?: string
  startedAt?: string
  endedAt?: string
  durationMs?: number
  createdAt?: string
}

export interface TraceDetail {
  summary: TraceSummary
  accessRecord?: Record<string, unknown>
  task?: ExtractTask
  result?: ResultDetail
  storageRecord?: StorageRecord
  pushRecords: PushRecord[]
  stages: TraceStage[]
  artifacts: DocumentArtifact[]
  artifactSteps: DocumentArtifactStep[]
  reviewLogs: ReviewLog[]
  suggestions: string[]
}

export interface TraceQuery {
  keyword?: string
  sourceType?: string
  status?: string
}

function toQuery(params: TraceQuery) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value) searchParams.set(key, value)
  })
  const query = searchParams.toString()
  return query ? `?${query}` : ''
}

export function listTraces(params: TraceQuery) {
  return request<TraceSummary[]>(`/api/traces${toQuery(params)}`)
}

export function getTraceDetail(traceId: string) {
  return request<TraceDetail>(`/api/traces/${traceId}`)
}
