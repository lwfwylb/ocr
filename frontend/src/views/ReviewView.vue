<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fieldResults, tasks, type FieldResult } from '../mock/data'
import DocumentPreview from '../components/DocumentPreview.vue'
import ConfidenceTag from '../components/ConfidenceTag.vue'

const activeField = ref('amount')
const fields = ref(fieldResults.map((item) => ({ ...item })))
const task = computed(() => tasks.find((item) => item.status === 'WAIT_REVIEW') || tasks[0])

const submit = () => {
  ElMessage.success('已模拟提交复核，任务状态更新为已完成')
}

const selectField = (row: FieldResult) => {
  activeField.value = row.fieldCode
}
</script>

<template>
  <div class="review-layout">
    <el-card shadow="never" class="review-doc">
      <template #header>
        <div class="card-header">
          <span>{{ task.fileName }}</span>
          <el-tag type="warning">待复核</el-tag>
        </div>
      </template>
      <DocumentPreview :active-field="activeField" />
    </el-card>

    <el-card shadow="never" class="review-fields">
      <template #header>
        <div class="card-header">
          <span>字段复核</span>
          <span class="muted">{{ task.taskId }}</span>
        </div>
      </template>
      <el-alert title="低于 90% 置信度或高风险字段需要人工确认。" type="warning" :closable="false" />
      <el-table :data="fields" class="mt-12" @row-click="selectField">
        <el-table-column prop="fieldName" label="字段" width="110" />
        <el-table-column label="置信度" width="86">
          <template #default="{ row }"><ConfidenceTag :value="row.confidence" /></template>
        </el-table-column>
        <el-table-column label="人工确认值" min-width="170">
          <template #default="{ row }">
            <el-input v-model="row.finalValue" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="异常" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.reviewRequired" type="warning">{{ row.issue || '需复核' }}</el-tag>
            <el-tag v-else type="success">通过</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-card shadow="never" class="evidence-card">
        <template #header>证据文本</template>
        <p>{{ fields.find((field) => field.fieldCode === activeField)?.evidence }}</p>
      </el-card>

      <div class="actions-bar">
        <el-button>保存草稿</el-button>
        <el-button>退回重提取</el-button>
        <el-button>标记无法识别</el-button>
        <el-button type="primary" @click="submit">通过复核</el-button>
      </div>
    </el-card>
  </div>
</template>
