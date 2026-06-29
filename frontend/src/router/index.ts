import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'
import DashboardView from '../views/DashboardView.vue'
import UploadView from '../views/UploadView.vue'
import TasksView from '../views/TasksView.vue'
import ReviewView from '../views/ReviewView.vue'
import ConfigWizardView from '../views/ConfigWizardView.vue'
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
        { path: 'reviews', name: 'reviews', component: ReviewView, meta: { title: '人工复核' } },
        { path: 'reviews/:reviewTaskId', name: 'review-detail', component: ReviewView, meta: { title: '复核详情' } },
        { path: 'configs/wizard', name: 'configs-wizard', component: ConfigWizardView, meta: { title: '配置向导' } },
        { path: 'documents/records', component: PlaceholderView, meta: { title: '接入记录', hint: 'P1 页面，原型阶段先预留入口。' } },
        { path: 'documents/unmatched', component: PlaceholderView, meta: { title: '待确认文档', hint: '展示未匹配解析提取规则的文档。' } },
        { path: 'tasks/failed', component: PlaceholderView, meta: { title: '失败任务', hint: '展示失败原因、重试入口和日志抽屉。' } },
        { path: 'configs', component: PlaceholderView, meta: { title: '配置列表', hint: '展示草稿、已发布、停用配置及版本操作。' } },
        { path: 'sandbox', component: PlaceholderView, meta: { title: '配置验证', hint: 'P1 页面，配置向导第 8 步已内置验证预览。' } },
        { path: 'results', component: PlaceholderView, meta: { title: '提取结果', hint: 'P1 页面，展示字段结果、落库预览和导出记录。' } },
        { path: 'models/ocr', component: PlaceholderView, meta: { title: 'OCR 引擎', hint: 'P1 页面，维护 PaddleOCR-VL、MinerU 等引擎配置。' } },
        { path: 'models/llm', component: PlaceholderView, meta: { title: 'LLM 配置', hint: 'P1 页面，维护模型接口和参数。' } },
        { path: 'models/logs', component: PlaceholderView, meta: { title: '调用日志', hint: 'P1 页面，展示 OCR/LLM 调用耗时、状态和错误。' } },
        { path: 'system/users', component: PlaceholderView, meta: { title: '用户管理', hint: 'P2 页面，原型阶段先预留。' } },
        { path: 'system/roles', component: PlaceholderView, meta: { title: '角色权限', hint: 'P2 页面，原型阶段先预留。' } },
        { path: 'system/data-permissions', component: PlaceholderView, meta: { title: '数据权限', hint: 'P2 页面，原型阶段先预留。' } }
      ]
    }
  ]
})

export default router
