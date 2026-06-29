<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { tasks, timeline, type TaskItem } from '../mock/data'
import StatusTag from '../components/StatusTag.vue'
import PriorityTag from '../components/PriorityTag.vue'
import ConfidenceTag from '../components/ConfidenceTag.vue'

const router = useRouter()
const drawerVisible = ref(false)
const selectedTask = ref<TaskItem | null>(null)
const query = reactive({
  keyword: '',
  sourceType: '',
  documentType: '',
  priority: '',
  status: ''
})

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
  </div>
</template>
