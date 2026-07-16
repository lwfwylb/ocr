<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { pageStorageRecords, listStorageTables, type StorageRecord, type StorageTable } from '../api/storage'
import { createTablePage, pageParams, resetPage } from '../composables/useTablePage'

const loadingTables = ref(false)
const loadingRows = ref(false)
const selectedTable = ref('')
const tableKeyword = ref('')
const drawerVisible = ref(false)
const selectedRow = ref<StorageRecord | null>(null)
const tables = ref<StorageTable[]>([])
const rows = ref<StorageRecord[]>([])
const page = createTablePage(20)
const query = reactive({
  keyword: '',
  documentType: '',
  sourceType: '',
  storageStatus: 'SUCCESS'
})

const sourceTypeMap: Record<string, string> = {
  MANUAL_UPLOAD: '手工上传',
  BUSINESS_API: '业务系统API',
  API: '业务系统API',
  EMAIL_DISPATCH: '邮件分拣',
  EMAIL: '邮件分拣',
  FILE_DISPATCH: '文件分拣'
}

const statusMap: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  SUCCESS: { label: '落库成功', type: 'success' },
  FAILED: { label: '落库失败', type: 'danger' }
}

const currentTable = computed(() => tables.value.find((table) => table.tableName === selectedTable.value))
const dynamicColumns = computed(() => {
  const keys = new Set<string>()
  rows.value.slice(0, 20).forEach((row) => {
    Object.keys(row.storageData || {}).forEach((key) => {
      if (!key.startsWith('_')) keys.add(key)
    })
  })
  return Array.from(keys).slice(0, 8)
})

const loadTables = async () => {
  loadingTables.value = true
  try {
    tables.value = await listStorageTables(tableKeyword.value)
    if (!selectedTable.value || !tables.value.some((table) => table.tableName === selectedTable.value)) {
      selectedTable.value = tables.value[0]?.tableName || ''
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询落库表失败')
  } finally {
    loadingTables.value = false
  }
}

const loadRows = async () => {
  loadingRows.value = true
  try {
    const result = await pageStorageRecords({
      keyword: query.keyword,
      targetTable: selectedTable.value,
      documentType: query.documentType,
      sourceType: query.sourceType,
      storageStatus: query.storageStatus,
      ...pageParams(page)
    })
    rows.value = result.records
    page.total = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询落库数据失败')
  } finally {
    loadingRows.value = false
  }
}

const selectTable = (table: StorageTable) => {
  selectedTable.value = table.tableName
  resetPage(page)
}

const openDetail = (row: StorageRecord) => {
  selectedRow.value = row
  drawerVisible.value = true
}

const exportRows = () => {
  ElMessage.success('已生成当前查询结果导出任务，后续接入导出记录接口')
}

const searchRows = () => {
  resetPage(page)
  loadRows()
}

const handlePageSizeChange = () => {
  resetPage(page)
  loadRows()
}

const handlePageChange = () => loadRows()

const resetQuery = () => {
  query.keyword = ''
  query.documentType = ''
  query.sourceType = ''
  query.storageStatus = 'SUCCESS'
  resetPage(page)
  loadRows()
}

const formatSource = (value?: string) => (value ? sourceTypeMap[value] || value : '-')
const getStatus = (value?: string) => statusMap[value || ''] || { label: value || '-', type: 'info' }
const formatValue = (value: unknown) => {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
const relatedProfiles = (value?: string) => (value ? value.split(',').filter(Boolean) : [])

watch(selectedTable, () => loadRows())

onMounted(async () => {
  await loadTables()
  await loadRows()
})
</script>

<template>
  <div class="storage-data-layout">
    <el-card shadow="never" class="table-sidebar">
      <template #header>落库表</template>
      <el-input
        v-model="tableKeyword"
        class="table-search"
        clearable
        placeholder="搜索表名/映射方案"
        @keyup.enter="loadTables"
        @clear="loadTables"
      />
      <el-button class="table-search-button" type="primary" plain @click="loadTables">查询表</el-button>
      <el-scrollbar height="calc(100vh - 220px)" v-loading="loadingTables">
        <div
          v-for="table in tables"
          :key="table.tableName"
          class="storage-table-item"
          :class="{ active: selectedTable === table.tableName }"
          @click="selectTable(table)"
        >
          <strong>{{ table.tableCnName || table.tableName }}</strong>
          <span>{{ table.tableName }}</span>
          <em>{{ table.rowCount }} 行</em>
        </div>
        <el-empty v-if="tables.length === 0" description="暂无已落库表" />
      </el-scrollbar>
    </el-card>

    <div class="page-stack">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <div>
              <strong>{{ currentTable?.tableCnName || currentTable?.tableName || '落库数据查询' }}</strong>
              <p class="muted">{{ currentTable?.description || '按目标表查看已经执行落库的提取结果。' }}</p>
            </div>
            <el-button type="primary" @click="exportRows">导出当前查询</el-button>
          </div>
        </template>
        <div class="mapping-tags">
          <span class="muted">关联映射方案：</span>
          <el-tag v-for="profile in relatedProfiles(currentTable?.relatedConfigs)" :key="profile" type="primary" effect="light">
            {{ profile }}
          </el-tag>
          <span v-if="!relatedProfiles(currentTable?.relatedConfigs).length" class="muted">暂无</span>
        </div>
      </el-card>

      <el-card shadow="never">
        <el-form :inline="true" :model="query" class="search-form compact-search">
          <el-form-item label="关键字">
            <el-input v-model="query.keyword" placeholder="任务/traceId/文件/业务号" clearable />
          </el-form-item>
          <el-form-item label="文档类型">
            <el-input v-model="query.documentType" placeholder="如：划款指令" clearable />
          </el-form-item>
          <el-form-item label="来源">
            <el-select v-model="query.sourceType" clearable filterable placeholder="全部">
              <el-option label="手工上传" value="MANUAL_UPLOAD" />
              <el-option label="业务系统API" value="BUSINESS_API" />
              <el-option label="邮件分拣" value="EMAIL_DISPATCH" />
              <el-option label="文件分拣" value="FILE_DISPATCH" />
            </el-select>
          </el-form-item>
          <el-form-item label="落库状态">
            <el-select v-model="query.storageStatus" clearable filterable placeholder="全部">
              <el-option label="落库成功" value="SUCCESS" />
              <el-option label="落库失败" value="FAILED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="searchRows">查询</el-button>
            <el-button @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>表数据</span>
            <span class="muted">第一版展示平台落库台账中的 JSON 数据，后续可切换为真实业务表查询。</span>
          </div>
        </template>
        <el-table v-loading="loadingRows" :data="rows" stripe>
          <el-table-column prop="taskId" label="来源任务" min-width="170" fixed />
          <el-table-column prop="traceId" label="TraceId" min-width="170" />
          <el-table-column prop="businessNo" label="业务号" min-width="140" />
          <el-table-column prop="fileName" label="文件名" min-width="160" />
          <el-table-column prop="documentType" label="文档类型" width="120" />
          <el-table-column label="来源" width="120">
            <template #default="{ row }">{{ formatSource(row.sourceType) }}</template>
          </el-table-column>
          <el-table-column v-for="column in dynamicColumns" :key="column" :label="column" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ formatValue(row.storageData?.[column]) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatus(row.storageStatus).type">{{ getStatus(row.storageStatus).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="storedBy" label="落库人" width="110" />
          <el-table-column prop="storedAt" label="落库时间" min-width="160" />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="page.pageNo"
          v-model:page-size="page.pageSize"
          class="table-pagination"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50, 100]"
          :total="page.total"
          @size-change="handlePageSizeChange"
          @current-change="handlePageChange"
        />
      </el-card>
    </div>

    <el-drawer v-model="drawerVisible" title="落库数据详情" size="680px">
      <template v-if="selectedRow">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="目标表">{{ selectedRow.targetTable }}</el-descriptions-item>
          <el-descriptions-item label="映射方案">{{ selectedRow.mappingProfile || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源任务">{{ selectedRow.taskId }}</el-descriptions-item>
          <el-descriptions-item label="TraceId">{{ selectedRow.traceId }}</el-descriptions-item>
          <el-descriptions-item label="业务号">{{ selectedRow.businessNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="落库策略">{{ selectedRow.duplicateStrategy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="唯一键">{{ selectedRow.uniqueKeyJson || '-' }}</el-descriptions-item>
          <el-descriptions-item label="落库时间">{{ selectedRow.storedAt || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="section-title">入库字段</h3>
        <el-table :data="Object.entries(selectedRow.storageData || {}).map(([field, value]) => ({ field, value }))">
          <el-table-column prop="field" label="字段" min-width="180" />
          <el-table-column label="值" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">{{ formatValue(row.value) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.table-search-button {
  width: 100%;
  margin: 8px 0 10px;
}
</style>
