<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

interface UserItem {
  userId: string
  userName: string
  account: string
  department: string
  role: string
  status: 'ENABLED' | 'DISABLED'
  authMode: 'LOCAL' | 'SSO'
  lastLogin: string
}

const drawerVisible = ref(false)
const selectedUser = ref<UserItem | null>(null)
const query = reactive({
  keyword: '',
  departments: [] as string[],
  roles: [] as string[],
  statuses: [] as string[]
})

const users = ref<UserItem[]>([
  { userId: 'U001', userName: '王老师', account: 'wang.ops', department: '运营部', role: '复核人员', status: 'ENABLED', authMode: 'SSO', lastLogin: '2026-06-29 08:45:00' },
  { userId: 'U002', userName: '李老师', account: 'li.finance', department: '财务部', role: '普通业务用户', status: 'ENABLED', authMode: 'SSO', lastLogin: '2026-06-28 19:20:00' },
  { userId: 'U003', userName: '赵老师', account: 'zhao.config', department: '运营部', role: '模板配置员', status: 'ENABLED', authMode: 'LOCAL', lastLogin: '2026-06-28 17:12:00' },
  { userId: 'U004', userName: '陈老师', account: 'chen.product', department: '产品部', role: '部门管理员', status: 'ENABLED', authMode: 'SSO', lastLogin: '2026-06-27 16:08:00' },
  { userId: 'U005', userName: '系统接入账号', account: 'api.dispatch', department: '平台', role: '系统账号', status: 'DISABLED', authMode: 'LOCAL', lastLogin: '-' }
])

const filteredUsers = computed(() => {
  return users.value.filter((user) => {
    const keywordMatched = !query.keyword || user.userName.includes(query.keyword) || user.account.includes(query.keyword)
    const deptMatched = query.departments.length === 0 || query.departments.includes(user.department)
    const roleMatched = query.roles.length === 0 || query.roles.includes(user.role)
    const statusMatched = query.statuses.length === 0 || query.statuses.includes(user.status)
    return keywordMatched && deptMatched && roleMatched && statusMatched
  })
})

const openDetail = (user: UserItem) => {
  selectedUser.value = user
  drawerVisible.value = true
}

const toggleStatus = (user: UserItem) => {
  user.status = user.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  ElMessage.success(user.status === 'ENABLED' ? '已启用用户' : '已停用用户')
}

const resetQuery = () => {
  query.keyword = ''
  query.departments = []
  query.roles = []
  query.statuses = []
}
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>用户总数</span><strong>{{ users.length }}</strong><em>含系统账号</em></el-card>
      <el-card shadow="never" class="metric-card"><span>启用用户</span><strong>{{ users.filter((u) => u.status === 'ENABLED').length }}</strong><em>可登录</em></el-card>
      <el-card shadow="never" class="metric-card"><span>SSO 用户</span><strong>{{ users.filter((u) => u.authMode === 'SSO').length }}</strong><em>统一认证</em></el-card>
      <el-card shadow="never" class="metric-card"><span>本地账号</span><strong>{{ users.filter((u) => u.authMode === 'LOCAL').length }}</strong><em>平台维护</em></el-card>
      <el-card shadow="never" class="metric-card"><span>部门数</span><strong>4</strong><em>数据隔离</em></el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="姓名/账号" /></el-form-item>
        <el-form-item label="部门">
          <el-select v-model="query.departments" multiple filterable clearable collapse-tags collapse-tags-tooltip placeholder="全部">
            <el-option label="运营部" value="运营部" />
            <el-option label="财务部" value="财务部" />
            <el-option label="产品部" value="产品部" />
            <el-option label="平台" value="平台" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="query.roles" multiple filterable clearable collapse-tags collapse-tags-tooltip placeholder="全部">
            <el-option label="普通业务用户" value="普通业务用户" />
            <el-option label="复核人员" value="复核人员" />
            <el-option label="模板配置员" value="模板配置员" />
            <el-option label="部门管理员" value="部门管理员" />
            <el-option label="系统账号" value="系统账号" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.statuses" multiple filterable clearable collapse-tags placeholder="全部">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>用户列表</span>
          <div><el-button>同步统一权限用户</el-button><el-button type="primary">新增用户</el-button></div>
        </div>
      </template>
      <el-table :data="filteredUsers" stripe>
        <el-table-column prop="userName" label="姓名" width="100" fixed />
        <el-table-column prop="account" label="账号" min-width="140" />
        <el-table-column prop="department" label="部门" width="100" />
        <el-table-column prop="role" label="角色" min-width="130" />
        <el-table-column label="认证方式" width="100">
          <template #default="{ row }"><el-tag :type="row.authMode === 'SSO' ? 'success' : 'info'">{{ row.authMode }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="row.status === 'ENABLED' ? 'success' : 'danger'">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="lastLogin" label="最后登录" min-width="150" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link>编辑</el-button>
            <el-button link :type="row.status === 'ENABLED' ? 'danger' : 'success'" @click="toggleStatus(row)">{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="用户详情" size="520px">
      <template v-if="selectedUser">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="姓名">{{ selectedUser.userName }}</el-descriptions-item>
          <el-descriptions-item label="账号">{{ selectedUser.account }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ selectedUser.department }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ selectedUser.role }}</el-descriptions-item>
          <el-descriptions-item label="认证方式">{{ selectedUser.authMode }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="section-title">数据权限摘要</h3>
        <el-tag>本人数据</el-tag>
        <el-tag type="primary">本部门数据</el-tag>
        <el-tag type="warning">划款指令</el-tag>
      </template>
    </el-drawer>
  </div>
</template>
