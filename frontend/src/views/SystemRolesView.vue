<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createSystemRole,
  deleteSystemRole,
  disableSystemRole,
  enableSystemRole,
  getPermissionTree,
  getRolePermissions,
  listSystemRoles,
  saveRolePermissions,
  updateSystemRole,
  type PermissionNode,
  type SystemRole,
  type SystemRolePayload
} from '../api/systemAccess'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingRoleId = ref('')
const activeRoleId = ref('')
const treeRef = ref<any>()
const roles = ref<SystemRole[]>([])
const permissionTree = ref<PermissionNode[]>([])
const checkedPermissions = ref<string[]>([])
const query = reactive({ keyword: '', status: '' })

const form = reactive<SystemRolePayload>({
  roleCode: '',
  roleName: '',
  description: '',
  status: 'ENABLED',
  sortNo: 100
})

const activeRole = computed(() => roles.value.find((role) => role.id === activeRoleId.value) || roles.value[0])
const enabledRoleCount = computed(() => roles.value.filter((role) => role.status === 'ENABLED').length)
const userCount = computed(() => roles.value.reduce((sum, role) => sum + Number(role.userCount || 0), 0))

const loadRoles = async () => {
  loading.value = true
  try {
    roles.value = await listSystemRoles(query)
    if (!activeRoleId.value || !roles.value.some((role) => role.id === activeRoleId.value)) {
      activeRoleId.value = roles.value[0]?.id || ''
    }
    await loadRolePermissions()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '角色加载失败')
  } finally {
    loading.value = false
  }
}

const loadRolePermissions = async () => {
  if (!activeRoleId.value) {
    checkedPermissions.value = []
    return
  }
  checkedPermissions.value = await getRolePermissions(activeRoleId.value)
  await nextTick()
  treeRef.value?.setCheckedKeys(checkedPermissions.value)
}

const selectRole = async (role: SystemRole) => {
  activeRoleId.value = role.id
  await loadRolePermissions()
}

const resetQuery = () => {
  query.keyword = ''
  query.status = ''
  loadRoles()
}

const openCreate = () => {
  editingRoleId.value = ''
  Object.assign(form, { roleCode: '', roleName: '', description: '', status: 'ENABLED', sortNo: 100 })
  dialogVisible.value = true
}

const openEdit = (role: SystemRole) => {
  editingRoleId.value = role.id
  Object.assign(form, {
    roleCode: role.roleCode,
    roleName: role.roleName,
    description: role.description || '',
    status: role.status,
    sortNo: role.sortNo || 100
  })
  dialogVisible.value = true
}

const saveRole = async () => {
  try {
    if (!form.roleCode || !form.roleName) {
      ElMessage.warning('请填写角色编码和角色名称')
      return
    }
    const saved = editingRoleId.value ? await updateSystemRole(editingRoleId.value, form) : await createSystemRole(form)
    activeRoleId.value = saved.id
    dialogVisible.value = false
    ElMessage.success(editingRoleId.value ? '角色已更新' : '角色已新增')
    await loadRoles()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存角色失败')
  }
}

const toggleRole = async (role: SystemRole) => {
  try {
    const saved = role.status === 'ENABLED' ? await disableSystemRole(role.id) : await enableSystemRole(role.id)
    Object.assign(role, saved)
    ElMessage.success(saved.status === 'ENABLED' ? '角色已启用' : '角色已停用')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新角色状态失败')
  }
}

const removeRole = async (role: SystemRole) => {
  try {
    await ElMessageBox.confirm(`确认删除角色「${role.roleName}」？删除前系统会校验用户绑定和数据权限策略引用。`, '删除角色', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
    await deleteSystemRole(role.id)
    ElMessage.success('角色已删除')
    if (activeRoleId.value === role.id) activeRoleId.value = ''
    await loadRoles()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除角色失败')
  }
}

const savePermissions = async () => {
  if (!activeRole.value) return
  saving.value = true
  try {
    const checked = treeRef.value?.getCheckedKeys(false) || []
    const halfChecked = treeRef.value?.getHalfCheckedKeys?.() || []
    checkedPermissions.value = await saveRolePermissions(activeRole.value.id, Array.from(new Set([...checked, ...halfChecked])))
    await nextTick()
    treeRef.value?.setCheckedKeys(checkedPermissions.value)
    ElMessage.success('角色权限已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存角色权限失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    permissionTree.value = await getPermissionTree()
    await loadRoles()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '角色权限基础数据加载失败')
  }
})
</script>

<template>
  <div class="role-permission-layout">
    <el-card shadow="never" class="role-list-card">
      <template #header>
        <div class="card-header">
          <span>角色列表</span>
          <el-button type="primary" @click="openCreate">新增</el-button>
        </div>
      </template>
      <el-input v-model="query.keyword" class="mb-12" clearable placeholder="搜索角色" @keyup.enter="loadRoles" />
      <el-select v-model="query.status" class="mb-12" clearable placeholder="全部状态" @change="loadRoles">
        <el-option label="启用" value="ENABLED" />
        <el-option label="停用" value="DISABLED" />
      </el-select>
      <div class="mb-12"><el-button type="primary" @click="loadRoles">查询</el-button><el-button @click="resetQuery">重置</el-button></div>
      <div v-loading="loading">
        <div
          v-for="role in roles"
          :key="role.id"
          class="role-item"
          :class="{ active: role.id === activeRoleId }"
          @click="selectRole(role)"
        >
          <strong>{{ role.roleName }}</strong>
          <span>{{ role.description || '-' }}</span>
          <em>{{ role.userCount || 0 }} 人</em>
        </div>
      </div>
    </el-card>

    <div class="page-stack">
      <section class="metric-grid config-summary">
        <el-card shadow="never" class="metric-card"><span>角色数</span><strong>{{ roles.length }}</strong><em>当前查询</em></el-card>
        <el-card shadow="never" class="metric-card"><span>启用角色</span><strong>{{ enabledRoleCount }}</strong><em>可授权</em></el-card>
        <el-card shadow="never" class="metric-card"><span>绑定用户</span><strong>{{ userCount }}</strong><em>按角色统计</em></el-card>
      </section>

      <el-card shadow="never" v-if="activeRole">
        <template #header>
          <div class="card-header">
            <span>{{ activeRole.roleName }}</span>
            <div>
              <el-button @click="openEdit(activeRole)">编辑角色</el-button>
              <el-button :type="activeRole.status === 'ENABLED' ? 'warning' : 'success'" @click="toggleRole(activeRole)">{{ activeRole.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
              <el-button type="danger" @click="removeRole(activeRole)">删除角色</el-button>
              <el-button type="primary" :loading="saving" @click="savePermissions">保存权限</el-button>
            </div>
          </div>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="角色编码">{{ activeRole.roleCode }}</el-descriptions-item>
          <el-descriptions-item label="用户数">{{ activeRole.userCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="activeRole.status === 'ENABLED' ? 'success' : 'danger'">{{ activeRole.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never">
        <template #header>菜单与按钮权限</template>
        <el-alert title="当前版本只维护权限数据，不会影响其他页面菜单和按钮显示。" type="info" :closable="false" class="mb-12" />
        <el-tree
          ref="treeRef"
          :data="permissionTree"
          show-checkbox
          node-key="id"
          default-expand-all
          :props="{ label: 'label', children: 'children' }"
        />
      </el-card>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingRoleId ? '编辑角色' : '新增角色'" width="620px">
      <el-form :model="form" label-width="100px" class="form-grid">
        <el-form-item label="角色编码" required><el-input v-model="form.roleCode" placeholder="如 reviewer" /></el-form-item>
        <el-form-item label="角色名称" required><el-input v-model="form.roleName" placeholder="如 复核人员" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status"><el-option label="启用" value="ENABLED" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" :max="9999" /></el-form-item>
        <el-form-item label="描述" class="wide"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="saveRole">保存</el-button></template>
    </el-dialog>
  </div>
</template>
