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
