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
  { label: 'Operations', value: 'OPS' },
  { label: 'Finance', value: 'FINANCE' },
  { label: 'Product', value: 'PRODUCT' }
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
    ElMessage.error(error instanceof Error ? error.message : 'Load pending documents failed')
  } finally {
    loading.value = false
  }
}

const loadConfigs = async () => {
  try {
    publishedConfigs.value = await listExtractConfigs({ status: 'PUBLISHED' })
  } catch (error) {
    publishedConfigs.value = []
    ElMessage.error(error instanceof Error ? error.message : 'Load published configs failed')
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
    ElMessage.warning('Please select an effective config')
    return
  }
  saving.value = true
  try {
    await confirmDocument(activeDoc.value.id, form)
    ElMessage.success('Document confirmed and task created')
    await loadDocs()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Confirm failed')
  } finally {
    saving.value = false
  }
}

const rematch = async () => {
  if (!activeDoc.value) return
  try {
    const result = await rematchDocument(activeDoc.value.id)
    if (result.accessStatus === 'CREATED_TASK') {
      ElMessage.success('Rematch succeeded and task created')
    } else {
      ElMessage.warning(result.matchMessage || 'Manual confirm is still required')
    }
    await loadDocs()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Rematch failed')
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
        <div class="card-header"><span>Pending Documents</span><el-tag type="warning">{{ docs.length }}</el-tag></div>
      </template>
      <el-form :model="query" class="mb-12">
        <el-form-item>
          <el-input v-model="query.keyword" clearable placeholder="Search trace/file/business" @keyup.enter="loadDocs" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.departmentId" filterable clearable placeholder="Department">
            <el-option v-for="item in departmentOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.matchStatus" clearable placeholder="Match Status">
            <el-option label="Unmatched" value="UNMATCHED" />
            <el-option label="Multiple" value="MULTIPLE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadDocs">Search</el-button>
          <el-button @click="resetQuery">Reset</el-button>
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
      <el-empty v-if="!docs.length" description="No pending documents" />
    </el-card>

    <div class="page-stack">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>Confirm Document</span>
            <div>
              <el-button :disabled="!activeDoc" @click="rematch">Rematch</el-button>
              <el-button type="primary" :disabled="!activeDoc" :loading="saving" @click="confirm">Confirm and Create Task</el-button>
            </div>
          </div>
        </template>
        <template v-if="activeDoc">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="TraceId">{{ activeDoc.traceId }}</el-descriptions-item>
            <el-descriptions-item label="Document Id">{{ activeDoc.documentId }}</el-descriptions-item>
            <el-descriptions-item label="Source">{{ activeDoc.sourceSystem }}</el-descriptions-item>
            <el-descriptions-item label="File Name">{{ activeDoc.fileName }}</el-descriptions-item>
            <el-descriptions-item label="Department">{{ activeDoc.departmentId }}</el-descriptions-item>
            <el-descriptions-item label="Access Time">{{ activeDoc.createdAt }}</el-descriptions-item>
          </el-descriptions>
          <el-alert class="mt-12" :title="activeDoc.matchMessage" type="warning" :closable="false" />
        </template>
        <el-empty v-else description="Select a pending document" />
      </el-card>

      <el-card shadow="never">
        <template #header>Manual Confirm</template>
        <el-form :model="form" label-width="130px" class="form-grid">
          <el-form-item label="Category">
            <el-input v-model="form.category" placeholder="Optional" />
          </el-form-item>
          <el-form-item label="Sub Category">
            <el-input v-model="form.subCategory" placeholder="Optional" />
          </el-form-item>
          <el-form-item label="Template Type">
            <el-input v-model="form.templateType" placeholder="Optional" />
          </el-form-item>
          <el-form-item label="Document Type">
            <el-input v-model="form.documentType" placeholder="Document type" />
          </el-form-item>
          <el-form-item label="Effective Config">
            <el-select v-model="form.configId" filterable clearable placeholder="Select published config">
              <el-option
                v-for="config in configOptions"
                :key="config.id"
                :label="`${config.configName} / V${config.version} / ${config.documentType}`"
                :value="config.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="Priority">
            <el-radio-group v-model="form.priority">
              <el-radio-button label="HIGH">High</el-radio-button>
              <el-radio-button label="MEDIUM">Medium</el-radio-button>
              <el-radio-button label="LOW">Low</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="Comment" class="wide">
            <el-input v-model="form.comment" type="textarea" :rows="3" placeholder="Confirm reason" />
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>Candidate Configs</template>
        <el-table :data="configOptions">
          <el-table-column prop="configName" label="Config Name" min-width="220" />
          <el-table-column prop="version" label="Version" width="80">
            <template #default="{ row }">V{{ row.version }}</template>
          </el-table-column>
          <el-table-column prop="departmentId" label="Dept" width="100" />
          <el-table-column prop="documentType" label="Doc Type" width="120" />
          <el-table-column prop="templateType" label="Template Type" min-width="170" />
          <el-table-column prop="targetTable" label="Target Table" min-width="180" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>
