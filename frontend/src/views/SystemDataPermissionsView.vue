<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

const activePolicy = ref('policy-ops-reviewer')
const policies = [
  { id: 'policy-ops-reviewer', name: '运营复核岗数据权限', subject: '复核人员', scope: '本部门 + 指定文档类型' },
  { id: 'policy-finance-user', name: '财务用户数据权限', subject: '普通业务用户', scope: '本部门数据' },
  { id: 'policy-auditor', name: '审计查询数据权限', subject: '审计人员', scope: '全部只读' }
]

const form = reactive({
  policyName: '运营复核岗数据权限',
  subjectType: 'ROLE',
  subject: '复核人员',
  dataScope: 'DEPARTMENT_AND_TYPES',
  departments: ['运营部'],
  documentTypes: ['划款指令', '开户资料'],
  sourceSystems: ['MANUAL_UPLOAD', 'EMAIL'],
  configScopes: ['划款指令-运营部-提取配置'],
  fieldMasking: ['certificate_no', 'counterparty_account'],
  allowExport: false
})

const departmentRoleBindings = ref([
  { department: '运营部', role: '复核人员', resource: '划款指令-运营部-提取配置', permission: '查看任务、复核字段、查看结果' },
  { department: '运营部', role: '模板配置员', resource: '划款指令-运营部-提取配置', permission: '查看配置、编辑草稿、验证配置' },
  { department: '财务部', role: '普通业务用户', resource: '银行回单-资金结果配置', permission: '查看本部门任务和结果' }
])

const save = () => ElMessage.success('已模拟保存数据权限策略')
</script>

<template>
  <div class="role-permission-layout">
    <el-card shadow="never" class="role-list-card">
      <template #header>
        <div class="card-header"><span>数据权限策略</span><el-button type="primary">新增</el-button></div>
      </template>
      <div
        v-for="policy in policies"
        :key="policy.id"
        class="role-item"
        :class="{ active: activePolicy === policy.id }"
        @click="activePolicy = policy.id"
      >
        <strong>{{ policy.name }}</strong>
        <span>{{ policy.subject }}</span>
        <em>{{ policy.scope }}</em>
      </div>
    </el-card>

    <div class="page-stack">
      <el-card shadow="never">
        <template #header>
          <div class="card-header"><span>策略配置</span><el-button type="primary" @click="save">保存策略</el-button></div>
        </template>
        <el-form :model="form" label-width="130px" class="form-grid">
          <el-form-item label="策略名称"><el-input v-model="form.policyName" /></el-form-item>
          <el-form-item label="授权对象类型">
            <el-radio-group v-model="form.subjectType"><el-radio-button label="ROLE">角色</el-radio-button><el-radio-button label="USER">用户</el-radio-button></el-radio-group>
          </el-form-item>
          <el-form-item label="授权对象"><el-select v-model="form.subject"><el-option label="复核人员" value="复核人员" /><el-option label="普通业务用户" value="普通业务用户" /></el-select></el-form-item>
          <el-form-item label="数据范围">
            <el-select v-model="form.dataScope">
              <el-option label="本人数据" value="SELF" />
              <el-option label="本部门数据" value="DEPARTMENT" />
              <el-option label="本部门 + 指定文档类型" value="DEPARTMENT_AND_TYPES" />
              <el-option label="全部只读" value="ALL_READONLY" />
            </el-select>
          </el-form-item>
          <el-form-item label="可见部门" class="wide">
            <el-select v-model="form.departments" multiple filterable clearable collapse-tags>
              <el-option label="运营部" value="运营部" />
              <el-option label="财务部" value="财务部" />
              <el-option label="产品部" value="产品部" />
            </el-select>
          </el-form-item>
          <el-form-item label="文档类型" class="wide">
            <el-select v-model="form.documentTypes" multiple filterable clearable collapse-tags>
              <el-option label="划款指令" value="划款指令" />
              <el-option label="银行回单" value="银行回单" />
              <el-option label="开户资料" value="开户资料" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源系统" class="wide">
            <el-select v-model="form.sourceSystems" multiple filterable clearable collapse-tags>
              <el-option label="手工上传" value="MANUAL_UPLOAD" />
              <el-option label="API" value="API" />
              <el-option label="邮件分拣" value="EMAIL" />
            </el-select>
          </el-form-item>
          <el-form-item label="配置范围" class="wide">
            <el-select v-model="form.configScopes" multiple filterable clearable collapse-tags collapse-tags-tooltip>
              <el-option label="划款指令-运营部-提取配置" value="划款指令-运营部-提取配置" />
              <el-option label="银行回单-资金结果配置" value="银行回单-资金结果配置" />
              <el-option label="开户资料-客户信息配置" value="开户资料-客户信息配置" />
            </el-select>
          </el-form-item>
          <el-form-item label="脱敏字段" class="wide">
            <el-select v-model="form.fieldMasking" multiple filterable clearable collapse-tags>
              <el-option label="证件号码 certificate_no" value="certificate_no" />
              <el-option label="交易对手账号 counterparty_account" value="counterparty_account" />
              <el-option label="客户名称 customer_name" value="customer_name" />
            </el-select>
          </el-form-item>
          <el-form-item label="允许导出"><el-switch v-model="form.allowExport" /></el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>部门角色绑定矩阵</span>
            <el-button>新增绑定</el-button>
          </div>
        </template>
        <el-alert
          title="同一个角色在不同部门可以拥有不同数据范围；访问时按 用户-部门-角色-配置范围 联合判断。"
          type="info"
          :closable="false"
          class="mb-12"
        />
        <el-table :data="departmentRoleBindings">
          <el-table-column label="部门" min-width="120">
            <template #default="{ row }">
              <el-select v-model="row.department" filterable>
                <el-option label="运营部" value="运营部" />
                <el-option label="财务部" value="财务部" />
                <el-option label="产品部" value="产品部" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="角色" min-width="130">
            <template #default="{ row }">
              <el-select v-model="row.role" filterable>
                <el-option label="普通业务用户" value="普通业务用户" />
                <el-option label="复核人员" value="复核人员" />
                <el-option label="模板配置员" value="模板配置员" />
                <el-option label="部门管理员" value="部门管理员" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="配置/资源范围" min-width="210">
            <template #default="{ row }">
              <el-select v-model="row.resource" filterable>
                <el-option label="划款指令-运营部-提取配置" value="划款指令-运营部-提取配置" />
                <el-option label="银行回单-资金结果配置" value="银行回单-资金结果配置" />
                <el-option label="开户资料-客户信息配置" value="开户资料-客户信息配置" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column prop="permission" label="权限效果" min-width="240" />
        </el-table>
      </el-card>

      <el-card shadow="never">
        <template #header>权限效果预览</template>
        <el-alert title="后端所有任务、结果、落库数据查询接口都必须应用该数据权限过滤，不允许仅前端控制。" type="warning" :closable="false" class="mb-12" />
        <el-table
          :data="[
            { scene: '任务中心', effect: '仅查看运营部的划款指令、开户资料任务' },
            { scene: '结果中心', effect: '交易对手账号脱敏展示' },
            { scene: '落库数据查询', effect: '仅查询授权表中满足部门与文档类型的数据' },
            { scene: '配置中心', effect: '仅运营部复核人员/模板配置员可查看授权配置' },
            { scene: '导出', effect: form.allowExport ? '允许导出' : '禁止导出' }
          ]"
        >
          <el-table-column prop="scene" label="场景" width="150" />
          <el-table-column prop="effect" label="权限效果" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>
