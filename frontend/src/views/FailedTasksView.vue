<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

type RetryMode = 'FROM_FAILED_STAGE' | 'REPARSE' | 'REEXTRACT' | 'FULL_RETRY'

interface FailedTask {
  traceId: string
  taskId: string
  documentId: string
  fileName: string
  documentType: string
  sourceType: string
  department: string
  priority: 'HIGH' | 'MEDIUM' | 'LOW'
  failedStage: string
  errorCode: string
  errorMessage: string
  retryable: boolean
  retryCount: number
  maxRetry: number
  failedAt: string
}

const router = useRouter()
const drawerVisible = ref(false)
const retryDialogVisible = ref(false)
const selectedTask = ref<FailedTask | null>(null)
const retryForm = reactive({
  retryMode: 'FROM_FAILED_STAGE' as RetryMode,
  priority: 'HIGH',
  reason: '修正配置后从失败阶段重试'
})
const query = reactive({
  keyword: '',
  stages: [] as string[],
  retryable: '',
  priorities: [] as string[]
})

const failedTasks = ref<FailedTask[]>([
  {
    traceId: 'TRACE-20260628-0004',
    taskId: 'TASK-20260628-0004',
    documentId: 'DOC-20260628-0004',
    fileName: '未知附件.zip',
    documentType: '未匹配',
    sourceType: 'FILE_DISPATCH',
    department: '产品部',
    priority: 'MEDIUM',
    failedStage: '规则匹配',
    errorCode: 'CONFIG_404',
    errorMessage: '未匹配到可用解析提取配置',
    retryable: true,
    retryCount: 0,
    maxRetry: 3,
    failedAt: '2026-06-28 11:20:07'
  },
  {
    traceId: 'TRACE-20260628-0011',
    taskId: 'TASK-20260628-0011',
    documentId: 'DOC-20260628-0011',
    fileName: '产品合同_扫描件.pdf',
    documentType: '产品合同',
    sourceType: 'EMAIL',
    department: '产品部',
    priority: 'LOW',
    failedStage: '文档解析',
    errorCode: 'OCR_TIMEOUT',
    errorMessage: 'OCR 服务调用超时',
    retryable: true,
    retryCount: 1,
    maxRetry: 3,
    failedAt: '2026-06-28 15:06:33'
  },
  {
    traceId: 'TRACE-20260628-0015',
    taskId: 'TASK-20260628-0015',
    documentId: 'DOC-20260628-0015',
    fileName: '大额划款指令.pdf',
    documentType: '划款指令',
    sourceType: 'API',
    department: '运营部',
    priority: 'HIGH',
    failedStage: '落库',
    errorCode: 'DB_DDL_REJECTED',
    errorMessage: '目标表字段不存在且未授权自动扩字段',
    retryable: false,
    retryCount: 3,
    maxRetry: 3,
    failedAt: '2026-06-28 16:28:10'
  }
])

const filteredTasks = computed(() => {
  return failedTasks.value.filter((task) => {
    const keywordMatched =
      !query.keyword ||
      task.traceId.includes(query.keyword) ||
      task.taskId.includes(query.keyword) ||
      task.fileName.includes(query.keyword) ||
      task.errorCode.includes(query.keyword)
    const stageMatched = query.stages.length === 0 || query.stages.includes(task.failedStage)
    const retryMatched = !query.retryable || String(task.retryable) === query.retryable
    const priorityMatched = query.priorities.length === 0 || query.priorities.includes(task.priority)
    return keywordMatched && stageMatched && retryMatched && priorityMatched
  })
})

const priorityMap = {
  HIGH: { label: '高', type: 'danger' },
  MEDIUM: { label: '中', type: 'warning' },
  LOW: { label: '低', type: 'info' }
} as const

const openDetail = (task: FailedTask) => {
  selectedTask.value = task
  drawerVisible.value = true
}

const openRetry = (task: FailedTask) => {
  selectedTask.value = task
  retryForm.priority = task.priority
  retryDialogVisible.value = true
}

const submitRetry = async () => {
  await ElMessageBox.confirm('确认提交重试任务？系统将记录重试原因和操作人。', '任务重试', { type: 'warning' })
  retryDialogVisible.value = false
  ElMessage.success('已模拟提交重试任务')
}

const resetQuery = () => {
  query.keyword = ''
  query.stages = []
  query.retryable = ''
  query.priorities = []
}
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>失败任务</span><strong>{{ failedTasks.length }}</strong><em>需排查</em></el-card>
      <el-card shadow="never" class="metric-card"><span>可重试</span><strong>{{ failedTasks.filter((t) => t.retryable).length }}</strong><em>可自动恢复</em></el-card>
      <el-card shadow="never" class="metric-card"><span>不可重试</span><strong>{{ failedTasks.filter((t) => !t.retryable).length }}</strong><em>需配置修复</em></el-card>
      <el-card shadow="never" class="metric-card"><span>高优先级</span><strong>{{ failedTasks.filter((t) => t.priority === 'HIGH').length }}</strong><em>优先处理</em></el-card>
      <el-card shadow="never" class="metric-card"><span>超限任务</span><strong>{{ failedTasks.filter((t) => t.retryCount >= t.maxRetry).length }}</strong><em>进入人工</em></el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="trace/任务/文件/错误码" /></el-form-item>
        <el-form-item label="失败阶段">
          <el-select v-model="query.stages" multiple filterable clearable collapse-tags placeholder="全部">
            <el-option label="规则匹配" value="规则匹配" />
            <el-option label="文档解析" value="文档解析" />
            <el-option label="要素提取" value="要素提取" />
            <el-option label="落库" value="落库" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否可重试">
          <el-select v-model="query.retryable" clearable placeholder="全部">
            <el-option label="可重试" value="true" />
            <el-option label="不可重试" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="query.priorities" multiple filterable clearable collapse-tags placeholder="全部">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>失败任务列表</span>
          <div><el-button>批量导出错误</el-button><el-button type="primary">批量重试可重试任务</el-button></div>
        </div>
      </template>
      <el-table :data="filteredTasks" stripe>
        <el-table-column prop="traceId" label="TraceId" min-width="170" fixed />
        <el-table-column prop="taskId" label="任务编号" min-width="170" />
        <el-table-column prop="fileName" label="文件名" min-width="170" />
        <el-table-column prop="documentType" label="文档类型" width="100" />
        <el-table-column prop="department" label="部门" width="90" />
        <el-table-column label="优先级" width="80">
          <template #default="{ row }">
            <el-tag :type="priorityMap[row.priority as keyof typeof priorityMap].type">{{ priorityMap[row.priority as keyof typeof priorityMap].label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failedStage" label="失败阶段" width="110" />
        <el-table-column prop="errorCode" label="错误码" min-width="140" />
        <el-table-column prop="errorMessage" label="错误信息" min-width="230" />
        <el-table-column label="重试" width="100">
          <template #default="{ row }">
            <el-tag :type="row.retryable ? 'success' : 'danger'">{{ row.retryable ? '可重试' : '不可重试' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="次数" width="80">
          <template #default="{ row }">{{ row.retryCount }}/{{ row.maxRetry }}</template>
        </el-table-column>
        <el-table-column prop="failedAt" label="失败时间" min-width="150" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link @click="router.push('/monitor/traces')">链路</el-button>
            <el-button link type="success" :disabled="!row.retryable" @click="openRetry(row)">重试</el-button>
            <el-button link>转人工</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="失败详情" size="620px">
      <template v-if="selectedTask">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="TraceId">{{ selectedTask.traceId }}</el-descriptions-item>
          <el-descriptions-item label="任务编号">{{ selectedTask.taskId }}</el-descriptions-item>
          <el-descriptions-item label="失败阶段">{{ selectedTask.failedStage }}</el-descriptions-item>
          <el-descriptions-item label="错误码">{{ selectedTask.errorCode }}</el-descriptions-item>
          <el-descriptions-item label="错误信息">{{ selectedTask.errorMessage }}</el-descriptions-item>
          <el-descriptions-item label="重试次数">{{ selectedTask.retryCount }}/{{ selectedTask.maxRetry }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="section-title">处理建议</h3>
        <el-alert
          :title="selectedTask.retryable ? '该任务可从失败阶段重试，建议先确认配置或服务状态。' : '该任务已超过重试限制或需管理员修复配置后再处理。'"
          :type="selectedTask.retryable ? 'success' : 'warning'"
          :closable="false"
        />
        <h3 class="section-title">错误日志摘要</h3>
        <el-input type="textarea" :rows="7" :model-value="`[${selectedTask.failedAt}] ${selectedTask.errorCode}: ${selectedTask.errorMessage}\ntraceId=${selectedTask.traceId}\nstage=${selectedTask.failedStage}`" />
      </template>
    </el-drawer>

    <el-dialog v-model="retryDialogVisible" title="提交重试" width="560px">
      <el-form :model="retryForm" label-width="120px">
        <el-form-item label="重试方式">
          <el-select v-model="retryForm.retryMode">
            <el-option label="从失败阶段重试" value="FROM_FAILED_STAGE" />
            <el-option label="重新解析" value="REPARSE" />
            <el-option label="重新提取" value="REEXTRACT" />
            <el-option label="全流程重试" value="FULL_RETRY" />
          </el-select>
        </el-form-item>
        <el-form-item label="重试优先级">
          <el-radio-group v-model="retryForm.priority">
            <el-radio-button label="HIGH">高</el-radio-button>
            <el-radio-button label="MEDIUM">中</el-radio-button>
            <el-radio-button label="LOW">低</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="重试原因">
          <el-input v-model="retryForm.reason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="retryDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRetry">提交重试</el-button>
      </template>
    </el-dialog>
  </div>
</template>
