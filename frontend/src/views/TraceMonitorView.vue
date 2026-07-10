<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getTraceDetail, listTraces, type TraceDetail, type TraceStage, type TraceSummary } from '../api/trace'

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

const selectedTraceId = ref('')
const loadingList = ref(false)
const loadingDetail = ref(false)
const records = ref<TraceSummary[]>([])
const detail = ref<TraceDetail | null>(null)
const query = reactive({
  keyword: '',
  sourceType: '',
  status: ''
})

const statusMap: Record<string, { label: string; type: TagType }> = {
  SUCCESS: { label: '成功', type: 'success' },
  WARNING: { label: '预警', type: 'warning' },
  WAITING: { label: '等待', type: 'warning' },
  PENDING: { label: '未开始', type: 'info' },
  FAILED: { label: '失败', type: 'danger' },
  WAIT_REVIEW: { label: '待复核', type: 'warning' },
  STORED: { label: '已落库', type: 'success' },
  COMPLETED: { label: '已完成', type: 'success' },
  QUEUED: { label: '排队中', type: 'info' },
  PARSING: { label: '解析中', type: 'primary' },
  EXTRACTING: { label: '提取中', type: 'primary' },
  CREATED_TASK: { label: '已建任务', type: 'success' },
  PENDING_CONFIRM: { label: '待确认', type: 'warning' },
  PUSHED: { label: '已推送', type: 'success' }
}

const sourceTypeMap: Record<string, string> = {
  MANUAL_UPLOAD: '手工上传',
  BUSINESS_API: '业务系统API',
  API: '业务系统API',
  EMAIL_DISPATCH: '邮件分拣',
  EMAIL: '邮件分拣',
  FILE_DISPATCH: '文件分拣'
}

const departmentMap: Record<string, string> = {
  OPS: '运营部',
  FINANCE: '财务部',
  PRODUCT: '产品部',
  运营部: '运营部',
  财务部: '财务部',
  产品部: '产品部'
}

const currentSummary = computed(() => detail.value?.summary || records.value.find((item) => item.traceId === selectedTraceId.value))
const activeStep = computed(() => {
  const stages = detail.value?.stages || []
  const failedIndex = stages.findIndex((stage) => stage.status === 'FAILED')
  if (failedIndex >= 0) return failedIndex
  const pendingIndex = stages.findIndex((stage) => ['PENDING', 'WAITING', 'WARNING'].includes(stage.status))
  return pendingIndex >= 0 ? pendingIndex : Math.max(stages.length - 1, 0)
})
const resultFields = computed(() => Object.entries(detail.value?.result?.result || {}).map(([field, value]) => ({ field, value })))
const storageFields = computed(() => Object.entries(detail.value?.storageRecord?.storageData || {}).map(([field, value]) => ({ field, value })))

const loadList = async (preferredTraceId?: string) => {
  loadingList.value = true
  try {
    records.value = await listTraces(query)
    const nextTraceId = preferredTraceId || selectedTraceId.value || records.value[0]?.traceId || ''
    if (nextTraceId) {
      await selectTrace(nextTraceId)
    } else {
      selectedTraceId.value = ''
      detail.value = null
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询链路失败')
  } finally {
    loadingList.value = false
  }
}

const selectTrace = async (traceId: string) => {
  selectedTraceId.value = traceId
  loadingDetail.value = true
  try {
    detail.value = await getTraceDetail(traceId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询链路详情失败')
  } finally {
    loadingDetail.value = false
  }
}

const resetQuery = () => {
  query.keyword = ''
  query.sourceType = ''
  query.status = ''
  loadList()
}

const getStatus = (status?: string) => statusMap[status || ''] || { label: status || '-', type: 'info' as TagType }
const formatSource = (value?: string) => (value ? sourceTypeMap[value] || value : '-')
const formatDepartment = (value?: string) => (value ? departmentMap[value] || value : '-')
const formatValue = (value: unknown) => {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
const stepStatus = (stage: TraceStage) => {
  if (stage.status === 'FAILED') return 'error'
  if (stage.status === 'PENDING') return 'wait'
  if (['WAITING', 'WARNING'].includes(stage.status)) return 'process'
  return 'success'
}

onMounted(() => loadList())
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
          <el-input v-model="query.keyword" clearable placeholder="traceId/任务/业务号/文件名" @keyup.enter="loadList()" />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="query.sourceType" filterable clearable placeholder="全部">
            <el-option label="手工上传" value="MANUAL_UPLOAD" />
            <el-option label="业务系统API" value="BUSINESS_API" />
            <el-option label="邮件分拣" value="EMAIL_DISPATCH" />
            <el-option label="文件分拣" value="FILE_DISPATCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" filterable clearable placeholder="全部">
            <el-option label="待确认" value="PENDING_CONFIRM" />
            <el-option label="排队中" value="QUEUED" />
            <el-option label="待复核" value="WAIT_REVIEW" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已落库" value="STORED" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-button type="primary" @click="loadList()">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form>

      <div v-loading="loadingList" class="trace-records">
        <el-empty v-if="!records.length" description="暂无链路记录" />
        <button
          v-for="record in records"
          :key="record.traceId"
          class="trace-record"
          :class="{ active: selectedTraceId === record.traceId }"
          @click="selectTrace(record.traceId)"
        >
          <strong>{{ record.traceId }}</strong>
          <span>{{ record.fileName || record.documentId || '-' }}</span>
          <em>{{ record.currentStage || '-' }} / {{ record.updatedAt || '-' }}</em>
        </button>
      </div>
    </el-card>

    <div class="page-stack" v-loading="loadingDetail">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>链路概览</span>
            <el-tag :type="getStatus(currentSummary?.status).type">
              {{ getStatus(currentSummary?.status).label }}
            </el-tag>
          </div>
        </template>
        <el-descriptions v-if="currentSummary" :column="4" border>
          <el-descriptions-item label="TraceId">{{ currentSummary.traceId }}</el-descriptions-item>
          <el-descriptions-item label="任务编号">{{ currentSummary.taskId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="文档编号">{{ currentSummary.documentId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务号">{{ currentSummary.businessNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ formatSource(currentSummary.sourceType) }}</el-descriptions-item>
          <el-descriptions-item label="文档类型">{{ currentSummary.documentType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="所属部门">{{ formatDepartment(currentSummary.departmentId) }}</el-descriptions-item>
          <el-descriptions-item label="当前阶段">{{ currentSummary.currentStage || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else description="请选择一条链路" />
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>全链路阶段</span>
            <span class="muted">接入、匹配、队列、解析、提取、校验、复核、落库、推送</span>
          </div>
        </template>
        <el-steps :active="activeStep" align-center finish-status="success" class="trace-steps">
          <el-step
            v-for="stage in detail?.stages || []"
            :key="stage.stageCode + stage.stageName"
            :title="stage.stageName"
            :status="stepStatus(stage)"
          />
        </el-steps>
        <el-table :data="detail?.stages || []" stripe class="mt-12">
          <el-table-column prop="stageName" label="环节" width="120" fixed />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="getStatus(row.status).type">{{ getStatus(row.status).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="startedAt" label="开始时间" min-width="150" />
          <el-table-column prop="endedAt" label="结束时间" min-width="150" />
          <el-table-column label="耗时" width="90">
            <template #default="{ row }">{{ row.durationMs ? `${row.durationMs}ms` : '-' }}</template>
          </el-table-column>
          <el-table-column prop="inputSummary" label="输入摘要" min-width="220" show-overflow-tooltip />
          <el-table-column prop="outputSummary" label="输出摘要" min-width="240" show-overflow-tooltip />
          <el-table-column prop="errorMessage" label="异常信息" min-width="220" show-overflow-tooltip />
        </el-table>
      </el-card>

      <div class="trace-detail-grid">
        <el-card shadow="never">
          <template #header>提取结果摘要</template>
          <el-table :data="resultFields" height="260">
            <el-table-column prop="field" label="字段" min-width="150" />
            <el-table-column label="值" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ formatValue(row.value) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!resultFields.length" description="暂无提取结果" />
        </el-card>

        <el-card shadow="never">
          <template #header>落库摘要</template>
          <el-descriptions v-if="detail?.storageRecord" :column="1" border>
            <el-descriptions-item label="目标表">{{ detail.storageRecord.targetTable }}</el-descriptions-item>
            <el-descriptions-item label="落库状态">
              <el-tag :type="getStatus(detail.storageRecord.storageStatus).type">
                {{ getStatus(detail.storageRecord.storageStatus).label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="落库人">{{ detail.storageRecord.storedBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="落库时间">{{ detail.storageRecord.storedAt || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="暂无落库记录" />
        </el-card>
      </div>

      <el-card shadow="never">
        <template #header>复核日志</template>
        <el-timeline v-if="(detail?.reviewLogs || []).length">
          <el-timeline-item v-for="log in detail?.reviewLogs || []" :key="log.id" :timestamp="log.createdAt">
            <strong>{{ log.action }}</strong>
            <span class="muted ml-8">{{ log.reviewer || '-' }}</span>
            <p>{{ log.comment || '无备注' }}</p>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无复核日志" />
      </el-card>

      <el-card shadow="never">
        <template #header>异常与回溯建议</template>
        <el-alert
          v-for="suggestion in detail?.suggestions || []"
          :key="suggestion"
          :title="suggestion"
          type="warning"
          :closable="false"
          class="mb-8"
        />
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.trace-records {
  margin-top: 14px;
}

.trace-record {
  width: 100%;
  display: grid;
  gap: 5px;
  padding: 10px;
  margin-bottom: 8px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
}

.trace-record.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.trace-record span,
.trace-record em {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-style: normal;
}

.trace-detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 12px;
}

@media (max-width: 1100px) {
  .trace-detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
