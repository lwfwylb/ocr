<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getConfigOptions, type ConfigOptions } from '../api/config'
import {
  createSystemUser,
  disableSystemUser,
  enableSystemUser,
  listSystemRoles,
  listSystemUsers,
  updateSystemUser,
  type SystemRole,
  type SystemUser,
  type SystemUserPayload
} from '../api/systemAccess'

const loading = ref(false)
const dialogVisible = ref(false)
const drawerVisible = ref(false)
const editingUserId = ref('')
const selectedUser = ref<SystemUser | null>(null)
const users = ref<SystemUser[]>([])
const roles = ref<SystemRole[]>([])
const options = ref<ConfigOptions>({
  departments: [],
  roles: [],
  categories: [],
  documentTypes: [],
  ocrEngines: [],
  resultTables: [],
  downstreamServices: []
})

const query = reactive({
  keyword: '',
  departmentId: '',
  status: ''
})

const form = reactive<SystemUserPayload>({
  userCode: '',
  userName: '',
  account: '',
  departmentId: '',
  authMode: 'SSO',
  status: 'ENABLED',
  email: '',
  mobile: '',
  roleIds: []
})

const enabledCount = computed(() => users.value.filter((u) => u.status === 'ENABLED').length)
const ssoCount = computed(() => users.value.filter((u) => u.authMode === 'SSO').length)
const localCount = computed(() => users.value.filter((u) => u.authMode === 'LOCAL').length)
const departmentCount = computed(() => new Set(users.value.map((u) => u.departmentId).filter(Boolean)).size)
const departmentOptions = computed(() => options.value.departments || [])
const roleOptions = computed(() => roles.value.filter((role) => role.status === 'ENABLED'))

const loadUsers = async () => {
  loading.value = true
  try {
    users.value = await listSystemUsers(query)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '用户加载失败')
  } finally {
    loading.value = false
  }
}

const loadBaseOptions = async () => {
  const [configOptions, roleRows] = await Promise.all([getConfigOptions(), listSystemRoles({ status: 'ENABLED' })])
  options.value = configOptions
  roles.value = roleRows
}

const resetQuery = () => {
  query.keyword = ''
  query.departmentId = ''
  query.status = ''
  loadUsers()
}

const openCreate = () => {
  editingUserId.value = ''
  Object.assign(form, {
    userCode: '',
    userName: '',
    account: '',
    departmentId: '',
    authMode: 'SSO',
    status: 'ENABLED',
    email: '',
    mobile: '',
    roleIds: []
  })
  dialogVisible.value = true
}

const openEdit = (row: SystemUser) => {
  editingUserId.value = row.id
  Object.assign(form, {
    userCode: row.userCode || '',
    userName: row.userName,
    account: row.account,
    departmentId: row.departmentId,
    authMode: row.authMode,
    status: row.status,
    email: row.email || '',
    mobile: row.mobile || '',
    roleIds: row.roleIds || []
  })
  dialogVisible.value = true
}

const saveUser = async () => {
  try {
    if (!form.userName || !form.account || !form.departmentId) {
      ElMessage.warning('请填写姓名、账号和所属部门')
      return
    }
    editingUserId.value ? await updateSystemUser(editingUserId.value, form) : await createSystemUser(form)
    ElMessage.success(editingUserId.value ? '用户已更新' : '用户已新增')
    dialogVisible.value = false
    await loadUsers()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存用户失败')
  }
}

const toggleStatus = async (user: SystemUser) => {
  try {
    const saved = user.status === 'ENABLED' ? await disableSystemUser(user.id) : await enableSystemUser(user.id)
    Object.assign(user, saved)
    ElMessage.success(saved.status === 'ENABLED' ? '用户已启用' : '用户已停用')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新用户状态失败')
  }
}

const openDetail = (user: SystemUser) => {
  selectedUser.value = user
  drawerVisible.value = true
}

const departmentLabel = (value?: string) => departmentOptions.value.find((item) => item.value === value)?.label || value || '-'

onMounted(async () => {
  try {
    await loadBaseOptions()
    await loadUsers()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '系统管理基础数据加载失败')
  }
})
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>用户总数</span><strong>{{ users.length }}</strong><em>含系统账号</em></el-card>
      <el-card shadow="never" class="metric-card"><span>启用用户</span><strong>{{ enabledCount }}</strong><em>可登录</em></el-card>
      <el-card shadow="never" class="metric-card"><span>SSO 用户</span><strong>{{ ssoCount }}</strong><em>统一认证</em></el-card>
      <el-card shadow="never" class="metric-card"><span>本地账号</span><strong>{{ localCount }}</strong><em>平台维护</em></el-card>
      <el-card shadow="never" class="metric-card"><span>部门数</span><strong>{{ departmentCount }}</strong><em>数据归属</em></el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键字"><el-input v-model="query.keyword" clearable placeholder="姓名/账号" @keyup.enter="loadUsers" /></el-form-item>
        <el-form-item label="部门">
          <el-select v-model="query.departmentId" filterable clearable placeholder="全部">
            <el-option v-for="item in departmentOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="loadUsers">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>用户列表</span>
          <el-button type="primary" @click="openCreate">新增用户</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="users" stripe>
        <el-table-column prop="userName" label="姓名" width="110" fixed />
        <el-table-column prop="account" label="账号" min-width="140" />
        <el-table-column label="部门" width="110"><template #default="{ row }">{{ departmentLabel(row.departmentId) }}</template></el-table-column>
        <el-table-column label="角色" min-width="180"><template #default="{ row }">{{ row.roleNames?.join('、') || '-' }}</template></el-table-column>
        <el-table-column label="认证方式" width="100"><template #default="{ row }"><el-tag :type="row.authMode === 'SSO' ? 'success' : 'info'">{{ row.authMode }}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 'ENABLED' ? 'success' : 'danger'">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column prop="lastLogin" label="最后登录" min-width="160" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 'ENABLED' ? 'danger' : 'success'" @click="toggleStatus(row)">{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingUserId ? '编辑用户' : '新增用户'" width="760px">
      <el-form :model="form" label-width="100px" class="form-grid">
        <el-form-item label="用户编码"><el-input v-model="form.userCode" placeholder="不填默认使用账号" /></el-form-item>
        <el-form-item label="姓名" required><el-input v-model="form.userName" /></el-form-item>
        <el-form-item label="账号" required><el-input v-model="form.account" /></el-form-item>
        <el-form-item label="部门" required>
          <el-select v-model="form.departmentId" filterable clearable>
            <el-option v-for="item in departmentOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" class="wide">
          <el-select v-model="form.roleIds" multiple filterable clearable collapse-tags collapse-tags-tooltip>
            <el-option v-for="role in roleOptions" :key="role.id" :label="role.roleName" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="认证方式">
          <el-select v-model="form.authMode"><el-option label="统一认证 SSO" value="SSO" /><el-option label="本地账号" value="LOCAL" /></el-select>
        </el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status"><el-option label="启用" value="ENABLED" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.mobile" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="saveUser">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="drawerVisible" title="用户详情" size="520px">
      <template v-if="selectedUser">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="姓名">{{ selectedUser.userName }}</el-descriptions-item>
          <el-descriptions-item label="账号">{{ selectedUser.account }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ departmentLabel(selectedUser.departmentId) }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ selectedUser.roleNames?.join('、') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="认证方式">{{ selectedUser.authMode }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ selectedUser.status === 'ENABLED' ? '启用' : '停用' }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="section-title">数据权限摘要</h3>
        <el-alert title="当前版本仅维护用户与角色基础数据，尚未对其他业务页面启用权限过滤。" type="info" :closable="false" />
      </template>
    </el-drawer>
  </div>
</template>
