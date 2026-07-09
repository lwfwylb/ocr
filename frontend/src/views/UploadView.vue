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
  { label: 'Operations', value: 'OPS' },
  { label: 'Finance', value: 'FINANCE' },
  { label: 'Product', value: 'PRODUCT' }
]
const documentTypeOptions = [
  { label: 'Payment Instruction', value: 'PAYMENT_INSTRUCTION' },
  { label: 'Bank Receipt', value: 'BANK_RECEIPT' },
  { label: 'Account Opening', value: 'ACCOUNT_OPENING' }
]
const form = reactive({
  departmentId: 'OPS',
  category: 'FUND_BUSINESS',
  subCategory: 'PAYMENT_INSTRUCTION',
  templateType: 'GENERAL_PAYMENT_INSTRUCTION_TEMPLATE',
  documentType: 'PAYMENT_INSTRUCTION',
  priority: 'HIGH',
  businessNo: 'BIZ-20260628-001',
  sourceRemark: 'Manual upload'
})

const upload = async () => {
  const selectedFile = fileList.value[0]
  const fileName = selectedFile?.name || `manual_upload_${Date.now()}.pdf`
  uploading.value = true
  try {
    uploadResult.value = await manualUploadDocument({
      sourceSystem: form.sourceRemark || 'Manual Upload',
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
    ElMessage.success('Document accepted')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Upload failed')
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="two-column">
    <el-card shadow="never">
      <template #header>Upload Document</template>
      <el-form :model="form" label-width="120px">
        <el-form-item label="Department">
          <el-select v-model="form.departmentId">
            <el-option v-for="item in departmentOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="Category">
          <el-input v-model="form.category" />
        </el-form-item>
        <el-form-item label="Sub Category">
          <el-input v-model="form.subCategory" />
        </el-form-item>
        <el-form-item label="Template Type">
          <el-input v-model="form.templateType" />
        </el-form-item>
        <el-form-item label="Document Type">
          <el-select v-model="form.documentType" clearable filterable allow-create>
            <el-option v-for="item in documentTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="Priority">
          <el-radio-group v-model="form.priority">
            <el-radio-button label="HIGH">High</el-radio-button>
            <el-radio-button label="MEDIUM">Medium</el-radio-button>
            <el-radio-button label="LOW">Low</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="Business No">
          <el-input v-model="form.businessNo" />
        </el-form-item>
        <el-form-item label="Source Note">
          <el-input v-model="form.sourceRemark" />
        </el-form-item>
        <el-form-item label="File">
          <el-upload v-model:file-list="fileList" drag multiple action="#" :auto-upload="false">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">Drop files here or click to select PDF/images/ZIP</div>
          </el-upload>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="uploading" @click="upload">Upload</el-button>
          <el-button @click="router.push('/documents/records')">Access Records</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>Upload Result</template>
      <el-empty v-if="!uploadResult" description="The access and match result will be shown here" />
      <el-descriptions v-else :column="1" border>
        <el-descriptions-item label="TraceId">{{ uploadResult.traceId }}</el-descriptions-item>
        <el-descriptions-item label="Document Id">{{ uploadResult.documentId }}</el-descriptions-item>
        <el-descriptions-item label="Task Id">{{ uploadResult.taskId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Match">
          <el-tag :type="uploadResult.matchStatus === 'MATCHED' ? 'success' : 'warning'">
            {{ uploadResult.matchStatus === 'MATCHED' ? 'Matched' : 'Pending' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Matched Config">
          {{ uploadResult.matchedConfigName || '-' }} {{ uploadResult.matchedConfigVersion ? `V${uploadResult.matchedConfigVersion}` : '' }}
        </el-descriptions-item>
        <el-descriptions-item label="Message">{{ uploadResult.matchMessage }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="uploadResult" class="mt-16">
        <el-button type="primary" @click="router.push('/documents/records')">Access Records</el-button>
        <el-button v-if="uploadResult.accessStatus === 'PENDING_CONFIRM'" type="warning" @click="router.push('/documents/unmatched')">Confirm</el-button>
      </div>
    </el-card>
  </div>
</template>
