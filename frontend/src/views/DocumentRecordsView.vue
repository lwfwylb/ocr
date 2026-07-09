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
  { label: '运营部', value: 'OPS' },
  { label: '财务部', value: 'FINANCE' },
  { label: '产品部', value: 'PRODUCT' }
]

const matchStatusMap = {
  MATCHED: { label: '已匹配', type: 'success' },
  UNMATCHED: { label: '未匹配', type: 'warning' },
  MULTIPLE: { label: '多配置命中', type: 'danger' }
} as const

const accessStatusMap = {
  PENDING_CONFIRM: { label: '待确认', type: 'warning' },
  CREATED_TASK: { label: '已建任务', type: 'success' },
  REJECTED: { label: '已拒绝', type: 'danger' }
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
    ElMessage.error(error instanceof Error ? error.message : '接入记录加载失败')
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
    ElMessage.error(error instanceof Error ? error.message : '接入详情加载失败')
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
    ElMessage.info('暂无待重新匹配的文档')
    return
  }
  try {
    for (const item of targets) {
      await rematchDocument(item.id)
    }
    ElMessage.success('重新匹配完成')
    await loadRecords()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重新匹配失败')
  }
}

onMounted(loadRecords)
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>接入总数</span><strong>{{ totalCount }}</strong><em>条记录</em></el-card>
      <el-card shadow="never" class="metric-card"><span>已匹配</span><strong>{{ matchedCount }}</strong><em>可建任务</em></el-card>
      <el-card shadow="never" class="metric-card"><span>待确认</span><strong>{{ pendingCount }}</strong><em>人工处理</em></el-card>
      <el-card shadow="never" class="metric-card"><span>API推送</span><strong>{{ apiCount }}</strong><em>业务系统</em></el-card>
      <el-card shadow="never" class="metric-card"><span>分拣推送</span><strong>{{ dispatchCount }}</strong><em>文件/邮件</em></el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键字"><el-input v-model="query.keyword" clearable placeholder="traceId/文档/任务/业务号/文件名" /></el-form-item>
        <el-form-item label="来源">
          <el-select v-model="query.sourceType" filterable clearable placeholder="全部">
            <el-option label="手工上传" value="MANUAL_UPLOAD" />
            <el-option label="业务系统API" value="BUSINESS_API" />
            <el-option label="邮件分拣" value="EMAIL_DISPATCH" />
            <el-option label="文件分拣" value="FILE_DISPATCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配状态">
          <el-select v-model="query.matchStatus" filterable clearable placeholder="全部">
            <el-option label="已匹配" value="MATCHED" />
            <el-option label="未匹配" value="UNMATCHED" />
            <el-option label="多配置命中" value="MULTIPLE" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属部门">
          <el-select v-model="query.departmentId" filterable clearable placeholder="全部">
            <el-option v-for="item in departmentOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadRecords">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>接入记录</span>
          <div>
            <el-button @click="retryMatch()">批量重新匹配</el-button>
            <el-button type="primary" @click="router.push('/documents/upload')">手工上传</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="traceId" label="TraceId" min-width="190" fixed />
        <el-table-column prop="fileName" label="文件名" min-width="170" />
        <el-table-column prop="sourceSystem" label="来源系统" min-width="130" />
        <el-table-column prop="businessNo" label="业务号" min-width="150" />
        <el-table-column prop="departmentId" label="部门" width="90" />
        <el-table-column prop="documentType" label="文档类型" width="110" />
        <el-table-column label="匹配" width="110">
          <template #default="{ row }">
            <el-tag :type="matchStatusMap[row.matchStatus as keyof typeof matchStatusMap].type">
              {{ matchStatusMap[row.matchStatus as keyof typeof matchStatusMap].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="接入状态" width="120">
          <template #default="{ row }">
            <el-tag :type="accessStatusMap[row.accessStatus as keyof typeof accessStatusMap].type">
              {{ accessStatusMap[row.accessStatus as keyof typeof accessStatusMap].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="taskId" label="任务编号" min-width="170" />
        <el-table-column label="大小" width="90">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="接入时间" min-width="160" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link @click="router.push('/monitor/traces')">链路</el-button>
            <el-button v-if="row.accessStatus === 'PENDING_CONFIRM'" link type="warning" @click="router.push('/documents/unmatched')">确认</el-button>
            <el-button v-if="row.accessStatus === 'PENDING_CONFIRM'" link @click="retryMatch(row)">重匹配</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="接入详情" size="620px">
      <template v-if="selectedRecord">
        <el-descriptions v-loading="detailLoading" :column="1" border>
          <el-descriptions-item label="TraceId">{{ selectedRecord.traceId }}</el-descriptions-item>
          <el-descriptions-item label="文档编号">{{ selectedRecord.documentId }}</el-descriptions-item>
          <el-descriptions-item label="任务编号">{{ selectedRecord.taskId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源系统">{{ selectedRecord.sourceSystem }}</el-descriptions-item>
          <el-descriptions-item label="业务号">{{ selectedRecord.businessNo }}</el-descriptions-item>
          <el-descriptions-item label="文件名">{{ selectedRecord.fileName }}</el-descriptions-item>
          <el-descriptions-item label="匹配状态">{{ matchStatusMap[selectedRecord.matchStatus].label }}</el-descriptions-item>
          <el-descriptions-item label="匹配配置">{{ selectedRecord.matchedConfigName || '-' }} {{ selectedRecord.matchedConfigVersion ? `V${selectedRecord.matchedConfigVersion}` : '' }}</el-descriptions-item>
          <el-descriptions-item label="匹配说明">{{ selectedRecord.matchMessage }}</el-descriptions-item>
          <el-descriptions-item label="存储地址">{{ selectedRecord.storagePath }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>
