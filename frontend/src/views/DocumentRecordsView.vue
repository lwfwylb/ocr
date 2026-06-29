<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

interface AccessRecord {
  traceId: string
  documentId: string
  taskId: string
  fileName: string
  sourceType: string
  sourceSystem: string
  businessNo: string
  department: string
  documentType: string
  matchStatus: 'MATCHED' | 'UNMATCHED' | 'MULTIPLE'
  accessStatus: 'ACCEPTED' | 'REJECTED' | 'CREATED_TASK'
  fileSize: string
  createdAt: string
}

const router = useRouter()
const drawerVisible = ref(false)
const selectedRecord = ref<AccessRecord | null>(null)
const query = reactive({
  keyword: '',
  sourceTypes: [] as string[],
  matchStatuses: [] as string[],
  departments: [] as string[]
})

const records = ref<AccessRecord[]>([
  {
    traceId: 'TRACE-20260628-0001',
    documentId: 'DOC-20260628-0001',
    taskId: 'TASK-20260628-0001',
    fileName: '划款指令_001.pdf',
    sourceType: 'MANUAL_UPLOAD',
    sourceSystem: '业务手工上传',
    businessNo: 'BIZ-20260628-001',
    department: '运营部',
    documentType: '划款指令',
    matchStatus: 'MATCHED',
    accessStatus: 'CREATED_TASK',
    fileSize: '1.8 MB',
    createdAt: '2026-06-28 09:30:00'
  },
  {
    traceId: 'TRACE-20260628-0002',
    documentId: 'DOC-20260628-0002',
    taskId: 'TASK-20260628-0002',
    fileName: '银行回单_002.png',
    sourceType: 'API',
    sourceSystem: '资金业务系统',
    businessNo: 'BANK-20260628-7788',
    department: '财务部',
    documentType: '银行回单',
    matchStatus: 'MATCHED',
    accessStatus: 'CREATED_TASK',
    fileSize: '860 KB',
    createdAt: '2026-06-28 10:15:00'
  },
  {
    traceId: 'TRACE-20260628-0004',
    documentId: 'DOC-20260628-0004',
    taskId: '-',
    fileName: '未知附件.zip',
    sourceType: 'FILE_DISPATCH',
    sourceSystem: '文件分拣系统',
    businessNo: 'DISPATCH-UNKNOWN-004',
    department: '产品部',
    documentType: '未确认',
    matchStatus: 'UNMATCHED',
    accessStatus: 'ACCEPTED',
    fileSize: '6.2 MB',
    createdAt: '2026-06-28 11:20:00'
  }
])

const filteredRecords = computed(() => {
  return records.value.filter((item) => {
    const keywordMatched =
      !query.keyword ||
      item.traceId.includes(query.keyword) ||
      item.documentId.includes(query.keyword) ||
      item.taskId.includes(query.keyword) ||
      item.fileName.includes(query.keyword) ||
      item.businessNo.includes(query.keyword)
    const sourceMatched = query.sourceTypes.length === 0 || query.sourceTypes.includes(item.sourceType)
    const matchMatched = query.matchStatuses.length === 0 || query.matchStatuses.includes(item.matchStatus)
    const deptMatched = query.departments.length === 0 || query.departments.includes(item.department)
    return keywordMatched && sourceMatched && matchMatched && deptMatched
  })
})

const matchStatusMap = {
  MATCHED: { label: '已匹配', type: 'success' },
  UNMATCHED: { label: '未匹配', type: 'warning' },
  MULTIPLE: { label: '多规则', type: 'danger' }
} as const

const openDetail = (record: AccessRecord) => {
  selectedRecord.value = record
  drawerVisible.value = true
}

const resetQuery = () => {
  query.keyword = ''
  query.sourceTypes = []
  query.matchStatuses = []
  query.departments = []
}

const retryMatch = () => ElMessage.success('已模拟重新匹配规则')
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>今日接入</span><strong>{{ records.length }}</strong><em>多来源汇总</em></el-card>
      <el-card shadow="never" class="metric-card"><span>已匹配</span><strong>{{ records.filter((r) => r.matchStatus === 'MATCHED').length }}</strong><em>已创建任务</em></el-card>
      <el-card shadow="never" class="metric-card"><span>待确认</span><strong>{{ records.filter((r) => r.matchStatus !== 'MATCHED').length }}</strong><em>需人工处理</em></el-card>
      <el-card shadow="never" class="metric-card"><span>API 推送</span><strong>1</strong><em>业务系统</em></el-card>
      <el-card shadow="never" class="metric-card"><span>分拣接入</span><strong>1</strong><em>文件/邮件</em></el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="trace/文档/任务/业务号/文件名" /></el-form-item>
        <el-form-item label="来源">
          <el-select v-model="query.sourceTypes" multiple filterable clearable collapse-tags placeholder="全部">
            <el-option label="手工上传" value="MANUAL_UPLOAD" />
            <el-option label="API 推送" value="API" />
            <el-option label="邮件分拣" value="EMAIL" />
            <el-option label="文件分拣" value="FILE_DISPATCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配状态">
          <el-select v-model="query.matchStatuses" multiple filterable clearable collapse-tags placeholder="全部">
            <el-option label="已匹配" value="MATCHED" />
            <el-option label="未匹配" value="UNMATCHED" />
            <el-option label="多规则" value="MULTIPLE" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="query.departments" multiple filterable clearable collapse-tags placeholder="全部">
            <el-option label="运营部" value="运营部" />
            <el-option label="财务部" value="财务部" />
            <el-option label="产品部" value="产品部" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>接入记录</span>
          <div><el-button @click="retryMatch">批量重新匹配</el-button><el-button type="primary" @click="router.push('/documents/upload')">手工上传</el-button></div>
        </div>
      </template>
      <el-table :data="filteredRecords" stripe>
        <el-table-column prop="traceId" label="TraceId" min-width="170" fixed />
        <el-table-column prop="fileName" label="文件名" min-width="160" />
        <el-table-column prop="sourceSystem" label="来源系统" min-width="130" />
        <el-table-column prop="businessNo" label="业务号" min-width="150" />
        <el-table-column prop="department" label="部门" width="90" />
        <el-table-column prop="documentType" label="文档类型" width="100" />
        <el-table-column label="匹配状态" width="100">
          <template #default="{ row }">
            <el-tag :type="matchStatusMap[row.matchStatus as keyof typeof matchStatusMap].type">
              {{ matchStatusMap[row.matchStatus as keyof typeof matchStatusMap].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="taskId" label="任务编号" min-width="170" />
        <el-table-column prop="fileSize" label="大小" width="90" />
        <el-table-column prop="createdAt" label="接入时间" min-width="150" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link @click="router.push('/monitor/traces')">链路</el-button>
            <el-button v-if="row.matchStatus !== 'MATCHED'" link type="warning" @click="router.push('/documents/unmatched')">确认</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="接入详情" size="560px">
      <template v-if="selectedRecord">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="TraceId">{{ selectedRecord.traceId }}</el-descriptions-item>
          <el-descriptions-item label="文档编号">{{ selectedRecord.documentId }}</el-descriptions-item>
          <el-descriptions-item label="任务编号">{{ selectedRecord.taskId }}</el-descriptions-item>
          <el-descriptions-item label="来源系统">{{ selectedRecord.sourceSystem }}</el-descriptions-item>
          <el-descriptions-item label="业务号">{{ selectedRecord.businessNo }}</el-descriptions-item>
          <el-descriptions-item label="文件名">{{ selectedRecord.fileName }}</el-descriptions-item>
          <el-descriptions-item label="匹配状态">{{ matchStatusMap[selectedRecord.matchStatus].label }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>
