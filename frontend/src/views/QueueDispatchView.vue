<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dispatchTask, executeNextTask, listTasks, type ExtractTask, type TaskDispatchPayload } from '../api/task'

type Priority = 'HIGH' | 'MEDIUM' | 'LOW'

const loading = ref(false)
const tasks = ref<ExtractTask[]>([])
const selectedDepartment = ref('运营部')
const selectedQueueLevel = ref<Priority>('HIGH')
const selectedRows = ref<ExtractTask[]>([])
const dispatchVisible = ref(false)
const dispatchTarget = ref<ExtractTask | null>(null)
const dispatchForm = reactive<TaskDispatchPayload>({
  mode: 'PROMOTE_HIGH_TOP',
  targetPriority: 'HIGH',
  position: 1,
  durationMinutes: 30,
  reason: ''
})

const departmentConfigs = [
  { department: '运营部', maxCapacity: 60, description: '运营部任务量大，分配最大队列容量 60' },
  { department: '财务部', maxCapacity: 10, description: '财务部任务较少，分配最大队列容量 10' },
  { department: '产品部', maxCapacity: 20, description: '产品部独立队列，避免与其他部门互相影响' }
]

const priorityLevels: Array<{ label: string; value: Priority }> = [
  { label: '高优先级', value: 'HIGH' },
  { label: '中优先级', value: 'MEDIUM' },
  { label: '低优先级', value: 'LOW' }
]

const loadTasks = async () => {
  loading.value = true
  try {
    tasks.value = await listTasks({})
  } catch (error) {
    tasks.value = []
    ElMessage.error(error instanceof Error ? error.message : '队列任务加载失败')
  } finally {
    loading.value = false
  }
}

const queueTasks = computed(() => tasks.value.filter((task) => ['QUEUED', 'PARSING', 'EXTRACTING', 'WAIT_REVIEW'].includes(task.status)))

const departmentCards = computed(() =>
  departmentConfigs.map((config) => {
    const rows = queueTasks.value.filter((task) => task.departmentId === config.department)
    const waitingRows = rows.filter((task) => task.status === 'QUEUED')
    return {
      ...config,
      total: rows.length,
      waiting: waitingRows.length,
      accelerated: rows.filter((task) => task.manualAccelerated).length,
      usageRate: Math.min(100, Math.round((waitingRows.length / config.maxCapacity) * 100))
    }
  })
)

const queueCards = computed(() =>
  priorityLevels.map((level) => {
    const rows = queueTasks.value.filter((task) => task.departmentId === selectedDepartment.value && task.queueLevel === level.value)
    return {
      ...level,
      total: rows.length,
      waiting: rows.filter((task) => task.status === 'QUEUED').length,
      accelerated: rows.filter((task) => task.manualAccelerated).length,
      maxWait: rows.length ? Math.max(...rows.map((task) => task.waitingMinutes || 0)) : 0
    }
  })
)

const currentQueueTasks = computed(() =>
  queueTasks.value
    .filter((task) => task.departmentId === selectedDepartment.value && task.queueLevel === selectedQueueLevel.value)
    .sort((left, right) => (left.queuePosition || 9999) - (right.queuePosition || 9999))
)

const currentDepartmentConfig = computed(() => departmentConfigs.find((item) => item.department === selectedDepartment.value) || departmentConfigs[0])

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
    ElMessage.warning('请填写调度原因')
    return
  }
  try {
    await dispatchTask(dispatchTarget.value.taskId, dispatchForm)
    ElMessage.success('本部门内手工插队成功')
    dispatchVisible.value = false
    await loadTasks()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '插队失败')
  }
}

const executeDepartmentNext = async () => {
  try {
    const result = await executeNextTask(selectedDepartment.value)
    if (result.status === 'FAILED') {
      ElMessage.error(result.errorMessage || `任务 ${result.taskId} 执行失败，请查看任务详情`)
    } else {
      ElMessage.success(`已执行${selectedDepartment.value}任务 ${result.taskId}`)
    }
    await loadTasks()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '执行当前部门下一条失败')
  }
}

const batchPromote = async () => {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先选择需要批量加急的任务')
    return
  }
  const crossDepartment = selectedRows.value.some((task) => task.departmentId !== selectedDepartment.value)
  if (crossDepartment) {
    ElMessage.warning('批量加急只允许处理当前部门队列内的任务')
    return
  }
  const nonQueued = selectedRows.value.some((task) => task.status !== 'QUEUED')
  if (nonQueued) {
    ElMessage.warning('批量加急只允许处理排队中的任务')
    return
  }
  try {
    for (const task of selectedRows.value) {
      await dispatchTask(task.taskId, {
        mode: 'PROMOTE_HIGH_TOP',
        targetPriority: 'HIGH',
        position: 1,
        durationMinutes: 30,
        reason: '本部门队列批量手工加急'
      })
    }
    selectedQueueLevel.value = 'HIGH'
    ElMessage.success(`已批量加急 ${selectedRows.value.length} 个任务`)
    selectedRows.value = []
    await loadTasks()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量加急失败')
  }
}

const priorityText = (priority?: string) => {
  if (priority === 'HIGH') return '高'
  if (priority === 'LOW') return '低'
  return '中'
}

const statusText = (status?: string) => {
  const map: Record<string, string> = {
    QUEUED: '排队中',
    PARSING: '解析中',
    EXTRACTING: '提取中',
    WAIT_REVIEW: '待复核',
    FAILED: '失败',
    COMPLETED: '已完成'
  }
  return map[status || ''] || status || '-'
}

onMounted(loadTasks)
</script>

<template>
  <div class="page-stack">
    <div class="queue-card-grid">
      <el-card
        v-for="item in departmentCards"
        :key="item.department"
        shadow="never"
        class="queue-summary-card"
        :class="{ active: selectedDepartment === item.department }"
        @click="selectedDepartment = item.department; selectedQueueLevel = 'HIGH'"
      >
        <strong>{{ item.department }}队列</strong>
        <div class="queue-metrics">
          <span>容量 {{ item.maxCapacity }}</span>
          <span>任务 {{ item.total }}</span>
          <span>等待 {{ item.waiting }}</span>
          <span>加急 {{ item.accelerated }}</span>
        </div>
        <el-progress :percentage="item.usageRate" />
        <p class="muted">{{ item.description }}</p>
      </el-card>
    </div>

    <div class="queue-card-grid">
      <el-card
        v-for="item in queueCards"
        :key="item.value"
        shadow="never"
        class="queue-summary-card"
        :class="{ active: selectedQueueLevel === item.value }"
        @click="selectedQueueLevel = item.value"
      >
        <strong>{{ selectedDepartment }}-{{ item.label }}队列</strong>
        <div class="queue-metrics">
          <span>任务 {{ item.total }}</span>
          <span>等待 {{ item.waiting }}</span>
          <span>加急 {{ item.accelerated }}</span>
          <span>最长等待 {{ item.maxWait }} 分钟</span>
        </div>
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ selectedDepartment }} / {{ priorityLevels.find((item) => item.value === selectedQueueLevel)?.label }}任务</span>
          <div>
            <el-button @click="executeDepartmentNext">执行当前部门下一条</el-button>
            <el-button @click="batchPromote">本部门批量加急到高优先级</el-button>
            <el-button type="primary" :loading="loading" @click="loadTasks">刷新队列</el-button>
          </div>
        </div>
      </template>
      <el-alert
        class="mb-12"
        :title="`${selectedDepartment} 队列容量为 ${currentDepartmentConfig.maxCapacity}，仅展示和调度本部门任务；部门队列互相隔离。`"
        type="info"
        :closable="false"
      />
      <el-table v-loading="loading" :data="currentQueueTasks" stripe @selection-change="selectedRows = $event">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="queuePosition" label="序号" width="70" />
        <el-table-column prop="taskId" label="任务编号" min-width="180" />
        <el-table-column prop="fileName" label="文件名" min-width="180" />
        <el-table-column label="优先级" width="80">
          <template #default="{ row }"><el-tag>{{ priorityText(row.priority) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag>{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="waitingMinutes" label="等待分钟" width="90" />
        <el-table-column prop="estimatedStartAt" label="预计开始" width="100" />
        <el-table-column label="手工加急" width="90">
          <template #default="{ row }"><el-tag :type="row.manualAccelerated ? 'danger' : 'info'">{{ row.manualAccelerated ? '是' : '否' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }"><el-button link type="primary" :disabled="row.status !== 'QUEUED'" @click="openDispatch(row)">插队</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dispatchVisible" title="本部门手工插队调度" width="620px">
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
          <el-form-item label="目标位置"><el-input-number v-model="dispatchForm.position" :min="1" /></el-form-item>
          <el-form-item label="加急时长"><el-input-number v-model="dispatchForm.durationMinutes" :min="5" :step="5" /></el-form-item>
          <el-form-item label="调度原因" class="wide">
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
