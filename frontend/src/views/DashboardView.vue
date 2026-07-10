<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getDashboardOverview, type DashboardOverview } from '../api/dashboard'
import StatusTag from '../components/StatusTag.vue'
import PriorityTag from '../components/PriorityTag.vue'

const router = useRouter()
const loading = ref(false)
const overview = ref<DashboardOverview>({
  metrics: [],
  taskTrend: [],
  queueSummary: [],
  pendingReviews: [],
  failedTasks: []
})

const maxTrendCount = computed(() => Math.max(...overview.value.taskTrend.map((item) => item.count), 1))
const failedRetryableCount = computed(() => overview.value.failedTasks.length)

const loadOverview = async () => {
  loading.value = true
  try {
    overview.value = await getDashboardOverview()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载首页数据失败')
  } finally {
    loading.value = false
  }
}

const barHeight = (count: number) => {
  if (!count) return 8
  return Math.max(16, Math.round((count / maxTrendCount.value) * 100))
}

onMounted(loadOverview)
</script>

<template>
  <div v-loading="loading" class="page-stack">
    <section class="metric-grid">
      <el-card v-for="stat in overview.metrics" :key="stat.label" shadow="never" class="metric-card">
        <span>{{ stat.label }}</span>
        <strong>{{ stat.value }}</strong>
        <em>{{ stat.trend }}</em>
      </el-card>
    </section>

    <section class="dashboard-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>近 7 天任务趋势</span>
            <el-button link type="primary" @click="loadOverview">刷新</el-button>
          </div>
        </template>
        <div class="chart-bars dashboard-bars">
          <div v-for="item in overview.taskTrend" :key="item.date" class="dashboard-bar-item">
            <div class="bar" :style="{ height: `${barHeight(item.count)}%` }" />
            <span>{{ item.label }}</span>
            <strong>{{ item.count }}</strong>
          </div>
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>优先级队列</span>
            <el-button link type="primary" @click="router.push('/tasks')">查看任务</el-button>
          </div>
        </template>
        <div class="queue-list">
          <div v-for="item in overview.queueSummary" :key="item.priority">
            <PriorityTag :priority="item.priority" />
            <span>{{ item.label }}</span>
            <strong>{{ item.count }}</strong>
          </div>
        </div>
      </el-card>
    </section>

    <section class="dashboard-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>待复核</span>
            <el-button link type="primary" @click="router.push('/reviews')">进入复核</el-button>
          </div>
        </template>
        <el-table :data="overview.pendingReviews" height="220">
          <el-table-column prop="taskId" label="任务编号" min-width="170" />
          <el-table-column prop="documentType" label="文档类型" width="120" />
          <el-table-column label="优先级" width="90">
            <template #default="{ row }"><PriorityTag :priority="row.priority" /></template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><StatusTag :status="row.status" /></template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" min-width="150" />
        </el-table>
        <el-empty v-if="!overview.pendingReviews.length" description="暂无待复核任务" />
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>异常提醒</span>
            <el-button link type="primary" @click="router.push('/tasks/failed')">查看失败</el-button>
          </div>
        </template>
        <el-alert
          :title="`${overview.failedTasks.length} 个任务处理失败，其中 ${failedRetryableCount} 个可在失败任务中重试`"
          type="error"
          :closable="false"
        />
        <el-table :data="overview.failedTasks" height="176" class="mt-12">
          <el-table-column prop="taskId" label="任务编号" min-width="170" />
          <el-table-column prop="fileName" label="文件名" min-width="130" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><StatusTag :status="row.status" /></template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="失败原因" min-width="180" show-overflow-tooltip />
        </el-table>
      </el-card>
    </section>
  </div>
</template>

<style scoped>
.dashboard-bars {
  align-items: end;
  gap: 12px;
  height: 220px;
  padding-bottom: 30px;
}

.dashboard-bar-item {
  height: 100%;
  flex: 1;
  display: grid;
  grid-template-rows: 1fr auto auto;
  align-items: end;
  gap: 4px;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.dashboard-bar-item .bar {
  width: 100%;
  min-height: 8px;
}

.dashboard-bar-item strong {
  color: var(--el-text-color-primary);
  font-size: 12px;
}
</style>
