<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listFailedTasks, retryTask, type ExtractTask } from '../api/task'

type RetryMode = 'FROM_FAILED_STAGE' | 'REPARSE' | 'REEXTRACT' | 'FULL_RETRY'

const router = useRouter()
const loading = ref(false)
const drawerVisible = ref(false)
const retryDialogVisible = ref(false)
const selectedTask = ref<ExtractTask | null>(null)
const failedTasks = ref<ExtractTask[]>([])

const retryForm = reactive({
  retryMode: 'FROM_FAILED_STAGE' as RetryMode,
  priority: 'HIGH',
  reason: '修正配置或服务后从失败阶段重试'
})

const query = reactive({
  keyword: '',
  departmentId: '',
  priority: ''
})

const filteredTasks = computed(() => failedTasks.value)
const retryableCount = computed(() => failedTasks.value.filter((task) => task.retryable).length)
const highCount = computed(() => failedTasks.value.filter((task) => task.priority === 'HIGH').length)
const overLimitCount = computed(() => failedTasks.value.filter((task) => (task.retryCount || 0) >= (task.maxRetry || 3)).length)

const priorityMap = {
  HIGH: { label: '高', type: 'danger' },
  MEDIUM: { label: '中', type: 'warning' },
  LOW: { label: '低', type: 'info' }
} as const

const loadFailedTasks = async () => {
  loading.value = true
  try {
    failedTasks.value = await listFailedTasks(query)
  } catch (error) {
    failedTasks.value = []
    ElMessage.error(error instanceof Error ? error.message : '失败任务加载失败')
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.keyword = ''
  query.departmentId = ''
  query.priority = ''
  loadFailedTasks()
}

const openDetail = (task: ExtractTask) => {
  selectedTask.value = task
  drawerVisible.value = true
}

const openRetry = (task: ExtractTask) => {
  selectedTask.value = task
  retryForm.priority = task.priority || 'HIGH'
  retryDialogVisible.value = true
}

const submitRetry = async () => {
  if (!selectedTask.value) return
  await ElMessageBox.confirm('确认提交重试任务？系统将记录重试原因和操作时间。', '任务重试', { type: 'warning' })
  try {
    await retryTask(selectedTask.value.taskId, retryForm)
    retryDialogVisible.value = false
    ElMessage.success('已提交重试任务')
    await loadFailedTasks()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交重试失败')
  }
}

const priorityText = (priority?: string) => priorityMap[priority as keyof typeof priorityMap]?.label || priority || '-'
const priorityType = (priority?: string) => priorityMap[priority as keyof typeof priorityMap]?.type || 'info'

onMounted(loadFailedTasks)
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>失败任务</span><strong>{{ failedTasks.length }}</strong><em>需排查</em></el-card>
      <el-card shadow="never" class="metric-card"><span>可重试</span><strong>{{ retryableCount }}</strong><em>可恢复</em></el-card>
      <el-card shadow="never" class="metric-card"><span>不可重试</span><strong>{{ failedTasks.length - retryableCount }}</strong><em>需人工处理</em></el-card>
      <el-card shadow="never" class="metric-card"><span>高优先级</span><strong>{{ highCount }}</strong><em>优先处理</em></el-card>
      <el-card shadow="never" class="metric-card"><span>超限任务</span><strong>{{ overLimitCount }}</strong><em>停止自动重试</em></el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="trace/任务/文件/错误码" @keyup.enter="loadFailedTasks" />
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
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadFailedTasks">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>失败任务列表</span>
          <div>
            <el-button>导出错误</el-button>
            <el-button type="primary" :loading="loading" @click="loadFailedTasks">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="filteredTasks" stripe>
        <el-table-column prop="traceId" label="TraceId" min-width="180" fixed />
        <el-table-column prop="taskId" label="任务编号" min-width="180" />
        <el-table-column prop="fileName" label="文件名" min-width="180" />
        <el-table-column prop="documentType" label="文档类型" width="110" />
        <el-table-column prop="departmentId" label="部门" width="100" />
        <el-table-column label="优先级" width="80">
          <template #default="{ row }"><el-tag :type="priorityType(row.priority)">{{ priorityText(row.priority) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="failedStage" label="失败阶段" width="120" />
        <el-table-column prop="errorCode" label="错误码" min-width="140" />
        <el-table-column prop="errorMessage" label="错误信息" min-width="230" />
        <el-table-column label="重试" width="100">
          <template #default="{ row }"><el-tag :type="row.retryable ? 'success' : 'danger'">{{ row.retryable ? '可重试' : '不可重试' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="次数" width="80">
          <template #default="{ row }">{{ row.retryCount || 0 }}/{{ row.maxRetry || 3 }}</template>
        </el-table-column>
        <el-table-column prop="failedAt" label="失败时间" min-width="165" />
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
          <el-descriptions-item label="失败阶段">{{ selectedTask.failedStage || selectedTask.currentStage }}</el-descriptions-item>
          <el-descriptions-item label="错误码">{{ selectedTask.errorCode }}</el-descriptions-item>
          <el-descriptions-item label="错误信息">{{ selectedTask.errorMessage }}</el-descriptions-item>
          <el-descriptions-item label="重试次数">{{ selectedTask.retryCount || 0 }}/{{ selectedTask.maxRetry || 3 }}</el-descriptions-item>
          <el-descriptions-item label="文件路径">{{ selectedTask.storagePath || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="section-title">处理建议</h3>
        <el-alert
          :title="selectedTask.retryable ? '该任务可从失败阶段重试，建议先确认配置或服务状态。' : '该任务已超过重试限制或需要管理员修复配置后再处理。'"
          :type="selectedTask.retryable ? 'success' : 'warning'"
          :closable="false"
        />
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
