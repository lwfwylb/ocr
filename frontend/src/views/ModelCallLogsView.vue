<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getModelCallLogDetail,
  listModelCallLogs,
  type ModelCallLog,
  type ModelCallStatus,
  type ModelCallType
} from '../api/modelCallLog'

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

const router = useRouter()
const loading = ref(false)
const detailLoading = ref(false)
const drawerVisible = ref(false)
const logs = ref<ModelCallLog[]>([])
const selectedLog = ref<ModelCallLog | null>(null)
const query = reactive({
  keyword: '',
  callType: '',
  status: '',
  stageCode: ''
})

const statusMap: Record<string, { label: string; type: TagType }> = {
  SUCCESS: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' }
}

const typeMap: Record<string, string> = {
  OCR: 'OCR',
  LLM: '大模型'
}

const stageMap: Record<string, string> = {
  PARSE: '文档解析',
  EXTRACT: '要素提取',
  VALIDATE: '加工校验'
}

const metrics = computed(() => {
  const total = logs.value.length
  const success = logs.value.filter((item) => item.status === 'SUCCESS').length
  const failed = logs.value.filter((item) => item.status === 'FAILED').length
  const durations = logs.value.map((item) => Number(item.durationMs || 0)).filter((item) => item > 0)
  const avgDuration = durations.length ? Math.round(durations.reduce((sum, item) => sum + item, 0) / durations.length) : 0
  const tokens = logs.value.reduce((sum, item) => sum + Number(item.inputTokens || 0) + Number(item.outputTokens || 0), 0)
  return { total, success, failed, avgDuration, tokens }
})

const loadLogs = async () => {
  loading.value = true
  try {
    logs.value = await listModelCallLogs(query)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询调用日志失败')
  } finally {
    loading.value = false
  }
}

const openDetail = async (row: ModelCallLog) => {
  drawerVisible.value = true
  selectedLog.value = row
  detailLoading.value = true
  try {
    selectedLog.value = await getModelCallLogDetail(row.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询调用详情失败')
  } finally {
    detailLoading.value = false
  }
}

const copyTraceId = async (traceId?: string) => {
  if (!traceId) {
    ElMessage.info('当前日志没有 TraceId')
    return
  }
  try {
    await navigator.clipboard.writeText(traceId)
    ElMessage.success('TraceId 已复制')
  } catch {
    ElMessage.warning('复制失败，请手工复制 TraceId')
  }
}

const resetQuery = () => {
  query.keyword = ''
  query.callType = ''
  query.status = ''
  query.stageCode = ''
  loadLogs()
}

const goTrace = (traceId?: string) => {
  if (traceId) {
    copyTraceId(traceId)
  }
  router.push('/monitor/traces')
}

const getStatus = (status?: ModelCallStatus) => statusMap[status || ''] || { label: status || '-', type: 'info' as TagType }
const getType = (type?: ModelCallType) => (type ? typeMap[type] || type : '-')
const getStage = (stage?: string, stageName?: string) => stageName || (stage ? stageMap[stage] || stage : '-')
const formatTokens = (row: ModelCallLog) => {
  const input = Number(row.inputTokens || 0)
  const output = Number(row.outputTokens || 0)
  return input || output ? `${input}/${output}` : '-'
}

onMounted(loadLogs)
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>调用次数</span><strong>{{ metrics.total }}</strong><em>当前查询范围</em></el-card>
      <el-card shadow="never" class="metric-card"><span>成功</span><strong>{{ metrics.success }}</strong><em>正常返回</em></el-card>
      <el-card shadow="never" class="metric-card"><span>失败</span><strong>{{ metrics.failed }}</strong><em>需要排查</em></el-card>
      <el-card shadow="never" class="metric-card"><span>平均耗时</span><strong>{{ metrics.avgDuration }}ms</strong><em>OCR/LLM</em></el-card>
      <el-card shadow="never" class="metric-card"><span>Token</span><strong>{{ metrics.tokens }}</strong><em>输入/输出合计</em></el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="trace/任务/调用/模型" @keyup.enter="loadLogs" />
        </el-form-item>
        <el-form-item label="调用类型">
          <el-select v-model="query.callType" filterable clearable placeholder="全部">
            <el-option label="OCR" value="OCR" />
            <el-option label="大模型" value="LLM" />
          </el-select>
        </el-form-item>
        <el-form-item label="环节">
          <el-select v-model="query.stageCode" filterable clearable placeholder="全部">
            <el-option label="文档解析" value="PARSE" />
            <el-option label="要素提取" value="EXTRACT" />
            <el-option label="加工校验" value="VALIDATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" filterable clearable placeholder="全部">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadLogs">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>调用日志</span>
          <el-button @click="loadLogs">刷新</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="logs" stripe>
        <el-table-column prop="callId" label="调用编号" min-width="170" fixed />
        <el-table-column prop="traceId" label="TraceId" min-width="180" />
        <el-table-column prop="taskId" label="任务编号" min-width="180" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ getType(row.callType) }}</template>
        </el-table-column>
        <el-table-column label="模型/引擎" min-width="180">
          <template #default="{ row }">{{ row.modelName || row.modelCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="环节" width="110">
          <template #default="{ row }">{{ getStage(row.stageCode, row.stageName) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="getStatus(row.status).type">{{ getStatus(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">{{ row.durationMs || 0 }}ms</template>
        </el-table-column>
        <el-table-column label="Token" width="100">
          <template #default="{ row }">{{ formatTokens(row) }}</template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误信息" min-width="180" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="调用时间" min-width="160" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="copyTraceId(row.traceId)">复制Trace</el-button>
            <el-button link type="primary" @click="goTrace(row.traceId)">链路</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="调用详情" size="680px">
      <template v-if="selectedLog">
        <div v-loading="detailLoading">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="调用编号">{{ selectedLog.callId }}</el-descriptions-item>
            <el-descriptions-item label="TraceId">{{ selectedLog.traceId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="任务编号">{{ selectedLog.taskId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="配置编号">{{ selectedLog.configId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="调用类型">{{ getType(selectedLog.callType) }}</el-descriptions-item>
            <el-descriptions-item label="环节">{{ getStage(selectedLog.stageCode, selectedLog.stageName) }}</el-descriptions-item>
            <el-descriptions-item label="供应商">{{ selectedLog.provider || '-' }}</el-descriptions-item>
            <el-descriptions-item label="模型/引擎">{{ selectedLog.modelName || selectedLog.modelCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="耗时">{{ selectedLog.durationMs || 0 }}ms</el-descriptions-item>
            <el-descriptions-item label="Token">{{ formatTokens(selectedLog) }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatus(selectedLog.status).type">{{ getStatus(selectedLog.status).label }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="错误信息">{{ selectedLog.errorMessage || '-' }}</el-descriptions-item>
          </el-descriptions>
          <h3 class="section-title">请求摘要</h3>
          <pre class="call-preview">{{ selectedLog.requestSummary || '暂无请求摘要' }}</pre>
          <h3 class="section-title">响应摘要</h3>
          <pre class="call-preview">{{ selectedLog.responseSummary || '暂无响应摘要' }}</pre>
          <h3 class="section-title">提示词预览</h3>
          <pre class="call-preview">{{ selectedLog.promptPreview || '暂无提示词预览' }}</pre>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.call-preview {
  min-height: 90px;
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
