const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'

interface ApiResponse<T> {
  code: string
  message: string
  data: T
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
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
