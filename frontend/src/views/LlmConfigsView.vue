<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

interface LlmConfig {
  modelCode: string
  modelName: string
  provider: string
  endpoint: string
  enabled: boolean
  defaultModel: boolean
  temperature: number
  maxTokens: number
  timeoutSeconds: number
  avgDuration: string
  tokenUsage: string
}

const drawerVisible = ref(false)
const selectedModel = ref<LlmConfig | null>(null)
const models = ref<LlmConfig[]>([
  { modelCode: 'qwen_36_27b', modelName: 'Qwen3.6-27B', provider: '私有化模型平台', endpoint: 'http://llm-gateway/v1/chat/completions', enabled: true, defaultModel: true, temperature: 0.1, maxTokens: 4096, timeoutSeconds: 120, avgDuration: '4.5s', tokenUsage: '38.2万' },
  { modelCode: 'qwen_36_72b', modelName: 'Qwen3.6-72B', provider: '私有化模型平台', endpoint: 'http://llm-gateway/v1/chat/completions', enabled: true, defaultModel: false, temperature: 0.1, maxTokens: 8192, timeoutSeconds: 180, avgDuration: '8.9s', tokenUsage: '12.6万' },
  { modelCode: 'openai_compatible', modelName: 'OpenAI Compatible', provider: '兼容接口', endpoint: 'https://api.example.com/v1/chat/completions', enabled: false, defaultModel: false, temperature: 0.2, maxTokens: 4096, timeoutSeconds: 90, avgDuration: '-', tokenUsage: '-' }
])

const openDetail = (model: LlmConfig) => {
  selectedModel.value = model
  drawerVisible.value = true
}

const setDefault = (model: LlmConfig) => {
  models.value.forEach((item) => (item.defaultModel = item.modelCode === model.modelCode))
  ElMessage.success(`已设置 ${model.modelName} 为默认模型`)
}

const testModel = (model: LlmConfig) => ElMessage.success(`${model.modelName} 测试调用成功`)
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>LLM 配置</span><strong>{{ models.length }}</strong><em>模型接口</em></el-card>
      <el-card shadow="never" class="metric-card"><span>启用模型</span><strong>{{ models.filter((m) => m.enabled).length }}</strong><em>可调用</em></el-card>
      <el-card shadow="never" class="metric-card"><span>默认模型</span><strong>1</strong><em>提取优先</em></el-card>
      <el-card shadow="never" class="metric-card"><span>平均耗时</span><strong>5.8s</strong><em>近 7 天</em></el-card>
      <el-card shadow="never" class="metric-card"><span>JSON 成功率</span><strong>98%</strong><em>Schema 校验</em></el-card>
    </section>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>LLM 配置</span>
          <el-button type="primary">新增模型</el-button>
        </div>
      </template>
      <el-table :data="models" stripe>
        <el-table-column prop="modelCode" label="模型编码" min-width="140" fixed />
        <el-table-column prop="modelName" label="模型名称" min-width="160" />
        <el-table-column prop="provider" label="供应方" min-width="150" />
        <el-table-column prop="endpoint" label="接口地址" min-width="260" />
        <el-table-column label="启用" width="80"><template #default="{ row }"><el-switch v-model="row.enabled" /></template></el-table-column>
        <el-table-column label="默认" width="80"><template #default="{ row }"><el-tag v-if="row.defaultModel" type="success">默认</el-tag><span v-else>-</span></template></el-table-column>
        <el-table-column prop="temperature" label="温度" width="80" />
        <el-table-column prop="maxTokens" label="MaxToken" width="100" />
        <el-table-column prop="avgDuration" label="平均耗时" width="100" />
        <el-table-column prop="tokenUsage" label="Token" width="100" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">配置</el-button>
            <el-button link @click="testModel(row)">测试</el-button>
            <el-button link @click="setDefault(row)">设默认</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="LLM 参数配置" size="560px">
      <template v-if="selectedModel">
        <el-form :model="selectedModel" label-width="130px" class="form-grid">
          <el-form-item label="模型名称"><el-input v-model="selectedModel.modelName" /></el-form-item>
          <el-form-item label="供应方"><el-input v-model="selectedModel.provider" /></el-form-item>
          <el-form-item label="接口地址" class="wide"><el-input v-model="selectedModel.endpoint" /></el-form-item>
          <el-form-item label="温度"><el-input-number v-model="selectedModel.temperature" :min="0" :max="1" :step="0.1" /></el-form-item>
          <el-form-item label="最大 Token"><el-input-number v-model="selectedModel.maxTokens" :min="512" :max="32768" /></el-form-item>
          <el-form-item label="超时秒数"><el-input-number v-model="selectedModel.timeoutSeconds" :min="10" /></el-form-item>
        </el-form>
        <el-alert title="建议要素提取场景使用低温度，并强制 JSON Schema 校验。" type="info" :closable="false" />
      </template>
    </el-drawer>
  </div>
</template>
