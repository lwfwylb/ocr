import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'
import DashboardView from '../views/DashboardView.vue'
import UploadView from '../views/UploadView.vue'
import TasksView from '../views/TasksView.vue'
import ReviewView from '../views/ReviewView.vue'
import ConfigWizardView from '../views/ConfigWizardView.vue'
import ConfigListView from '../views/ConfigListView.vue'
import ResultsView from '../views/ResultsView.vue'
import StorageDataView from '../views/StorageDataView.vue'
import TraceMonitorView from '../views/TraceMonitorView.vue'
import DocumentRecordsView from '../views/DocumentRecordsView.vue'
import UnmatchedDocumentsView from '../views/UnmatchedDocumentsView.vue'
import FailedTasksView from '../views/FailedTasksView.vue'
import SystemUsersView from '../views/SystemUsersView.vue'
import SystemRolesView from '../views/SystemRolesView.vue'
import SystemDataPermissionsView from '../views/SystemDataPermissionsView.vue'
import OcrEnginesView from '../views/OcrEnginesView.vue'
import LlmConfigsView from '../views/LlmConfigsView.vue'
import ModelCallLogsView from '../views/ModelCallLogsView.vue'
import PlaceholderView from '../views/PlaceholderView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: DashboardView, meta: { title: '工作台' } },
        { path: 'documents/upload', name: 'documents-upload', component: UploadView, meta: { title: '手工上传' } },
        { path: 'tasks', name: 'tasks', component: TasksView, meta: { title: '全部任务' } },
        { path: 'monitor/traces', component: TraceMonitorView, meta: { title: '全链路监控' } },
        { path: 'reviews', name: 'reviews', component: ReviewView, meta: { title: '人工复核' } },
        { path: 'reviews/:reviewTaskId', name: 'review-detail', component: ReviewView, meta: { title: '复核详情' } },
        { path: 'configs/wizard', name: 'configs-wizard', component: ConfigWizardView, meta: { title: '配置向导' } },
        { path: 'documents/records', component: DocumentRecordsView, meta: { title: '接入记录' } },
        { path: 'documents/unmatched', component: UnmatchedDocumentsView, meta: { title: '待确认文档' } },
        { path: 'tasks/failed', component: FailedTasksView, meta: { title: '失败任务' } },
        { path: 'configs', component: ConfigListView, meta: { title: '配置列表' } },
        { path: 'sandbox', component: PlaceholderView, meta: { title: '配置验证', hint: 'P1 页面，配置向导第 8 步已内置验证预览。' } },
        { path: 'results', component: ResultsView, meta: { title: '提取结果' } },
        { path: 'storage-data', component: StorageDataView, meta: { title: '落库数据查询' } },
        { path: 'models/ocr', component: OcrEnginesView, meta: { title: 'OCR 引擎' } },
        { path: 'models/llm', component: LlmConfigsView, meta: { title: 'LLM 配置' } },
        { path: 'models/logs', component: ModelCallLogsView, meta: { title: '调用日志' } },
        { path: 'system/users', component: SystemUsersView, meta: { title: '用户管理' } },
        { path: 'system/roles', component: SystemRolesView, meta: { title: '角色权限' } },
        { path: 'system/data-permissions', component: SystemDataPermissionsView, meta: { title: '数据权限' } }
      ]
    }
  ]
})

export default router
