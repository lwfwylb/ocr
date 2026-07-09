<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listExtractConfigs, type ConfigSummary } from '../api/config'
import {
  confirmDocument,
  listPendingDocuments,
  rematchDocument,
  type DocumentAccessRecord
} from '../api/document'

const loading = ref(false)
const saving = ref(false)
const docs = ref<DocumentAccessRecord[]>([])
const activeDoc = ref<DocumentAccessRecord | null>(null)
const publishedConfigs = ref<ConfigSummary[]>([])
const departmentOptions = [
  { label: '运营部', value: 'OPS' },
  { label: '财务部', value: 'FINANCE' },
  { label: '产品部', value: 'PRODUCT' }
]
const query = reactive({
  keyword: '',
  departmentId: '',
  matchStatus: ''
})
const form = reactive({
  category: '',
  subCategory: '',
  templateType: '',
  documentType: '',
  configId: '',
  priority: 'MEDIUM',
  comment: ''
})

const configOptions = computed(() => {
  if (!activeDoc.value) return publishedConfigs.value
  return publishedConfigs.value.filter((config) => {
    const deptMatched = !activeDoc.value?.departmentId || config.departmentId === activeDoc.value.departmentId
    const docTypeMatched = !form.documentType || config.documentType === form.documentType
    return deptMatched && docTypeMatched
  })
})

const loadDocs = async () => {
  loading.value = true
  try {
    docs.value = await listPendingDocuments(query)
    if (!activeDoc.value || !docs.value.some((item) => item.id === activeDoc.value?.id)) {
      selectDoc(docs.value[0] || null)
    }
  } catch (error) {
    docs.value = []
    activeDoc.value = null
    ElMessage.error(error instanceof Error ? error.message : '待确认文档加载失败')
  } finally {
    loading.value = false
  }
}

const loadConfigs = async () => {
  try {
    publishedConfigs.value = await listExtractConfigs({ status: 'PUBLISHED' })
  } catch (error) {
    publishedConfigs.value = []
    ElMessage.error(error instanceof Error ? error.message : '生效配置加载失败')
  }
}

const selectDoc = (doc: DocumentAccessRecord | null) => {
  activeDoc.value = doc
  Object.assign(form, {
    category: doc?.category || '',
    subCategory: doc?.subCategory || '',
    templateType: doc?.templateType || '',
    documentType: doc?.documentType || '',
    configId: doc?.matchedConfigId || '',
    priority: doc?.priority || 'MEDIUM',
    comment: ''
  })
}

const confirm = async () => {
  if (!activeDoc.value) return
  if (!form.configId) {
    ElMessage.warning('请选择生效配置')
    return
  }
  saving.value = true
  try {
    await confirmDocument(activeDoc.value.id, form)
    ElMessage.success('文档已确认，任务已创建')
    await loadDocs()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '确认失败')
  } finally {
    saving.value = false
  }
}

const rematch = async () => {
  if (!activeDoc.value) return
  try {
    const result = await rematchDocument(activeDoc.value.id)
    if (result.accessStatus === 'CREATED_TASK') {
      ElMessage.success('重新匹配成功，任务已创建')
    } else {
      ElMessage.warning(result.matchMessage || '仍需人工确认')
    }
    await loadDocs()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重新匹配失败')
  }
}

const resetQuery = () => {
  query.keyword = ''
  query.departmentId = ''
  query.matchStatus = ''
  loadDocs()
}

onMounted(async () => {
  await Promise.all([loadConfigs(), loadDocs()])
})
</script>

<template>
  <div class="unmatched-layout">
    <el-card shadow="never" class="table-sidebar">
      <template #header>
        <div class="card-header"><span>待确认文档</span><el-tag type="warning">{{ docs.length }}</el-tag></div>
      </template>
      <el-form :model="query" class="mb-12">
        <el-form-item>
          <el-input v-model="query.keyword" clearable placeholder="搜索 traceId/文件名/业务号" @keyup.enter="loadDocs" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.departmentId" filterable clearable placeholder="所属部门">
            <el-option v-for="item in departmentOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.matchStatus" clearable placeholder="匹配状态">
            <el-option label="未匹配" value="UNMATCHED" />
            <el-option label="多配置命中" value="MULTIPLE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadDocs">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <div
        v-for="doc in docs"
        :key="doc.id"
        class="storage-table-item"
        :class="{ active: activeDoc?.id === doc.id }"
        @click="selectDoc(doc)"
      >
        <strong>{{ doc.fileName }}</strong>
        <span>{{ doc.traceId }}</span>
        <em>{{ doc.matchMessage }}</em>
      </div>
      <el-empty v-if="!docs.length" description="暂无待确认文档" />
    </el-card>

    <div class="page-stack">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>确认文档</span>
            <div>
              <el-button :disabled="!activeDoc" @click="rematch">重新匹配</el-button>
              <el-button type="primary" :disabled="!activeDoc" :loading="saving" @click="confirm">确认并创建任务</el-button>
            </div>
          </div>
        </template>
        <template v-if="activeDoc">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="TraceId">{{ activeDoc.traceId }}</el-descriptions-item>
            <el-descriptions-item label="文档编号">{{ activeDoc.documentId }}</el-descriptions-item>
            <el-descriptions-item label="来源系统">{{ activeDoc.sourceSystem }}</el-descriptions-item>
            <el-descriptions-item label="文件名">{{ activeDoc.fileName }}</el-descriptions-item>
            <el-descriptions-item label="所属部门">{{ activeDoc.departmentId }}</el-descriptions-item>
            <el-descriptions-item label="接入时间">{{ activeDoc.createdAt }}</el-descriptions-item>
          </el-descriptions>
          <el-alert class="mt-12" :title="activeDoc.matchMessage" type="warning" :closable="false" />
        </template>
        <el-empty v-else description="请选择待确认文档" />
      </el-card>

      <el-card shadow="never">
        <template #header>人工确认</template>
        <el-form :model="form" label-width="130px" class="form-grid">
          <el-form-item label="分类">
            <el-input v-model="form.category" placeholder="可选" />
          </el-form-item>
          <el-form-item label="子类">
            <el-input v-model="form.subCategory" placeholder="可选" />
          </el-form-item>
          <el-form-item label="模板类型">
            <el-input v-model="form.templateType" placeholder="可选" />
          </el-form-item>
          <el-form-item label="文档类型">
            <el-input v-model="form.documentType" placeholder="文档类型" />
          </el-form-item>
          <el-form-item label="生效配置">
            <el-select v-model="form.configId" filterable clearable placeholder="请选择已发布配置">
              <el-option
                v-for="config in configOptions"
                :key="config.id"
                :label="`${config.configName} / V${config.version} / ${config.documentType}`"
                :value="config.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级">
            <el-radio-group v-model="form.priority">
              <el-radio-button label="HIGH">高</el-radio-button>
              <el-radio-button label="MEDIUM">中</el-radio-button>
              <el-radio-button label="LOW">低</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="确认备注" class="wide">
            <el-input v-model="form.comment" type="textarea" :rows="3" placeholder="填写确认原因或处理说明" />
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>候选配置</template>
        <el-table :data="configOptions">
          <el-table-column prop="configName" label="配置名称" min-width="220" />
          <el-table-column prop="version" label="版本" width="80">
            <template #default="{ row }">V{{ row.version }}</template>
          </el-table-column>
          <el-table-column prop="departmentId" label="部门" width="100" />
          <el-table-column prop="documentType" label="文档类型" width="120" />
          <el-table-column prop="templateType" label="模板类型" min-width="170" />
          <el-table-column prop="targetTable" label="目标表" min-width="180" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>
