<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { API_BASE_URL } from '../api/http'
import { listTaskArtifacts, type DocumentArtifact } from '../api/artifact'
import {
  dispatchTask,
  executeNextTask,
  executeTask,
  getTaskDetail,
  listTaskStageLogs,
  listTasks,
  retryTask,
  type ExtractTask,
  type TaskDispatchPayload,
  type TaskStageLog
} from '../api/task'

const router = useRouter()
const loading = ref(false)
const drawerVisible = ref(false)
const dispatchVisible = ref(false)
const selectedTask = ref<ExtractTask | null>(null)
const dispatchTarget = ref<ExtractTask | null>(null)
const tasks = ref<ExtractTask[]>([])
const stageLogs = ref<TaskStageLog[]>([])
const taskArtifacts = ref<DocumentArtifact[]>([])

const query = reactive({
  keyword: '',
  sourceType: '',
  documentType: '',
  departmentId: '',
  priority: '',
  status: ''
})

const dispatchForm = reactive<TaskDispatchPayload>({
  mode: 'PROMOTE_HIGH_TOP',
  targetPriority: 'HIGH',
  position: 1,
  durationMinutes: 30,
  reason: ''
})

const statusMap: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' | 'primary' }> = {
  QUEUED: { label: '排队中', type: 'warning' },
  PARSING: { label: '解析中', type: 'primary' },
  EXTRACTING: { label: '提取中', type: 'primary' },
  WAIT_REVIEW: { label: '待复核', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

const priorityMap: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  HIGH: { label: '高', type: 'danger' },
  MEDIUM: { label: '中', type: 'warning' },
  LOW: { label: '低', type: 'info' }
}

const totalCount = computed(() => tasks.value.length)
const queuedCount = computed(() => tasks.value.filter((item) => item.status === 'QUEUED').length)
const failedCount = computed(() => tasks.value.filter((item) => item.status === 'FAILED').length)
const acceleratedCount = computed(() => tasks.value.filter((item) => item.manualAccelerated).length)

const loadTasks = async () => {
  loading.value = true
  try {
    tasks.value = await listTasks(query)
  } catch (error) {
    tasks.value = []
    ElMessage.error(error instanceof Error ? error.message : '任务列表加载失败')
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.keyword = ''
  query.sourceType = ''
  query.documentType = ''
  query.departmentId = ''
  query.priority = ''
  query.status = ''
  loadTasks()
}

const openDetail = async (task: ExtractTask) => {
  drawerVisible.value = true
  selectedTask.value = task
  stageLogs.value = []
  taskArtifacts.value = []
  try {
    const [taskDetail, logs, artifacts] = await Promise.all([
      getTaskDetail(task.taskId),
      listTaskStageLogs(task.taskId),
      listTaskArtifacts(task.taskId)
    ])
    selectedTask.value = taskDetail
    stageLogs.value = logs
    taskArtifacts.value = artifacts
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '任务详情加载失败')
  }
}

const openDispatch = (task: ExtractTask) => {
  if (task.status !== 'QUEUED') {
    ElMessage.warning('仅排队中的任务允许插队调度')
    return
  }
  dispatchTarget.value = task
  dispatchForm.targetPriority = task.priority || 'HIGH'
  dispatchForm.position = Math.max(1, task.queuePosition || 1)
  dispatchForm.durationMinutes = 30
  dispatchForm.reason = task.dispatchReason || ''
  dispatchVisible.value = true
}

const submitDispatch = async () => {
  if (!dispatchTarget.value) return
  if (!dispatchForm.reason.trim()) {
    ElMessage.warning('请填写插队原因')
    return
  }
  try {
    await dispatchTask(dispatchTarget.value.taskId, dispatchForm)
    ElMessage.success('插队调整成功')
    dispatchVisible.value = false
    await loadTasks()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '插队调整失败')
  }
}

const retry = async (task: ExtractTask) => {
  try {
    await retryTask(task.taskId, {
      retryMode: 'FROM_FAILED_STAGE',
      priority: task.priority,
      reason: '任务中心手工重试'
    })
    ElMessage.success('已提交重试')
    await loadTasks()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交重试失败')
  }
}

const execute = async (task: ExtractTask) => {
  try {
    const result = await executeTask(task.taskId)
    if (result.status === 'FAILED') {
      ElMessage.error(result.errorMessage || '模拟执行失败，请查看任务详情')
    } else {
      ElMessage.success('模拟执行完成')
    }
    await loadTasks()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模拟执行失败')
  }
}

const executeNext = async () => {
  try {
    const result = await executeNextTask()
    if (result.status === 'FAILED') {
      ElMessage.error(result.errorMessage || `任务 ${result.taskId} 执行失败，请查看任务详情`)
    } else {
      ElMessage.success(`已执行任务 ${result.taskId}`)
    }
    await loadTasks()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '执行下一条失败')
  }
}

const statusText = (status?: string) => statusMap[status || '']?.label || status || '-'
const statusType = (status?: string) => statusMap[status || '']?.type || 'info'
const priorityText = (priority?: string) => priorityMap[priority || '']?.label || priority || '-'
const priorityType = (priority?: string) => priorityMap[priority || '']?.type || 'info'
const logType = (status?: string) => status === 'FAILED' ? 'danger' : 'success'
const artifactTypeMap: Record<string, string> = {
  ORIGINAL: '原始文件',
  PREPROCESSED: '预处理文件',
  PAGE_IMAGE: '页面图片',
  OCR_INPUT_MANIFEST: 'OCR输入清单',
  OCR_OUTPUT_MARKDOWN: 'OCR输出文本'
}
const artifactTypeText = (type?: string) => (type ? artifactTypeMap[type] || type : '-')
const formatFileSize = (value?: number) => {
  if (!value) return '-'
  if (value < 1024) return `${value}B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)}KB`
  return `${(value / 1024 / 1024).toFixed(1)}MB`
}
const artifactUrl = (row: DocumentArtifact, action: 'preview' | 'download') => {
  const url = action === 'preview' ? row.previewUrl : row.downloadUrl
  if (url?.startsWith('http')) return url
  return `${API_BASE_URL}${url || `/api/artifacts/${row.id}/${action}`}`
}

onMounted(loadTasks)
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>任务总数</span><strong>{{ totalCount }}</strong><em>真实台账</em></el-card>
      <el-card shadow="never" class="metric-card"><span>排队中</span><strong>{{ queuedCount }}</strong><em>等待调度</em></el-card>
      <el-card shadow="never" class="metric-card"><span>失败</span><strong>{{ failedCount }}</strong><em>需处理</em></el-card>
      <el-card shadow="never" class="metric-card"><span>手工插队</span><strong>{{ acceleratedCount }}</strong><em>已加急</em></el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="任务/trace/文档/文件/业务号" @keyup.enter="loadTasks" />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="query.sourceType" clearable filterable placeholder="全部">
            <el-option label="手工上传" value="MANUAL_UPLOAD" />
            <el-option label="业务系统API" value="BUSINESS_API" />
            <el-option label="邮件分拣" value="EMAIL_DISPATCH" />
            <el-option label="文件分拣" value="FILE_DISPATCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="query.departmentId" clearable filterable placeholder="全部">
            <el-option label="运营部" value="OPS" />
            <el-option label="财务部" value="FINANCE" />
            <el-option label="产品部" value="PRODUCT" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="query.priority" clearable placeholder="全部">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option label="排队中" value="QUEUED" />
            <el-option label="解析中" value="PARSING" />
            <el-option label="提取中" value="EXTRACTING" />
            <el-option label="待复核" value="WAIT_REVIEW" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadTasks">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务列表</span>
          <div>
            <el-button @click="router.push('/documents/upload')">上传文档</el-button>
            <el-button @click="executeNext">执行下一条</el-button>
            <el-button type="primary" :loading="loading" @click="loadTasks">刷新队列</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="tasks" stripe>
        <el-table-column prop="taskId" label="任务编号" min-width="180" fixed />
        <el-table-column prop="traceId" label="TraceId" min-width="180" />
        <el-table-column prop="fileName" label="文件名" min-width="180" />
        <el-table-column prop="sourceType" label="来源" width="120" />
        <el-table-column prop="documentType" label="文档类型" width="120" />
        <el-table-column label="优先级" width="80">
          <template #default="{ row }"><el-tag :type="priorityType(row.priority)">{{ priorityText(row.priority) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="departmentId" label="部门" width="100" />
        <el-table-column prop="queueName" label="当前队列" min-width="170" />
        <el-table-column label="排队序号" width="90">
          <template #default="{ row }">{{ row.queuePosition ? `第 ${row.queuePosition} 位` : '-' }}</template>
        </el-table-column>
        <el-table-column label="等待" width="90">
          <template #default="{ row }">{{ row.waitingMinutes ? `${row.waitingMinutes} 分钟` : '-' }}</template>
        </el-table-column>
        <el-table-column label="手工插队" width="90">
          <template #default="{ row }"><el-tag :type="row.manualAccelerated ? 'danger' : 'info'">{{ row.manualAccelerated ? '是' : '否' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="进度" width="150">
          <template #default="{ row }"><el-progress :percentage="row.progress || 0" /></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="165" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" :disabled="row.status !== 'QUEUED'" @click="openDispatch(row)">插队</el-button>
            <el-button link type="success" :disabled="!['QUEUED', 'PARSING', 'EXTRACTING'].includes(row.status)" @click="execute(row)">模拟执行</el-button>
            <el-button link @click="router.push('/monitor/traces')">链路</el-button>
            <el-button link type="success" :disabled="row.status !== 'FAILED'" @click="retry(row)">重试</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="任务详情" size="760px">
      <template v-if="selectedTask">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="任务编号">{{ selectedTask.taskId }}</el-descriptions-item>
          <el-descriptions-item label="TraceId">{{ selectedTask.traceId }}</el-descriptions-item>
          <el-descriptions-item label="文档编号">{{ selectedTask.documentId }}</el-descriptions-item>
          <el-descriptions-item label="文件名">{{ selectedTask.fileName }}</el-descriptions-item>
          <el-descriptions-item label="配置">{{ selectedTask.configName || '-' }} {{ selectedTask.configVersion ? `V${selectedTask.configVersion}` : '' }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ selectedTask.departmentId }}</el-descriptions-item>
          <el-descriptions-item label="当前阶段">{{ selectedTask.currentStage || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前队列">{{ selectedTask.queueName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="排队序号">{{ selectedTask.queuePosition ? `第 ${selectedTask.queuePosition} 位` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="存储路径">{{ selectedTask.storagePath || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="selectedTask.dispatchReason" label="插队原因">{{ selectedTask.dispatchReason }}</el-descriptions-item>
          <el-descriptions-item v-if="selectedTask.errorMessage" label="错误信息">{{ selectedTask.errorMessage }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="section-title">过程文件</h3>
        <el-table v-if="taskArtifacts.length" :data="taskArtifacts" stripe size="small">
          <el-table-column label="类型" width="112">
            <template #default="{ row }">{{ artifactTypeText(row.artifactType) }}</template>
          </el-table-column>
          <el-table-column prop="fileName" label="文件名称" min-width="190" show-overflow-tooltip />
          <el-table-column label="页码" width="92">
            <template #default="{ row }">{{ row.pageRange || row.pageNo || '-' }}</template>
          </el-table-column>
          <el-table-column label="大小" width="92">
            <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="生成时间" min-width="150" show-overflow-tooltip />
          <el-table-column label="操作" width="112" fixed="right">
            <template #default="{ row }">
              <el-link type="primary" :href="artifactUrl(row, 'preview')" target="_blank">预览</el-link>
              <el-divider direction="vertical" />
              <el-link type="primary" :href="artifactUrl(row, 'download')" target="_blank">下载</el-link>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无过程文件" />
        <h3 class="section-title">阶段日志</h3>
        <el-timeline v-if="stageLogs.length">
          <el-timeline-item
            v-for="log in stageLogs"
            :key="log.id"
            :timestamp="log.endedAt || log.createdAt"
            :type="logType(log.status)"
          >
            <strong>{{ log.stageName }}</strong>
            <p>{{ log.outputSummary || log.errorMessage || log.inputSummary }}</p>
            <p class="muted">耗时：{{ log.durationMs || 0 }} ms</p>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无阶段日志" />
      </template>
    </el-drawer>

    <el-dialog v-model="dispatchVisible" title="手工插队调度" width="620px">
      <template v-if="dispatchTarget">
        <el-descriptions :column="2" border class="mb-12">
          <el-descriptions-item label="任务编号">{{ dispatchTarget.taskId }}</el-descriptions-item>
          <el-descriptions-item label="所属部门">{{ dispatchTarget.departmentId }}</el-descriptions-item>
          <el-descriptions-item label="当前队列">{{ dispatchTarget.queueName }}</el-descriptions-item>
          <el-descriptions-item label="当前序号">{{ dispatchTarget.queuePosition ? `第 ${dispatchTarget.queuePosition} 位` : '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-form :model="dispatchForm" label-width="120px" class="form-grid">
          <el-form-item label="调整方式">
            <el-select v-model="dispatchForm.mode">
              <el-option label="提升到本部门高优先级队列并置顶" value="PROMOTE_HIGH_TOP" />
              <el-option label="本部门内提升优先级并指定位置" value="PROMOTE_TO_POSITION" />
              <el-option label="本部门内临时加权加急" value="TEMP_WEIGHT" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标优先级">
            <el-select v-model="dispatchForm.targetPriority">
              <el-option label="高" value="HIGH" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="低" value="LOW" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标位置">
            <el-input-number v-model="dispatchForm.position" :min="1" />
          </el-form-item>
          <el-form-item label="加急时长">
            <el-input-number v-model="dispatchForm.durationMinutes" :min="5" :step="5" />
          </el-form-item>
          <el-form-item label="插队原因" class="wide">
            <el-input v-model="dispatchForm.reason" type="textarea" :rows="3" placeholder="请输入调度原因，便于审计追溯" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="dispatchVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDispatch">确认插队</el-button>
      </template>
    </el-dialog>
  </div>
</template>
