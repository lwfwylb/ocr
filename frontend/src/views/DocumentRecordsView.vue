<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getDocumentAccessRecord,
  listDocumentAccessRecords,
  rematchDocument,
  type DocumentAccessRecord
} from '../api/document'

const router = useRouter()
const drawerVisible = ref(false)
const loading = ref(false)
const detailLoading = ref(false)
const selectedRecord = ref<DocumentAccessRecord | null>(null)
const query = reactive({
  keyword: '',
  sourceType: '',
  matchStatus: '',
  departmentId: ''
})

const records = ref<DocumentAccessRecord[]>([])
const departmentOptions = [
  { label: 'Operations', value: 'OPS' },
  { label: 'Finance', value: 'FINANCE' },
  { label: 'Product', value: 'PRODUCT' }
]

const matchStatusMap = {
  MATCHED: { label: 'Matched', type: 'success' },
  UNMATCHED: { label: 'Unmatched', type: 'warning' },
  MULTIPLE: { label: 'Multiple', type: 'danger' }
} as const

const accessStatusMap = {
  PENDING_CONFIRM: { label: 'Pending', type: 'warning' },
  CREATED_TASK: { label: 'Task Created', type: 'success' },
  REJECTED: { label: 'Rejected', type: 'danger' }
} as const

const totalCount = computed(() => records.value.length)
const matchedCount = computed(() => records.value.filter((item) => item.matchStatus === 'MATCHED').length)
const pendingCount = computed(() => records.value.filter((item) => item.accessStatus === 'PENDING_CONFIRM').length)
const apiCount = computed(() => records.value.filter((item) => item.sourceType === 'BUSINESS_API').length)
const dispatchCount = computed(() => records.value.filter((item) => ['FILE_DISPATCH', 'EMAIL_DISPATCH'].includes(item.sourceType)).length)

const formatSize = (size?: number) => {
  if (!size) return '-'
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

const loadRecords = async () => {
  loading.value = true
  try {
    records.value = await listDocumentAccessRecords(query)
  } catch (error) {
    records.value = []
    ElMessage.error(error instanceof Error ? error.message : 'Load access records failed')
  } finally {
    loading.value = false
  }
}

const openDetail = async (record: DocumentAccessRecord) => {
  drawerVisible.value = true
  detailLoading.value = true
  selectedRecord.value = record
  try {
    selectedRecord.value = await getDocumentAccessRecord(record.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Load access detail failed')
  } finally {
    detailLoading.value = false
  }
}

const resetQuery = () => {
  query.keyword = ''
  query.sourceType = ''
  query.matchStatus = ''
  query.departmentId = ''
  loadRecords()
}

const retryMatch = async (record?: DocumentAccessRecord) => {
  const targets = record ? [record] : records.value.filter((item) => item.accessStatus === 'PENDING_CONFIRM')
  if (!targets.length) {
    ElMessage.info('No pending document to rematch')
    return
  }
  try {
    for (const item of targets) {
      await rematchDocument(item.id)
    }
    ElMessage.success('Rematch completed')
    await loadRecords()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Rematch failed')
  }
}

onMounted(loadRecords)
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>Access</span><strong>{{ totalCount }}</strong><em>records</em></el-card>
      <el-card shadow="never" class="metric-card"><span>Matched</span><strong>{{ matchedCount }}</strong><em>task ready</em></el-card>
      <el-card shadow="never" class="metric-card"><span>Pending</span><strong>{{ pendingCount }}</strong><em>manual confirm</em></el-card>
      <el-card shadow="never" class="metric-card"><span>API</span><strong>{{ apiCount }}</strong><em>business push</em></el-card>
      <el-card shadow="never" class="metric-card"><span>Dispatch</span><strong>{{ dispatchCount }}</strong><em>file/email</em></el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="Keyword"><el-input v-model="query.keyword" clearable placeholder="trace/document/task/business/file" /></el-form-item>
        <el-form-item label="Source">
          <el-select v-model="query.sourceType" filterable clearable placeholder="All">
            <el-option label="Manual Upload" value="MANUAL_UPLOAD" />
            <el-option label="Business API" value="BUSINESS_API" />
            <el-option label="Email Dispatch" value="EMAIL_DISPATCH" />
            <el-option label="File Dispatch" value="FILE_DISPATCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="Match">
          <el-select v-model="query.matchStatus" filterable clearable placeholder="All">
            <el-option label="Matched" value="MATCHED" />
            <el-option label="Unmatched" value="UNMATCHED" />
            <el-option label="Multiple" value="MULTIPLE" />
          </el-select>
        </el-form-item>
        <el-form-item label="Department">
          <el-select v-model="query.departmentId" filterable clearable placeholder="All">
            <el-option v-for="item in departmentOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadRecords">Search</el-button>
          <el-button @click="resetQuery">Reset</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Access Records</span>
          <div>
            <el-button @click="retryMatch()">Batch Rematch</el-button>
            <el-button type="primary" @click="router.push('/documents/upload')">Manual Upload</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="traceId" label="TraceId" min-width="190" fixed />
        <el-table-column prop="fileName" label="File Name" min-width="170" />
        <el-table-column prop="sourceSystem" label="Source System" min-width="130" />
        <el-table-column prop="businessNo" label="Business No" min-width="150" />
        <el-table-column prop="departmentId" label="Dept" width="90" />
        <el-table-column prop="documentType" label="Doc Type" width="110" />
        <el-table-column label="Match" width="100">
          <template #default="{ row }">
            <el-tag :type="matchStatusMap[row.matchStatus as keyof typeof matchStatusMap].type">
              {{ matchStatusMap[row.matchStatus as keyof typeof matchStatusMap].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Access" width="120">
          <template #default="{ row }">
            <el-tag :type="accessStatusMap[row.accessStatus as keyof typeof accessStatusMap].type">
              {{ accessStatusMap[row.accessStatus as keyof typeof accessStatusMap].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="taskId" label="Task Id" min-width="170" />
        <el-table-column label="Size" width="90">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="Access Time" min-width="160" />
        <el-table-column label="Actions" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">Detail</el-button>
            <el-button link @click="router.push('/monitor/traces')">Trace</el-button>
            <el-button v-if="row.accessStatus === 'PENDING_CONFIRM'" link type="warning" @click="router.push('/documents/unmatched')">Confirm</el-button>
            <el-button v-if="row.accessStatus === 'PENDING_CONFIRM'" link @click="retryMatch(row)">Rematch</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="Access Detail" size="620px">
      <template v-if="selectedRecord">
        <el-descriptions v-loading="detailLoading" :column="1" border>
          <el-descriptions-item label="TraceId">{{ selectedRecord.traceId }}</el-descriptions-item>
          <el-descriptions-item label="Document Id">{{ selectedRecord.documentId }}</el-descriptions-item>
          <el-descriptions-item label="Task Id">{{ selectedRecord.taskId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Source System">{{ selectedRecord.sourceSystem }}</el-descriptions-item>
          <el-descriptions-item label="Business No">{{ selectedRecord.businessNo }}</el-descriptions-item>
          <el-descriptions-item label="File Name">{{ selectedRecord.fileName }}</el-descriptions-item>
          <el-descriptions-item label="Match">{{ matchStatusMap[selectedRecord.matchStatus].label }}</el-descriptions-item>
          <el-descriptions-item label="Matched Config">{{ selectedRecord.matchedConfigName || '-' }} {{ selectedRecord.matchedConfigVersion ? `V${selectedRecord.matchedConfigVersion}` : '' }}</el-descriptions-item>
          <el-descriptions-item label="Match Message">{{ selectedRecord.matchMessage }}</el-descriptions-item>
          <el-descriptions-item label="Storage Path">{{ selectedRecord.storagePath }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>
