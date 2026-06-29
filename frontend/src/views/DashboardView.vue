<script setup lang="ts">
import { useRouter } from 'vue-router'
import { dashboardStats, tasks } from '../mock/data'
import StatusTag from '../components/StatusTag.vue'
import PriorityTag from '../components/PriorityTag.vue'

const router = useRouter()
const pendingReviews = tasks.filter((task) => task.status === 'WAIT_REVIEW')
const failedTasks = tasks.filter((task) => task.status === 'FAILED')
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid">
      <el-card v-for="stat in dashboardStats" :key="stat.label" shadow="never" class="metric-card">
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
            <el-tag type="success">Mock 数据</el-tag>
          </div>
        </template>
        <div class="chart-bars">
          <div v-for="height in [40, 66, 52, 78, 63, 88, 72]" :key="height" class="bar" :style="{ height: `${height}%` }" />
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
          <div><PriorityTag priority="HIGH" /><span>高优先级积压</span><strong>6</strong></div>
          <div><PriorityTag priority="MEDIUM" /><span>中优先级积压</span><strong>13</strong></div>
          <div><PriorityTag priority="LOW" /><span>低优先级积压</span><strong>28</strong></div>
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
        <el-table :data="pendingReviews" height="220">
          <el-table-column prop="taskId" label="任务编号" min-width="170" />
          <el-table-column prop="documentType" label="文档类型" width="110" />
          <el-table-column label="优先级" width="90">
            <template #default="{ row }"><PriorityTag :priority="row.priority" /></template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><StatusTag :status="row.status" /></template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>异常提醒</span>
            <el-button link type="primary" @click="router.push('/tasks/failed')">查看失败</el-button>
          </div>
        </template>
        <el-alert title="4 个任务处理失败，其中 3 个可重试" type="error" :closable="false" />
        <el-table :data="failedTasks" height="176" class="mt-12">
          <el-table-column prop="taskId" label="任务编号" min-width="170" />
          <el-table-column prop="fileName" label="文件名" min-width="130" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><StatusTag :status="row.status" /></template>
          </el-table-column>
        </el-table>
      </el-card>
    </section>
  </div>
</template>
