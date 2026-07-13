<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getConfigOptions, type ConfigOptions } from '../api/config'
import {
  createDataPolicy,
  deleteDataPolicy,
  listDataPolicies,
  listSystemRoles,
  listSystemUsers,
  previewDataPolicy,
  updateDataPolicy,
  type DataPolicy,
  type DataPolicyPayload,
  type SystemRole,
  type SystemUser
} from '../api/systemAccess'

const loading = ref(false)
const saving = ref(false)
const activePolicyId = ref('')
const policies = ref<DataPolicy[]>([])
const roles = ref<SystemRole[]>([])
const users = ref<SystemUser[]>([])
const previewEffects = ref<string[]>([])
const options = ref<ConfigOptions>({
  departments: [],
  roles: [],
  categories: [],
  documentTypes: [],
  ocrEngines: [],
  resultTables: [],
  downstreamServices: []
})

const query = reactive({ keyword: '', subjectType: '', status: '' })
const form = reactive<DataPolicyPayload>({
  policyName: '',
  subjectType: 'ROLE',
  subjectId: '',
  subjectName: '',
  dataScope: 'DEPARTMENT',
  allowExport: false,
  status: 'ENABLED',
  departments: [],
  documentTypes: [],
  sourceSystems: [],
  configScopes: [],
  fieldMasking: []
})

const activePolicy = computed(() => policies.value.find((policy) => policy.id === activePolicyId.value))
const enabledPolicyCount = computed(() => policies.value.filter((policy) => policy.status === 'ENABLED').length)
const departmentOptions = computed(() => options.value.departments || [])
const documentTypeOptions = computed(() => options.value.documentTypes || [])
const subjectOptions = computed(() => form.subjectType === 'ROLE'
  ? roles.value.map((role) => ({ value: role.id, label: role.roleName }))
  : users.value.map((user) => ({ value: user.id, label: `${user.userName} (${user.account})` }))
)

const sourceOptions = [
  { label: '手工上传', value: 'MANUAL_UPLOAD' },
  { label: '业务系统API', value: 'API' },
  { label: '邮件分拣', value: 'EMAIL' },
  { label: '文件分拣', value: 'FILE' }
]
const dataScopeOptions = [
  { label: '本人数据', value: 'SELF' },
  { label: '本部门数据', value: 'DEPARTMENT' },
  { label: '本部门 + 指定文档类型', value: 'DEPARTMENT_AND_TYPES' },
  { label: '指定范围', value: 'CUSTOM' },
  { label: '全部只读', value: 'ALL_READONLY' }
]
const fieldOptions = [
  { label: '证件号码 certificate_no', value: 'certificate_no' },
  { label: '交易对手账号 counterparty_account', value: 'counterparty_account' },
  { label: '客户名称 customer_name', value: 'customer_name' },
  { label: '金额 amount', value: 'amount' }
]

const loadPolicies = async () => {
  loading.value = true
  try {
    policies.value = await listDataPolicies(query)
    if (!activePolicyId.value || !policies.value.some((policy) => policy.id === activePolicyId.value)) {
      activePolicyId.value = policies.value[0]?.id || ''
    }
    if (activePolicy.value) applyPolicy(activePolicy.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '数据权限策略加载失败')
  } finally {
    loading.value = false
  }
}

const loadBaseOptions = async () => {
  const [configOptions, roleRows, userRows] = await Promise.all([getConfigOptions(), listSystemRoles({ status: 'ENABLED' }), listSystemUsers({ status: 'ENABLED' })])
  options.value = configOptions
  roles.value = roleRows
  users.value = userRows
}

const applyPolicy = (policy: DataPolicy) => {
  Object.assign(form, {
    policyName: policy.policyName,
    subjectType: policy.subjectType,
    subjectId: policy.subjectId || '',
    subjectName: policy.subjectName || '',
    dataScope: policy.dataScope,
    allowExport: policy.allowExport,
    status: policy.status,
    departments: [...(policy.departments || [])],
    documentTypes: [...(policy.documentTypes || [])],
    sourceSystems: [...(policy.sourceSystems || [])],
    configScopes: [...(policy.configScopes || [])],
    fieldMasking: [...(policy.fieldMasking || [])]
  })
  previewEffects.value = []
}

const selectPolicy = (policy: DataPolicy) => {
  activePolicyId.value = policy.id
  applyPolicy(policy)
}

const newPolicy = () => {
  activePolicyId.value = ''
  Object.assign(form, {
    policyName: '',
    subjectType: 'ROLE',
    subjectId: '',
    subjectName: '',
    dataScope: 'DEPARTMENT',
    allowExport: false,
    status: 'ENABLED',
    departments: [],
    documentTypes: [],
    sourceSystems: [],
    configScopes: [],
    fieldMasking: []
  })
  previewEffects.value = []
}

const resetQuery = () => {
  query.keyword = ''
  query.subjectType = ''
  query.status = ''
  loadPolicies()
}

const syncSubjectName = () => {
  form.subjectName = subjectOptions.value.find((item) => item.value === form.subjectId)?.label || ''
}

const save = async () => {
  try {
    if (!form.policyName || !form.subjectType || !form.dataScope) {
      ElMessage.warning('请填写策略名称、授权对象类型和数据范围')
      return
    }
    syncSubjectName()
    saving.value = true
    const isEdit = Boolean(activePolicyId.value)
    const saved = isEdit ? await updateDataPolicy(activePolicyId.value, form) : await createDataPolicy(form)
    activePolicyId.value = saved.id
    ElMessage.success(isEdit ? '数据权限策略已保存' : '数据权限策略已新增')
    await loadPolicies()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存数据权限策略失败')
  } finally {
    saving.value = false
  }
}

const removePolicy = async () => {
  if (!activePolicyId.value) return
  try {
    await ElMessageBox.confirm('确认删除当前数据权限策略？', '删除策略', { type: 'warning' })
    await deleteDataPolicy(activePolicyId.value)
    ElMessage.success('数据权限策略已删除')
    activePolicyId.value = ''
    await loadPolicies()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除数据权限策略失败')
  }
}

const preview = async () => {
  try {
    syncSubjectName()
    const result = await previewDataPolicy(form)
    previewEffects.value = result.effects || []
    ElMessage.success('已生成权限效果预览')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '权限效果预览失败')
  }
}

onMounted(async () => {
  try {
    await loadBaseOptions()
    await loadPolicies()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '数据权限基础数据加载失败')
  }
})
</script>

<template>
  <div class="role-permission-layout">
    <el-card shadow="never" class="role-list-card">
      <template #header>
        <div class="card-header"><span>数据权限策略</span><el-button type="primary" @click="newPolicy">新增</el-button></div>
      </template>
      <el-input v-model="query.keyword" class="mb-12" clearable placeholder="搜索策略/授权对象" @keyup.enter="loadPolicies" />
      <el-select v-model="query.subjectType" class="mb-12" clearable placeholder="授权对象类型" @change="loadPolicies">
        <el-option label="角色" value="ROLE" />
        <el-option label="用户" value="USER" />
      </el-select>
      <el-select v-model="query.status" class="mb-12" clearable placeholder="状态" @change="loadPolicies">
        <el-option label="启用" value="ENABLED" />
        <el-option label="停用" value="DISABLED" />
      </el-select>
      <div class="mb-12"><el-button type="primary" @click="loadPolicies">查询</el-button><el-button @click="resetQuery">重置</el-button></div>
      <div v-loading="loading">
        <div v-for="policy in policies" :key="policy.id" class="role-item" :class="{ active: activePolicyId === policy.id }" @click="selectPolicy(policy)">
          <strong>{{ policy.policyName }}</strong>
          <span>{{ policy.subjectName || '-' }}</span>
          <em>{{ policy.scopeSummary || '-' }}</em>
        </div>
      </div>
    </el-card>

    <div class="page-stack">
      <section class="metric-grid config-summary">
        <el-card shadow="never" class="metric-card"><span>策略数</span><strong>{{ policies.length }}</strong><em>当前查询</em></el-card>
        <el-card shadow="never" class="metric-card"><span>启用策略</span><strong>{{ enabledPolicyCount }}</strong><em>可用于后续过滤</em></el-card>
      </section>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>策略配置</span>
            <div>
              <el-button @click="preview">预览效果</el-button>
              <el-button type="danger" :disabled="!activePolicyId" @click="removePolicy">删除</el-button>
              <el-button type="primary" :loading="saving" @click="save">保存策略</el-button>
            </div>
          </div>
        </template>
        <el-form :model="form" label-width="130px" class="form-grid">
          <el-form-item label="策略名称" required><el-input v-model="form.policyName" /></el-form-item>
          <el-form-item label="授权对象类型">
            <el-radio-group v-model="form.subjectType" @change="form.subjectId = ''; form.subjectName = ''"><el-radio-button label="ROLE">角色</el-radio-button><el-radio-button label="USER">用户</el-radio-button></el-radio-group>
          </el-form-item>
          <el-form-item label="授权对象">
            <el-select v-model="form.subjectId" filterable clearable @change="syncSubjectName">
              <el-option v-for="item in subjectOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态"><el-select v-model="form.status"><el-option label="启用" value="ENABLED" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
          <el-form-item label="数据范围">
            <el-select v-model="form.dataScope">
              <el-option v-for="item in dataScopeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="允许导出"><el-switch v-model="form.allowExport" /></el-form-item>
          <el-form-item label="可见部门" class="wide">
            <el-select v-model="form.departments" multiple filterable clearable collapse-tags collapse-tags-tooltip>
              <el-option v-for="item in departmentOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="文档类型" class="wide">
            <el-select v-model="form.documentTypes" multiple filterable clearable collapse-tags collapse-tags-tooltip>
              <el-option v-for="item in documentTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源系统" class="wide">
            <el-select v-model="form.sourceSystems" multiple filterable clearable collapse-tags>
              <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="配置范围" class="wide">
            <el-select v-model="form.configScopes" multiple filterable clearable allow-create collapse-tags collapse-tags-tooltip placeholder="可选择或输入配置名称">
              <el-option label="划款指令-运营部-提取配置" value="划款指令-运营部-提取配置" />
              <el-option label="银行回单-资金结果配置" value="银行回单-资金结果配置" />
            </el-select>
          </el-form-item>
          <el-form-item label="脱敏字段" class="wide">
            <el-select v-model="form.fieldMasking" multiple filterable clearable allow-create collapse-tags collapse-tags-tooltip>
              <el-option v-for="item in fieldOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>权限效果预览</template>
        <el-alert title="当前版本仅维护和预览数据权限策略，尚未对任务、结果、落库查询等业务接口启用强制过滤。" type="warning" :closable="false" class="mb-12" />
        <el-table :data="previewEffects.map((effect, index) => ({ scene: `效果 ${index + 1}`, effect }))">
          <el-table-column prop="scene" label="场景" width="150" />
          <el-table-column prop="effect" label="权限效果" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>
