<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createIntegrationService,
  createIntegrationSystem,
  deleteIntegrationService,
  deleteIntegrationSystem,
  disableIntegrationService,
  disableIntegrationSystem,
  enableIntegrationService,
  enableIntegrationSystem,
  listIntegrationServices,
  listIntegrationSystems,
  testIntegrationService,
  updateIntegrationService,
  updateIntegrationSystem,
  type DownstreamService,
  type DownstreamServicePayload,
  type DownstreamSystem,
  type DownstreamSystemPayload,
  type IntegrationServiceType
} from '../api/integration'

const systems = ref<DownstreamSystem[]>([])
const services = ref<DownstreamService[]>([])
const selectedSystemCode = ref('')
const serviceDrawerVisible = ref(false)
const systemDialogVisible = ref(false)
const serviceDialogVisible = ref(false)
const editingSystemId = ref('')
const editingServiceId = ref('')
const selectedService = ref<DownstreamService | null>(null)
const loading = ref(false)
const saving = ref(false)

const query = reactive({
  keyword: '',
  ownerDepartmentId: '',
  serviceType: '',
  status: ''
})

const systemForm = reactive<DownstreamSystemPayload>({
  systemCode: '',
  systemName: '',
  ownerDepartmentId: '运营部',
  defaultAuthMode: 'NONE',
  defaultTimeoutSeconds: 30,
  defaultRetryCount: 3,
  status: 'ENABLED'
})

const serviceForm = reactive<DownstreamServicePayload>({
  systemId: '',
  serviceCode: '',
  serviceName: '',
  purpose: '结果推送',
  serviceType: 'HTTP',
  endpoint: '',
  httpMethod: 'POST',
  authMode: 'INHERIT',
  timeoutSeconds: 30,
  retryCount: 3,
  responseSuccessRule: 'httpStatus in [200,202] && body.code == 0',
  enabled: true
})

const methodMap: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'info' }> = {
  HTTP: { label: 'HTTP', type: 'primary' },
  MICROSERVICE: { label: '微服务', type: 'success' },
  MQ: { label: 'MQ', type: 'warning' }
}

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

const openCreateSystem = () => {
  editingSystemId.value = ''
  Object.assign(systemForm, {
    systemCode: '',
    systemName: '',
    ownerDepartmentId: '运营部',
    defaultAuthMode: 'NONE',
    defaultTimeoutSeconds: 30,
    defaultRetryCount: 3,
    status: 'ENABLED'
  })
  systemDialogVisible.value = true
}

const openEditSystem = () => {
  const system = selectedSystem.value
  if (!system) return
  editingSystemId.value = system.id
  Object.assign(systemForm, {
    systemCode: system.systemCode,
    systemName: system.systemName,
    ownerDepartmentId: system.ownerDepartmentId || '运营部',
    defaultAuthMode: system.defaultAuthMode || 'NONE',
    defaultTimeoutSeconds: system.defaultTimeoutSeconds || 30,
    defaultRetryCount: system.defaultRetryCount || 3,
    status: system.status || 'ENABLED'
  })
  systemDialogVisible.value = true
}

const saveSystem = async () => {
  saving.value = true
  try {
    const saved = editingSystemId.value
      ? await updateIntegrationSystem(editingSystemId.value, systemForm)
      : await createIntegrationSystem(systemForm)
    selectedSystemCode.value = saved.systemCode
    systemDialogVisible.value = false
    ElMessage.success(editingSystemId.value ? '系统已更新' : '系统已新增')
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存系统失败')
  } finally {
    saving.value = false
  }
}

const deleteSystem = async () => {
  const system = selectedSystem.value
  if (!system) return
  try {
    await ElMessageBox.confirm(
      `确认删除下游系统「${system.systemName}」？删除后不可恢复；如系统下仍有接口服务，需要先删除接口服务。`,
      '删除下游系统',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await deleteIntegrationSystem(system.id)
    ElMessage.success('下游系统已删除')
    selectedSystemCode.value = ''
    await loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除下游系统失败')
  }
}

const openCreateService = () => {
  if (!selectedSystem.value) {
    ElMessage.info('请先选择下游系统')
    return
  }
  editingServiceId.value = ''
  Object.assign(serviceForm, {
    systemId: selectedSystem.value.id,
    serviceCode: '',
    serviceName: '',
    purpose: '结果推送',
    serviceType: 'HTTP',
    endpoint: '',
    httpMethod: 'POST',
    authMode: 'INHERIT',
    timeoutSeconds: selectedSystem.value.defaultTimeoutSeconds || 30,
    retryCount: selectedSystem.value.defaultRetryCount || 3,
    responseSuccessRule: 'httpStatus in [200,202] && body.code == 0',
    enabled: true
  })
  serviceDialogVisible.value = true
}

const openEditService = (service: DownstreamService) => {
  editingServiceId.value = service.id
  Object.assign(serviceForm, {
    systemId: service.systemId,
    serviceCode: service.serviceCode,
    serviceName: service.serviceName,
    purpose: service.purpose || '结果推送',
    serviceType: service.serviceType || 'HTTP',
    endpoint: service.endpoint || '',
    httpMethod: service.httpMethod || 'POST',
    authMode: service.authMode || 'INHERIT',
    timeoutSeconds: service.timeoutSeconds || 30,
    retryCount: service.retryCount || 3,
    responseSuccessRule: service.responseSuccessRule || '',
    enabled: service.enabled
  })
  serviceDialogVisible.value = true
}

const saveService = async () => {
  saving.value = true
  try {
    const saved = editingServiceId.value
      ? await updateIntegrationService(editingServiceId.value, serviceForm)
      : await createIntegrationService(serviceForm)
    serviceDialogVisible.value = false
    selectedSystemCode.value = saved.systemCode
    ElMessage.success(editingServiceId.value ? '接口服务已更新' : '接口服务已新增')
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存接口服务失败')
  } finally {
    saving.value = false
  }
}

const deleteService = async (service: DownstreamService) => {
  try {
    await ElMessageBox.confirm(
      `确认删除接口服务「${service.serviceName}」？删除后不可恢复；如已被配置向导引用，需要先解除绑定或停用。`,
      '删除接口服务',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await deleteIntegrationService(service.id)
    ElMessage.success('接口服务已删除')
    if (selectedService.value?.id === service.id) {
      serviceDrawerVisible.value = false
      selectedService.value = null
    }
    await loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除接口服务失败')
  }
}

const openServiceDetail = (service: DownstreamService) => {
  selectedService.value = service
  serviceDrawerVisible.value = true
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

const importServices = () => {
  ElMessage.info('批量导入接口清单将在后续版本接入，当前可通过新增接口服务逐条维护')
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
            <el-button type="primary" @click="openCreateSystem">新增系统</el-button>
          </div>
        </template>
        <el-empty v-if="!systems.length" description="暂无下游系统" />
        <div
          v-for="system in systems"
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
                <el-button :disabled="!selectedSystem" type="danger" @click="deleteSystem">删除系统</el-button>
                <el-button :disabled="!selectedSystem" type="primary" @click="openEditSystem">编辑系统</el-button>
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
                <el-button @click="importServices">导入接口清单</el-button>
                <el-button type="primary" :disabled="!selectedSystem" @click="openCreateService">新增接口服务</el-button>
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
            <el-table-column label="操作" width="300" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openServiceDetail(row)">详情</el-button>
                <el-button link type="primary" @click="openEditService(row)">编辑</el-button>
                <el-button link @click="testConnect(row)">测试</el-button>
                <el-button link :type="row.enabled ? 'danger' : 'success'" @click="toggleService(row)">
                  {{ row.enabled ? '停用' : '启用' }}
                </el-button>
                <el-button link type="danger" @click="deleteService(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>

    <el-dialog v-model="systemDialogVisible" :title="editingSystemId ? '编辑下游系统' : '新增下游系统'" width="620px">
      <el-form :model="systemForm" label-width="110px" class="form-grid">
        <el-form-item label="系统编码"><el-input v-model="systemForm.systemCode" placeholder="如 fund_ops" /></el-form-item>
        <el-form-item label="系统名称"><el-input v-model="systemForm.systemName" placeholder="如 运营业务系统" /></el-form-item>
        <el-form-item label="归属部门">
          <el-select v-model="systemForm.ownerDepartmentId" filterable clearable>
            <el-option label="运营部" value="运营部" />
            <el-option label="财务部" value="财务部" />
            <el-option label="产品部" value="产品部" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认鉴权">
          <el-select v-model="systemForm.defaultAuthMode">
            <el-option label="无鉴权" value="NONE" />
            <el-option label="Token" value="TOKEN" />
            <el-option label="签名" value="SIGN" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认超时"><el-input-number v-model="systemForm.defaultTimeoutSeconds" :min="1" :max="600" /></el-form-item>
        <el-form-item label="默认重试"><el-input-number v-model="systemForm.defaultRetryCount" :min="0" :max="20" /></el-form-item>
        <el-form-item label="系统状态">
          <el-select v-model="systemForm.status">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="systemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSystem">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="serviceDialogVisible" :title="editingServiceId ? '编辑接口服务' : '新增接口服务'" width="720px">
      <el-alert
        class="mb-12"
        title="第一版下游推送执行链路仅支持 HTTP JSON；微服务和 MQ 可后续扩展接入，当前不建议绑定到配置向导做真实验证。"
        type="warning"
        :closable="false"
      />
      <el-form :model="serviceForm" label-width="120px" class="form-grid">
        <el-form-item label="所属系统">
          <el-select v-model="serviceForm.systemId" filterable>
            <el-option v-for="system in systems" :key="system.id" :label="`${system.systemName} (${system.systemCode})`" :value="system.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务编码"><el-input v-model="serviceForm.serviceCode" placeholder="如 fund_ops_result_receive" /></el-form-item>
        <el-form-item label="服务名称"><el-input v-model="serviceForm.serviceName" placeholder="如 接收提取结果服务" /></el-form-item>
        <el-form-item label="用途"><el-input v-model="serviceForm.purpose" placeholder="如 结果推送" /></el-form-item>
        <el-form-item label="服务类型">
          <el-select v-model="serviceForm.serviceType">
            <el-option label="HTTP" value="HTTP" />
            <el-option label="微服务（后续接入）" value="MICROSERVICE" disabled />
            <el-option label="MQ（后续接入）" value="MQ" disabled />
          </el-select>
        </el-form-item>
        <el-form-item label="请求方式">
          <el-select v-model="serviceForm.httpMethod">
            <el-option label="POST" value="POST" />
            <el-option label="GET" value="GET" />
            <el-option label="PUT" value="PUT" />
            <el-option label="-" value="-" />
          </el-select>
        </el-form-item>
        <el-form-item label="接口地址" class="wide"><el-input v-model="serviceForm.endpoint" placeholder="HTTP URL，如 http://host/api/result/receive" /></el-form-item>
        <el-form-item label="鉴权方式">
          <el-select v-model="serviceForm.authMode">
            <el-option label="继承系统默认" value="INHERIT" />
            <el-option label="无鉴权" value="NONE" />
            <el-option label="Token" value="TOKEN" />
            <el-option label="签名" value="SIGN" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态"><el-switch v-model="serviceForm.enabled" /></el-form-item>
        <el-form-item label="超时时间"><el-input-number v-model="serviceForm.timeoutSeconds" :min="1" :max="600" /></el-form-item>
        <el-form-item label="重试次数"><el-input-number v-model="serviceForm.retryCount" :min="0" :max="20" /></el-form-item>
        <el-form-item label="成功判断" class="wide"><el-input v-model="serviceForm.responseSuccessRule" placeholder="如 httpStatus in [200,202] && body.code == 0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="serviceDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveService">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="serviceDrawerVisible" title="接口服务详情" size="680px">
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
        <div class="mt-12">
          <el-button type="primary" @click="openEditService(selectedService)">编辑接口服务</el-button>
          <el-button type="danger" @click="deleteService(selectedService)">删除接口服务</el-button>
        </div>
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
