<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

interface UnmatchedDoc {
  traceId: string
  documentId: string
  fileName: string
  sourceSystem: string
  department: string
  reason: string
  suggestedTypes: string[]
  selectedType: string
  selectedConfig: string
  priority: string
  createdAt: string
}

const docs = ref<UnmatchedDoc[]>([
  {
    traceId: 'TRACE-20260628-0004',
    documentId: 'DOC-20260628-0004',
    fileName: '未知附件.zip',
    sourceSystem: '文件分拣系统',
    department: '产品部',
    reason: '未匹配到文档类型，压缩包内存在多个 PDF',
    suggestedTypes: ['产品合同', '托管协议', '开户资料'],
    selectedType: '',
    selectedConfig: '',
    priority: 'MEDIUM',
    createdAt: '2026-06-28 11:20:00'
  },
  {
    traceId: 'TRACE-20260628-0008',
    documentId: 'DOC-20260628-0008',
    fileName: '扫描件_无标题.pdf',
    sourceSystem: '邮件分拣系统',
    department: '运营部',
    reason: '命中多个配置，需要人工确认',
    suggestedTypes: ['划款指令', '银行回单'],
    selectedType: '划款指令',
    selectedConfig: '划款指令-运营部-提取配置',
    priority: 'HIGH',
    createdAt: '2026-06-28 15:42:00'
  }
])

const activeDoc = ref(docs.value[0])
const form = reactive({
  category: '基金交易',
  subCategory: '',
  templateType: '',
  documentType: activeDoc.value.selectedType,
  extractConfig: activeDoc.value.selectedConfig,
  priority: activeDoc.value.priority,
  comment: ''
})

const selectDoc = (doc: UnmatchedDoc) => {
  activeDoc.value = doc
  form.documentType = doc.selectedType
  form.extractConfig = doc.selectedConfig
  form.priority = doc.priority
  form.comment = ''
}

const confirm = () => {
  activeDoc.value.selectedType = form.documentType
  activeDoc.value.selectedConfig = form.extractConfig
  activeDoc.value.priority = form.priority
  ElMessage.success('已模拟确认文档类型并创建任务')
}

const rematch = () => ElMessage.success('已模拟重新匹配规则')
</script>

<template>
  <div class="unmatched-layout">
    <el-card shadow="never" class="table-sidebar">
      <template #header>
        <div class="card-header"><span>待确认文档</span><el-tag type="warning">{{ docs.length }}</el-tag></div>
      </template>
      <div
        v-for="doc in docs"
        :key="doc.documentId"
        class="storage-table-item"
        :class="{ active: activeDoc.documentId === doc.documentId }"
        @click="selectDoc(doc)"
      >
        <strong>{{ doc.fileName }}</strong>
        <span>{{ doc.traceId }}</span>
        <em>{{ doc.reason }}</em>
      </div>
    </el-card>

    <div class="page-stack">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>确认处理</span>
            <div><el-button @click="rematch">重新匹配</el-button><el-button type="primary" @click="confirm">确认并创建任务</el-button></div>
          </div>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="TraceId">{{ activeDoc.traceId }}</el-descriptions-item>
          <el-descriptions-item label="文档编号">{{ activeDoc.documentId }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ activeDoc.sourceSystem }}</el-descriptions-item>
          <el-descriptions-item label="文件名">{{ activeDoc.fileName }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ activeDoc.department }}</el-descriptions-item>
          <el-descriptions-item label="接入时间">{{ activeDoc.createdAt }}</el-descriptions-item>
        </el-descriptions>
        <el-alert class="mt-12" :title="activeDoc.reason" type="warning" :closable="false" />
      </el-card>

      <el-card shadow="never">
        <template #header>人工确认</template>
        <el-form :model="form" label-width="130px" class="form-grid">
          <el-form-item label="业务分类">
            <el-select v-model="form.category" filterable>
              <el-option label="资金业务" value="资金业务" />
              <el-option label="基金交易" value="基金交易" />
              <el-option label="客户业务" value="客户业务" />
              <el-option label="产品业务" value="产品业务" />
            </el-select>
          </el-form-item>
          <el-form-item label="业务子类">
            <el-select v-model="form.subCategory" filterable clearable>
              <el-option label="基金申购" value="基金申购" />
              <el-option label="基金赎回" value="基金赎回" />
              <el-option label="划款指令" value="划款指令" />
              <el-option label="银行回单" value="银行回单" />
            </el-select>
          </el-form-item>
          <el-form-item label="模板/表单类型">
            <el-select v-model="form.templateType" filterable clearable allow-create>
              <el-option label="大成基金申购单" value="大成基金申购单" />
              <el-option label="南方基金申购单" value="南方基金申购单" />
              <el-option label="通用划款指令模板" value="通用划款指令模板" />
            </el-select>
          </el-form-item>
          <el-form-item label="文档类型">
            <el-select v-model="form.documentType" filterable clearable>
              <el-option v-for="type in activeDoc.suggestedTypes" :key="type" :label="type" :value="type" />
            </el-select>
          </el-form-item>
          <el-form-item label="提取配置">
            <el-select v-model="form.extractConfig" filterable clearable allow-create>
              <el-option label="划款指令-运营部-提取配置" value="划款指令-运营部-提取配置" />
              <el-option label="银行回单-资金结果配置" value="银行回单-资金结果配置" />
              <el-option label="产品合同-条款表映射" value="产品合同-条款表映射" />
            </el-select>
          </el-form-item>
          <el-form-item label="任务优先级">
            <el-radio-group v-model="form.priority">
              <el-radio-button label="HIGH">高</el-radio-button>
              <el-radio-button label="MEDIUM">中</el-radio-button>
              <el-radio-button label="LOW">低</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="处理说明" class="wide">
            <el-input v-model="form.comment" type="textarea" :rows="3" placeholder="记录人工确认依据" />
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>候选规则</template>
        <el-table
          :data="activeDoc.suggestedTypes.map((type, index) => ({ type, confidence: [0.72, 0.64, 0.51][index] || 0.5, reason: '关键字/版面特征相似' }))"
        >
          <el-table-column prop="type" label="候选文档类型" />
          <el-table-column label="匹配置信度">
            <template #default="{ row }">{{ Math.round(row.confidence * 100) }}%</template>
          </el-table-column>
          <el-table-column prop="reason" label="命中原因" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>
