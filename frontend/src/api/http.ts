export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8889'

interface ApiResponse<T> {
  code: string
  message: string
  data: T
}

export interface PageResponse<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
  pages: number
}

export interface PageQuery {
  pageNo?: number | string
  pageSize?: number | string
}

export function toQuery(params: object = {}) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') searchParams.set(key, String(value))
  })
  const query = searchParams.toString()
  return query ? `?${query}` : ''
}

export function pageRecords<T>(page: PageResponse<T>) {
  return page.records || []
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const isFormData = options.body instanceof FormData
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...(options.headers || {})
    },
    ...options
  })
  const body = (await response.json()) as ApiResponse<T>
  if (!response.ok || body.code !== '0') {
    throw new Error(body.message || `接口请求失败：${response.status}`)
  }
  return body.data
}
