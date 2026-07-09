<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  copyExtractConfig,
  deleteExtractConfigDraft,
  disableExtractConfig,
  getConfigOptions,
  getExtractConfigDetail,
  listExtractConfigVersions,
  listExtractConfigs,
  publishExtractConfig,
  validateExtractConfig,
  type ConfigDetail,
  type ConfigOptions,
  type ConfigSummary
} from '../api/config'

type ConfigStatus = 'DRAFT' | 'TESTING' | 'PUBLISHED' | 'DISABLED'

interface ConfigItem {
  configId: string
  configCode: string
  configName: string
  category: string
  subCategory: string
  templateType: string
  documentType: string
  department: string
  version: string
  currentEffective: boolean
  status: ConfigStatus
  parseEngine: string
  targetTable: string
  mappingProfile: string
  confidenceThreshold: number
  publishedAt: string
  updatedAt: string
  updatedBy: string
}

const router = useRouter()
const drawerVisible = ref(false)
const versionDrawerVisible = ref(false)
const selectedConfig = ref<ConfigItem | null>(null)
const selectedDetail = ref<ConfigDetail | null>(null)
const selectedVersionConfig = ref<ConfigItem | null>(null)
const versionRows = ref<ConfigItem[]>([])
const loading = ref(false)
const detailLoading = ref(false)
const versionLoading = ref(false)
const options = ref<ConfigOptions>({
  departments: [],
  roles: [],
  categories: [],
  ocrEngines: [],
  resultTables: [],
  downstreamServices: []
})
const query = reactive({
  keyword: '',
  category: '',
  subCategory: '',
  templateType: '',
  documentType: '',
  department: '',
  status: ''
})

const configs = ref<ConfigItem[]>([])
const filteredConfigs = computed(() => configs.value)
const publishedCount = computed(() => configs.value.filter((item) => item.status === 'PUBLISHED').length)
const draftCount = computed(() => configs.value.filter((item) => item.status === 'DRAFT').length)
const testingCount = computed(() => configs.value.filter((item) => item.status === 'TESTING').length)
const reusedTableCount = computed(() => {
  const tableCounts = configs.value.reduce<Record<string, number>>((counts, item) => {
    if (item.targetTable && item.targetTable !== '-') counts[item.targetTable] = (counts[item.targetTable] || 0) + 1
    return counts
  }, {})
  return Object.values(tableCounts).filter((count) => count > 1).length
})

const statusMap: Record<ConfigStatus, { label: string; type: 'success' | 'warning' | 'info' | 'danger' }> = {
  DRAFT: { label: '草稿', type: 'info' },
  TESTING: { label: '验证中', type: 'warning' },
  PUBLISHED: { label: '已发布', type: 'success' },
  DISABLED: { label: '已停用', type: 'danger' }
}

const toConfigItem = (item: ConfigSummary): ConfigItem => ({
  configId: item.id,
  configCode: item.configCode,
  configName: item.configName,
  category: item.category || '',
  subCategory: item.subCategory || '',
  templateType: item.templateType || '',
  documentType: item.documentType || '',
  department: item.departmentId || '',
  version: `V${item.version || 1}`,
  currentEffective: Boolean(item.currentEffective),
  status: item.status,
  parseEngine: item.parseEngine || '-',
  targetTable: item.targetTable || '-',
  mappingProfile: item.mappingProfile || '-',
  confidenceThreshold: item.confidenceThreshold ?? 0.9,
  publishedAt: item.publishedAt || '',
  updatedAt: item.updatedAt || '',
  updatedBy: item.updatedBy || item.createdBy || 'system'
})

const categoryOptions = computed(() => options.value.categories || [])
const subCategoryOptions = computed(() => {
  const matchedCategory = categoryOptions.value.find((item) => item.value === query.category)
  return (matchedCategory?.children || []) as Array<Record<string, any>>
})
const documentTypeOptions = computed(() => {
  const values = new Set<string>()
  categoryOptions.value.forEach((category) => {
    ;(category.children || []).forEach((child: Record<string, any>) => values.add(child.value))
  })
  return Array.from(values).map((value) => ({ label: value, value }))
})
const detailFieldMappings = computed(() => {
  const payload = selectedDetail.value?.payload
  if (!payload?.fieldMappings?.length) return []
  return payload.fieldMappings.map((item: any) => ({
    extractField: item.extractFieldCode,
    targetColumn: item.targetColumn,
    transform: item.requiredForStorage ? '必填入库' : '普通映射'
  }))
})
const detailTimeline = computed(() => {
  const summary = selectedDetail.value?.summary
  if (!summary) return []
  return [
    { timestamp: summary.updatedAt, content: `更新配置，状态：${statusMap[summary.status]?.label || summary.status}` },
    { timestamp: summary.publishedAt, content: `发布 ${summary.version ? `V${summary.version}` : ''}` },
    { timestamp: summary.createdAt, content: '创建配置草稿' }
  ].filter((item) => item.timestamp)
})
const versionTimeline = computed(() =>
  versionRows.value.map((item) => ({
    timestamp: item.publishedAt || item.updatedAt,
    content: `${item.version} ${statusMap[item.status]?.label || item.status}：${item.mappingProfile}`
  }))
)

const loadConfigs = async () => {
  loading.value = true
  try {
    const rows = await listExtractConfigs({
      keyword: query.keyword,
      status: query.status,
      departmentId: query.department,
      documentType: query.documentType,
      category: query.category,
      subCategory: query.subCategory,
      templateType: query.templateType
    })
    configs.value = rows.map(toConfigItem)
  } catch (error) {
    configs.value = []
    ElMessage.error(error instanceof Error ? error.message : '配置列表加载失败')
  } finally {
    loading.value = false
  }
}

const loadOptions = async () => {
  try {
    options.value = await getConfigOptions()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '配置选项加载失败')
  }
}

const openDetail = async (config: ConfigItem) => {
  drawerVisible.value = true
  detailLoading.value = true
  selectedConfig.value = config
  selectedDetail.value = null
  try {
    selectedDetail.value = await getExtractConfigDetail(config.configId)
    selectedConfig.value = toConfigItem(selectedDetail.value.summary)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '配置详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

const copyVersion = async (config: ConfigItem) => {
  try {
    const result = await copyExtractConfig(config.configId)
    const copied = toConfigItem(result.summary)
    configs.value.unshift(copied)
    if (versionDrawerVisible.value && selectedVersionConfig.value?.configCode === copied.configCode) {
      await openVersions(copied, false)
    }
    ElMessage.success(`已复制为 ${copied.version} 草稿`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '复制失败')
  }
}

const publishConfig = async (config: ConfigItem) => {
  try {
    await ElMessageBox.confirm('发布后新任务将使用该配置版本，历史任务不受影响。确认发布？', '发布配置', {
      type: 'warning'
    })
    const result = await publishExtractConfig(config.configId)
    Object.assign(config, toConfigItem(result.summary))
    await loadConfigs()
    if (versionDrawerVisible.value && selectedVersionConfig.value?.configCode === config.configCode) {
      await openVersions(config, false)
    }
    ElMessage.success('配置已发布')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '发布失败')
  }
}

const openVersions = async (config: ConfigItem, showDrawer = true) => {
  if (showDrawer) {
    versionDrawerVisible.value = true
    selectedVersionConfig.value = config
  }
  versionLoading.value = true
  try {
    const rows = await listExtractConfigVersions(config.configId)
    versionRows.value = rows.map(toConfigItem)
  } catch (error) {
    versionRows.value = []
    ElMessage.error(error instanceof Error ? error.message : '版本列表加载失败')
  } finally {
    versionLoading.value = false
  }
}

const disableConfig = async (config: ConfigItem) => {
  try {
    await ElMessageBox.confirm('停用后该配置不会再被新任务匹配。确认停用？', '停用配置', {
      type: 'warning'
    })
    const result = await disableExtractConfig(config.configId)
    Object.assign(config, toConfigItem(result.summary))
    ElMessage.success('配置已停用')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '停用失败')
  }
}

const deleteDraftConfig = async (config: ConfigItem) => {
  try {
    await ElMessageBox.confirm(
      `确认删除该草稿版本？<br />配置编码：${config.configCode}<br />版本：${config.version}<br />删除后不会影响已发布版本和历史任务。`,
      '删除草稿版本',
      {
        type: 'warning',
        dangerouslyUseHTMLString: true,
        confirmButtonText: '确认删除',
        cancelButtonText: '取消'
      }
    )
    await deleteExtractConfigDraft(config.configId)
    configs.value = configs.value.filter((item) => item.configId !== config.configId)
    if (versionDrawerVisible.value && selectedVersionConfig.value?.configCode === config.configCode) {
      await openVersions(config, false)
    }
    ElMessage.success('草稿版本已删除')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

const validateConfig = async (config: ConfigItem) => {
  try {
    const result = await validateExtractConfig(config.configId)
    if (result.passed) {
      ElMessage.success(result.message)
    } else {
      ElMessageBox.alert(result.errors.map((error, index) => `${index + 1}. ${error}`).join('<br />'), result.message, {
        dangerouslyUseHTMLString: true,
        confirmButtonText: '我知道了'
      })
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '验证失败')
  }
}

const editConfig = (config: ConfigItem) => {
  router.push({ path: '/configs/wizard', query: { id: config.configId } })
}

const resetQuery = () => {
  query.keyword = ''
  query.category = ''
  query.subCategory = ''
  query.templateType = ''
  query.documentType = ''
  query.department = ''
  query.status = ''
  loadConfigs()
}

onMounted(() => {
  loadOptions()
  loadConfigs()
})
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card">
        <span>全部配置</span>
        <strong>{{ configs.length }}</strong>
        <em>按文档类型管理</em>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>已发布</span>
        <strong>{{ publishedCount }}</strong>
        <em>可被任务匹配</em>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>草稿</span>
        <strong>{{ draftCount }}</strong>
        <em>待验证发布</em>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>验证中</span>
        <strong>{{ testingCount }}</strong>
        <em>样本测试</em>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>复用结果表</span>
        <strong>{{ reusedTableCount }}</strong>
        <em>多映射方案</em>
      </el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="配置名/编号/映射方案" clearable />
        </el-form-item>
        <el-form-item label="业务分类">
          <el-select v-model="query.category" clearable filterable placeholder="全部" @change="query.subCategory = ''">
            <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务子类">
          <el-select v-model="query.subCategory" clearable filterable placeholder="全部">
            <el-option v-for="item in subCategoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="query.documentType" clearable filterable placeholder="全部">
            <el-option v-for="item in documentTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="query.department" clearable placeholder="全部">
            <el-option v-for="item in options.departments" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="验证中" value="TESTING" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadConfigs">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>配置列表</span>
          <div>
            <el-button @click="router.push('/sandbox')">配置验证</el-button>
            <el-button type="primary" @click="router.push('/configs/wizard')">新建配置</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="filteredConfigs" stripe>
        <el-table-column prop="configCode" label="配置编码" min-width="170" fixed />
        <el-table-column prop="configName" label="配置名称" min-width="220" fixed />
        <el-table-column prop="category" label="业务分类" width="100" />
        <el-table-column prop="subCategory" label="业务子类" width="100" />
        <el-table-column prop="templateType" label="模板/表单类型" min-width="150" />
        <el-table-column prop="documentType" label="文档类型" width="100" />
        <el-table-column prop="department" label="部门" width="90" />
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column label="状态" width="92">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status as ConfigStatus].type">
              {{ statusMap[row.status as ConfigStatus].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.currentEffective" type="success">生效中</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="parseEngine" label="解析引擎" min-width="150" />
        <el-table-column prop="targetTable" label="目标表" min-width="190" />
        <el-table-column prop="mappingProfile" label="映射方案" min-width="190" />
        <el-table-column label="置信度" width="90">
          <template #default="{ row }">{{ Math.round(row.confidenceThreshold * 100) }}%</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="150" />
        <el-table-column label="操作" width="310" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openVersions(row)">版本</el-button>
            <el-button link type="primary" @click="editConfig(row)">编辑</el-button>
            <el-button link type="primary" @click="validateConfig(row)">验证</el-button>
            <el-button v-if="row.status !== 'DRAFT' && row.status !== 'TESTING'" link @click="copyVersion(row)">复制新版本</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'TESTING'" link type="success" @click="publishConfig(row)">发布</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" @click="deleteDraftConfig(row)">删除</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="danger" @click="disableConfig(row)">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="配置详情" size="640px">
      <template v-if="selectedConfig">
        <el-descriptions v-loading="detailLoading" :column="1" border>
          <el-descriptions-item label="配置编号">{{ selectedConfig.configId }}</el-descriptions-item>
          <el-descriptions-item label="配置名称">{{ selectedConfig.configName }}</el-descriptions-item>
          <el-descriptions-item label="业务分类">{{ selectedConfig.category }}</el-descriptions-item>
          <el-descriptions-item label="业务子类">{{ selectedConfig.subCategory }}</el-descriptions-item>
          <el-descriptions-item label="模板/表单类型">{{ selectedConfig.templateType }}</el-descriptions-item>
          <el-descriptions-item label="文档类型">{{ selectedConfig.documentType }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ selectedConfig.version }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusMap[selectedConfig.status].type">{{ statusMap[selectedConfig.status].label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="解析引擎">{{ selectedConfig.parseEngine }}</el-descriptions-item>
          <el-descriptions-item label="目标表">{{ selectedConfig.targetTable }}</el-descriptions-item>
          <el-descriptions-item label="映射方案">{{ selectedConfig.mappingProfile }}</el-descriptions-item>
          <el-descriptions-item label="更新人">{{ selectedConfig.updatedBy }}</el-descriptions-item>
        </el-descriptions>

        <h3 class="section-title">配置版本流转</h3>
        <el-timeline v-if="detailTimeline.length">
          <el-timeline-item v-for="item in detailTimeline" :key="`${item.timestamp}-${item.content}`" :timestamp="item.timestamp">
            {{ item.content }}
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无版本流转记录" />

        <h3 class="section-title">字段映射摘要</h3>
        <el-table :data="detailFieldMappings">
          <el-table-column prop="extractField" label="提取字段" />
          <el-table-column prop="targetColumn" label="目标字段" />
          <el-table-column prop="transform" label="加工逻辑" />
        </el-table>
      </template>
    </el-drawer>

    <el-drawer v-model="versionDrawerVisible" title="配置版本管理" size="860px">
      <template v-if="selectedVersionConfig">
        <el-alert
          class="mb-12"
          type="info"
          :closable="false"
          title="同一配置编码下的多个版本共享一个配置族。已发布版本用于历史任务回溯，修改已发布配置时请先复制为新草稿版本。"
        />
        <el-descriptions :column="2" border class="mb-12">
          <el-descriptions-item label="配置编码">{{ selectedVersionConfig.configCode }}</el-descriptions-item>
          <el-descriptions-item label="配置名称">{{ selectedVersionConfig.configName }}</el-descriptions-item>
          <el-descriptions-item label="业务分类">{{ selectedVersionConfig.category }}</el-descriptions-item>
          <el-descriptions-item label="目标表">{{ selectedVersionConfig.targetTable }}</el-descriptions-item>
        </el-descriptions>
        <el-table v-loading="versionLoading" :data="versionRows" stripe>
          <el-table-column prop="version" label="版本" width="80" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusMap[row.status as ConfigStatus].type">
                {{ statusMap[row.status as ConfigStatus].label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="生效" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.currentEffective" type="success">生效中</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="mappingProfile" label="映射方案" min-width="180" />
          <el-table-column prop="parseEngine" label="解析引擎" min-width="140" />
          <el-table-column prop="updatedAt" label="更新时间" min-width="150" />
          <el-table-column prop="updatedBy" label="更新人" width="110" />
          <el-table-column label="操作" width="290" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-if="row.status === 'DRAFT' || row.status === 'TESTING'" link type="primary" @click="editConfig(row)">编辑</el-button>
              <el-button v-else link type="primary" @click="editConfig(row)">查看</el-button>
              <el-button v-if="row.status !== 'DRAFT' && row.status !== 'TESTING'" link @click="copyVersion(row)">复制新版本</el-button>
              <el-button v-if="row.status === 'DRAFT' || row.status === 'TESTING'" link type="success" @click="publishConfig(row)">发布</el-button>
              <el-button v-if="row.status === 'DRAFT'" link type="danger" @click="deleteDraftConfig(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <h3 class="section-title">版本流转</h3>
        <el-timeline v-if="versionTimeline.length">
          <el-timeline-item v-for="item in versionTimeline" :key="`${item.timestamp}-${item.content}`" :timestamp="item.timestamp">
            {{ item.content }}
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </div>
</template>
