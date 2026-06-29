<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { traceRecords, traceStages } from '../mock/data'

type StageStatus = 'SUCCESS' | 'WARNING' | 'WAITING' | 'PENDING' | 'FAILED'

const selectedTraceId = ref('TRACE-20260628-0001')
const query = reactive({
  keyword: '',
  sourceTypes: [] as string[],
  statuses: [] as string[]
})

const selectedTrace = computed(() => traceRecords.find((item) => item.traceId === selectedTraceId.value) || traceRecords[0])
const filteredRecords = computed(() => {
  return traceRecords.filter((item) => {
    const keywordMatched =
      !query.keyword ||
      item.traceId.includes(query.keyword) ||
      item.taskId.includes(query.keyword) ||
      item.documentId.includes(query.keyword) ||
      item.businessNo.includes(query.keyword) ||
      item.fileName.includes(query.keyword)
    const sourceMatched = query.sourceTypes.length === 0 || query.sourceTypes.includes(item.sourceType)
    const statusMatched = query.statuses.length === 0 || query.statuses.includes(item.status)
    return keywordMatched && sourceMatched && statusMatched
  })
})

const statusMap: Record<StageStatus | string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' | 'primary' }> = {
  SUCCESS: { label: '成功', type: 'success' },
  WARNING: { label: '预警', type: 'warning' },
  WAITING: { label: '等待', type: 'warning' },
  PENDING: { label: '未开始', type: 'info' },
  FAILED: { label: '失败', type: 'danger' },
  WAIT_REVIEW: { label: '待复核', type: 'warning' },
  PUSHED: { label: '已推送', type: 'success' }
}

const resetQuery = () => {
  query.keyword = ''
  query.sourceTypes = []
  query.statuses = []
}
</script>

<template>
  <div class="trace-monitor-layout">
    <el-card shadow="never" class="trace-list-card">
      <template #header>
        <div class="card-header">
          <span>链路检索</span>
          <el-tag type="primary">Trace</el-tag>
        </div>
      </template>
      <el-form :model="query" label-position="top">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="traceId/任务/业务号/文件名" />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="query.sourceTypes" multiple filterable clearable collapse-tags placeholder="全部">
            <el-option label="手工上传" value="MANUAL_UPLOAD" />
            <el-option label="API 推送" value="API" />
            <el-option label="邮件分拣" value="EMAIL" />
            <el-option label="文件分拣" value="FILE_DISPATCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.statuses" multiple filterable clearable collapse-tags placeholder="全部">
            <el-option label="待复核" value="WAIT_REVIEW" />
            <el-option label="已推送" value="PUSHED" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-button type="primary">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form>

      <div class="trace-records">
        <div
          v-for="record in filteredRecords"
          :key="record.traceId"
          class="trace-record"
          :class="{ active: selectedTraceId === record.traceId }"
          @click="selectedTraceId = record.traceId"
        >
          <strong>{{ record.traceId }}</strong>
          <span>{{ record.fileName }}</span>
          <em>{{ record.currentStage }} / {{ record.duration }}</em>
        </div>
      </div>
    </el-card>

    <div class="page-stack">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>链路概览</span>
            <el-tag :type="statusMap[selectedTrace.status]?.type || 'info'">
              {{ statusMap[selectedTrace.status]?.label || selectedTrace.status }}
            </el-tag>
          </div>
        </template>
        <el-descriptions :column="4" border>
          <el-descriptions-item label="TraceId">{{ selectedTrace.traceId }}</el-descriptions-item>
          <el-descriptions-item label="任务编号">{{ selectedTrace.taskId }}</el-descriptions-item>
          <el-descriptions-item label="文档编号">{{ selectedTrace.documentId }}</el-descriptions-item>
          <el-descriptions-item label="业务号">{{ selectedTrace.businessNo }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ selectedTrace.sourceType }}</el-descriptions-item>
          <el-descriptions-item label="文档类型">{{ selectedTrace.documentType }}</el-descriptions-item>
          <el-descriptions-item label="所属部门">{{ selectedTrace.owner }}</el-descriptions-item>
          <el-descriptions-item label="当前阶段">{{ selectedTrace.currentStage }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>全链路阶段</span>
            <span class="muted">接入、匹配、队列、解析、提取、加工、落库、复核、推送</span>
          </div>
        </template>
        <el-steps :active="6" align-center finish-status="success" class="trace-steps">
          <el-step v-for="stage in traceStages" :key="stage.stage" :title="stage.stage" :status="stage.status === 'FAILED' ? 'error' : stage.status === 'WAITING' ? 'process' : stage.status === 'PENDING' ? 'wait' : 'success'" />
        </el-steps>
        <el-table :data="traceStages" stripe class="mt-12">
          <el-table-column prop="stage" label="环节" width="110" fixed />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="statusMap[row.status]?.type || 'info'">{{ statusMap[row.status]?.label || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="startedAt" label="开始时间" width="100" />
          <el-table-column label="耗时" width="90">
            <template #default="{ row }">{{ row.durationMs ? `${row.durationMs}ms` : '-' }}</template>
          </el-table-column>
          <el-table-column prop="operator" label="处理方" min-width="130" />
          <el-table-column prop="input" label="输入摘要" min-width="240" />
          <el-table-column prop="output" label="输出摘要" min-width="220" />
        </el-table>
      </el-card>

      <el-card shadow="never">
        <template #header>异常与回溯建议</template>
        <el-alert
          title="当前链路在“加工校验”环节触发低置信度复核，建议查看字段 amount、payee_account 的证据文本和复核记录。"
          type="warning"
          :closable="false"
        />
      </el-card>
    </div>
  </div>
</template>
