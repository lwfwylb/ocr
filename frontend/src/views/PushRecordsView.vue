<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pushRecords, type PushMethod, type PushRecord, type PushStatus } from '../mock/data'

const records = ref<PushRecord[]>(pushRecords.map((item) => ({ ...item })))
const drawerVisible = ref(false)
const selectedRecord = ref<PushRecord | null>(null)
const query = reactive({
  keyword: '',
  targetSystems: [] as string[],
  services: [] as string[],
  statuses: [] as PushStatus[],
  methods: [] as PushMethod[]
})

const statusMap: Record<PushStatus, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  SUCCESS: { label: '成功', type: 'success' },
  PENDING: { label: '待推送', type: 'info' },
  FAILED: { label: '失败', type: 'danger' },
  RETRYING: { label: '重试中', type: 'warning' }
}

const methodMap: Record<PushMethod, string> = {
  HTTP: 'HTTP',
  MICROSERVICE: '微服务',
  MQ: 'MQ'
}

const targetSystemOptions = computed(() => Array.from(new Set(records.value.map((item) => item.targetSystem))))
const serviceOptions = computed(() => Array.from(new Set(records.value.map((item) => item.serviceName || item.serviceCode || '默认接收服务'))))
const filteredRecords = computed(() => {
  return records.value.filter((record) => {
    const keywordMatched =
      !query.keyword ||
      record.pushId.includes(query.keyword) ||
      record.traceId.includes(query.keyword) ||
      record.taskId.includes(query.keyword) ||
      (record.serviceName || '').includes(query.keyword) ||
      (record.serviceCode || '').includes(query.keyword) ||
      record.idempotentKey.includes(query.keyword)
    const targetMatched = query.targetSystems.length === 0 || query.targetSystems.includes(record.targetSystem)
    const serviceMatched = query.services.length === 0 || query.services.includes(record.serviceName || record.serviceCode || '默认接收服务')
    const statusMatched = query.statuses.length === 0 || query.statuses.includes(record.status)
    const methodMatched = query.methods.length === 0 || query.methods.includes(record.pushMethod)
    return keywordMatched && targetMatched && serviceMatched && statusMatched && methodMatched
  })
})

const successCount = computed(() => records.value.filter((item) => item.status === 'SUCCESS').length)
const failedCount = computed(() => records.value.filter((item) => item.status === 'FAILED').length)
const waitingCount = computed(() => records.value.filter((item) => item.status === 'PENDING' || item.status === 'RETRYING').length)

const openDetail = (record: PushRecord) => {
  selectedRecord.value = record
  drawerVisible.value = true
}

const retryPush = async (record: PushRecord) => {
  await ElMessageBox.confirm(`确认重新推送 ${record.pushId}？`, '失败重试', { type: 'warning' })
  record.retryCount += 1
  record.status = 'RETRYING'
  record.responseMessage = 'manual retry submitted'
  ElMessage.success('已提交重试，状态将在调用日志中持续更新')
}

const markSuccess = (record: PushRecord) => {
  record.status = 'SUCCESS'
  record.responseMessage = 'manual confirmed'
  ElMessage.success('已人工确认为成功')
}

const resetQuery = () => {
  query.keyword = ''
  query.targetSystems = []
  query.services = []
  query.statuses = []
  query.methods = []
}
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>推送总数</span><strong>{{ records.length }}</strong><em>近 7 日模拟</em></el-card>
      <el-card shadow="never" class="metric-card"><span>成功</span><strong>{{ successCount }}</strong><em>下游已接收</em></el-card>
      <el-card shadow="never" class="metric-card"><span>待处理</span><strong>{{ waitingCount }}</strong><em>待推送/重试中</em></el-card>
      <el-card shadow="never" class="metric-card"><span>失败</span><strong>{{ failedCount }}</strong><em>可人工重试</em></el-card>
      <el-card shadow="never" class="metric-card"><span>成功率</span><strong>96%</strong><em>按目标系统统计</em></el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="pushId/traceId/任务/幂等键" />
        </el-form-item>
        <el-form-item label="目标系统">
          <el-select v-model="query.targetSystems" multiple filterable clearable collapse-tags collapse-tags-tooltip placeholder="全部">
            <el-option v-for="item in targetSystemOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="接口服务">
          <el-select v-model="query.services" multiple filterable clearable collapse-tags collapse-tags-tooltip placeholder="全部">
            <el-option v-for="item in serviceOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.statuses" multiple filterable clearable collapse-tags placeholder="全部">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="待推送" value="PENDING" />
            <el-option label="失败" value="FAILED" />
            <el-option label="重试中" value="RETRYING" />
          </el-select>
        </el-form-item>
        <el-form-item label="方式">
          <el-select v-model="query.methods" multiple filterable clearable collapse-tags placeholder="全部">
            <el-option label="HTTP" value="HTTP" />
            <el-option label="微服务" value="MICROSERVICE" />
            <el-option label="MQ" value="MQ" />
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
          <span>推送记录</span>
          <div>
            <el-button>导出明细</el-button>
            <el-button type="primary">批量重试失败</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filteredRecords" stripe>
        <el-table-column prop="pushId" label="推送编号" min-width="170" fixed />
        <el-table-column prop="traceId" label="TraceId" min-width="180" />
        <el-table-column prop="taskId" label="任务编号" min-width="170" />
        <el-table-column prop="targetSystem" label="目标系统" min-width="140" />
        <el-table-column label="接口服务" min-width="160">
          <template #default="{ row }">{{ row.serviceName || row.serviceCode || '默认接收服务' }}</template>
        </el-table-column>
        <el-table-column label="方式" width="90">
          <template #default="{ row }">{{ methodMap[row.pushMethod as PushMethod] }}</template>
        </el-table-column>
        <el-table-column prop="triggerType" label="触发方式" width="120" />
        <el-table-column prop="idempotentKey" label="幂等键" min-width="260" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status as PushStatus].type">{{ statusMap[row.status as PushStatus].label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试" width="70" />
        <el-table-column prop="pushedAt" label="最近推送时间" min-width="150" />
        <el-table-column prop="responseMessage" label="响应摘要" min-width="180" />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="warning" :disabled="row.status === 'SUCCESS'" @click="retryPush(row)">重试</el-button>
            <el-button link type="success" :disabled="row.status === 'SUCCESS'" @click="markSuccess(row)">确认成功</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="推送详情" size="640px">
      <template v-if="selectedRecord">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="推送编号">{{ selectedRecord.pushId }}</el-descriptions-item>
          <el-descriptions-item label="TraceId">{{ selectedRecord.traceId }}</el-descriptions-item>
          <el-descriptions-item label="任务编号">{{ selectedRecord.taskId }}</el-descriptions-item>
          <el-descriptions-item label="目标系统">{{ selectedRecord.targetSystem }}</el-descriptions-item>
          <el-descriptions-item label="接口服务">{{ selectedRecord.serviceName || selectedRecord.serviceCode || '默认接收服务' }}</el-descriptions-item>
          <el-descriptions-item label="推送方式">{{ methodMap[selectedRecord.pushMethod] }}</el-descriptions-item>
          <el-descriptions-item label="触发方式">{{ selectedRecord.triggerType }}</el-descriptions-item>
          <el-descriptions-item label="幂等键">{{ selectedRecord.idempotentKey }}</el-descriptions-item>
          <el-descriptions-item label="响应摘要">{{ selectedRecord.responseMessage }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="section-title">排障建议</h3>
        <el-timeline>
          <el-timeline-item timestamp="落库成功后" type="success">生成推送任务，写入 pushId 与幂等键。</el-timeline-item>
          <el-timeline-item timestamp="调用下游" :type="selectedRecord.status === 'FAILED' ? 'danger' : 'success'">
            记录请求摘要、响应码、耗时与下游返回消息。
          </el-timeline-item>
          <el-timeline-item timestamp="失败处理" type="warning">自动重试耗尽后进入本页面，支持人工重试或确认成功。</el-timeline-item>
        </el-timeline>
        <el-alert
          title="点击 TraceId 可在全链路监控中回溯文档接入、规则匹配、解析、提取、加工校验、复核、落库和推送全过程。"
          type="info"
          :closable="false"
        />
      </template>
    </el-drawer>
  </div>
</template>
