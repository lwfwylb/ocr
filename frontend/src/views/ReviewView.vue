<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ConfidenceTag from '../components/ConfidenceTag.vue'
import {
  approveReview,
  getReviewDetail,
  pageReviewTasks,
  rejectReview,
  saveReviewDraft,
  type ReviewDetail,
  type ReviewField,
  type ReviewSubmitPayload
} from '../api/review'
import { createTablePage, pageParams, resetPage } from '../composables/useTablePage'
import type { ResultSummary } from '../api/result'

interface EditableField extends ReviewField {
  finalValueText: string
}

const route = useRoute()
const loading = ref(false)
const detailLoading = ref(false)
const submitting = ref(false)
const activeField = ref('')
const activeTab = ref('fields')
const tasks = ref<ResultSummary[]>([])
const selectedTaskId = ref('')
const page = createTablePage(20)
const detail = ref<ReviewDetail | null>(null)
const fields = ref<EditableField[]>([])
const reviewForm = reactive({
  comment: '',
  reviewer: '当前用户'
})
const query = reactive({
  keyword: '',
  departmentId: '',
  documentType: '',
  sourceType: ''
})

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

const actionMap: Record<string, string> = {
  SAVE_DRAFT: '保存草稿',
  APPROVE: '通过复核',
  REJECT: '退回重提取'
}

const selectedTask = computed(() => tasks.value.find((item) => item.taskId === selectedTaskId.value) || detail.value?.summary)
const lowConfidenceCount = computed(() => fields.value.filter((field) => field.reviewRequired).length)
const activeFieldInfo = computed(() => fields.value.find((field) => field.fieldCode === activeField.value))
const parseText = computed(() => detail.value?.parseText || '暂无解析文本。执行任务后会展示 OCR/文档解析结果，便于复核人员核对字段来源。')

const loadTasks = async (preferredTaskId?: string) => {
  loading.value = true
  try {
    const result = await pageReviewTasks({ ...query, ...pageParams(page) })
    tasks.value = result.records
    page.total = result.total
    const routeTaskId = typeof route.params.reviewTaskId === 'string' ? route.params.reviewTaskId : ''
    const routeMatched = tasks.value.some((item) => item.taskId === routeTaskId)
    const nextTaskId = preferredTaskId || (routeMatched ? routeTaskId : '') || tasks.value[0]?.taskId || ''
    if (nextTaskId) {
      await selectTask(nextTaskId)
    } else {
      selectedTaskId.value = ''
      detail.value = null
      fields.value = []
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询待复核任务失败')
  } finally {
    loading.value = false
  }
}

const selectTask = async (taskId: string) => {
  selectedTaskId.value = taskId
  detailLoading.value = true
  try {
    detail.value = await getReviewDetail(taskId)
    fields.value = detail.value.fields.map((field) => ({
      ...field,
      finalValueText: formatValue(field.finalValue)
    }))
    activeField.value = fields.value[0]?.fieldCode || ''
    reviewForm.comment = ''
    activeTab.value = 'fields'
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询复核详情失败')
  } finally {
    detailLoading.value = false
  }
}

const buildPayload = (): ReviewSubmitPayload => ({
  fields: fields.value.map((field) => ({
    fieldCode: field.fieldCode,
    finalValue: field.finalValueText
  })),
  comment: reviewForm.comment,
  reviewer: reviewForm.reviewer
})

const saveDraft = async () => {
  if (!selectedTaskId.value) return
  submitting.value = true
  try {
    detail.value = await saveReviewDraft(selectedTaskId.value, buildPayload())
    ElMessage.success('复核草稿已保存')
    await selectTask(selectedTaskId.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存复核草稿失败')
  } finally {
    submitting.value = false
  }
}

const approve = async () => {
  if (!selectedTaskId.value) return
  await ElMessageBox.confirm('确认字段值无误并通过复核？通过后结果会进入可落库/推送状态。', '通过复核', { type: 'success' })
  submitting.value = true
  try {
    await approveReview(selectedTaskId.value, buildPayload())
    ElMessage.success('已通过复核')
    await loadTasks()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '通过复核失败')
  } finally {
    submitting.value = false
  }
}

const reject = async () => {
  if (!selectedTaskId.value) return
  await ElMessageBox.confirm('确认退回该任务重新提取？退回后任务会进入失败状态，可在失败任务中重试。', '退回重提取', { type: 'warning' })
  submitting.value = true
  try {
    await rejectReview(selectedTaskId.value, buildPayload())
    ElMessage.success('已退回重提取')
    await loadTasks()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退回重提取失败')
  } finally {
    submitting.value = false
  }
}

const searchTasks = () => {
  resetPage(page)
  loadTasks()
}

const handlePageSizeChange = () => {
  resetPage(page)
  loadTasks()
}

const handlePageChange = () => loadTasks()

const resetQuery = () => {
  query.keyword = ''
  query.departmentId = ''
  query.documentType = ''
  query.sourceType = ''
  resetPage(page)
  loadTasks()
}

const selectField = (row: EditableField) => {
  activeField.value = row.fieldCode
}

const formatValue = (value: unknown) => {
  if (value === null || value === undefined) return ''
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
const formatSource = (value?: string) => (value ? sourceTypeMap[value] || value : '-')
const formatDepartment = (value?: string) => (value ? departmentMap[value] || value : '-')
const formatConfidence = (value?: number) => Number(value || 0)
const formatAction = (value: string) => actionMap[value] || value

onMounted(() => loadTasks())
</script>

<template>
  <div class="page-stack">
    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="任务号/traceId/文件名" clearable />
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
        <el-form-item label="文档类型">
          <el-input v-model="query.documentType" placeholder="如：银行回单" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchTasks">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="review-workbench">
      <el-card shadow="never" class="review-list">
        <template #header>
          <div class="card-header">
            <span>待复核任务</span>
            <el-tag type="warning">{{ tasks.length }}</el-tag>
          </div>
        </template>
        <el-scrollbar height="calc(100vh - 230px)" v-loading="loading">
          <el-empty v-if="!tasks.length" description="暂无待复核任务" />
          <button
            v-for="item in tasks"
            :key="item.taskId"
            class="review-task-item"
            :class="{ active: item.taskId === selectedTaskId }"
            @click="selectTask(item.taskId)"
          >
            <strong>{{ item.fileName || item.taskId }}</strong>
            <span>{{ item.taskId }}</span>
            <span>{{ formatDepartment(item.departmentId) }} / {{ formatSource(item.sourceType) }}</span>
            <div>
              <ConfidenceTag :value="formatConfidence(item.overallConfidence)" />
              <em>{{ item.updatedAt || '-' }}</em>
            </div>
          </button>
        </el-scrollbar>
        <el-pagination
          v-model:current-page="page.pageNo"
          v-model:page-size="page.pageSize"
          class="table-pagination"
          small
          background
          layout="total, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="page.total"
          @size-change="handlePageSizeChange"
          @current-change="handlePageChange"
        />
      </el-card>

      <el-card shadow="never" class="review-detail" v-loading="detailLoading">
        <template #header>
          <div class="card-header">
            <div>
              <span>字段复核</span>
              <span v-if="selectedTask" class="muted ml-8">{{ selectedTask.taskId }}</span>
            </div>
            <div class="review-header-actions">
              <el-tag v-if="selectedTask" type="warning">待复核 {{ lowConfidenceCount }} 项</el-tag>
              <el-button :disabled="!selectedTaskId" :loading="submitting" @click="saveDraft">保存草稿</el-button>
              <el-button :disabled="!selectedTaskId" :loading="submitting" @click="reject">退回重提取</el-button>
              <el-button type="primary" :disabled="!selectedTaskId" :loading="submitting" @click="approve">通过复核</el-button>
            </div>
          </div>
        </template>

        <el-empty v-if="!selectedTaskId" description="请选择左侧待复核任务" />
        <template v-else>
          <el-descriptions :column="4" border class="mb-12">
            <el-descriptions-item label="文档类型">{{ selectedTask?.documentType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ formatDepartment(selectedTask?.departmentId) }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ formatSource(selectedTask?.sourceType) }}</el-descriptions-item>
            <el-descriptions-item label="目标表">{{ selectedTask?.targetTable || '-' }}</el-descriptions-item>
          </el-descriptions>

          <el-alert
            title="请重点确认低于 90% 置信度的字段；通过复核后，结果会从待复核列表移除。"
            type="warning"
            :closable="false"
            class="mb-12"
          />

          <el-tabs v-model="activeTab" type="border-card">
            <el-tab-pane label="字段确认" name="fields">
              <el-table :data="fields" stripe height="360" @row-click="selectField">
                <el-table-column prop="fieldName" label="字段" min-width="150" />
                <el-table-column prop="rawValue" label="提取值" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">{{ formatValue(row.rawValue) }}</template>
                </el-table-column>
                <el-table-column label="人工确认值" min-width="220">
                  <template #default="{ row }">
                    <el-input v-model="row.finalValueText" size="small" />
                  </template>
                </el-table-column>
                <el-table-column label="置信度" width="95">
                  <template #default="{ row }"><ConfidenceTag :value="row.confidence" /></template>
                </el-table-column>
                <el-table-column label="异常" width="130">
                  <template #default="{ row }">
                    <el-tag v-if="row.reviewRequired" type="warning">{{ row.issue || '需复核' }}</el-tag>
                    <el-tag v-else type="success">通过</el-tag>
                  </template>
                </el-table-column>
              </el-table>

              <div class="review-bottom">
                <div class="evidence-panel">
                  <strong>证据文本</strong>
                  <p>{{ activeFieldInfo?.evidence || '请选择字段查看证据文本。' }}</p>
                </div>
                <el-form label-width="72px" class="review-comment">
                  <el-form-item label="复核人">
                    <el-input v-model="reviewForm.reviewer" />
                  </el-form-item>
                  <el-form-item label="复核备注">
                    <el-input v-model="reviewForm.comment" type="textarea" :rows="3" placeholder="可填写修正原因、退回原因或特别说明" />
                  </el-form-item>
                </el-form>
              </div>
            </el-tab-pane>
            <el-tab-pane label="解析文本" name="parse">
              <pre class="parse-preview">{{ parseText }}</pre>
            </el-tab-pane>
            <el-tab-pane label="复核日志" name="logs">
              <el-timeline>
                <el-timeline-item v-for="log in detail?.logs || []" :key="log.id" :timestamp="log.createdAt">
                  <strong>{{ formatAction(log.action) }}</strong>
                  <span class="muted ml-8">{{ log.reviewer || '-' }}</span>
                  <p>{{ log.comment || '无备注' }}</p>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-if="!(detail?.logs || []).length" description="暂无复核日志" />
            </el-tab-pane>
          </el-tabs>
        </template>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.review-workbench {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 12px;
}

.review-list,
.review-detail {
  min-height: calc(100vh - 190px);
}

.review-task-item {
  width: 100%;
  display: grid;
  gap: 5px;
  padding: 10px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  margin-bottom: 8px;
}

.review-task-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.review-task-item span,
.review-task-item em {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-style: normal;
}

.review-task-item div {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.review-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.review-bottom {
  display: grid;
  grid-template-columns: minmax(260px, 0.9fr) minmax(320px, 1.1fr);
  gap: 12px;
  margin-top: 12px;
}

.evidence-panel {
  padding: 10px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}

.evidence-panel p {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.review-comment :deep(.el-form-item) {
  margin-bottom: 8px;
}

.parse-preview {
  min-height: 420px;
  max-height: 560px;
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

@media (max-width: 1100px) {
  .review-workbench {
    grid-template-columns: 1fr;
  }

  .review-bottom {
    grid-template-columns: 1fr;
  }
}
</style>
