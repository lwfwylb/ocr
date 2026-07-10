<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  disableIntegrationService,
  disableIntegrationSystem,
  enableIntegrationService,
  enableIntegrationSystem,
  listIntegrationServices,
  listIntegrationSystems,
  testIntegrationService,
  type DownstreamService,
  type DownstreamSystem,
  type IntegrationServiceType
} from '../api/integration'

const systems = ref<DownstreamSystem[]>([])
const services = ref<DownstreamService[]>([])
const selectedSystemCode = ref('')
const drawerVisible = ref(false)
const selectedService = ref<DownstreamService | null>(null)
const loading = ref(false)
const query = reactive({
  keyword: '',
  ownerDepartmentId: '',
  serviceType: '',
  status: ''
})

const methodMap: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'info' }> = {
  HTTP: { label: 'HTTP', type: 'primary' },
  MICROSERVICE: { label: '微服务', type: 'success' },
  MQ: { label: 'MQ', type: 'warning' }
}

const filteredSystems = computed(() => systems.value)
const selectedSystem = computed(() => systems.value.find((system) => system.systemCode === selectedSystemCode.value) || systems.value[0])
const selectedServices = computed(() => services.value.filter((service) => service.systemCode === selectedSystem.value?.systemCode))
const enabledSystemCount = computed(() => systems.value.filter((system) => system.enabled).length)
const enabledServiceCount = computed(() => services.value.filter((service) => service.enabled).length)
const avgSuccessRate = computed(() => {
  if (!services.value.length) return 0
  const total = services.value.reduce((sum, item) => sum + Number(item.successRate || 0), 0)
  return Math.round(total / services.value.length)
})

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      keyword: query.keyword,
      ownerDepartmentId: query.ownerDepartmentId,
      serviceType: query.serviceType,
      status: query.status
    }
    const [systemRows, serviceRows] = await Promise.all([listIntegrationSystems(params), listIntegrationServices(params)])
    systems.value = systemRows
    services.value = serviceRows
    if (!selectedSystemCode.value || !systems.value.some((item) => item.systemCode === selectedSystemCode.value)) {
      selectedSystemCode.value = systems.value[0]?.systemCode || ''
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询集成配置失败')
  } finally {
    loading.value = false
  }
}

const selectSystem = (system: DownstreamSystem) => {
  selectedSystemCode.value = system.systemCode
}

const openService = (service: DownstreamService) => {
  selectedService.value = service
  drawerVisible.value = true
}

const testConnect = async (service: DownstreamService) => {
  try {
    const result = await testIntegrationService(service.id)
    ElMessage.success(String(result.message || `${service.systemName} / ${service.serviceName} 连接测试通过`))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '连接测试失败')
  }
}

const toggleSystem = async (system?: DownstreamSystem) => {
  if (!system) return
  try {
    const updated = system.enabled ? await disableIntegrationSystem(system.id) : await enableIntegrationSystem(system.id)
    replaceSystem(updated)
    ElMessage.success(updated.enabled ? '已启用下游系统' : '已停用下游系统')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新系统状态失败')
  }
}

const toggleService = async (service: DownstreamService) => {
  try {
    const updated = service.enabled ? await disableIntegrationService(service.id) : await enableIntegrationService(service.id)
    replaceService(updated)
    if (selectedService.value?.id === updated.id) selectedService.value = updated
    ElMessage.success(updated.enabled ? '已启用接口服务' : '已停用接口服务')
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新服务状态失败')
  }
}

const replaceSystem = (system: DownstreamSystem) => {
  const index = systems.value.findIndex((item) => item.id === system.id)
  if (index >= 0) systems.value.splice(index, 1, system)
}

const replaceService = (service: DownstreamService) => {
  const index = services.value.findIndex((item) => item.id === service.id)
  if (index >= 0) services.value.splice(index, 1, service)
}

const resetQuery = () => {
  query.keyword = ''
  query.ownerDepartmentId = ''
  query.serviceType = ''
  query.status = ''
  loadData()
}

const methodLabel = (value?: IntegrationServiceType) => (value ? methodMap[value]?.label || value : '-')
const methodType = (value?: IntegrationServiceType) => methodMap[value || '']?.type || 'info'

onMounted(loadData)
</script>

<template>
  <div v-loading="loading" class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>下游系统</span><strong>{{ systems.length }}</strong><em>系统级治理对象</em></el-card>
      <el-card shadow="never" class="metric-card"><span>接口服务</span><strong>{{ services.length }}</strong><em>真实推送目标</em></el-card>
      <el-card shadow="never" class="metric-card"><span>启用服务</span><strong>{{ enabledServiceCount }}</strong><em>{{ enabledSystemCount }} 个系统启用</em></el-card>
      <el-card shadow="never" class="metric-card">
        <span>HTTP/微服务/MQ</span>
        <strong>{{ services.filter((s) => s.serviceType === 'HTTP').length }}/{{ services.filter((s) => s.serviceType === 'MICROSERVICE').length }}/{{ services.filter((s) => s.serviceType === 'MQ').length }}</strong>
        <em>服务类型</em>
      </el-card>
      <el-card shadow="never" class="metric-card"><span>成功率</span><strong>{{ avgSuccessRate }}%</strong><em>按推送记录统计</em></el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="系统/服务/地址" @keyup.enter="loadData" />
        </el-form-item>
        <el-form-item label="归属部门">
          <el-select v-model="query.ownerDepartmentId" filterable clearable placeholder="全部">
            <el-option label="运营部" value="运营部" />
            <el-option label="财务部" value="财务部" />
            <el-option label="产品部" value="产品部" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务方式">
          <el-select v-model="query.serviceType" filterable clearable placeholder="全部">
            <el-option label="HTTP" value="HTTP" />
            <el-option label="微服务" value="MICROSERVICE" />
            <el-option label="MQ" value="MQ" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="integration-layout">
      <el-card shadow="never" class="integration-sidebar">
        <template #header>
          <div class="card-header">
            <span>下游系统</span>
            <el-button disabled type="primary">新增系统</el-button>
          </div>
        </template>
        <el-empty v-if="!filteredSystems.length" description="暂无下游系统" />
        <div
          v-for="system in filteredSystems"
          :key="system.systemCode"
          class="integration-system-item"
          :class="{ active: selectedSystemCode === system.systemCode }"
          @click="selectSystem(system)"
        >
          <strong>{{ system.systemName }}</strong>
          <span>{{ system.systemCode }} / {{ system.ownerDepartmentId || '-' }}</span>
          <em>{{ system.serviceCount || 0 }} 个接口服务，{{ system.enabledServiceCount || 0 }} 个启用</em>
          <el-tag :type="system.enabled ? 'success' : 'info'">{{ system.enabled ? '启用' : '停用' }}</el-tag>
        </div>
      </el-card>

      <div class="page-stack">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>系统基础信息</span>
              <div>
                <el-button :disabled="!selectedSystem" @click="toggleSystem(selectedSystem)">
                  {{ selectedSystem?.enabled ? '停用系统' : '启用系统' }}
                </el-button>
                <el-button disabled type="primary">编辑系统</el-button>
              </div>
            </div>
          </template>
          <el-descriptions v-if="selectedSystem" :column="4" border>
            <el-descriptions-item label="系统名称">{{ selectedSystem.systemName }}</el-descriptions-item>
            <el-descriptions-item label="系统编码">{{ selectedSystem.systemCode }}</el-descriptions-item>
            <el-descriptions-item label="归属部门">{{ selectedSystem.ownerDepartmentId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="默认鉴权">{{ selectedSystem.defaultAuthMode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="默认超时">{{ selectedSystem.defaultTimeoutSeconds || '-' }}s</el-descriptions-item>
            <el-descriptions-item label="默认重试">{{ selectedSystem.defaultRetryCount || 0 }} 次</el-descriptions-item>
            <el-descriptions-item label="服务数">{{ selectedSystem.serviceCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="成功率">{{ selectedSystem.successRate || 0 }}%</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="请选择下游系统" />
        </el-card>

        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>接口服务</span>
              <div>
                <el-button disabled>导入接口清单</el-button>
                <el-button disabled type="primary">新增接口服务</el-button>
              </div>
            </div>
          </template>
          <el-table :data="selectedServices" stripe>
            <el-table-column prop="serviceName" label="服务名称" min-width="160" fixed />
            <el-table-column prop="serviceCode" label="服务编码" min-width="180" />
            <el-table-column prop="purpose" label="用途" width="100" />
            <el-table-column label="方式" width="100">
              <template #default="{ row }">
                <el-tag :type="methodType(row.serviceType)">{{ methodLabel(row.serviceType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="endpoint" label="地址/Topic/方法" min-width="280" show-overflow-tooltip />
            <el-table-column prop="boundConfigCount" label="绑定场景" width="90" />
            <el-table-column label="成功率" width="90">
              <template #default="{ row }">
                <el-progress :percentage="row.successRate || 0" :stroke-width="6" :show-text="false" />
                <span class="muted">{{ row.successRate || 0 }}%</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openService(row)">详情</el-button>
                <el-button link @click="testConnect(row)">测试</el-button>
                <el-button link :type="row.enabled ? 'danger' : 'success'" @click="toggleService(row)">
                  {{ row.enabled ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>

    <el-drawer v-model="drawerVisible" title="接口服务详情" size="680px">
      <template v-if="selectedService">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="所属系统">{{ selectedService.systemName }}</el-descriptions-item>
          <el-descriptions-item label="服务名称">{{ selectedService.serviceName }}</el-descriptions-item>
          <el-descriptions-item label="服务编码">{{ selectedService.serviceCode }}</el-descriptions-item>
          <el-descriptions-item label="服务用途">{{ selectedService.purpose || '-' }}</el-descriptions-item>
          <el-descriptions-item label="服务类型">{{ methodLabel(selectedService.serviceType) }}</el-descriptions-item>
          <el-descriptions-item label="地址/Topic/方法">{{ selectedService.endpoint || '-' }}</el-descriptions-item>
          <el-descriptions-item label="请求方式">{{ selectedService.httpMethod || '-' }}</el-descriptions-item>
          <el-descriptions-item label="鉴权方式">{{ selectedService.authMode === 'INHERIT' ? '继承系统默认' : selectedService.authMode || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="section-title">调用治理</h3>
        <el-form label-width="120px" class="form-grid">
          <el-form-item label="超时时间"><el-input-number v-model="selectedService.timeoutSeconds" disabled :min="1" /></el-form-item>
          <el-form-item label="重试次数"><el-input-number v-model="selectedService.retryCount" disabled :min="0" /></el-form-item>
          <el-form-item label="成功判断" class="wide"><el-input v-model="selectedService.responseSuccessRule" disabled /></el-form-item>
        </el-form>
        <el-alert
          class="mt-12"
          title="配置向导绑定的是具体接口服务，而不是下游系统本身。同一个系统可以提供多个服务，分别承载结果推送、附件推送、状态回调或批量同步。"
          type="info"
          :closable="false"
        />
      </template>
    </el-drawer>
  </div>
</template>
