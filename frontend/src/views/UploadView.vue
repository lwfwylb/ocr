<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { listExtractConfigs, type ConfigSummary } from '../api/config'
import { manualUploadDocumentFile, type DocumentAccessRecord } from '../api/document'

const router = useRouter()
const loadingConfigs = ref(false)
const uploading = ref(false)
const uploadResult = ref<DocumentAccessRecord | null>(null)
const fileList = ref<any[]>([])
const configs = ref<ConfigSummary[]>([])

const form = reactive({
  configId: '',
  priority: '',
  businessNo: '',
  remark: ''
})

const selectedConfig = computed(() => configs.value.find((item) => item.id === form.configId))

const priorityOptions = [
  { label: '按配置默认', value: '' },
  { label: '高', value: 'HIGH' },
  { label: '中', value: 'MEDIUM' },
  { label: '低', value: 'LOW' }
]

const statusOrder: Record<string, number> = {
  PUBLISHED: 1,
  TESTING: 2,
  DRAFT: 3
}

const statusText = (status?: string) => {
  if (status === 'PUBLISHED') return '已发布'
  if (status === 'TESTING') return '验证中'
  if (status === 'DRAFT') return '草稿'
  if (status === 'DISABLED') return '已停用'
  return status || '-'
}

const statusTagType = (status?: string) => {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'TESTING') return 'warning'
  if (status === 'DRAFT') return 'info'
  return 'info'
}

const priorityText = (value?: string) => {
  if (value === 'HIGH') return '高'
  if (value === 'MEDIUM') return '中'
  if (value === 'LOW') return '低'
  return value || '-'
}

const configOptionLabel = (config: ConfigSummary) =>
  `${config.configName} / V${config.version} / ${statusText(config.status)} / ${config.documentType || '-'}`

const loadConfigs = async () => {
  loadingConfigs.value = true
  try {
    const rows = await Promise.all([
      listExtractConfigs({ status: 'PUBLISHED' }),
      listExtractConfigs({ status: 'TESTING' }),
      listExtractConfigs({ status: 'DRAFT' })
    ])
    const merged = rows.flat()
    const unique = new Map<string, ConfigSummary>()
    merged.forEach((config) => unique.set(config.id, config))
    configs.value = Array.from(unique.values()).sort((a, b) => {
      const statusDiff = (statusOrder[a.status] || 99) - (statusOrder[b.status] || 99)
      if (statusDiff !== 0) return statusDiff
      return (b.version || 0) - (a.version || 0)
    })
    if (!form.configId && configs.value.length) {
      form.configId = configs.value[0].id
    }
  } catch (error) {
    configs.value = []
    ElMessage.error(error instanceof Error ? error.message : '解析配置加载失败')
  } finally {
    loadingConfigs.value = false
  }
}

const upload = async () => {
  if (!form.configId) {
    ElMessage.warning('请选择解析配置')
    return
  }
  const selectedFile = fileList.value[0]
  if (!selectedFile) {
    ElMessage.warning('请先选择上传文件')
    return
  }
  const rawFile = selectedFile.raw as File | undefined
  if (!rawFile) {
    ElMessage.warning('无法读取上传文件')
    return
  }
  uploading.value = true
  try {
    uploadResult.value = await manualUploadDocumentFile({
      configId: form.configId,
      businessNo: form.businessNo,
      priority: form.priority,
      file: rawFile
    })
    ElMessage.success('文档已接入并创建任务')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传接入失败')
  } finally {
    uploading.value = false
  }
}

onMounted(loadConfigs)
</script>

<template>
  <div class="two-column">
    <el-card shadow="never">
      <template #header>手工上传文档</template>
      <el-alert
        class="mb-12"
        type="warning"
        show-icon
        :closable="false"
        title="测试阶段允许选择草稿、验证中、已发布配置；正式接入稳定后将收敛为仅允许已发布配置。"
      />
      <el-form :model="form" label-width="110px">
        <el-form-item label="解析配置" required>
          <el-select
            v-model="form.configId"
            filterable
            clearable
            :loading="loadingConfigs"
            placeholder="请选择配置名称"
            no-data-text="暂无可用解析配置"
          >
            <el-option
              v-for="config in configs"
              :key="config.id"
              :label="configOptionLabel(config)"
              :value="config.id"
            >
              <div class="select-option-row">
                <span>{{ config.configName }} / V{{ config.version }} / {{ config.documentType || '-' }}</span>
                <el-tag size="small" :type="statusTagType(config.status)">{{ statusText(config.status) }}</el-tag>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="上传文件" required>
          <el-upload v-model:file-list="fileList" drag action="#" :limit="1" :auto-upload="false">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或点击选择 PDF、图片、Office、TXT、CSV</div>
          </el-upload>
        </el-form-item>
        <el-form-item label="业务号">
          <el-input v-model="form.businessNo" clearable placeholder="选填，用于关联上游业务单据" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" placeholder="默认取配置优先级">
            <el-option v-for="item in priorityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="选填，记录本次上传说明" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="uploading" @click="upload">上传接入</el-button>
          <el-button @click="router.push('/documents/records')">接入记录</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="page-stack">
      <el-card shadow="never">
        <template #header>配置摘要</template>
        <el-empty v-if="!selectedConfig" description="请选择解析配置" />
        <el-descriptions v-else :column="1" border>
          <el-descriptions-item label="配置名称">{{ selectedConfig.configName }}</el-descriptions-item>
          <el-descriptions-item label="当前版本">V{{ selectedConfig.version }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(selectedConfig.status)">{{ statusText(selectedConfig.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="所属部门">{{ selectedConfig.departmentId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="分类/子类">
            {{ selectedConfig.category || '-' }} / {{ selectedConfig.subCategory || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="模板类型">{{ selectedConfig.templateType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="文档类型">{{ selectedConfig.documentType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="默认优先级">{{ priorityText(selectedConfig.defaultPriority) }}</el-descriptions-item>
          <el-descriptions-item label="目标表">{{ selectedConfig.targetTable || '-' }}</el-descriptions-item>
          <el-descriptions-item label="OCR引擎">{{ selectedConfig.parseEngine || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never">
        <template #header>接入结果</template>
        <el-empty v-if="!uploadResult" description="上传后将在这里展示 traceId、任务编号和匹配配置" />
        <el-descriptions v-else :column="1" border>
          <el-descriptions-item label="TraceId">{{ uploadResult.traceId }}</el-descriptions-item>
          <el-descriptions-item label="文档编号">{{ uploadResult.documentId }}</el-descriptions-item>
          <el-descriptions-item label="任务编号">{{ uploadResult.taskId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="匹配状态"><el-tag type="success">已匹配</el-tag></el-descriptions-item>
          <el-descriptions-item label="匹配配置">
            {{ uploadResult.matchedConfigName || '-' }} {{ uploadResult.matchedConfigVersion ? `V${uploadResult.matchedConfigVersion}` : '' }}
          </el-descriptions-item>
          <el-descriptions-item label="匹配说明">{{ uploadResult.matchMessage }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="uploadResult" class="mt-16">
          <el-button type="primary" @click="router.push('/documents/records')">查看接入记录</el-button>
          <el-button @click="router.push('/tasks')">查看任务中心</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>
