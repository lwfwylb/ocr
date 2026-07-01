<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  downstreamServices,
  downstreamSystems,
  type DownstreamService,
  type DownstreamSystem,
  type PushMethod
} from '../mock/data'

const systems = ref<DownstreamSystem[]>(downstreamSystems.map((item) => ({ ...item })))
const services = ref<DownstreamService[]>(downstreamServices.map((item) => ({ ...item })))
const selectedSystemCode = ref(systems.value[0]?.systemCode || '')
const drawerVisible = ref(false)
const selectedService = ref<DownstreamService | null>(null)
const query = reactive({
  keyword: '',
  departments: [] as string[],
  methods: [] as PushMethod[],
  enabled: ''
})

const methodMap: Record<PushMethod, { label: string; type: 'primary' | 'success' | 'warning' }> = {
  HTTP: { label: 'HTTP', type: 'primary' },
  MICROSERVICE: { label: '微服务', type: 'success' },
  MQ: { label: 'MQ', type: 'warning' }
}

const serviceCountBySystem = computed(() => {
  return services.value.reduce<Record<string, number>>((map, service) => {
    map[service.systemCode] = (map[service.systemCode] || 0) + 1
    return map
  }, {})
})

const filteredSystems = computed(() => {
  return systems.value.filter((system) => {
    const systemServices = services.value.filter((service) => service.systemCode === system.systemCode)
    const keywordMatched =
      !query.keyword ||
      system.systemName.includes(query.keyword) ||
      system.systemCode.includes(query.keyword) ||
      systemServices.some((service) => service.serviceName.includes(query.keyword) || service.endpoint.includes(query.keyword))
    const departmentMatched = query.departments.length === 0 || query.departments.includes(system.ownerDepartment)
    const methodMatched = query.methods.length === 0 || systemServices.some((service) => query.methods.includes(service.serviceType))
    const enabledMatched = query.enabled === '' || String(system.enabled) === query.enabled
    return keywordMatched && departmentMatched && methodMatched && enabledMatched
  })
})

const selectedSystem = computed(() => systems.value.find((system) => system.systemCode === selectedSystemCode.value) || systems.value[0])
const selectedServices = computed(() => services.value.filter((service) => service.systemCode === selectedSystem.value?.systemCode))
const enabledSystemCount = computed(() => systems.value.filter((system) => system.enabled).length)
const enabledServiceCount = computed(() => services.value.filter((service) => service.enabled).length)
const avgSuccessRate = computed(() => {
  const total = services.value.reduce((sum, item) => sum + item.successRate, 0)
  return Math.round(total / services.value.length)
})

const selectSystem = (system: DownstreamSystem) => {
  selectedSystemCode.value = system.systemCode
}

const openService = (service: DownstreamService) => {
  selectedService.value = service
  drawerVisible.value = true
}

const testConnect = (service: DownstreamService) => {
  ElMessage.success(`${service.systemName} / ${service.serviceName} 连接测试通过`)
}

const toggleSystem = (system: DownstreamSystem) => {
  system.enabled = !system.enabled
  ElMessage.success(system.enabled ? '已启用下游系统' : '已停用下游系统')
}

const toggleService = (service: DownstreamService) => {
  service.enabled = !service.enabled
  ElMessage.success(service.enabled ? '已启用接口服务' : '已停用接口服务')
}

const resetQuery = () => {
  query.keyword = ''
  query.departments = []
  query.methods = []
  query.enabled = ''
}
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>下游系统</span><strong>{{ systems.length }}</strong><em>系统级治理对象</em></el-card>
      <el-card shadow="never" class="metric-card"><span>接口服务</span><strong>{{ services.length }}</strong><em>真实推送目标</em></el-card>
      <el-card shadow="never" class="metric-card"><span>启用服务</span><strong>{{ enabledServiceCount }}</strong><em>{{ enabledSystemCount }} 个系统启用</em></el-card>
      <el-card shadow="never" class="metric-card"><span>HTTP/微服务/MQ</span><strong>{{ services.filter((s) => s.serviceType === 'HTTP').length }}/{{ services.filter((s) => s.serviceType === 'MICROSERVICE').length }}/{{ services.filter((s) => s.serviceType === 'MQ').length }}</strong><em>服务类型</em></el-card>
      <el-card shadow="never" class="metric-card"><span>成功率</span><strong>{{ avgSuccessRate }}%</strong><em>近 7 日模拟</em></el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="系统/服务/地址" />
        </el-form-item>
        <el-form-item label="归属部门">
          <el-select v-model="query.departments" multiple filterable clearable collapse-tags collapse-tags-tooltip placeholder="全部">
            <el-option label="运营部" value="运营部" />
            <el-option label="财务部" value="财务部" />
            <el-option label="产品部" value="产品部" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务方式">
          <el-select v-model="query.methods" multiple filterable clearable collapse-tags placeholder="全部">
            <el-option label="HTTP" value="HTTP" />
            <el-option label="微服务" value="MICROSERVICE" />
            <el-option label="MQ" value="MQ" />
          </el-select>
        </el-form-item>
        <el-form-item label="系统状态">
          <el-select v-model="query.enabled" clearable placeholder="全部">
            <el-option label="启用" value="true" />
            <el-option label="停用" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="integration-layout">
      <el-card shadow="never" class="integration-sidebar">
        <template #header>
          <div class="card-header">
            <span>下游系统</span>
            <el-button type="primary">新增系统</el-button>
          </div>
        </template>
        <div
          v-for="system in filteredSystems"
          :key="system.systemCode"
          class="integration-system-item"
          :class="{ active: selectedSystemCode === system.systemCode }"
          @click="selectSystem(system)"
        >
          <strong>{{ system.systemName }}</strong>
          <span>{{ system.systemCode }} / {{ system.ownerDepartment }}</span>
          <em>{{ serviceCountBySystem[system.systemCode] || 0 }} 个接口服务</em>
          <el-tag :type="system.enabled ? 'success' : 'info'">{{ system.enabled ? '启用' : '停用' }}</el-tag>
        </div>
      </el-card>

      <div class="page-stack">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>系统基础信息</span>
              <div>
                <el-button @click="toggleSystem(selectedSystem)">{{ selectedSystem?.enabled ? '停用系统' : '启用系统' }}</el-button>
                <el-button type="primary">编辑系统</el-button>
              </div>
            </div>
          </template>
          <el-descriptions v-if="selectedSystem" :column="4" border>
            <el-descriptions-item label="系统名称">{{ selectedSystem.systemName }}</el-descriptions-item>
            <el-descriptions-item label="系统编码">{{ selectedSystem.systemCode }}</el-descriptions-item>
            <el-descriptions-item label="归属部门">{{ selectedSystem.ownerDepartment }}</el-descriptions-item>
            <el-descriptions-item label="默认鉴权">{{ selectedSystem.authType }}</el-descriptions-item>
            <el-descriptions-item label="默认超时">{{ selectedSystem.timeoutSeconds }}s</el-descriptions-item>
            <el-descriptions-item label="默认重试">{{ selectedSystem.retryCount }} 次</el-descriptions-item>
            <el-descriptions-item label="服务数">{{ selectedServices.length }}</el-descriptions-item>
            <el-descriptions-item label="成功率">{{ selectedSystem.successRate }}%</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>接口服务</span>
              <div>
                <el-button>导入接口清单</el-button>
                <el-button type="primary">新增接口服务</el-button>
              </div>
            </div>
          </template>
          <el-table :data="selectedServices" stripe>
            <el-table-column prop="serviceName" label="服务名称" min-width="160" fixed />
            <el-table-column prop="serviceCode" label="服务编码" min-width="180" />
            <el-table-column prop="purpose" label="用途" width="90" />
            <el-table-column label="方式" width="90">
              <template #default="{ row }">
                <el-tag :type="methodMap[row.serviceType as PushMethod].type">{{ methodMap[row.serviceType as PushMethod].label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="endpoint" label="地址/Topic/方法" min-width="280" />
            <el-table-column prop="boundConfigCount" label="绑定场景" width="90" />
            <el-table-column label="成功率" width="90">
              <template #default="{ row }">
                <el-progress :percentage="row.successRate" :stroke-width="6" :show-text="false" />
                <span class="muted">{{ row.successRate }}%</span>
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
          <el-descriptions-item label="服务用途">{{ selectedService.purpose }}</el-descriptions-item>
          <el-descriptions-item label="服务类型">{{ methodMap[selectedService.serviceType].label }}</el-descriptions-item>
          <el-descriptions-item label="地址/Topic/方法">{{ selectedService.endpoint }}</el-descriptions-item>
          <el-descriptions-item label="请求方式">{{ selectedService.httpMethod }}</el-descriptions-item>
          <el-descriptions-item label="鉴权方式">{{ selectedService.authType === 'INHERIT' ? '继承系统默认' : selectedService.authType }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="section-title">调用治理</h3>
        <el-form label-width="120px" class="form-grid">
          <el-form-item label="超时时间"><el-input-number v-model="selectedService.timeoutSeconds" :min="1" /></el-form-item>
          <el-form-item label="重试次数"><el-input-number v-model="selectedService.retryCount" :min="0" /></el-form-item>
          <el-form-item label="幂等规则" class="wide"><el-input v-model="selectedService.idempotentRule" /></el-form-item>
          <el-form-item label="成功判断" class="wide"><el-input v-model="selectedService.responseSuccessRule" /></el-form-item>
        </el-form>
        <el-alert
          class="mt-12"
          title="配置向导绑定的是具体接口服务，而不是下游系统本身；同一个系统可以提供多个服务，分别承载结果推送、附件推送、状态回调或批量同步。"
          type="info"
          :closable="false"
        />
      </template>
    </el-drawer>
  </div>
</template>
