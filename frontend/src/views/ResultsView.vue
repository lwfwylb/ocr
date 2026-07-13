<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ConfidenceTag from '../components/ConfidenceTag.vue'
import { getResultDetail, listResults, type ResultDetail, type ResultSummary } from '../api/result'
import { executeStorage } from '../api/storage'
import { pushResultToDownstream } from '../api/push'

type ResultStatus = 'STORED' | 'WAIT_REVIEW' | 'PUSHED' | 'FAILED'

interface FieldRow {
  fieldCode?: string
  fieldName: string
  extractField: string
  targetColumn: string
  rawValue: unknown
  finalValue: unknown
  confidence: number
  reviewRequired?: boolean
  sourceType?: string
  issue?: string
  sourcePage: string
}

const drawerVisible = ref(false)
const loading = ref(false)
const detailLoading = ref(false)
const activeTab = ref('fields')
const selectedResult = ref<ResultSummary | null>(null)
const selectedDetail = ref<ResultDetail | null>(null)
const query = reactive({
  keyword: '',
  documentType: '',
  departmentId: '',
  sourceType: '',
  resultStatus: ''
})

const results = ref<ResultSummary[]>([])

const statusMap: Record<ResultStatus, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  STORED: { label: '已落库', type: 'success' },
  WAIT_REVIEW: { label: '待复核', type: 'warning' },
  PUSHED: { label: '已推送', type: 'success' },
  FAILED: { label: '失败', type: 'danger' }
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

const metrics = computed(() => {
  const total = results.value.length
  const stored = results.value.filter((item) => ['STORED', 'PUSHED'].includes(item.resultStatus)).length
  const waitReview = results.value.filter((item) => item.resultStatus === 'WAIT_REVIEW').length
  const pushed = results.value.filter((item) => item.resultStatus === 'PUSHED').length
  const confidenceItems = results.value.filter((item) => item.overallConfidence !== undefined && item.overallConfidence !== null)
  const avgConfidence = confidenceItems.length
    ? confidenceItems.reduce((sum, item) => sum + Number(item.overallConfidence || 0), 0) / confidenceItems.length
    : 0
  return { total, stored, waitReview, pushed, avgConfidence }
})

const detailSummary = computed(() => selectedDetail.value?.summary || selectedResult.value)

const fieldRows = computed<FieldRow[]>(() => {
  const detail = selectedDetail.value
  if (detail?.fields?.length) {
    return detail.fields.map((field) => ({
      fieldCode: field.fieldCode,
      fieldName: field.fieldName,
      extractField: field.extractField,
      targetColumn: field.targetColumn,
      rawValue: field.rawValue,
      finalValue: field.finalValue,
      confidence: Number(field.confidence || 0),
      reviewRequired: field.reviewRequired,
      sourceType: field.sourceType,
      issue: field.issue,
      sourcePage: field.sourcePage || '-'
    }))
  }
  const result = detail?.result || {}
  const confidence = detail?.confidence || {}
  const fallbackConfidence = Number(detail?.summary.overallConfidence || 0)
  return Object.entries(result)
    .filter(([field]) => !field.startsWith('_'))
    .map(([field, value]) => ({
    fieldName: field,
    extractField: field,
    targetColumn: field,
    rawValue: value,
    finalValue: value,
    confidence: Number(confidence[field] ?? fallbackConfidence),
    sourcePage: detail?.pageCount ? `1/${detail.pageCount}` : '1'
  }))
})

const storageRows = computed(() => {
  if (selectedDetail.value?.storagePreview?.length) return selectedDetail.value.storagePreview
  const targetTable = detailSummary.value?.targetTable || '-'
  return fieldRows.value.map((row) => ({
    targetTable,
    targetColumn: row.targetColumn,
    columnName: row.fieldName,
    value: row.finalValue,
    required: false,
    uniqueKey: false,
    ready: true,
    transform: '按当前配置加工后写入'
  }))
})

const loadResults = async () => {
  loading.value = true
  try {
    results.value = await listResults(query)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询提取结果失败')
  } finally {
    loading.value = false
  }
}

const openDetail = async (row: ResultSummary, tab = 'fields') => {
  selectedResult.value = row
  selectedDetail.value = null
  activeTab.value = tab
  drawerVisible.value = true
  detailLoading.value = true
  try {
    selectedDetail.value = await getResultDetail(row.taskId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询结果详情失败')
  } finally {
    detailLoading.value = false
  }
}

const exportResult = (type: string) => {
  ElMessage.success(`已生成 ${type} 导出任务，后续接入导出记录接口`)
}

const pushResult = async (row: ResultSummary) => {
  await ElMessageBox.confirm('确认将该结果推送到下游业务系统？仅成功落库的数据允许推送。', '手工推送', { type: 'warning' })
  try {
    const record = await pushResultToDownstream(row.taskId, {
      targetSystem: '模拟业务系统',
      serviceCode: 'mock_result_receive',
      serviceName: '模拟结果接收服务',
      pushMethod: 'HTTP',
      triggerType: 'MANUAL',
      operator: '当前用户'
    })
    ElMessage.success(`推送完成：${record.pushId}`)
    await loadResults()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '推送失败')
  }
}

const executeStorageForResult = async (row: ResultSummary) => {
  await ElMessageBox.confirm('确认将该提取结果写入落库台账？待复核或失败结果不允许落库。', '执行落库', { type: 'warning' })
  try {
    await executeStorage(row.taskId, { storedBy: '当前用户', duplicateStrategy: 'UPSERT_BY_TASK_ID' })
    ElMessage.success('落库成功，可在落库数据查询中查看')
    await loadResults()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '执行落库失败')
  }
}

const resetQuery = () => {
  query.keyword = ''
  query.documentType = ''
  query.departmentId = ''
  query.sourceType = ''
  query.resultStatus = ''
  loadResults()
}

const getStatus = (status?: string) => statusMap[(status || 'STORED') as ResultStatus] || { label: status || '-', type: 'info' }
const formatSource = (value?: string) => (value ? sourceTypeMap[value] || value : '-')
const formatDepartment = (value?: string) => (value ? departmentMap[value] || value : '-')
const formatConfidence = (value?: number) => Number(value || 0)
const formatValue = (value: unknown) => {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

onMounted(loadResults)
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card">
        <span>结果总数</span>
        <strong>{{ metrics.total }}</strong>
        <em>后端结果记录</em>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>已落库</span>
        <strong>{{ metrics.stored }}</strong>
        <em>含已推送</em>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>待复核</span>
        <strong>{{ metrics.waitReview }}</strong>
        <em>低置信度</em>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>已推送</span>
        <strong>{{ metrics.pushed }}</strong>
        <em>下游已接收</em>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>平均置信度</span>
        <strong>{{ Math.round(metrics.avgConfidence * 100) }}%</strong>
        <em>当前查询范围</em>
      </el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="任务号/traceId/文件名" clearable @keyup.enter="loadResults" />
        </el-form-item>
        <el-form-item label="文档类型">
          <el-input v-model="query.documentType" placeholder="如：划款指令" clearable />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="query.departmentId" clearable filterable placeholder="全部">
            <el-option label="运营部" value="OPS" />
            <el-option label="财务部" value="FINANCE" />
            <el-option label="产品部" value="PRODUCT" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="query.sourceType" clearable filterable placeholder="全部">
            <el-option label="手工上传" value="MANUAL_UPLOAD" />
            <el-option label="业务系统API" value="BUSINESS_API" />
            <el-option label="邮件分拣" value="EMAIL_DISPATCH" />
            <el-option label="文件分拣" value="FILE_DISPATCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果状态">
          <el-select v-model="query.resultStatus" clearable filterable placeholder="全部">
            <el-option label="已落库" value="STORED" />
            <el-option label="待复核" value="WAIT_REVIEW" />
            <el-option label="已推送" value="PUSHED" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadResults">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>提取结果</span>
          <div>
            <el-button @click="exportResult('Excel')">导出 Excel</el-button>
            <el-button @click="exportResult('JSON')">导出 JSON</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="results" stripe>
        <el-table-column prop="taskId" label="任务编号" min-width="170" fixed />
        <el-table-column prop="traceId" label="TraceId" min-width="170" />
        <el-table-column prop="fileName" label="文件名" min-width="160" />
        <el-table-column prop="documentType" label="文档类型" width="120" />
        <el-table-column label="部门" width="90">
          <template #default="{ row }">{{ formatDepartment(row.departmentId) }}</template>
        </el-table-column>
        <el-table-column label="来源" width="120">
          <template #default="{ row }">{{ formatSource(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="getStatus(row.resultStatus).type">{{ getStatus(row.resultStatus).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetTable" label="目标表" min-width="190" />
        <el-table-column prop="mappingProfile" label="映射方案" min-width="190" />
        <el-table-column label="字段数" width="80" prop="fieldCount" />
        <el-table-column label="置信度" width="90">
          <template #default="{ row }"><ConfidenceTag :value="formatConfidence(row.overallConfidence)" /></template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="160" />
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row, 'fields')">字段</el-button>
            <el-button link type="primary" @click="openDetail(row, 'parse')">解析</el-button>
            <el-button link type="primary" @click="openDetail(row, 'storage')">落库</el-button>
            <el-button link type="warning" @click="executeStorageForResult(row)">执行落库</el-button>
            <el-button link type="success" @click="pushResult(row)">推送</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="结果详情" size="760px">
      <template v-if="detailSummary">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务编号">{{ detailSummary.taskId }}</el-descriptions-item>
          <el-descriptions-item label="TraceId">{{ detailSummary.traceId }}</el-descriptions-item>
          <el-descriptions-item label="文档编号">{{ detailSummary.documentId }}</el-descriptions-item>
          <el-descriptions-item label="文件名">{{ detailSummary.fileName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="目标表">{{ detailSummary.targetTable || '-' }}</el-descriptions-item>
          <el-descriptions-item label="映射方案">{{ detailSummary.mappingProfile || '-' }}</el-descriptions-item>
          <el-descriptions-item label="复核状态">{{ detailSummary.reviewStatus || '-' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detailSummary.updatedAt || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="activeTab" v-loading="detailLoading" class="mt-12" type="border-card">
          <el-tab-pane label="字段结果" name="fields">
            <el-table :data="fieldRows">
              <el-table-column prop="fieldName" label="字段名称" width="140" />
              <el-table-column prop="extractField" label="提取字段" min-width="130" />
              <el-table-column prop="targetColumn" label="目标字段" min-width="150" />
              <el-table-column label="原始值" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">{{ formatValue(row.rawValue) }}</template>
              </el-table-column>
              <el-table-column label="最终值" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">{{ formatValue(row.finalValue) }}</template>
              </el-table-column>
              <el-table-column label="置信度" width="90">
                <template #default="{ row }"><ConfidenceTag :value="row.confidence" /></template>
              </el-table-column>
              <el-table-column prop="sourceType" label="来源" width="100" />
              <el-table-column label="复核" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.reviewRequired ? 'warning' : 'success'">{{ row.reviewRequired ? '需要' : '通过' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="issue" label="问题说明" min-width="150" show-overflow-tooltip />
              <el-table-column prop="sourcePage" label="页码" width="80" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="解析文本" name="parse">
            <div class="parse-meta">
              <span>解析引擎：{{ selectedDetail?.engineCode || '-' }}</span>
              <span>页数：{{ selectedDetail?.pageCount || '-' }}</span>
            </div>
            <pre class="parse-preview">{{ selectedDetail?.parseText || '暂无解析文本' }}</pre>
          </el-tab-pane>
          <el-tab-pane label="落库预览" name="storage">
            <el-alert title="落库预览由后端按当前配置的目标表字段定义、字段映射、必填和唯一约束生成；第一版仍写入平台落库台账，不直接写真实业务结果表。" type="info" :closable="false" class="mb-12" />
            <el-table :data="storageRows">
              <el-table-column prop="targetTable" label="目标表" min-width="160" />
              <el-table-column prop="targetColumn" label="目标字段" min-width="150" />
              <el-table-column prop="columnName" label="字段名称" min-width="130" />
              <el-table-column prop="typeDescription" label="字段类型" width="110" />
              <el-table-column label="入库值" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ formatValue(row.value) }}</template>
              </el-table-column>
              <el-table-column label="约束" width="120">
                <template #default="{ row }">
                  <el-tag v-if="row.required" size="small" type="danger">必填</el-tag>
                  <el-tag v-if="row.uniqueKey" size="small" class="ml-4">唯一</el-tag>
                  <span v-if="!row.required && !row.uniqueKey" class="muted">-</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.ready ? 'success' : 'warning'">{{ row.ready ? '可入库' : '需处理' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="issue" label="问题说明" min-width="140" show-overflow-tooltip />
              <el-table-column prop="transform" label="加工逻辑" min-width="180" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.parse-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.parse-preview {
  min-height: 220px;
  max-height: 460px;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-primary);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
