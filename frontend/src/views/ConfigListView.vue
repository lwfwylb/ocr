<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusTag from '../components/StatusTag.vue'

type ConfigStatus = 'DRAFT' | 'TESTING' | 'PUBLISHED' | 'DISABLED'

interface ConfigItem {
  configId: string
  configName: string
  category: string
  subCategory: string
  templateType: string
  documentType: string
  department: string
  version: string
  status: ConfigStatus
  parseEngine: string
  targetTable: string
  mappingProfile: string
  confidenceThreshold: number
  updatedAt: string
  updatedBy: string
}

const router = useRouter()
const drawerVisible = ref(false)
const selectedConfig = ref<ConfigItem | null>(null)
const query = reactive({
  keyword: '',
  category: '',
  subCategory: '',
  documentType: '',
  department: '',
  status: ''
})

const configs = ref<ConfigItem[]>([
  {
    configId: 'CFG-20260628-001',
    configName: '划款指令-运营部-提取配置',
    category: '资金业务',
    subCategory: '划款指令',
    templateType: '通用划款指令模板',
    documentType: '划款指令',
    department: '运营部',
    version: 'V1.3',
    status: 'PUBLISHED',
    parseEngine: 'PaddleOCR-VL-1.6',
    targetTable: 'ext_fund_business_result',
    mappingProfile: '划款指令-资金结果表映射',
    confidenceThreshold: 0.9,
    updatedAt: '2026-06-28 21:18:00',
    updatedBy: '王老师'
  },
  {
    configId: 'CFG-20260628-002',
    configName: '银行回单-资金结果配置',
    category: '资金业务',
    subCategory: '银行回单',
    templateType: '通用银行回单模板',
    documentType: '银行回单',
    department: '财务部',
    version: 'V1.1',
    status: 'PUBLISHED',
    parseEngine: 'MinerU',
    targetTable: 'ext_fund_business_result',
    mappingProfile: '银行回单-资金结果表映射',
    confidenceThreshold: 0.9,
    updatedAt: '2026-06-28 18:40:00',
    updatedBy: '李老师'
  },
  {
    configId: 'CFG-20260628-003',
    configName: '开户资料-客户信息配置',
    category: '客户业务',
    subCategory: '开户资料',
    templateType: '机构客户开户资料',
    documentType: '开户资料',
    department: '运营部',
    version: 'V0.8',
    status: 'DRAFT',
    parseEngine: 'PaddleOCR-VL-1.6',
    targetTable: 'ext_customer_open_account',
    mappingProfile: '开户资料-客户表映射',
    confidenceThreshold: 0.88,
    updatedAt: '2026-06-28 16:12:00',
    updatedBy: '赵老师'
  },
  {
    configId: 'CFG-20260628-004',
    configName: '产品合同-条款提取配置',
    category: '产品业务',
    subCategory: '产品合同',
    templateType: '通用产品合同模板',
    documentType: '产品合同',
    department: '产品部',
    version: 'V0.3',
    status: 'TESTING',
    parseEngine: 'PDFBox + LLM',
    targetTable: 'ext_product_contract_terms',
    mappingProfile: '产品合同-条款表映射',
    confidenceThreshold: 0.85,
    updatedAt: '2026-06-28 14:05:00',
    updatedBy: '陈老师'
  },
  {
    configId: 'CFG-20260627-005',
    configName: '旧版划款指令配置',
    category: '资金业务',
    subCategory: '划款指令',
    templateType: '旧版划款指令模板',
    documentType: '划款指令',
    department: '运营部',
    version: 'V0.9',
    status: 'DISABLED',
    parseEngine: 'PaddleOCR',
    targetTable: 'ext_payment_instruction',
    mappingProfile: '旧版独立表映射',
    confidenceThreshold: 0.8,
    updatedAt: '2026-06-27 19:42:00',
    updatedBy: '系统管理员'
  }
])

const filteredConfigs = computed(() => {
  return configs.value.filter((config) => {
    const keywordMatched =
      !query.keyword ||
      config.configName.includes(query.keyword) ||
      config.configId.includes(query.keyword) ||
      config.mappingProfile.includes(query.keyword) ||
      config.templateType.includes(query.keyword)
    const categoryMatched = !query.category || config.category === query.category
    const subCategoryMatched = !query.subCategory || config.subCategory === query.subCategory
    const typeMatched = !query.documentType || config.documentType === query.documentType
    const departmentMatched = !query.department || config.department === query.department
    const statusMatched = !query.status || config.status === query.status
    return keywordMatched && categoryMatched && subCategoryMatched && typeMatched && departmentMatched && statusMatched
  })
})

const statusMap: Record<ConfigStatus, { label: string; type: 'success' | 'warning' | 'info' | 'danger' }> = {
  DRAFT: { label: '草稿', type: 'info' },
  TESTING: { label: '验证中', type: 'warning' },
  PUBLISHED: { label: '已发布', type: 'success' },
  DISABLED: { label: '已停用', type: 'danger' }
}

const openDetail = (config: ConfigItem) => {
  selectedConfig.value = config
  drawerVisible.value = true
}

const copyVersion = (config: ConfigItem) => {
  const copy: ConfigItem = {
    ...config,
    configId: `CFG-20260629-${String(configs.value.length + 1).padStart(3, '0')}`,
    configName: `${config.configName}-副本`,
    version: 'DRAFT',
    status: 'DRAFT',
    updatedAt: '2026-06-29 09:00:00',
    updatedBy: '当前用户'
  }
  configs.value.unshift(copy)
  ElMessage.success('已复制为草稿版本')
}

const publishConfig = async (config: ConfigItem) => {
  await ElMessageBox.confirm('发布后新任务将使用该配置版本，历史任务不受影响。确认发布？', '发布配置', {
    type: 'warning'
  })
  config.status = 'PUBLISHED'
  config.version = config.version.startsWith('V') ? config.version : 'V1.0'
  ElMessage.success('配置已发布')
}

const disableConfig = async (config: ConfigItem) => {
  await ElMessageBox.confirm('停用后该配置不会再被新任务匹配。确认停用？', '停用配置', {
    type: 'warning'
  })
  config.status = 'DISABLED'
  ElMessage.success('配置已停用')
}

const resetQuery = () => {
  query.keyword = ''
  query.category = ''
  query.subCategory = ''
  query.documentType = ''
  query.department = ''
  query.status = ''
}
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
        <strong>{{ configs.filter((item) => item.status === 'PUBLISHED').length }}</strong>
        <em>可被任务匹配</em>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>草稿</span>
        <strong>{{ configs.filter((item) => item.status === 'DRAFT').length }}</strong>
        <em>待验证发布</em>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>验证中</span>
        <strong>{{ configs.filter((item) => item.status === 'TESTING').length }}</strong>
        <em>样本测试</em>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>复用结果表</span>
        <strong>2</strong>
        <em>多映射方案</em>
      </el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="配置名/编号/映射方案" clearable />
        </el-form-item>
        <el-form-item label="业务分类">
          <el-select v-model="query.category" clearable filterable placeholder="全部">
            <el-option label="资金业务" value="资金业务" />
            <el-option label="基金交易" value="基金交易" />
            <el-option label="客户业务" value="客户业务" />
            <el-option label="产品业务" value="产品业务" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务子类">
          <el-select v-model="query.subCategory" clearable filterable placeholder="全部">
            <el-option label="划款指令" value="划款指令" />
            <el-option label="银行回单" value="银行回单" />
            <el-option label="基金申购" value="基金申购" />
            <el-option label="基金赎回" value="基金赎回" />
            <el-option label="开户资料" value="开户资料" />
          </el-select>
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
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="验证中" value="TESTING" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已停用" value="DISABLED" />
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
          <span>配置列表</span>
          <div>
            <el-button @click="router.push('/sandbox')">配置验证</el-button>
            <el-button type="primary" @click="router.push('/configs/wizard')">新建配置</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filteredConfigs" stripe>
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
        <el-table-column prop="parseEngine" label="解析引擎" min-width="150" />
        <el-table-column prop="targetTable" label="目标表" min-width="190" />
        <el-table-column prop="mappingProfile" label="映射方案" min-width="190" />
        <el-table-column label="置信度" width="90">
          <template #default="{ row }">{{ Math.round(row.confidenceThreshold * 100) }}%</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="150" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="router.push('/configs/wizard')">编辑</el-button>
            <el-button link @click="copyVersion(row)">复制</el-button>
            <el-button v-if="row.status !== 'PUBLISHED'" link type="success" @click="publishConfig(row)">发布</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="danger" @click="disableConfig(row)">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="配置详情" size="560px">
      <template v-if="selectedConfig">
        <el-descriptions :column="1" border>
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
        <el-timeline>
          <el-timeline-item timestamp="2026-06-29 09:00:00">复制历史版本为草稿</el-timeline-item>
          <el-timeline-item timestamp="2026-06-28 21:18:00">发布 {{ selectedConfig.version }}</el-timeline-item>
          <el-timeline-item timestamp="2026-06-28 20:40:00">样本文档验证通过</el-timeline-item>
        </el-timeline>

        <h3 class="section-title">字段映射摘要</h3>
        <el-table
          :data="[
            { extractField: 'payer_name', targetColumn: 'payer_name', transform: '原值写入' },
            { extractField: 'payee_account', targetColumn: 'counterparty_account', transform: '脱敏后写入' },
            { extractField: 'amount', targetColumn: 'business_amount', transform: '金额标准化' }
          ]"
        >
          <el-table-column prop="extractField" label="提取字段" />
          <el-table-column prop="targetColumn" label="目标字段" />
          <el-table-column prop="transform" label="加工逻辑" />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>
