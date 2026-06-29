<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

interface OcrEngine {
  engineCode: string
  engineName: string
  engineType: string
  endpoint: string
  enabled: boolean
  priority: number
  timeoutSeconds: number
  retryCount: number
  avgDuration: string
  successRate: string
}

const drawerVisible = ref(false)
const selectedEngine = ref<OcrEngine | null>(null)
const engines = ref<OcrEngine[]>([
  { engineCode: 'paddleocr_vl', engineName: 'PaddleOCR-VL-1.6', engineType: 'OCR/LAYOUT/TABLE', endpoint: 'http://ocr-service/paddle/parse', enabled: true, priority: 100, timeoutSeconds: 120, retryCount: 2, avgDuration: '8.2s', successRate: '98.6%' },
  { engineCode: 'mineru', engineName: 'MinerU', engineType: 'PDF/PARSE', endpoint: 'http://ocr-service/mineru/parse', enabled: true, priority: 90, timeoutSeconds: 180, retryCount: 2, avgDuration: '11.5s', successRate: '97.2%' },
  { engineCode: 'legacy_ocr', engineName: '传统 OCR 引擎', engineType: 'OCR', endpoint: 'http://legacy-ocr/parse', enabled: false, priority: 30, timeoutSeconds: 60, retryCount: 1, avgDuration: '5.1s', successRate: '91.4%' }
])

const defaultParams = reactive({
  enablePreprocess: true,
  enableDeskew: true,
  enableHeader: false,
  enableFooter: false,
  enableSeal: true,
  enableTable: true,
  outputFormat: 'Markdown'
})

const openDetail = (engine: OcrEngine) => {
  selectedEngine.value = engine
  drawerVisible.value = true
}

const testConnection = (engine: OcrEngine) => {
  ElMessage.success(`${engine.engineName} 连接测试成功`)
}
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>OCR 引擎</span><strong>{{ engines.length }}</strong><em>已配置</em></el-card>
      <el-card shadow="never" class="metric-card"><span>启用中</span><strong>{{ engines.filter((e) => e.enabled).length }}</strong><em>可调度</em></el-card>
      <el-card shadow="never" class="metric-card"><span>平均成功率</span><strong>97%</strong><em>近 7 天</em></el-card>
      <el-card shadow="never" class="metric-card"><span>平均耗时</span><strong>8.9s</strong><em>单文档</em></el-card>
      <el-card shadow="never" class="metric-card"><span>兜底引擎</span><strong>1</strong><em>已启用</em></el-card>
    </section>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>OCR 引擎配置</span>
          <el-button type="primary">新增引擎</el-button>
        </div>
      </template>
      <el-table :data="engines" stripe>
        <el-table-column prop="engineCode" label="引擎编码" min-width="130" fixed />
        <el-table-column prop="engineName" label="引擎名称" min-width="170" />
        <el-table-column prop="engineType" label="能力类型" min-width="150" />
        <el-table-column prop="endpoint" label="服务地址" min-width="240" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }"><el-switch v-model="row.enabled" /></template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column prop="timeoutSeconds" label="超时(s)" width="90" />
        <el-table-column prop="avgDuration" label="平均耗时" width="100" />
        <el-table-column prop="successRate" label="成功率" width="90" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">配置</el-button>
            <el-button link @click="testConnection(row)">测试</el-button>
            <el-button link>日志</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="OCR 引擎参数" size="560px">
      <template v-if="selectedEngine">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="引擎">{{ selectedEngine.engineName }}</el-descriptions-item>
          <el-descriptions-item label="服务地址">{{ selectedEngine.endpoint }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="section-title">默认解析参数</h3>
        <el-form :model="defaultParams" label-width="130px" class="form-grid">
          <el-form-item label="文档预处理"><el-switch v-model="defaultParams.enablePreprocess" /></el-form-item>
          <el-form-item label="倾斜矫正"><el-switch v-model="defaultParams.enableDeskew" /></el-form-item>
          <el-form-item label="解析页头"><el-switch v-model="defaultParams.enableHeader" /></el-form-item>
          <el-form-item label="解析页尾"><el-switch v-model="defaultParams.enableFooter" /></el-form-item>
          <el-form-item label="识别印章"><el-switch v-model="defaultParams.enableSeal" /></el-form-item>
          <el-form-item label="解析表格"><el-switch v-model="defaultParams.enableTable" /></el-form-item>
          <el-form-item label="输出格式"><el-select v-model="defaultParams.outputFormat"><el-option label="Markdown" value="Markdown" /><el-option label="JSON" value="JSON" /></el-select></el-form-item>
        </el-form>
      </template>
    </el-drawer>
  </div>
</template>
