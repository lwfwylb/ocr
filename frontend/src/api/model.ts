import { request } from './http'

export interface LlmModelConfig {
  id: string
  modelCode: string
  modelName: string
  provider: string
  baseUrl: string
  apiKeySecretRef?: string
  modelIdentifier: string
  temperature: number
  maxTokens: number
  timeoutSeconds: number
  retryCount: number
  jsonSchemaRequired: boolean
  defaultModel: boolean
  status: 'ENABLED' | 'DISABLED'
  description?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface LlmModelOption {
  value: string
  label: string
  modelCode: string
  modelName: string
  provider: string
  defaultModel: boolean
}

export interface LlmModelTestResult {
  passed: boolean
  message: string
  modelCode: string
  modelIdentifier: string
  checkedAt: string
}

export type LlmModelPayload = Omit<LlmModelConfig, 'id' | 'createdBy' | 'createdAt' | 'updatedAt'>

export function listLlmModelConfigs(params: Record<string, string>) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value) searchParams.set(key, value)
  })
  const query = searchParams.toString()
  return request<LlmModelConfig[]>(`/api/model/llm-configs${query ? `?${query}` : ''}`)
}

export function getLlmModelConfig(id: string) {
  return request<LlmModelConfig>(`/api/model/llm-configs/${id}`)
}

export function createLlmModelConfig(payload: LlmModelPayload) {
  return request<LlmModelConfig>('/api/model/llm-configs', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateLlmModelConfig(id: string, payload: LlmModelPayload) {
  return request<LlmModelConfig>(`/api/model/llm-configs/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function enableLlmModelConfig(id: string) {
  return request<LlmModelConfig>(`/api/model/llm-configs/${id}/enable`, { method: 'POST' })
}

export function disableLlmModelConfig(id: string) {
  return request<LlmModelConfig>(`/api/model/llm-configs/${id}/disable`, { method: 'POST' })
}

export function setDefaultLlmModelConfig(id: string) {
  return request<LlmModelConfig>(`/api/model/llm-configs/${id}/default`, { method: 'POST' })
}

export function testLlmModelConfig(id: string) {
  return request<LlmModelTestResult>(`/api/model/llm-configs/${id}/test`, { method: 'POST' })
}

export function listLlmModelOptions() {
  return request<LlmModelOption[]>('/api/model/llm-configs/options')
}

export interface OcrEngineConfig {
  id: string
  engineCode: string
  engineName: string
  engineType: string
  provider: string
  baseUrl: string
  authMode?: string
  apiKeySecretRef?: string
  defaultEngine: boolean
  priority: number
  timeoutSeconds: number
  retryCount: number
  supportedFileTypes?: string
  outputFormat: string
  maxPagesPerCall?: number
  status: 'ENABLED' | 'DISABLED'
  description?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface OcrEngineOption {
  value: string
  label: string
  engineCode: string
  engineName: string
  engineType: string
  provider: string
  defaultEngine: boolean
  outputFormat: string
}

export interface OcrEngineTestResult {
  passed: boolean
  message: string
  engineCode: string
  baseUrl: string
  outputFormat: string
  checkedAt: string
}

export type OcrEnginePayload = Omit<OcrEngineConfig, 'id' | 'createdBy' | 'createdAt' | 'updatedAt'>

export function listOcrEngineConfigs(params: Record<string, string>) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value) searchParams.set(key, value)
  })
  const query = searchParams.toString()
  return request<OcrEngineConfig[]>(`/api/model/ocr-engines${query ? `?${query}` : ''}`)
}

export function getOcrEngineConfig(id: string) {
  return request<OcrEngineConfig>(`/api/model/ocr-engines/${id}`)
}

export function createOcrEngineConfig(payload: OcrEnginePayload) {
  return request<OcrEngineConfig>('/api/model/ocr-engines', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateOcrEngineConfig(id: string, payload: OcrEnginePayload) {
  return request<OcrEngineConfig>(`/api/model/ocr-engines/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function enableOcrEngineConfig(id: string) {
  return request<OcrEngineConfig>(`/api/model/ocr-engines/${id}/enable`, { method: 'POST' })
}

export function disableOcrEngineConfig(id: string) {
  return request<OcrEngineConfig>(`/api/model/ocr-engines/${id}/disable`, { method: 'POST' })
}

export function setDefaultOcrEngineConfig(id: string) {
  return request<OcrEngineConfig>(`/api/model/ocr-engines/${id}/default`, { method: 'POST' })
}

export function testOcrEngineConfig(id: string) {
  return request<OcrEngineTestResult>(`/api/model/ocr-engines/${id}/test`, { method: 'POST' })
}

export function listOcrEngineOptions() {
  return request<OcrEngineOption[]>('/api/model/ocr-engines/options')
}
