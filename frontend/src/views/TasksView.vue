<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { departmentQueues, tasks, timeline, type Priority, type TaskItem } from '../mock/data'
import StatusTag from '../components/StatusTag.vue'
import PriorityTag from '../components/PriorityTag.vue'
import ConfidenceTag from '../components/ConfidenceTag.vue'

const router = useRouter()
const drawerVisible = ref(false)
const dispatchVisible = ref(false)
const selectedTask = ref<TaskItem | null>(null)
const dispatchTask = ref<TaskItem | null>(null)
const query = reactive({
  keyword: '',
  sourceType: '',
  documentType: '',
  priority: '',
  status: ''
})
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
const getDepartmentCapacity = (department: string) => {
  return departmentQueues.find((queue) => queue.department === department)?.maxCapacity || 0
}
const buildDepartmentQueueName = (task: TaskItem, priority: Priority) => {
  return `${task.department}-${priorityNameMap[priority]}队列`
}

const filteredTasks = computed(() => {
  return tasks.filter((task) => {
    const keywordMatched = !query.keyword || task.taskId.includes(query.keyword) || task.fileName.includes(query.keyword)
    const sourceMatched = !query.sourceType || task.sourceType === query.sourceType
    const typeMatched = !query.documentType || task.documentType === query.documentType
    const priorityMatched = !query.priority || task.priority === query.priority
    const statusMatched = !query.status || task.status === query.status
    return keywordMatched && sourceMatched && typeMatched && priorityMatched && statusMatched
  })
})

const openDetail = (task: TaskItem) => {
  selectedTask.value = task
  drawerVisible.value = true
}
const openDispatch = (task: TaskItem) => {
  dispatchTask.value = task
  dispatchForm.targetPriority = task.priority
  dispatchForm.position = Math.max(1, task.queuePosition || 1)
  dispatchForm.reason = task.dispatchReason || ''
  dispatchVisible.value = true
}
const submitDispatch = () => {
  if (!dispatchTask.value) return
  if (!dispatchForm.reason.trim()) {
    ElMessage.warning('请填写插队原因')
    return
  }
  dispatchTask.value.priority = dispatchForm.targetPriority
  dispatchTask.value.queueLevel = dispatchForm.targetPriority
  dispatchTask.value.queueName = buildDepartmentQueueName(dispatchTask.value, dispatchForm.targetPriority)
  dispatchTask.value.queueCapacity = getDepartmentCapacity(dispatchTask.value.department)
  dispatchTask.value.queuePosition = dispatchForm.mode === 'PROMOTE_HIGH_TOP' ? 1 : dispatchForm.position
  dispatchTask.value.waitingMinutes = 0
  dispatchTask.value.estimatedStartAt = '尽快执行'
  dispatchTask.value.manualAccelerated = true
  dispatchTask.value.dispatchReason = dispatchForm.reason
  dispatchVisible.value = false
  ElMessage.success('已提交手工插队调整')
}
const cancelAcceleration = (task: TaskItem) => {
  task.manualAccelerated = false
  task.dispatchReason = ''
  ElMessage.success('已取消手工加急标记')
}
</script>

<template>
  <div class="page-stack">
    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="任务编号/文件名" clearable />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="query.sourceType" clearable placeholder="全部">
            <el-option label="手工上传" value="MANUAL_UPLOAD" />
            <el-option label="API" value="API" />
            <el-option label="邮件分拣" value="EMAIL" />
            <el-option label="文件分拣" value="FILE_DISPATCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="query.documentType" clearable placeholder="全部">
            <el-option label="划款指令" value="划款指令" />
            <el-option label="银行回单" value="银行回单" />
            <el-option label="开户资料" value="开户资料" />
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
            <el-option label="待复核" value="WAIT_REVIEW" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="失败" value="FAILED" />
            <el-option label="解析中" value="PARSING" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary">查询</el-button>
          <el-button>重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务列表</span>
          <div>
            <el-button @click="router.push('/documents/upload')">上传文档</el-button>
            <el-button type="primary">刷新队列</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filteredTasks" stripe>
        <el-table-column prop="taskId" label="任务编号" min-width="180" fixed />
        <el-table-column prop="traceId" label="TraceId" min-width="170" />
        <el-table-column prop="fileName" label="文件名" min-width="170" />
        <el-table-column prop="sourceType" label="来源" width="130" />
        <el-table-column prop="documentType" label="文档类型" width="110" />
        <el-table-column label="优先级" width="80">
          <template #default="{ row }"><PriorityTag :priority="row.priority" /></template>
        </el-table-column>
        <el-table-column prop="department" label="部门队列" width="90" />
        <el-table-column prop="queueCapacity" label="队列容量" width="90" />
        <el-table-column prop="queueName" label="当前队列" width="160" />
        <el-table-column label="排队序号" width="90">
          <template #default="{ row }">
            <span v-if="row.queuePosition > 0">第 {{ row.queuePosition }} 位</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="等待" width="84">
          <template #default="{ row }">{{ row.waitingMinutes ? `${row.waitingMinutes} 分钟` : '-' }}</template>
        </el-table-column>
        <el-table-column prop="estimatedStartAt" label="预计开始" width="100" />
        <el-table-column label="手工加急" width="90">
          <template #default="{ row }">
            <el-tag :type="row.manualAccelerated ? 'danger' : 'info'">{{ row.manualAccelerated ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="进度" width="150">
          <template #default="{ row }"><el-progress :percentage="row.progress" /></template>
        </el-table-column>
        <el-table-column label="置信度" width="100">
          <template #default="{ row }">
            <ConfidenceTag v-if="row.confidence" :value="row.confidence" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openDispatch(row)">插队</el-button>
            <el-button v-if="row.manualAccelerated" link @click="cancelAcceleration(row)">取消加急</el-button>
            <el-button link type="primary" @click="router.push('/reviews')">复核</el-button>
            <el-button link>重试</el-button>
            <el-button link type="danger">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="任务详情" size="520px">
      <template v-if="selectedTask">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="任务编号">{{ selectedTask.taskId }}</el-descriptions-item>
          <el-descriptions-item label="TraceId">{{ selectedTask.traceId }}</el-descriptions-item>
          <el-descriptions-item label="文件名">{{ selectedTask.fileName }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ selectedTask.department }}</el-descriptions-item>
          <el-descriptions-item label="当前阶段">{{ selectedTask.currentStage }}</el-descriptions-item>
          <el-descriptions-item label="当前队列">{{ selectedTask.queueName }}</el-descriptions-item>
          <el-descriptions-item label="部门队列容量">{{ selectedTask.queueCapacity }}</el-descriptions-item>
          <el-descriptions-item label="排队序号">{{ selectedTask.queuePosition > 0 ? `第 ${selectedTask.queuePosition} 位` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="手工加急">{{ selectedTask.manualAccelerated ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item v-if="selectedTask.dispatchReason" label="调度原因">{{ selectedTask.dispatchReason }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="section-title">状态时间线</h3>
        <el-timeline>
          <el-timeline-item v-for="item in timeline" :key="item.stage" :timestamp="item.time">
            <strong>{{ item.stage }}</strong>
            <p>{{ item.description }}</p>
          </el-timeline-item>
        </el-timeline>
        <h3 class="section-title">处理摘要</h3>
        <el-alert title="金额、收款账号字段置信度低于 90%，已触发人工复核。" type="warning" :closable="false" />
      </template>
    </el-drawer>

    <el-dialog v-model="dispatchVisible" title="手工插队调度" width="620px">
      <template v-if="dispatchTask">
        <el-alert
          class="mb-12"
          title="仅建议对排队中或等待资源的任务插队。已被 Worker 取走的运行中任务，实际系统应转为后续阶段加急或重新排队。"
          type="warning"
          :closable="false"
        />
        <el-descriptions :column="2" border class="mb-12">
          <el-descriptions-item label="任务编号">{{ dispatchTask.taskId }}</el-descriptions-item>
          <el-descriptions-item label="文件名">{{ dispatchTask.fileName }}</el-descriptions-item>
          <el-descriptions-item label="所属部门">{{ dispatchTask.department }}</el-descriptions-item>
          <el-descriptions-item label="部门队列容量">{{ dispatchTask.queueCapacity }}</el-descriptions-item>
          <el-descriptions-item label="当前队列">{{ dispatchTask.queueName }}</el-descriptions-item>
          <el-descriptions-item label="排队序号">第 {{ dispatchTask.queuePosition }} 位</el-descriptions-item>
        </el-descriptions>
        <el-form :model="dispatchForm" label-width="120px" class="form-grid">
          <el-form-item label="调整方式">
            <el-select v-model="dispatchForm.mode">
              <el-option label="提升到本部门高优先级队列并置顶" value="PROMOTE_HIGH_TOP" />
              <el-option label="在本部门内提升优先级并指定位置" value="PROMOTE_TO_POSITION" />
              <el-option label="临时加权加急" value="TEMP_WEIGHT" />
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
