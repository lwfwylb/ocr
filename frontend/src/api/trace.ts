import { pageRecords, request, toQuery, type PageResponse } from './http'
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
  previewUrl?: string
  downloadUrl?: string
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
  pageNo?: number
  pageSize?: number
}

export function pageTraces(params: TraceQuery) {
  return request<PageResponse<TraceSummary>>(`/api/traces${toQuery(params)}`)
}

export async function listTraces(params: TraceQuery) {
  return pageRecords(await pageTraces({ pageSize: 200, ...params }))
}

export function getTraceDetail(traceId: string) {
  return request<TraceDetail>(`/api/traces/${traceId}`)
}
