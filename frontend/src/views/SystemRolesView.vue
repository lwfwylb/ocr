<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

interface RoleItem {
  roleCode: string
  roleName: string
  description: string
  userCount: number
  status: 'ENABLED' | 'DISABLED'
}

const activeRoleCode = ref('reviewer')
const query = reactive({ keyword: '', statuses: [] as string[] })
const roles = ref<RoleItem[]>([
  { roleCode: 'biz_user', roleName: '普通业务用户', description: '上传文档、查看本人或本部门任务', userCount: 28, status: 'ENABLED' },
  { roleCode: 'reviewer', roleName: '复核人员', description: '处理低置信度和高风险字段复核', userCount: 9, status: 'ENABLED' },
  { roleCode: 'template_admin', roleName: '模板配置员', description: '维护文档类型、解析配置、提取配置', userCount: 6, status: 'ENABLED' },
  { roleCode: 'dept_admin', roleName: '部门管理员', description: '管理本部门用户、配置和任务', userCount: 5, status: 'ENABLED' },
  { roleCode: 'auditor', roleName: '审计人员', description: '查看审计日志和处理链路', userCount: 2, status: 'DISABLED' }
])

const permissionTree = ref([
  {
    id: 'dashboard',
    label: '工作台',
    children: [{ id: 'dashboard:view', label: '查看工作台' }]
  },
  {
    id: 'document',
    label: '文档接入',
    children: [
      { id: 'document:upload', label: '手工上传' },
      { id: 'document:records', label: '接入记录' }
    ]
  },
  {
    id: 'config',
    label: '配置中心',
    children: [
      { id: 'config:view', label: '查看配置' },
      { id: 'config:create', label: '新建配置' },
      { id: 'config:publish', label: '发布配置' }
    ]
  },
  {
    id: 'review',
    label: '复核中心',
    children: [
      { id: 'review:view', label: '查看复核任务' },
      { id: 'review:submit', label: '提交复核' }
    ]
  }
])

const checkedPermissions = ref(['dashboard:view', 'document:upload', 'review:view', 'review:submit'])
const activeRole = computed(() => roles.value.find((role) => role.roleCode === activeRoleCode.value) || roles.value[0])
const filteredRoles = computed(() => {
  return roles.value.filter((role) => {
    const keywordMatched = !query.keyword || role.roleName.includes(query.keyword) || role.description.includes(query.keyword)
    const statusMatched = query.statuses.length === 0 || query.statuses.includes(role.status)
    return keywordMatched && statusMatched
  })
})

const savePermissions = () => ElMessage.success('已模拟保存角色权限')
</script>

<template>
  <div class="role-permission-layout">
    <el-card shadow="never" class="role-list-card">
      <template #header>
        <div class="card-header">
          <span>角色列表</span>
          <el-button type="primary">新增</el-button>
        </div>
      </template>
      <el-input v-model="query.keyword" class="mb-12" clearable placeholder="搜索角色" />
      <div
        v-for="role in filteredRoles"
        :key="role.roleCode"
        class="role-item"
        :class="{ active: role.roleCode === activeRoleCode }"
        @click="activeRoleCode = role.roleCode"
      >
        <strong>{{ role.roleName }}</strong>
        <span>{{ role.description }}</span>
        <em>{{ role.userCount }} 人</em>
      </div>
    </el-card>

    <div class="page-stack">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>{{ activeRole.roleName }}</span>
            <div><el-button>复制角色</el-button><el-button type="primary" @click="savePermissions">保存权限</el-button></div>
          </div>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="角色编码">{{ activeRole.roleCode }}</el-descriptions-item>
          <el-descriptions-item label="用户数">{{ activeRole.userCount }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="activeRole.status === 'ENABLED' ? 'success' : 'danger'">{{ activeRole.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never">
        <template #header>菜单与按钮权限</template>
        <el-tree
          :data="permissionTree"
          show-checkbox
          node-key="id"
          default-expand-all
          :default-checked-keys="checkedPermissions"
          :props="{ label: 'label', children: 'children' }"
        />
      </el-card>

      <el-card shadow="never">
        <template #header>接口权限摘要</template>
        <el-table
          :data="[
            { api: '/api/review-tasks', method: 'GET', permission: 'review:view' },
            { api: '/api/review-tasks/{id}/submit', method: 'POST', permission: 'review:submit' },
            { api: '/api/tasks', method: 'GET', permission: 'task:view' }
          ]"
        >
          <el-table-column prop="api" label="接口" />
          <el-table-column prop="method" label="方法" width="90" />
          <el-table-column prop="permission" label="权限点" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>
