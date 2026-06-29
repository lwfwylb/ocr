<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ConfidenceTag from '../components/ConfidenceTag.vue'

type ResultStatus = 'STORED' | 'WAIT_REVIEW' | 'PUSHED' | 'FAILED'

interface ResultItem {
  taskId: string
  documentId: string
  fileName: string
  documentType: string
  department: string
  sourceType: string
  resultStatus: ResultStatus
  reviewStatus: string
  targetTable: string
  mappingProfile: string
  fieldCount: number
  avgConfidence: number
  storedAt: string
  pushedAt?: string
}

const drawerVisible = ref(false)
const activeTab = ref('fields')
const selectedResult = ref<ResultItem | null>(null)
const query = reactive({
  keyword: '',
  documentType: '',
  department: '',
  sourceType: '',
  resultStatus: ''
})

const results = ref<ResultItem[]>([
  {
    taskId: 'TASK-20260628-0001',
    documentId: 'DOC-20260628-0001',
    fileName: '划款指令_001.pdf',
    documentType: '划款指令',
    department: '运营部',
    sourceType: 'MANUAL_UPLOAD',
    resultStatus: 'STORED',
    reviewStatus: '已复核',
    targetTable: 'ext_fund_business_result',
    mappingProfile: '划款指令-资金结果表映射',
    fieldCount: 8,
    avgConfidence: 0.93,
    storedAt: '2026-06-28 09:42:00'
  },
  {
    taskId: 'TASK-20260628-0002',
    documentId: 'DOC-20260628-0002',
    fileName: '银行回单_002.png',
    documentType: '银行回单',
    department: '财务部',
    sourceType: 'API',
    resultStatus: 'PUSHED',
    reviewStatus: '自动通过',
    targetTable: 'ext_fund_business_result',
    mappingProfile: '银行回单-资金结果表映射',
    fieldCount: 7,
    avgConfidence: 0.95,
    storedAt: '2026-06-28 10:18:00',
    pushedAt: '2026-06-28 10:19:00'
  },
  {
    taskId: 'TASK-20260628-0005',
    documentId: 'DOC-20260628-0005',
    fileName: '开户资料_客户A.pdf',
    documentType: '开户资料',
    department: '运营部',
    sourceType: 'EMAIL',
    resultStatus: 'WAIT_REVIEW',
    reviewStatus: '待复核',
    targetTable: 'ext_customer_open_account',
    mappingProfile: '开户资料-客户表映射',
    fieldCount: 12,
    avgConfidence: 0.86,
    storedAt: '2026-06-28 11:30:00'
  },
  {
    taskId: 'TASK-20260628-0006',
    documentId: 'DOC-20260628-0006',
    fileName: '产品合同_001.pdf',
    documentType: '产品合同',
    department: '产品部',
    sourceType: 'FILE_DISPATCH',
    resultStatus: 'FAILED',
    reviewStatus: '无需复核',
    targetTable: 'ext_product_contract_terms',
    mappingProfile: '产品合同-条款表映射',
    fieldCount: 16,
    avgConfidence: 0.89,
    storedAt: '2026-06-28 15:24:00'
  }
])

const fieldRows = [
  { fieldName: '付款方名称', extractField: 'payer_name', targetColumn: 'payer_name', rawValue: '示例基金管理有限公司', finalValue: '示例基金管理有限公司', confidence: 0.96, sourcePage: 1 },
  { fieldName: '收款账号', extractField: 'payee_account', targetColumn: 'counterparty_account', rawValue: '6222 **** 8910', finalValue: '6222 **** 8910', confidence: 0.91, sourcePage: 1 },
  { fieldName: '划款金额', extractField: 'amount', targetColumn: 'business_amount', rawValue: '100000.00', finalValue: '100000.00', confidence: 0.92, sourcePage: 1 },
  { fieldName: '划款日期', extractField: 'payment_date', targetColumn: 'business_date', rawValue: '2026-06-28', finalValue: '2026-06-28', confidence: 0.94, sourcePage: 1 }
]

const storageRows = [
  { targetColumn: 'task_id', value: 'TASK-20260628-0001', transform: '系统字段' },
  { targetColumn: 'document_type', value: '划款指令', transform: '固定值填充' },
  { targetColumn: 'counterparty_account', value: '6222 **** 8910', transform: '脱敏写入' },
  { targetColumn: 'business_amount', value: '100000.00', transform: '金额格式标准化' }
]

const exportRows = [
  { exportId: 'EXP-0001', type: 'Excel', status: '成功', operator: '王老师', createdAt: '2026-06-28 10:00:00' },
  { exportId: 'EXP-0002', type: 'JSON', status: '成功', operator: '李老师', createdAt: '2026-06-28 10:20:00' }
]

const pushRows = [
  { targetSystem: '资金业务系统', status: '成功', pushedAt: '2026-06-28 10:19:00', message: '已接收' },
  { targetSystem: '数据仓库', status: '待推送', pushedAt: '-', message: '等待批量同步' }
]

const filteredResults = computed(() => {
  return results.value.filter((item) => {
    const keywordMatched = !query.keyword || item.taskId.includes(query.keyword) || item.fileName.includes(query.keyword)
    const typeMatched = !query.documentType || item.documentType === query.documentType
    const departmentMatched = !query.department || item.department === query.department
    const sourceMatched = !query.sourceType || item.sourceType === query.sourceType
    const statusMatched = !query.resultStatus || item.resultStatus === query.resultStatus
    return keywordMatched && typeMatched && departmentMatched && sourceMatched && statusMatched
  })
})

const statusMap: Record<ResultStatus, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  STORED: { label: '已落库', type: 'success' },
  WAIT_REVIEW: { label: '待复核', type: 'warning' },
  PUSHED: { label: '已推送', type: 'success' },
  FAILED: { label: '失败', type: 'danger' }
}

const openDetail = (row: ResultItem, tab = 'fields') => {
  selectedResult.value = row
  activeTab.value = tab
  drawerVisible.value = true
}

const exportResult = (type: string) => {
  ElMessage.success(`已模拟导出 ${type}`)
}

const pushResult = async (row: ResultItem) => {
  await ElMessageBox.confirm('确认将该结果重新推送到下游系统？', '手工推送', { type: 'warning' })
  row.resultStatus = 'PUSHED'
  row.pushedAt = '2026-06-29 09:30:00'
  ElMessage.success('已模拟推送下游')
}

const resetQuery = () => {
  query.keyword = ''
  query.documentType = ''
  query.department = ''
  query.sourceType = ''
  query.resultStatus = ''
}
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card">
        <span>结果总数</span>
        <strong>{{ results.length }}</strong>
        <em>可查询导出</em>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>已落库</span>
        <strong>{{ results.filter((item) => item.resultStatus === 'STORED' || item.resultStatus === 'PUSHED').length }}</strong>
        <em>含已推送</em>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>待复核</span>
        <strong>{{ results.filter((item) => item.resultStatus === 'WAIT_REVIEW').length }}</strong>
        <em>低置信度</em>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>已推送</span>
        <strong>{{ results.filter((item) => item.resultStatus === 'PUSHED').length }}</strong>
        <em>下游已接收</em>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>平均置信度</span>
        <strong>91%</strong>
        <em>试点样本</em>
      </el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="任务编号/文件名" clearable />
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="query.documentType" clearable placeholder="全部">
            <el-option label="划款指令" value="划款指令" />
            <el-option label="银行回单" value="银行回单" />
            <el-option label="开户资料" value="开户资料" />
            <el-option label="产品合同" value="产品合同" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="query.department" clearable placeholder="全部">
            <el-option label="运营部" value="运营部" />
            <el-option label="财务部" value="财务部" />
            <el-option label="产品部" value="产品部" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="query.sourceType" clearable placeholder="全部">
            <el-option label="手工上传" value="MANUAL_UPLOAD" />
            <el-option label="API" value="API" />
            <el-option label="邮件分拣" value="EMAIL" />
            <el-option label="文件分拣" value="FILE_DISPATCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果状态">
          <el-select v-model="query.resultStatus" clearable placeholder="全部">
            <el-option label="已落库" value="STORED" />
            <el-option label="待复核" value="WAIT_REVIEW" />
            <el-option label="已推送" value="PUSHED" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary">查询</el-button>
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
      <el-table :data="filteredResults" stripe>
        <el-table-column prop="taskId" label="任务编号" min-width="170" fixed />
        <el-table-column prop="fileName" label="文件名" min-width="160" />
        <el-table-column prop="documentType" label="文档类型" width="100" />
        <el-table-column prop="department" label="部门" width="90" />
        <el-table-column prop="sourceType" label="来源" width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.resultStatus as ResultStatus].type">{{ statusMap[row.resultStatus as ResultStatus].label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetTable" label="目标表" min-width="190" />
        <el-table-column prop="mappingProfile" label="映射方案" min-width="190" />
        <el-table-column label="字段数" width="80" prop="fieldCount" />
        <el-table-column label="置信度" width="90">
          <template #default="{ row }"><ConfidenceTag :value="row.avgConfidence" /></template>
        </el-table-column>
        <el-table-column prop="storedAt" label="落库时间" min-width="150" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row, 'fields')">字段</el-button>
            <el-button link type="primary" @click="openDetail(row, 'storage')">落库</el-button>
            <el-button link @click="openDetail(row, 'exports')">导出</el-button>
            <el-button link type="success" @click="pushResult(row)">推送</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="结果详情" size="720px">
      <template v-if="selectedResult">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务编号">{{ selectedResult.taskId }}</el-descriptions-item>
          <el-descriptions-item label="文档编号">{{ selectedResult.documentId }}</el-descriptions-item>
          <el-descriptions-item label="文档类型">{{ selectedResult.documentType }}</el-descriptions-item>
          <el-descriptions-item label="目标表">{{ selectedResult.targetTable }}</el-descriptions-item>
          <el-descriptions-item label="映射方案">{{ selectedResult.mappingProfile }}</el-descriptions-item>
          <el-descriptions-item label="复核状态">{{ selectedResult.reviewStatus }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="activeTab" class="mt-12" type="border-card">
          <el-tab-pane label="字段结果" name="fields">
            <el-table :data="fieldRows">
              <el-table-column prop="fieldName" label="字段名称" width="110" />
              <el-table-column prop="extractField" label="提取字段" min-width="130" />
              <el-table-column prop="targetColumn" label="目标字段" min-width="150" />
              <el-table-column prop="rawValue" label="原始值" min-width="150" />
              <el-table-column prop="finalValue" label="最终值" min-width="150" />
              <el-table-column label="置信度" width="90">
                <template #default="{ row }"><ConfidenceTag :value="row.confidence" /></template>
              </el-table-column>
              <el-table-column prop="sourcePage" label="页码" width="70" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="落库预览" name="storage">
            <el-alert title="落库预览展示字段映射和加工后的最终入库值。" type="info" :closable="false" class="mb-12" />
            <el-table :data="storageRows">
              <el-table-column prop="targetColumn" label="目标字段" min-width="150" />
              <el-table-column prop="value" label="入库值" min-width="180" />
              <el-table-column prop="transform" label="加工逻辑" min-width="180" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="导出记录" name="exports">
            <el-table :data="exportRows">
              <el-table-column prop="exportId" label="导出编号" />
              <el-table-column prop="type" label="格式" />
              <el-table-column prop="status" label="状态" />
              <el-table-column prop="operator" label="操作人" />
              <el-table-column prop="createdAt" label="导出时间" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="推送记录" name="push">
            <el-table :data="pushRows">
              <el-table-column prop="targetSystem" label="目标系统" />
              <el-table-column prop="status" label="状态" />
              <el-table-column prop="pushedAt" label="推送时间" />
              <el-table-column prop="message" label="消息" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
  </div>
</template>
