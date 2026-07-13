import { request } from './http'

export interface DictType {
  id: string
  dictCode: string
  dictName: string
  usageScene?: string
  status: 'ENABLED' | 'DISABLED'
  sortNo?: number
  remark?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface DictItem {
  id: string
  dictCode: string
  itemValue: string
  itemLabel: string
  parentValue?: string
  sortNo?: number
  enabled: boolean
  extraJson?: string
  remark?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export type DictTypePayload = Omit<DictType, 'id' | 'createdBy' | 'createdAt' | 'updatedAt'>
export type DictItemPayload = Omit<DictItem, 'id' | 'createdBy' | 'createdAt' | 'updatedAt'>

const toQuery = (params: Record<string, any> = {}) => {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') searchParams.set(key, String(value))
  })
  const query = searchParams.toString()
  return query ? `?${query}` : ''
}

export function listDictTypes(params: Record<string, any> = {}) {
  return request<DictType[]>(`/api/system/dictionaries/types${toQuery(params)}`)
}

export function createDictType(payload: DictTypePayload) {
  return request<DictType>('/api/system/dictionaries/types', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateDictType(id: string, payload: DictTypePayload) {
  return request<DictType>(`/api/system/dictionaries/types/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function enableDictType(id: string) {
  return request<DictType>(`/api/system/dictionaries/types/${id}/enable`, { method: 'POST' })
}

export function disableDictType(id: string) {
  return request<DictType>(`/api/system/dictionaries/types/${id}/disable`, { method: 'POST' })
}

export function deleteDictType(id: string) {
  return request<void>(`/api/system/dictionaries/types/${id}`, { method: 'DELETE' })
}

export function listDictItems(params: Record<string, any> = {}) {
  return request<DictItem[]>(`/api/system/dictionaries/items${toQuery(params)}`)
}

export function createDictItem(payload: DictItemPayload) {
  return request<DictItem>('/api/system/dictionaries/items', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateDictItem(id: string, payload: DictItemPayload) {
  return request<DictItem>(`/api/system/dictionaries/items/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function enableDictItem(id: string) {
  return request<DictItem>(`/api/system/dictionaries/items/${id}/enable`, { method: 'POST' })
}

export function disableDictItem(id: string) {
  return request<DictItem>(`/api/system/dictionaries/items/${id}/disable`, { method: 'POST' })
}

export function deleteDictItem(id: string) {
  return request<void>(`/api/system/dictionaries/items/${id}`, { method: 'DELETE' })
}
