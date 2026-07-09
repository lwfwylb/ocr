<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { manualUploadDocument, type DocumentAccessRecord } from '../api/document'

const router = useRouter()
const uploading = ref(false)
const uploadResult = ref<DocumentAccessRecord | null>(null)
const fileList = ref<any[]>([])
const departmentOptions = [
  { label: '运营部', value: 'OPS' },
  { label: '财务部', value: 'FINANCE' },
  { label: '产品部', value: 'PRODUCT' }
]
const documentTypeOptions = [
  { label: '划款指令', value: 'PAYMENT_INSTRUCTION' },
  { label: '银行回单', value: 'BANK_RECEIPT' },
  { label: '开户资料', value: 'ACCOUNT_OPENING' }
]
const form = reactive({
  departmentId: 'OPS',
  category: 'FUND_BUSINESS',
  subCategory: 'PAYMENT_INSTRUCTION',
  templateType: 'GENERAL_PAYMENT_INSTRUCTION_TEMPLATE',
  documentType: 'PAYMENT_INSTRUCTION',
  priority: 'HIGH',
  businessNo: 'BIZ-20260628-001',
  sourceRemark: '手工上传'
})

const upload = async () => {
  const selectedFile = fileList.value[0]
  const fileName = selectedFile?.name || `manual_upload_${Date.now()}.pdf`
  uploading.value = true
  try {
    uploadResult.value = await manualUploadDocument({
      sourceSystem: form.sourceRemark || '手工上传',
      businessNo: form.businessNo,
      departmentId: form.departmentId,
      category: form.category,
      subCategory: form.subCategory,
      templateType: form.templateType,
      documentType: form.documentType,
      priority: form.priority,
      fileName,
      fileSize: selectedFile?.size || 1024 * 1024,
      storagePath: `mock://manual-upload/${fileName}`
    })
    ElMessage.success('文档已接入')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败')
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="two-column">
    <el-card shadow="never">
      <template #header>手工上传文档</template>
      <el-form :model="form" label-width="120px">
        <el-form-item label="所属部门">
          <el-select v-model="form.departmentId">
            <el-option v-for="item in departmentOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="form.category" />
        </el-form-item>
        <el-form-item label="子类">
          <el-input v-model="form.subCategory" />
        </el-form-item>
        <el-form-item label="模板类型">
          <el-input v-model="form.templateType" />
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="form.documentType" clearable filterable allow-create>
            <el-option v-for="item in documentTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="form.priority">
            <el-radio-button label="HIGH">高</el-radio-button>
            <el-radio-button label="MEDIUM">中</el-radio-button>
            <el-radio-button label="LOW">低</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="业务号">
          <el-input v-model="form.businessNo" />
        </el-form-item>
        <el-form-item label="来源说明">
          <el-input v-model="form.sourceRemark" />
        </el-form-item>
        <el-form-item label="文件">
          <el-upload v-model:file-list="fileList" drag multiple action="#" :auto-upload="false">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或点击选择 PDF、图片、压缩包</div>
          </el-upload>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="uploading" @click="upload">上传接入</el-button>
          <el-button @click="router.push('/documents/records')">接入记录</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>接入结果</template>
      <el-empty v-if="!uploadResult" description="上传后将在这里展示接入与匹配结果" />
      <el-descriptions v-else :column="1" border>
        <el-descriptions-item label="TraceId">{{ uploadResult.traceId }}</el-descriptions-item>
        <el-descriptions-item label="文档编号">{{ uploadResult.documentId }}</el-descriptions-item>
        <el-descriptions-item label="任务编号">{{ uploadResult.taskId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="匹配状态">
          <el-tag :type="uploadResult.matchStatus === 'MATCHED' ? 'success' : 'warning'">
            {{ uploadResult.matchStatus === 'MATCHED' ? '已匹配' : '待确认' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="匹配配置">
          {{ uploadResult.matchedConfigName || '-' }} {{ uploadResult.matchedConfigVersion ? `V${uploadResult.matchedConfigVersion}` : '' }}
        </el-descriptions-item>
        <el-descriptions-item label="匹配说明">{{ uploadResult.matchMessage }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="uploadResult" class="mt-16">
        <el-button type="primary" @click="router.push('/documents/records')">接入记录</el-button>
        <el-button v-if="uploadResult.accessStatus === 'PENDING_CONFIRM'" type="warning" @click="router.push('/documents/unmatched')">去确认</el-button>
      </div>
    </el-card>
  </div>
</template>
