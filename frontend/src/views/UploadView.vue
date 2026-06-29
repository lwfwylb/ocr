<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'

const router = useRouter()
const uploaded = ref(false)
const form = reactive({
  departmentId: 'ops',
  documentType: '划款指令',
  priority: 'HIGH',
  businessNo: 'BIZ-20260628-001',
  sourceRemark: '手工上传测试'
})

const upload = () => {
  uploaded.value = true
  ElMessage.success('已模拟上传并创建任务')
}
</script>

<template>
  <div class="two-column">
    <el-card shadow="never">
      <template #header>上传文档</template>
      <el-form :model="form" label-width="110px">
        <el-form-item label="所属部门">
          <el-select v-model="form.departmentId">
            <el-option label="运营部" value="ops" />
            <el-option label="财务部" value="finance" />
            <el-option label="产品部" value="product" />
          </el-select>
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="form.documentType" clearable>
            <el-option label="划款指令" value="划款指令" />
            <el-option label="银行回单" value="银行回单" />
            <el-option label="开户资料" value="开户资料" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="form.priority">
            <el-radio-button label="HIGH">高</el-radio-button>
            <el-radio-button label="MEDIUM">中</el-radio-button>
            <el-radio-button label="LOW">低</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="业务流水号">
          <el-input v-model="form.businessNo" />
        </el-form-item>
        <el-form-item label="来源说明">
          <el-input v-model="form.sourceRemark" />
        </el-form-item>
        <el-form-item label="文件">
          <el-upload drag multiple action="#" :auto-upload="false">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或点击选择 PDF/图片/ZIP</div>
          </el-upload>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="upload">模拟上传</el-button>
          <el-button @click="router.push('/tasks')">查看任务中心</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>上传结果</template>
      <el-empty v-if="!uploaded" description="上传后将在这里展示任务创建结果" />
      <el-descriptions v-else :column="1" border>
        <el-descriptions-item label="documentId">DOC-20260628-0001</el-descriptions-item>
        <el-descriptions-item label="taskId">TASK-20260628-0001</el-descriptions-item>
        <el-descriptions-item label="匹配状态"><el-tag type="success">已匹配</el-tag></el-descriptions-item>
        <el-descriptions-item label="命中配置">划款指令-运营部-V1</el-descriptions-item>
        <el-descriptions-item label="任务状态"><el-tag type="warning">待复核</el-tag></el-descriptions-item>
      </el-descriptions>
      <el-button v-if="uploaded" type="primary" class="mt-16" @click="router.push('/tasks')">查看任务</el-button>
    </el-card>
  </div>
</template>
