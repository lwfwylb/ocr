import { request } from './http'

export interface DashboardMetric {
  label: string
  value: string
  trend: string
}

export interface DashboardTrend {
  date: string
  label: string
  count: number
}

export interface DashboardQueue {
  priority: string
  label: string
  count: number
}

export interface DashboardTaskItem {
  taskId: string
  traceId?: string
  fileName?: string
  documentType?: string
  priority?: string
  status: string
  currentStage?: string
  errorMessage?: string
  updatedAt?: string
}

export interface DashboardOverview {
  metrics: DashboardMetric[]
  taskTrend: DashboardTrend[]
  queueSummary: DashboardQueue[]
  pendingReviews: DashboardTaskItem[]
  failedTasks: DashboardTaskItem[]
}

export function getDashboardOverview() {
  return request<DashboardOverview>('/api/dashboard/overview')
}
