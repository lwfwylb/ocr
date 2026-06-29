<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

interface CallLog {
  callId: string
  traceId: string
  taskId: string
  callType: 'OCR' | 'LLM'
  modelName: string
  stage: string
  status: 'SUCCESS' | 'FAILED'
  durationMs: number
  tokenUsage: number | null
  errorMessage: string | null
  createdAt: string
}

const router = useRouter()
const query = reactive({
  keyword: '',
  callTypes: [] as string[],
  statuses: [] as string[]
})

const logs = ref<CallLog[]>([
  { callId: 'CALL-0001', traceId: 'TRACE-20260628-0001', taskId: 'TASK-20260628-0001', callType: 'OCR', modelName: 'PaddleOCR-VL-1.6', stage: '文档解析', status: 'SUCCESS', durationMs: 8120, tokenUsage: null, errorMessage: null, createdAt: '2026-06-28 09:30:08' },
  { callId: 'CALL-0002', traceId: 'TRACE-20260628-0001', taskId: 'TASK-20260628-0001', callType: 'LLM', modelName: 'Qwen3.6-27B', stage: '要素提取', status: 'SUCCESS', durationMs: 4320, tokenUsage: 4096, errorMessage: null, createdAt: '2026-06-28 09:30:16' },
  { callId: 'CALL-0003', traceId: 'TRACE-20260628-0011', taskId: 'TASK-20260628-0011', callType: 'OCR', modelName: 'MinerU', stage: '文档解析', status: 'FAILED', durationMs: 180000, tokenUsage: null, errorMessage: 'OCR 服务调用超时', createdAt: '2026-06-28 15:06:33' }
])

const filteredLogs = computed(() => {
  return logs.value.filter((log) => {
    const keywordMatched = !query.keyword || log.traceId.includes(query.keyword) || log.taskId.includes(query.keyword) || log.callId.includes(query.keyword) || log.modelName.includes(query.keyword)
    const typeMatched = query.callTypes.length === 0 || query.callTypes.includes(log.callType)
    const statusMatched = query.statuses.length === 0 || query.statuses.includes(log.status)
    return keywordMatched && typeMatched && statusMatched
  })
})

const resetQuery = () => {
  query.keyword = ''
  query.callTypes = []
  query.statuses = []
}
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>调用次数</span><strong>{{ logs.length }}</strong><em>今日</em></el-card>
      <el-card shadow="never" class="metric-card"><span>成功</span><strong>{{ logs.filter((l) => l.status === 'SUCCESS').length }}</strong><em>正常返回</em></el-card>
      <el-card shadow="never" class="metric-card"><span>失败</span><strong>{{ logs.filter((l) => l.status === 'FAILED').length }}</strong><em>需排查</em></el-card>
      <el-card shadow="never" class="metric-card"><span>平均耗时</span><strong>6.4s</strong><em>OCR/LLM</em></el-card>
      <el-card shadow="never" class="metric-card"><span>Token</span><strong>4096</strong><em>示例任务</em></el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="trace/任务/调用/模型" /></el-form-item>
        <el-form-item label="调用类型">
          <el-select v-model="query.callTypes" multiple filterable clearable collapse-tags>
            <el-option label="OCR" value="OCR" />
            <el-option label="LLM" value="LLM" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.statuses" multiple filterable clearable collapse-tags>
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>调用日志</template>
      <el-table :data="filteredLogs" stripe>
        <el-table-column prop="callId" label="调用编号" min-width="120" fixed />
        <el-table-column prop="traceId" label="TraceId" min-width="170" />
        <el-table-column prop="taskId" label="任务编号" min-width="170" />
        <el-table-column prop="callType" label="类型" width="80" />
        <el-table-column prop="modelName" label="模型/引擎" min-width="160" />
        <el-table-column prop="stage" label="环节" width="100" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }"><el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'">{{ row.status === 'SUCCESS' ? '成功' : '失败' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="耗时" width="100"><template #default="{ row }">{{ row.durationMs }}ms</template></el-table-column>
        <el-table-column label="Token" width="90"><template #default="{ row }">{{ row.tokenUsage || '-' }}</template></el-table-column>
        <el-table-column prop="errorMessage" label="错误信息" min-width="180" />
        <el-table-column prop="createdAt" label="调用时间" min-width="150" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default><el-button link type="primary" @click="router.push('/monitor/traces')">链路</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
