<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createLlmModelConfig,
  disableLlmModelConfig,
  enableLlmModelConfig,
  listLlmModelConfigs,
  setDefaultLlmModelConfig,
  testLlmModelConfig,
  updateLlmModelConfig,
  type LlmModelConfig,
  type LlmModelPayload
} from '../api/model'

const drawerVisible = ref(false)
const loading = ref(false)
const saving = ref(false)
const models = ref<LlmModelConfig[]>([])
const editingId = ref('')
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

const enabledCount = computed(() => models.value.filter((item) => item.status === 'ENABLED').length)
const defaultModel = computed(() => models.value.find((item) => item.defaultModel))
const providerOptions = computed(() => Array.from(new Set(models.value.map((item) => item.provider).filter(Boolean))))
const jsonSchemaRate = computed(() => {
  if (!models.value.length) return 0
  return Math.round((models.value.filter((item) => item.jsonSchemaRequired).length / models.value.length) * 100)
})

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
    models.value = await listLlmModelConfigs(query)
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

const testModel = async (model: LlmModelConfig) => {
  try {
    const result = await testLlmModelConfig(model.id)
    ElMessage.success(result.message)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '测试连接失败')
  }
}

const resetQuery = () => {
  query.keyword = ''
  query.provider = ''
  query.status = ''
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
      <el-card shadow="never" class="metric-card"><span>强制 JSON</span><strong>{{ jsonSchemaRate }}%</strong><em>Schema 输出</em></el-card>
      <el-card shadow="never" class="metric-card"><span>测试调用</span><strong>模拟</strong><em>暂不真实调用</em></el-card>
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
          <el-button type="primary" :loading="loading" @click="loadModels">查询</el-button>
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
            <el-button link @click="testModel(row)">测试</el-button>
            <el-button link @click="setDefault(row)">设默认</el-button>
            <el-button link :type="row.status === 'ENABLED' ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
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
        <el-form-item label="密钥引用" class="wide"><el-input v-model="form.apiKeySecretRef" placeholder="如 secret://llm/qwen，不保存明文 Key" /></el-form-item>
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
      <el-alert class="mb-12" title="第一版只维护调用配置和模拟测试；真实调用、密钥加密托管、Token 统计将在执行链路中接入。" type="info" :closable="false" />
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveModel">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>
