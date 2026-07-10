<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listPushRecords,
  markPushRecordSuccess,
  retryPushRecord,
  type PushMethod,
  type PushRecord,
  type PushStatus
} from '../api/push'

const records = ref<PushRecord[]>([])
const loading = ref(false)
const drawerVisible = ref(false)
const selectedRecord = ref<PushRecord | null>(null)
const query = reactive({
  keyword: '',
  targetSystem: '',
  serviceCode: '',
  status: '',
  pushMethod: ''
})

const statusMap: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  SUCCESS: { label: '成功', type: 'success' },
  PENDING: { label: '待推送', type: 'info' },
  FAILED: { label: '失败', type: 'danger' },
  RETRYING: { label: '重试中', type: 'warning' }
}

const methodMap: Record<string, string> = {
  HTTP: 'HTTP',
  MICROSERVICE: '微服务',
  MQ: 'MQ'
}

const triggerMap: Record<string, string> = {
  MANUAL: '手工触发',
  AUTO_AFTER_STORAGE: '落库后自动'
}

const targetSystemOptions = computed(() => Array.from(new Set(records.value.map((item) => item.targetSystem).filter(Boolean))) as string[])
const serviceOptions = computed(() => {
  const items = records.value
    .map((item) => ({ code: item.serviceCode || '', name: item.serviceName || item.serviceCode || '' }))
    .filter((item) => item.code)
  return Array.from(new Map(items.map((item) => [item.code, item])).values())
})

const metrics = computed(() => {
  const total = records.value.length
  const success = records.value.filter((item) => item.status === 'SUCCESS').length
  const failed = records.value.filter((item) => item.status === 'FAILED').length
  const waiting = records.value.filter((item) => ['PENDING', 'RETRYING'].includes(item.status)).length
  const successRate = total ? Math.round((success / total) * 100) : 0
  return { total, success, failed, waiting, successRate }
})

const loadRecords = async () => {
  loading.value = true
  try {
    records.value = await listPushRecords(query)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询推送记录失败')
  } finally {
    loading.value = false
  }
}

const openDetail = (record: PushRecord) => {
  selectedRecord.value = record
  drawerVisible.value = true
}

const retryPush = async (record: PushRecord) => {
  await ElMessageBox.confirm(`确认重新推送 ${record.pushId}？`, '失败重试', { type: 'warning' })
  try {
    const updated = await retryPushRecord(record.pushId)
    replaceRecord(updated)
    selectedRecord.value = selectedRecord.value?.pushId === updated.pushId ? updated : selectedRecord.value
    ElMessage.success('重试完成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重试失败')
  }
}

const markSuccess = async (record: PushRecord) => {
  await ElMessageBox.confirm(`确认将 ${record.pushId} 标记为下游已接收？`, '人工确认成功', { type: 'warning' })
  try {
    const updated = await markPushRecordSuccess(record.pushId)
    replaceRecord(updated)
    selectedRecord.value = selectedRecord.value?.pushId === updated.pushId ? updated : selectedRecord.value
    ElMessage.success('已人工确认成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '人工确认失败')
  }
}

const batchRetryFailed = async () => {
  const failedRecords = records.value.filter((item) => item.status === 'FAILED')
  if (!failedRecords.length) {
    ElMessage.info('当前没有失败记录')
    return
  }
  await ElMessageBox.confirm(`确认重试当前列表中的 ${failedRecords.length} 条失败记录？`, '批量重试', { type: 'warning' })
  let success = 0
  for (const record of failedRecords) {
    try {
      const updated = await retryPushRecord(record.pushId)
      replaceRecord(updated)
      success += 1
    } catch {
      // 批量重试不中断，失败记录仍保留在列表中。
    }
  }
  ElMessage.success(`已提交 ${success} 条重试`)
}

const replaceRecord = (record: PushRecord) => {
  const index = records.value.findIndex((item) => item.pushId === record.pushId)
  if (index >= 0) {
    records.value.splice(index, 1, record)
  } else {
    records.value.unshift(record)
  }
}

const resetQuery = () => {
  query.keyword = ''
  query.targetSystem = ''
  query.serviceCode = ''
  query.status = ''
  query.pushMethod = ''
  loadRecords()
}

const getStatus = (status?: PushStatus) => statusMap[status || ''] || { label: status || '-', type: 'info' }
const getMethod = (method?: PushMethod) => (method ? methodMap[method] || method : '-')
const getTrigger = (trigger?: string) => (trigger ? triggerMap[trigger] || trigger : '-')

onMounted(loadRecords)
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>推送总数</span><strong>{{ metrics.total }}</strong><em>后端推送记录</em></el-card>
      <el-card shadow="never" class="metric-card"><span>成功</span><strong>{{ metrics.success }}</strong><em>下游已接收</em></el-card>
      <el-card shadow="never" class="metric-card"><span>待处理</span><strong>{{ metrics.waiting }}</strong><em>待推送或重试中</em></el-card>
      <el-card shadow="never" class="metric-card"><span>失败</span><strong>{{ metrics.failed }}</strong><em>可人工重试</em></el-card>
      <el-card shadow="never" class="metric-card"><span>成功率</span><strong>{{ metrics.successRate }}%</strong><em>当前查询范围</em></el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="pushId/traceId/任务/幂等键" @keyup.enter="loadRecords" />
        </el-form-item>
        <el-form-item label="目标系统">
          <el-select v-model="query.targetSystem" filterable clearable placeholder="全部">
            <el-option v-for="item in targetSystemOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="接口服务">
          <el-select v-model="query.serviceCode" filterable clearable placeholder="全部">
            <el-option v-for="item in serviceOptions" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" filterable clearable placeholder="全部">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="待推送" value="PENDING" />
            <el-option label="失败" value="FAILED" />
            <el-option label="重试中" value="RETRYING" />
          </el-select>
        </el-form-item>
        <el-form-item label="方式">
          <el-select v-model="query.pushMethod" filterable clearable placeholder="全部">
            <el-option label="HTTP" value="HTTP" />
            <el-option label="微服务" value="MICROSERVICE" />
            <el-option label="MQ" value="MQ" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadRecords">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>推送记录</span>
          <div>
            <el-button @click="loadRecords">刷新</el-button>
            <el-button type="primary" @click="batchRetryFailed">批量重试失败</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="pushId" label="推送编号" min-width="180" fixed />
        <el-table-column prop="traceId" label="TraceId" min-width="180" />
        <el-table-column prop="taskId" label="任务编号" min-width="180" />
        <el-table-column prop="targetSystem" label="目标系统" min-width="140" />
        <el-table-column label="接口服务" min-width="170">
          <template #default="{ row }">{{ row.serviceName || row.serviceCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="方式" width="90">
          <template #default="{ row }">{{ getMethod(row.pushMethod) }}</template>
        </el-table-column>
        <el-table-column label="触发方式" width="110">
          <template #default="{ row }">{{ getTrigger(row.triggerType) }}</template>
        </el-table-column>
        <el-table-column prop="idempotentKey" label="幂等键" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="getStatus(row.status).type">{{ getStatus(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试" width="70" />
        <el-table-column prop="pushedAt" label="最近推送时间" min-width="165" />
        <el-table-column prop="responseMessage" label="响应摘要" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="warning" :disabled="row.status === 'SUCCESS'" @click="retryPush(row)">重试</el-button>
            <el-button link type="success" :disabled="row.status === 'SUCCESS'" @click="markSuccess(row)">确认成功</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="推送详情" size="680px">
      <template v-if="selectedRecord">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="推送编号">{{ selectedRecord.pushId }}</el-descriptions-item>
          <el-descriptions-item label="TraceId">{{ selectedRecord.traceId }}</el-descriptions-item>
          <el-descriptions-item label="任务编号">{{ selectedRecord.taskId }}</el-descriptions-item>
          <el-descriptions-item label="目标系统">{{ selectedRecord.targetSystem || '-' }}</el-descriptions-item>
          <el-descriptions-item label="接口服务">{{ selectedRecord.serviceName || selectedRecord.serviceCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="推送方式">{{ getMethod(selectedRecord.pushMethod) }}</el-descriptions-item>
          <el-descriptions-item label="触发方式">{{ getTrigger(selectedRecord.triggerType) }}</el-descriptions-item>
          <el-descriptions-item label="幂等键">{{ selectedRecord.idempotentKey || '-' }}</el-descriptions-item>
          <el-descriptions-item label="响应码">{{ selectedRecord.responseCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="响应摘要">{{ selectedRecord.responseMessage || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="section-title">请求报文</h3>
        <pre class="payload-preview">{{ selectedRecord.requestPayload || '暂无请求报文' }}</pre>
        <h3 class="section-title">响应报文</h3>
        <pre class="payload-preview">{{ selectedRecord.responsePayload || '暂无响应报文' }}</pre>
        <el-alert
          title="可使用 TraceId 到全链路监控中回溯文档接入、解析、提取、复核、落库和下游推送全过程。"
          type="info"
          :closable="false"
        />
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.payload-preview {
  min-height: 100px;
  max-height: 220px;
  overflow: auto;
  padding: 10px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
