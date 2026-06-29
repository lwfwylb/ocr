<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ConfidenceTag from '../components/ConfidenceTag.vue'

interface TableMeta {
  tableName: string
  tableCnName: string
  description: string
  rowCount: number
  relatedConfigs: string[]
}

interface StorageRow {
  id: string
  taskId: string
  documentType: string
  sourceSystem: string
  bizNo: string
  payerName: string
  counterpartyName: string
  counterpartyAccount: string
  businessAmount: string
  businessDate: string
  productCode: string
  productName: string
  reviewStatus: string
  confidence: number
  createdAt: string
}

const selectedTable = ref('ext_fund_business_result')
const tableKeyword = ref('')
const drawerVisible = ref(false)
const selectedRow = ref<StorageRow | null>(null)
const query = reactive({
  keyword: '',
  documentTypes: [] as string[],
  sourceSystems: [] as string[],
  reviewStatuses: [] as string[],
  dateRange: ''
})

const tables: TableMeta[] = [
  {
    tableName: 'ext_fund_business_result',
    tableCnName: '资金业务要素结果表',
    description: '划款指令、银行回单等资金类任务复用的统一结果表',
    rowCount: 2846,
    relatedConfigs: ['划款指令-资金结果表映射', '银行回单-资金结果表映射']
  },
  {
    tableName: 'ext_customer_open_account',
    tableCnName: '开户资料客户信息表',
    description: '开户资料提取后的客户、证件、账户信息',
    rowCount: 638,
    relatedConfigs: ['开户资料-客户表映射']
  },
  {
    tableName: 'ext_product_contract_terms',
    tableCnName: '产品合同条款表',
    description: '产品合同、托管协议等长文本条款提取结果',
    rowCount: 126,
    relatedConfigs: ['产品合同-条款表映射']
  }
]

const rows = ref<StorageRow[]>([
  {
    id: 'ROW-0001',
    taskId: 'TASK-20260628-0001',
    documentType: '划款指令',
    sourceSystem: 'MANUAL_UPLOAD',
    bizNo: 'BIZ-20260628-001',
    payerName: '示例基金管理有限公司',
    counterpartyName: '示例托管银行',
    counterpartyAccount: '6222 **** 8910',
    businessAmount: '100000.00',
    businessDate: '2026-06-28',
    productCode: 'PRD001',
    productName: '示例 REITs 一号',
    reviewStatus: '已复核',
    confidence: 0.93,
    createdAt: '2026-06-28 09:42:00'
  },
  {
    id: 'ROW-0002',
    taskId: 'TASK-20260628-0002',
    documentType: '银行回单',
    sourceSystem: 'API',
    bizNo: 'BANK-20260628-7788',
    payerName: '示例托管银行',
    counterpartyName: '示例基金管理有限公司',
    counterpartyAccount: '4333 **** 1188',
    businessAmount: '100000.00',
    businessDate: '2026-06-28',
    productCode: 'PRD001',
    productName: '示例 REITs 一号',
    reviewStatus: '自动通过',
    confidence: 0.95,
    createdAt: '2026-06-28 10:18:00'
  },
  {
    id: 'ROW-0003',
    taskId: 'TASK-20260628-0007',
    documentType: '划款指令',
    sourceSystem: 'EMAIL',
    bizNo: 'BIZ-20260628-009',
    payerName: '示例基金管理有限公司',
    counterpartyName: '示例证券公司',
    counterpartyAccount: '9888 **** 0002',
    businessAmount: '560000.00',
    businessDate: '2026-06-28',
    productCode: 'PRD002',
    productName: '示例基础设施基金',
    reviewStatus: '待复核',
    confidence: 0.86,
    createdAt: '2026-06-28 14:35:00'
  }
])

const currentTable = computed(() => tables.find((table) => table.tableName === selectedTable.value) || tables[0])
const filteredTables = computed(() => {
  const keyword = tableKeyword.value.trim().toLowerCase()
  if (!keyword) return tables
  return tables.filter((table) => {
    return (
      table.tableName.toLowerCase().includes(keyword) ||
      table.tableCnName.toLowerCase().includes(keyword) ||
      table.description.toLowerCase().includes(keyword) ||
      table.relatedConfigs.some((profile) => profile.toLowerCase().includes(keyword))
    )
  })
})
const filteredRows = computed(() => {
  return rows.value.filter((row) => {
    const keywordMatched =
      !query.keyword ||
      row.taskId.includes(query.keyword) ||
      row.bizNo.includes(query.keyword) ||
      row.payerName.includes(query.keyword) ||
      row.counterpartyName.includes(query.keyword)
    const typeMatched = query.documentTypes.length === 0 || query.documentTypes.includes(row.documentType)
    const sourceMatched = query.sourceSystems.length === 0 || query.sourceSystems.includes(row.sourceSystem)
    const reviewMatched = query.reviewStatuses.length === 0 || query.reviewStatuses.includes(row.reviewStatus)
    return keywordMatched && typeMatched && sourceMatched && reviewMatched
  })
})

const openDetail = (row: StorageRow) => {
  selectedRow.value = row
  drawerVisible.value = true
}

const exportRows = () => {
  ElMessage.success(`已模拟导出 ${currentTable.value.tableCnName} 查询结果`)
}

const resetQuery = () => {
  query.keyword = ''
  query.documentTypes = []
  query.sourceSystems = []
  query.reviewStatuses = []
  query.dateRange = ''
}
</script>

<template>
  <div class="storage-data-layout">
    <el-card shadow="never" class="table-sidebar">
      <template #header>落库表</template>
      <el-input
        v-model="tableKeyword"
        class="table-search"
        clearable
        placeholder="搜索表名/中文名/映射方案"
      />
      <div
        v-for="table in filteredTables"
        :key="table.tableName"
        class="storage-table-item"
        :class="{ active: selectedTable === table.tableName }"
        @click="selectedTable = table.tableName"
      >
        <strong>{{ table.tableCnName }}</strong>
        <span>{{ table.tableName }}</span>
        <em>{{ table.rowCount }} 行</em>
      </div>
      <el-empty v-if="filteredTables.length === 0" description="未找到匹配表" />
    </el-card>

    <div class="page-stack">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <div>
              <strong>{{ currentTable.tableCnName }}</strong>
              <p class="muted">{{ currentTable.description }}</p>
            </div>
            <el-button type="primary" @click="exportRows">导出当前查询</el-button>
          </div>
        </template>
        <div class="mapping-tags">
          <span class="muted">关联映射方案：</span>
          <el-tag v-for="profile in currentTable.relatedConfigs" :key="profile" type="primary" effect="light">{{ profile }}</el-tag>
        </div>
      </el-card>

      <el-card shadow="never">
        <el-form :inline="true" :model="query" class="search-form compact-search">
          <el-form-item label="关键词">
            <el-input v-model="query.keyword" placeholder="任务/业务号/主体名称" clearable />
          </el-form-item>
          <el-form-item label="文档类型">
            <el-select
              v-model="query.documentTypes"
              clearable
              collapse-tags
              collapse-tags-tooltip
              filterable
              multiple
              placeholder="全部"
            >
              <el-option label="划款指令" value="划款指令" />
              <el-option label="银行回单" value="银行回单" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源">
            <el-select
              v-model="query.sourceSystems"
              clearable
              collapse-tags
              collapse-tags-tooltip
              filterable
              multiple
              placeholder="全部"
            >
              <el-option label="手工上传" value="MANUAL_UPLOAD" />
              <el-option label="API" value="API" />
              <el-option label="邮件分拣" value="EMAIL" />
            </el-select>
          </el-form-item>
          <el-form-item label="复核状态">
            <el-select
              v-model="query.reviewStatuses"
              clearable
              collapse-tags
              collapse-tags-tooltip
              filterable
              multiple
              placeholder="全部"
            >
              <el-option label="已复核" value="已复核" />
              <el-option label="自动通过" value="自动通过" />
              <el-option label="待复核" value="待复核" />
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
            <span>表数据</span>
            <span class="muted">按目标表维度查看所有任务写入的数据</span>
          </div>
        </template>
        <el-table :data="filteredRows" stripe>
          <el-table-column prop="bizNo" label="业务号" min-width="150" fixed />
          <el-table-column prop="taskId" label="来源任务" min-width="170" />
          <el-table-column prop="documentType" label="文档类型" width="100" />
          <el-table-column prop="payerName" label="付款方/主体" min-width="170" />
          <el-table-column prop="counterpartyName" label="交易对手" min-width="150" />
          <el-table-column prop="counterpartyAccount" label="对手账号" min-width="140" />
          <el-table-column prop="businessAmount" label="金额" width="110" align="right" />
          <el-table-column prop="businessDate" label="业务日期" width="110" />
          <el-table-column prop="productName" label="产品名称" min-width="150" />
          <el-table-column prop="reviewStatus" label="复核状态" width="100" />
          <el-table-column label="置信度" width="90">
            <template #default="{ row }"><ConfidenceTag :value="row.confidence" /></template>
          </el-table-column>
          <el-table-column prop="createdAt" label="入库时间" min-width="150" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button link>任务</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <el-drawer v-model="drawerVisible" title="落库数据详情" size="620px">
      <template v-if="selectedRow">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="目标表">{{ currentTable.tableName }}</el-descriptions-item>
          <el-descriptions-item label="业务号">{{ selectedRow.bizNo }}</el-descriptions-item>
          <el-descriptions-item label="来源任务">{{ selectedRow.taskId }}</el-descriptions-item>
          <el-descriptions-item label="文档类型">{{ selectedRow.documentType }}</el-descriptions-item>
          <el-descriptions-item label="付款方/主体">{{ selectedRow.payerName }}</el-descriptions-item>
          <el-descriptions-item label="交易对手">{{ selectedRow.counterpartyName }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ selectedRow.businessAmount }}</el-descriptions-item>
          <el-descriptions-item label="产品名称">{{ selectedRow.productName }}</el-descriptions-item>
          <el-descriptions-item label="置信度">
            <ConfidenceTag :value="selectedRow.confidence" />
          </el-descriptions-item>
        </el-descriptions>
        <h3 class="section-title">来源追溯</h3>
        <el-timeline>
          <el-timeline-item timestamp="2026-06-28 09:30:00">任务创建并命中映射方案</el-timeline-item>
          <el-timeline-item timestamp="2026-06-28 09:40:00">人工复核确认关键字段</el-timeline-item>
          <el-timeline-item timestamp="2026-06-28 09:42:00">按映射方案写入 {{ currentTable.tableName }}</el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </div>
</template>
