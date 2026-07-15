<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPromptTemplateDefaults,
  resetPromptTemplateDefaults,
  updatePromptTemplateDefaults
} from '../api/model'

const loading = ref(false)
const saving = ref(false)
const form = reactive({
  systemTemplate: '',
  userTemplate: '',
  updatedAt: ''
})

const loadTemplates = async () => {
  loading.value = true
  try {
    const data = await getPromptTemplateDefaults()
    form.systemTemplate = data.systemTemplate || ''
    form.userTemplate = data.userTemplate || ''
    form.updatedAt = data.updatedAt || ''
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提示词模板加载失败')
  } finally {
    loading.value = false
  }
}

const saveTemplates = async () => {
  if (!form.systemTemplate.trim()) {
    ElMessage.warning('请维护系统提示词模板')
    return
  }
  if (!form.userTemplate.trim()) {
    ElMessage.warning('请维护用户提示词模板')
    return
  }
  if (!form.userTemplate.includes('${fields}')) {
    ElMessage.warning('用户提示词模板必须包含 ${fields} 变量')
    return
  }
  saving.value = true
  try {
    const data = await updatePromptTemplateDefaults({
      systemTemplate: form.systemTemplate.trim(),
      userTemplate: form.userTemplate.trim()
    })
    form.systemTemplate = data.systemTemplate || ''
    form.userTemplate = data.userTemplate || ''
    form.updatedAt = data.updatedAt || ''
    ElMessage.success('提示词模板已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提示词模板保存失败')
  } finally {
    saving.value = false
  }
}

const resetTemplates = async () => {
  try {
    await ElMessageBox.confirm('确认恢复为系统内置提示词模板？当前模板内容会被覆盖。', '恢复默认', {
      type: 'warning'
    })
    saving.value = true
    const data = await resetPromptTemplateDefaults()
    form.systemTemplate = data.systemTemplate || ''
    form.userTemplate = data.userTemplate || ''
    form.updatedAt = data.updatedAt || ''
    ElMessage.success('已恢复内置默认模板')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '恢复默认失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadTemplates)
</script>

<template>
  <div class="page-stack" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <h2>提示词模板</h2>
            <p class="muted">维护配置向导新增配置时使用的默认提示词。已保存的配置不会被自动覆盖。</p>
          </div>
          <div class="header-actions">
            <el-button @click="loadTemplates">刷新</el-button>
            <el-button @click="resetTemplates">恢复内置默认</el-button>
            <el-button type="primary" :loading="saving" @click="saveTemplates">保存</el-button>
          </div>
        </div>
      </template>

      <div class="template-grid">
        <el-card shadow="never" class="template-card">
          <template #header>
            <div class="template-title">
              <span>系统提示词模板</span>
              <el-tag size="small">SYSTEM</el-tag>
            </div>
          </template>
          <el-input v-model="form.systemTemplate" type="textarea" :rows="10" />
        </el-card>

        <el-card shadow="never" class="template-card">
          <template #header>
            <div class="template-title">
              <span>用户提示词模板</span>
              <el-tag size="small" type="success">USER</el-tag>
            </div>
          </template>
          <el-alert
            class="mb-12"
            title="仅支持 ${fields} 变量，配置向导会用字段编码、字段名称、字段说明自动替换。"
            type="info"
            :closable="false"
          />
          <el-input v-model="form.userTemplate" type="textarea" :rows="8" />
        </el-card>
      </div>

      <div class="template-footer muted">
        最近更新时间：{{ form.updatedAt || '暂无' }}
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.template-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.template-card :deep(.el-card__body) {
  padding: 12px;
}

.template-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.template-footer {
  margin-top: 12px;
  text-align: right;
}

@media (max-width: 1100px) {
  .template-grid {
    grid-template-columns: 1fr;
  }
}
</style>
