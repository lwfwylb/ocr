<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { departmentQueues, queueWorkers, tasks, type DepartmentName, type Priority, type TaskItem } from '../mock/data'
import PriorityTag from '../components/PriorityTag.vue'
import StatusTag from '../components/StatusTag.vue'

const selectedDepartment = ref<DepartmentName>('运营部')
const selectedQueueLevel = ref<Priority>('HIGH')
const selectedRows = ref<TaskItem[]>([])
const dispatchVisible = ref(false)
const dispatchTask = ref<TaskItem | null>(null)
const dispatchForm = reactive({
  mode: 'PROMOTE_HIGH_TOP',
  targetPriority: 'HIGH' as Priority,
  position: 1,
  durationMinutes: 30,
  reason: ''
})

const priorityNameMap: Record<Priority, string> = {
  HIGH: '高优先级',
  MEDIUM: '中优先级',
  LOW: '低优先级'
}
const priorityLevels: Array<{ label: string; value: Priority }> = [
  { label: '高优先级', value: 'HIGH' },
  { label: '中优先级', value: 'MEDIUM' },
  { label: '低优先级', value: 'LOW' }
]
const currentDepartmentConfig = computed(() => {
  return departmentQueues.find((queue) => queue.department === selectedDepartment.value) || departmentQueues[0]
})
const departmentCards = computed(() => {
  return departmentQueues.map((queue) => {
    const rows = tasks.filter((task) => task.department === queue.department)
    const waitingRows = rows.filter((task) => task.queuePosition > 0)
    return {
      ...queue,
      total: rows.length,
      waiting: waitingRows.length,
      accelerated: rows.filter((task) => task.manualAccelerated).length,
      usageRate: Math.min(100, Math.round((waitingRows.length / queue.maxCapacity) * 100))
    }
  })
})
const queueCards = computed(() => {
  return priorityLevels.map((level) => {
    const rows = tasks.filter((task) => task.department === selectedDepartment.value && task.queueLevel === level.value)
    const waitingRows = rows.filter((task) => task.queuePosition > 0)
    return {
      ...level,
      total: rows.length,
      waiting: waitingRows.length,
      accelerated: rows.filter((task) => task.manualAccelerated).length,
      maxWait: waitingRows.length ? Math.max(...waitingRows.map((task) => task.waitingMinutes)) : 0
    }
  })
})
const currentQueueTasks = computed(() => {
  return tasks
    .filter((task) => task.department === selectedDepartment.value && task.queueLevel === selectedQueueLevel.value)
    .sort((left, right) => left.queuePosition - right.queuePosition)
})
const currentWorkers = computed(() => queueWorkers.filter((worker) => worker.department === selectedDepartment.value))

const buildDepartmentQueueName = (task: TaskItem, priority: Priority) => `${task.department}-${priorityNameMap[priority]}队列`
const openDispatch = (task: TaskItem) => {
  dispatchTask.value = task
  dispatchForm.targetPriority = task.priority
  dispatchForm.position = Math.max(1, task.queuePosition || 1)
  dispatchForm.reason = task.dispatchReason || ''
  dispatchVisible.value = true
}
const applyDispatch = (task: TaskItem) => {
  task.priority = dispatchForm.targetPriority
  task.queueLevel = dispatchForm.targetPriority
  task.queueName = buildDepartmentQueueName(task, dispatchForm.targetPriority)
  task.queueCapacity = currentDepartmentConfig.value.maxCapacity
  task.queuePosition = dispatchForm.mode === 'PROMOTE_HIGH_TOP' ? 1 : dispatchForm.position
  task.waitingMinutes = 0
  task.estimatedStartAt = '尽快执行'
  task.manualAccelerated = true
  task.dispatchReason = dispatchForm.reason
}
const submitDispatch = () => {
  if (!dispatchTask.value) return
  if (!dispatchForm.reason.trim()) {
    ElMessage.warning('请填写调度原因')
    return
  }
  applyDispatch(dispatchTask.value)
  selectedDepartment.value = dispatchTask.value.department
  selectedQueueLevel.value = dispatchTask.value.queueLevel
  dispatchVisible.value = false
  ElMessage.success('已提交本部门内手工插队')
}
const batchPromote = () => {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先选择需要批量加急的任务')
    return
  }
  const crossDepartment = selectedRows.value.some((task) => task.department !== selectedDepartment.value)
  if (crossDepartment) {
    ElMessage.warning('批量加急只允许处理当前部门队列内的任务')
    return
  }
  selectedRows.value.forEach((task, index) => {
    task.priority = 'HIGH'
    task.queueLevel = 'HIGH'
    task.queueName = buildDepartmentQueueName(task, 'HIGH')
    task.queueCapacity = currentDepartmentConfig.value.maxCapacity
    task.queuePosition = index + 1
    task.waitingMinutes = 0
    task.estimatedStartAt = '尽快执行'
    task.manualAccelerated = true
    task.dispatchReason = '本部门队列批量手工加急'
  })
  selectedQueueLevel.value = 'HIGH'
  ElMessage.success(`已在${selectedDepartment.value}内批量加急 ${selectedRows.value.length} 个任务`)
}
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

    <div class="queue-dispatch-layout">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>{{ selectedDepartment }} / {{ priorityNameMap[selectedQueueLevel] }}任务</span>
            <div>
              <el-button @click="batchPromote">本部门批量加急到高优先级</el-button>
              <el-button type="primary">刷新队列</el-button>
            </div>
          </div>
        </template>
        <el-alert
          class="mb-12"
          :title="`${selectedDepartment}队列容量为 ${currentDepartmentConfig.maxCapacity}，只展示和调度本部门任务；其他部门队列互相隔离。`"
          type="info"
          :closable="false"
        />
        <el-table :data="currentQueueTasks" stripe @selection-change="selectedRows = $event">
          <el-table-column type="selection" width="42" />
          <el-table-column prop="queuePosition" label="序号" width="70" />
          <el-table-column prop="taskId" label="任务编号" min-width="170" />
          <el-table-column prop="fileName" label="文件名" min-width="160" />
          <el-table-column label="优先级" width="80">
            <template #default="{ row }"><PriorityTag :priority="row.priority" /></template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><StatusTag :status="row.status" /></template>
          </el-table-column>
          <el-table-column prop="waitingMinutes" label="等待分钟" width="90" />
          <el-table-column prop="estimatedStartAt" label="预计开始" width="100" />
          <el-table-column label="手工加急" width="90">
            <template #default="{ row }">
              <el-tag :type="row.manualAccelerated ? 'danger' : 'info'">{{ row.manualAccelerated ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDispatch(row)">插队</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never">
        <template #header>{{ selectedDepartment }} Worker 负载</template>
        <div v-for="worker in currentWorkers" :key="worker.workerId" class="worker-item">
          <div class="card-header">
            <strong>{{ worker.workerId }}</strong>
            <el-tag :type="worker.status === 'IDLE' ? 'success' : 'warning'">{{ worker.status }}</el-tag>
          </div>
          <p class="muted">绑定队列：{{ worker.queueName }}</p>
          <p class="muted">当前任务：{{ worker.currentTask }}</p>
          <p class="muted">阶段：{{ worker.stage }}</p>
          <el-progress :percentage="worker.load" />
        </div>
        <el-alert
          title="部门队列互相隔离，运营部任务不会占用财务部队列容量；部门内再按高、中、低优先级调度。"
          type="info"
          :closable="false"
        />
      </el-card>
    </div>

    <el-dialog v-model="dispatchVisible" title="本部门手工插队调度" width="620px">
      <template v-if="dispatchTask">
        <el-descriptions :column="2" border class="mb-12">
          <el-descriptions-item label="任务编号">{{ dispatchTask.taskId }}</el-descriptions-item>
          <el-descriptions-item label="所属部门">{{ dispatchTask.department }}</el-descriptions-item>
          <el-descriptions-item label="部门容量">{{ dispatchTask.queueCapacity }}</el-descriptions-item>
          <el-descriptions-item label="当前队列">{{ dispatchTask.queueName }}</el-descriptions-item>
          <el-descriptions-item label="当前序号">第 {{ dispatchTask.queuePosition }} 位</el-descriptions-item>
          <el-descriptions-item label="等待时长">{{ dispatchTask.waitingMinutes }} 分钟</el-descriptions-item>
        </el-descriptions>
        <el-form :model="dispatchForm" label-width="120px" class="form-grid">
          <el-form-item label="调整方式">
            <el-select v-model="dispatchForm.mode">
              <el-option label="提升到本部门高优先级队列并置顶" value="PROMOTE_HIGH_TOP" />
              <el-option label="在本部门内提升优先级并指定位置" value="PROMOTE_TO_POSITION" />
              <el-option label="本部门内临时加权加急" value="TEMP_WEIGHT" />
            </el-select>
          </el-form-item>
          <el-form-item label="部门内优先级">
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
