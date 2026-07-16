import { pageRecords, request, toQuery, type PageQuery, type PageResponse } from './http'
import type { ResultSummary } from './result'

export interface ReviewField {
  fieldCode: string
  fieldName: string
  rawValue: unknown
  finalValue: unknown
  confidence: number
  reviewRequired: boolean
  issue?: string
  evidence?: string
}

export interface ReviewLog {
  id: string
  taskId: string
  traceId: string
  action: 'SAVE_DRAFT' | 'APPROVE' | 'REJECT' | string
  comment?: string
  reviewer?: string
  createdAt: string
}

export interface ReviewDetail {
  summary: ResultSummary
  parseText?: string
  fields: ReviewField[]
  logs: ReviewLog[]
}

export interface ReviewQuery {
  keyword?: string
  departmentId?: string
  documentType?: string
  sourceType?: string
  pageNo?: number | string
  pageSize?: number | string
}

export interface ReviewSubmitPayload {
  fields: Array<{
    fieldCode: string
    finalValue: unknown
  }>
  comment?: string
  reviewer?: string
}

export function pageReviewTasks(params: ReviewQuery & PageQuery) {
  return request<PageResponse<ResultSummary>>(`/api/reviews${toQuery(params)}`)
}

export async function listReviewTasks(params: ReviewQuery & PageQuery) {
  return pageRecords(await pageReviewTasks({ pageSize: 200, ...params }))
}

export function getReviewDetail(taskId: string) {
  return request<ReviewDetail>(`/api/reviews/${taskId}`)
}

export function saveReviewDraft(taskId: string, payload: ReviewSubmitPayload) {
  return request<ReviewDetail>(`/api/reviews/${taskId}/draft`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function approveReview(taskId: string, payload: ReviewSubmitPayload) {
  return request<ReviewDetail>(`/api/reviews/${taskId}/approve`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function rejectReview(taskId: string, payload: ReviewSubmitPayload) {
  return request<ReviewDetail>(`/api/reviews/${taskId}/reject`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}
