<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createLlmModelConfig,
  disableLlmModelConfig,
  enableLlmModelConfig,
  pageLlmModelConfigs,
  setDefaultLlmModelConfig,
  testLlmModelConfig,
  updateLlmModelConfig,
  type LlmModelConfig,
  type LlmModelPayload,
  type LlmModelTestResult
} from '../api/model'
import { createTablePage, pageParams, resetPage } from '../composables/useTablePage'

const DEFAULT_SYSTEM_PROMPT = '你是一个严谨的要素提取助手。请只返回 JSON，不要输出解释、Markdown 或额外文本。'
const DEFAULT_USER_PROMPT = '请从文本中提取要素：payee_account_name（付款方名称）、business_date（回执日期，转为 yyyyMMdd 格式）。要求无法识别的字段返回 null，严格 JSON 格式输出。文本：付款方：张三，回执日期：2026-07-16'

const drawerVisible = ref(false)
const testDialogVisible = ref(false)
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const page = createTablePage(20)
const models = ref<LlmModelConfig[]>([])
const editingId = ref('')
const testingModel = ref<LlmModelConfig | null>(null)
const testResult = ref<LlmModelTestResult | null>(null)
const activeTestTab = ref('content')
const query = reactive({
  keyword: '',
  provider: '',
  status: ''
})
const form = reactive<LlmModelPayload>({
  modelCode: '',
  modelName: '',
  provider: '私有化模型平台',
  baseUrl: '',
  apiKeySecretRef: '',
  modelIdentifier: '',
  temperature: 0.1,
  maxTokens: 4096,
  timeoutSeconds: 120,
  retryCount: 1,
  jsonSchemaRequired: true,
  defaultModel: false,
  status: 'ENABLED',
  description: ''
})
const testForm = reactive({
  systemPrompt: DEFAULT_SYSTEM_PROMPT,
  userPrompt: DEFAULT_USER_PROMPT
})

const enabledCount = computed(() => models.value.filter((item) => item.status === 'ENABLED').length)
const defaultModel = computed(() => models.value.find((item) => item.defaultModel))
const providerOptions = computed(() => Array.from(new Set(models.value.map((item) => item.provider).filter(Boolean))))
const jsonSchemaRate = computed(() => {
  if (!models.value.length) return 0
  return Math.round((models.value.filter((item) => item.jsonSchemaRequired).length / models.value.length) * 100)
})
const passedModelCount = computed(() => models.value.filter((item) => item.status === 'ENABLED').length)

const resetForm = () => {
  editingId.value = ''
  Object.assign(form, {
    modelCode: '',
    modelName: '',
    provider: '私有化模型平台',
    baseUrl: '',
    apiKeySecretRef: '',
    modelIdentifier: '',
    temperature: 0.1,
    maxTokens: 4096,
    timeoutSeconds: 120,
    retryCount: 1,
    jsonSchemaRequired: true,
    defaultModel: false,
    status: 'ENABLED',
    description: ''
  })
}

const loadModels = async () => {
  loading.value = true
  try {
    const result = await pageLlmModelConfigs({ ...query, ...pageParams(page) })
    models.value = result.records
    page.total = result.total
  } catch (error) {
    models.value = []
    ElMessage.error(error instanceof Error ? error.message : 'LLM 配置加载失败')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  resetForm()
  drawerVisible.value = true
}

const openEdit = (model: LlmModelConfig) => {
  editingId.value = model.id
  Object.assign(form, {
    modelCode: model.modelCode,
    modelName: model.modelName,
    provider: model.provider,
    baseUrl: model.baseUrl,
    apiKeySecretRef: model.apiKeySecretRef || '',
    modelIdentifier: model.modelIdentifier,
    temperature: Number(model.temperature ?? 0.1),
    maxTokens: model.maxTokens,
    timeoutSeconds: model.timeoutSeconds,
    retryCount: model.retryCount,
    jsonSchemaRequired: model.jsonSchemaRequired,
    defaultModel: model.defaultModel,
    status: model.status,
    description: model.description || ''
  })
  drawerVisible.value = true
}

const saveModel = async () => {
  saving.value = true
  try {
    const result = editingId.value
      ? await updateLlmModelConfig(editingId.value, form)
      : await createLlmModelConfig(form)
    ElMessage.success(`LLM 配置已保存：${result.modelName}`)
    drawerVisible.value = false
    await loadModels()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'LLM 配置保存失败')
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (model: LlmModelConfig) => {
  try {
    if (model.status === 'ENABLED') {
      await ElMessageBox.confirm('停用后配置向导和任务执行不再选择该模型。确认停用？', '停用 LLM 配置', { type: 'warning' })
      await disableLlmModelConfig(model.id)
      ElMessage.success('模型已停用')
    } else {
      await enableLlmModelConfig(model.id)
      ElMessage.success('模型已启用')
    }
    await loadModels()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '状态更新失败')
  }
}

const setDefault = async (model: LlmModelConfig) => {
  try {
    await setDefaultLlmModelConfig(model.id)
    ElMessage.success(`已设置 ${model.modelName} 为默认模型`)
    await loadModels()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '设置默认模型失败')
  }
}

const openTestDialog = (model: LlmModelConfig) => {
  testingModel.value = model
  testResult.value = null
  activeTestTab.value = 'content'
  testForm.systemPrompt = DEFAULT_SYSTEM_PROMPT
  testForm.userPrompt = DEFAULT_USER_PROMPT
  testDialogVisible.value = true
}

const runTestModel = async () => {
  if (!testingModel.value) return
  testing.value = true
  try {
    const result = await testLlmModelConfig(testingModel.value.id, {
      systemPrompt: testForm.systemPrompt,
      userPrompt: testForm.userPrompt
    })
    testResult.value = result
    if (result.passed) {
      ElMessage.success(result.message)
    } else {
      ElMessage.warning(result.message)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '测试调用失败')
  } finally {
    testing.value = false
  }
}

const searchModels = () => {
  resetPage(page)
  loadModels()
}

const handlePageSizeChange = () => {
  resetPage(page)
  loadModels()
}

const handlePageChange = () => loadModels()

const resetQuery = () => {
  query.keyword = ''
  query.provider = ''
  query.status = ''
  resetPage(page)
  loadModels()
}

onMounted(loadModels)
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>LLM 配置</span><strong>{{ models.length }}</strong><em>模型接口</em></el-card>
      <el-card shadow="never" class="metric-card"><span>启用模型</span><strong>{{ enabledCount }}</strong><em>可被选择</em></el-card>
      <el-card shadow="never" class="metric-card"><span>默认模型</span><strong>{{ defaultModel ? 1 : 0 }}</strong><em>{{ defaultModel?.modelName || '未设置' }}</em></el-card>
      <el-card shadow="never" class="metric-card"><span>强制 JSON</span><strong>{{ jsonSchemaRate }}%</strong><em>结构化输出</em></el-card>
      <el-card shadow="never" class="metric-card"><span>测试调用</span><strong>真实</strong><em>{{ passedModelCount }} 个启用模型可测试</em></el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="编码/名称/供应方/模型标识" clearable />
        </el-form-item>
        <el-form-item label="供应方">
          <el-select v-model="query.provider" clearable filterable placeholder="全部">
            <el-option v-for="provider in providerOptions" :key="provider" :label="provider" :value="provider" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="searchModels">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>LLM 配置</span>
          <el-button type="primary" @click="openCreate">新增模型</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="models" stripe>
        <el-table-column prop="modelCode" label="模型编码" min-width="150" fixed />
        <el-table-column prop="modelName" label="模型名称" min-width="170" />
        <el-table-column prop="provider" label="供应方" min-width="150" />
        <el-table-column prop="modelIdentifier" label="调用标识" min-width="170" />
        <el-table-column prop="baseUrl" label="接口地址" min-width="260" />
        <el-table-column label="状态" width="86">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="默认" width="80">
          <template #default="{ row }"><el-tag v-if="row.defaultModel" type="success">默认</el-tag><span v-else>-</span></template>
        </el-table-column>
        <el-table-column prop="temperature" label="温度" width="80" />
        <el-table-column prop="maxTokens" label="MaxToken" width="100" />
        <el-table-column prop="timeoutSeconds" label="超时" width="80" />
        <el-table-column prop="retryCount" label="重试" width="70" />
        <el-table-column label="JSON" width="80">
          <template #default="{ row }"><el-tag :type="row.jsonSchemaRequired ? 'success' : 'info'">{{ row.jsonSchemaRequired ? '强制' : '否' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">配置</el-button>
            <el-button link @click="openTestDialog(row)">测试</el-button>
            <el-button link @click="setDefault(row)">设默认</el-button>
            <el-button link :type="row.status === 'ENABLED' ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
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

    <el-drawer v-model="drawerVisible" :title="editingId ? '编辑 LLM 配置' : '新增 LLM 配置'" size="640px">
      <el-form :model="form" label-width="130px" class="form-grid">
        <el-form-item label="模型编码"><el-input v-model="form.modelCode" placeholder="如 qwen_36_27b" /></el-form-item>
        <el-form-item label="模型名称"><el-input v-model="form.modelName" placeholder="如 Qwen3.6-27B" /></el-form-item>
        <el-form-item label="供应方">
          <el-select v-model="form.provider" filterable allow-create>
            <el-option label="私有化模型平台" value="私有化模型平台" />
            <el-option label="通义千问" value="通义千问" />
            <el-option label="DeepSeek" value="DeepSeek" />
            <el-option label="OpenAI Compatible" value="OpenAI Compatible" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型调用标识"><el-input v-model="form.modelIdentifier" placeholder="如 qwen3.6-27b" /></el-form-item>
        <el-form-item label="接口地址" class="wide"><el-input v-model="form.baseUrl" placeholder="https://llm-gateway/v1/chat/completions" /></el-form-item>
        <el-form-item label="密钥引用" class="wide"><el-input v-model="form.apiKeySecretRef" placeholder="可为空；或 env:OPENAI_API_KEY；或 Bearer xxx" /></el-form-item>
        <el-form-item label="温度"><el-input-number v-model="form.temperature" :min="0" :max="1" :step="0.01" /></el-form-item>
        <el-form-item label="最大 Token"><el-input-number v-model="form.maxTokens" :min="1" :max="128000" /></el-form-item>
        <el-form-item label="超时秒数"><el-input-number v-model="form.timeoutSeconds" :min="1" :max="600" /></el-form-item>
        <el-form-item label="重试次数"><el-input-number v-model="form.retryCount" :min="0" :max="10" /></el-form-item>
        <el-form-item label="强制 JSON"><el-switch v-model="form.jsonSchemaRequired" active-text="启用" inactive-text="关闭" /></el-form-item>
        <el-form-item label="设为默认"><el-switch v-model="form.defaultModel" active-text="默认" inactive-text="普通" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button label="ENABLED">启用</el-radio-button>
            <el-radio-button label="DISABLED">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明" class="wide"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <el-alert class="mb-12" title="测试调用按 OpenAI Compatible Chat Completions 协议发起。密钥引用为空时不加鉴权头，env: 前缀会读取环境变量。" type="info" :closable="false" />
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveModel">保存</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="testDialogVisible" title="LLM 测试调用" width="92vw" class="llm-test-dialog" destroy-on-close>
      <div v-if="testingModel" class="llm-test-layout">
        <el-card shadow="never" class="llm-test-panel">
          <template #header>
            <div class="card-header">
              <span>{{ testingModel.modelName }}</span>
              <el-tag :type="testingModel.jsonSchemaRequired ? 'success' : 'info'">{{ testingModel.jsonSchemaRequired ? '强制 JSON' : '普通输出' }}</el-tag>
            </div>
          </template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="模型编码">{{ testingModel.modelCode }}</el-descriptions-item>
            <el-descriptions-item label="调用标识">{{ testingModel.modelIdentifier }}</el-descriptions-item>
            <el-descriptions-item label="接口地址">{{ testingModel.baseUrl }}</el-descriptions-item>
          </el-descriptions>
          <el-form label-position="top" class="mt-12">
            <el-form-item label="系统提示词">
              <el-input v-model="testForm.systemPrompt" type="textarea" :rows="4" />
            </el-form-item>
            <el-form-item label="用户提示词">
              <el-input v-model="testForm.userPrompt" type="textarea" :rows="6" />
            </el-form-item>
          </el-form>
          <el-button class="full-button" type="primary" :loading="testing" @click="runTestModel">开始测试</el-button>
        </el-card>

        <el-card shadow="never" class="llm-test-result">
          <template #header>
            <div class="card-header">
              <span>测试结果</span>
              <el-tag v-if="testResult" :type="testResult.passed ? 'success' : 'danger'">{{ testResult.passed ? '通过' : '未通过' }}</el-tag>
            </div>
          </template>
          <el-empty v-if="!testResult" description="点击开始测试后查看模型响应" />
          <template v-else>
            <el-alert :type="testResult.passed ? 'success' : 'warning'" :title="testResult.message" :closable="false" class="mb-12" />
            <div class="llm-test-metrics">
              <span>耗时 <strong>{{ testResult.durationMs ?? '-' }}</strong> ms</span>
              <span>JSON <strong>{{ testResult.jsonValid ? '通过' : '未通过' }}</strong></span>
              <span>输入 Token <strong>{{ testResult.inputTokens ?? '-' }}</strong></span>
              <span>输出 Token <strong>{{ testResult.outputTokens ?? '-' }}</strong></span>
              <span>总 Token <strong>{{ testResult.totalTokens ?? '-' }}</strong></span>
            </div>
            <el-tabs v-model="activeTestTab" class="mt-12">
              <el-tab-pane label="模型内容" name="content">
                <pre class="llm-test-pre">{{ testResult.contentPreview || '无内容' }}</pre>
              </el-tab-pane>
              <el-tab-pane label="请求体" name="request">
                <pre class="llm-test-pre">{{ testResult.requestPreview || '无内容' }}</pre>
              </el-tab-pane>
              <el-tab-pane label="原始响应" name="response">
                <pre class="llm-test-pre">{{ testResult.responsePreview || '无内容' }}</pre>
              </el-tab-pane>
            </el-tabs>
          </template>
        </el-card>
      </div>
    </el-dialog>
  </div>
</template>
