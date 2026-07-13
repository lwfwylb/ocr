<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createDictItem,
  createDictType,
  deleteDictItem,
  deleteDictType,
  disableDictItem,
  disableDictType,
  enableDictItem,
  enableDictType,
  listDictItems,
  listDictTypes,
  updateDictItem,
  updateDictType,
  type DictItem,
  type DictItemPayload,
  type DictType,
  type DictTypePayload
} from '../api/dictionary'

const loading = ref(false)
const typeDialogVisible = ref(false)
const itemDialogVisible = ref(false)
const editingTypeId = ref('')
const editingItemId = ref('')
const activeDictCode = ref('')
const types = ref<DictType[]>([])
const items = ref<DictItem[]>([])
const allEnabledItems = ref<DictItem[]>([])

const query = reactive({
  keyword: '',
  status: ''
})

const itemQuery = reactive({
  keyword: '',
  enabled: ''
})

const typeForm = reactive<DictTypePayload>({
  dictCode: '',
  dictName: '',
  usageScene: '',
  status: 'ENABLED',
  sortNo: 100,
  remark: ''
})

const itemForm = reactive<DictItemPayload>({
  dictCode: '',
  itemValue: '',
  itemLabel: '',
  parentValue: '',
  sortNo: 100,
  enabled: true,
  extraJson: '',
  remark: ''
})

const activeType = computed(() => types.value.find((item) => item.dictCode === activeDictCode.value) || types.value[0])
const enabledTypeCount = computed(() => types.value.filter((item) => item.status === 'ENABLED').length)
const enabledItemCount = computed(() => items.value.filter((item) => item.enabled).length)
const parentDictMap: Record<string, string> = {
  BUSINESS_SUB_CATEGORY: 'BUSINESS_CATEGORY',
  TEMPLATE_TYPE: 'BUSINESS_SUB_CATEGORY'
}
const parentOptions = computed(() => {
  const parentDictCode = parentDictMap[itemForm.dictCode]
  if (parentDictCode) {
    return allEnabledItems.value.filter((item) => item.dictCode === parentDictCode)
  }
  return allEnabledItems.value.filter((item) => item.enabled && item.itemValue !== itemForm.itemValue)
})

const loadTypes = async () => {
  loading.value = true
  try {
    types.value = await listDictTypes(query)
    if (!activeDictCode.value || !types.value.some((item) => item.dictCode === activeDictCode.value)) {
      activeDictCode.value = types.value[0]?.dictCode || ''
    }
    await loadItems()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '字典类型加载失败')
  } finally {
    loading.value = false
  }
}

const loadItems = async () => {
  if (!activeDictCode.value) {
    items.value = []
    return
  }
  try {
    items.value = await listDictItems({
      dictCode: activeDictCode.value,
      keyword: itemQuery.keyword,
      enabled: itemQuery.enabled
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '字典项加载失败')
  }
}

const loadAllEnabledItems = async () => {
  allEnabledItems.value = await listDictItems({ enabled: 'true' })
}

const selectType = async (row: DictType) => {
  activeDictCode.value = row.dictCode
  itemQuery.keyword = ''
  itemQuery.enabled = ''
  await loadItems()
}

const resetTypeQuery = () => {
  query.keyword = ''
  query.status = ''
  loadTypes()
}

const resetItemQuery = () => {
  itemQuery.keyword = ''
  itemQuery.enabled = ''
  loadItems()
}

const openCreateType = () => {
  editingTypeId.value = ''
  Object.assign(typeForm, {
    dictCode: '',
    dictName: '',
    usageScene: '',
    status: 'ENABLED',
    sortNo: 100,
    remark: ''
  })
  typeDialogVisible.value = true
}

const openEditType = (row: DictType) => {
  editingTypeId.value = row.id
  Object.assign(typeForm, {
    dictCode: row.dictCode,
    dictName: row.dictName,
    usageScene: row.usageScene || '',
    status: row.status,
    sortNo: row.sortNo || 100,
    remark: row.remark || ''
  })
  typeDialogVisible.value = true
}

const saveType = async () => {
  try {
    const saved = editingTypeId.value
      ? await updateDictType(editingTypeId.value, typeForm)
      : await createDictType(typeForm)
    activeDictCode.value = saved.dictCode
    typeDialogVisible.value = false
    ElMessage.success(editingTypeId.value ? '字典类型已更新' : '字典类型已新增')
    await loadTypes()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存字典类型失败')
  }
}

const toggleType = async (row: DictType) => {
  try {
    const saved = row.status === 'ENABLED' ? await disableDictType(row.id) : await enableDictType(row.id)
    Object.assign(row, saved)
    ElMessage.success(saved.status === 'ENABLED' ? '字典类型已启用' : '字典类型已停用')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新字典类型状态失败')
  }
}

const removeType = async (row: DictType) => {
  try {
    await ElMessageBox.confirm(`确认删除字典类型「${row.dictName}」？需先删除该类型下所有字典项。`, '删除字典类型', {
      type: 'warning'
    })
    await deleteDictType(row.id)
    ElMessage.success('字典类型已删除')
    if (activeDictCode.value === row.dictCode) activeDictCode.value = ''
    await loadTypes()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除字典类型失败')
  }
}

const openCreateItem = async () => {
  if (!activeType.value) {
    ElMessage.info('请先选择字典类型')
    return
  }
  await loadAllEnabledItems()
  editingItemId.value = ''
  Object.assign(itemForm, {
    dictCode: activeType.value.dictCode,
    itemValue: '',
    itemLabel: '',
    parentValue: '',
    sortNo: 100,
    enabled: true,
    extraJson: '',
    remark: ''
  })
  itemDialogVisible.value = true
}

const openEditItem = async (row: DictItem) => {
  await loadAllEnabledItems()
  editingItemId.value = row.id
  Object.assign(itemForm, {
    dictCode: row.dictCode,
    itemValue: row.itemValue,
    itemLabel: row.itemLabel,
    parentValue: row.parentValue || '',
    sortNo: row.sortNo || 100,
    enabled: row.enabled,
    extraJson: row.extraJson || '',
    remark: row.remark || ''
  })
  itemDialogVisible.value = true
}

const saveItem = async () => {
  try {
    const saved = editingItemId.value
      ? await updateDictItem(editingItemId.value, itemForm)
      : await createDictItem(itemForm)
    itemDialogVisible.value = false
    activeDictCode.value = saved.dictCode
    ElMessage.success(editingItemId.value ? '字典项已更新' : '字典项已新增')
    await loadItems()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存字典项失败')
  }
}

const toggleItem = async (row: DictItem) => {
  try {
    const saved = row.enabled ? await disableDictItem(row.id) : await enableDictItem(row.id)
    Object.assign(row, saved)
    ElMessage.success(saved.enabled ? '字典项已启用' : '字典项已停用')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新字典项状态失败')
  }
}

const removeItem = async (row: DictItem) => {
  try {
    await ElMessageBox.confirm(`确认删除字典项「${row.itemLabel}」？`, '删除字典项', {
      type: 'warning'
    })
    await deleteDictItem(row.id)
    ElMessage.success('字典项已删除')
    await loadItems()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除字典项失败')
  }
}

onMounted(loadTypes)
</script>

<template>
  <div v-loading="loading" class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>字典类型</span><strong>{{ types.length }}</strong><em>统一维护下拉来源</em></el-card>
      <el-card shadow="never" class="metric-card"><span>启用类型</span><strong>{{ enabledTypeCount }}</strong><em>可被业务引用</em></el-card>
      <el-card shadow="never" class="metric-card"><span>当前字典项</span><strong>{{ items.length }}</strong><em>{{ activeType?.dictName || '-' }}</em></el-card>
      <el-card shadow="never" class="metric-card"><span>启用字典项</span><strong>{{ enabledItemCount }}</strong><em>下拉框可见</em></el-card>
    </section>

    <div class="dict-layout">
      <el-card shadow="never" class="dict-sidebar">
        <template #header>
          <div class="card-header">
            <span>字典类型</span>
            <el-button type="primary" @click="openCreateType">新增</el-button>
          </div>
        </template>
        <el-form :model="query" class="compact-search">
          <el-form-item>
            <el-input v-model="query.keyword" clearable placeholder="编码/名称/场景" @keyup.enter="loadTypes" />
          </el-form-item>
          <el-form-item>
            <el-select v-model="query.status" clearable placeholder="全部状态">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadTypes">查询</el-button>
            <el-button @click="resetTypeQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="types" stripe height="520" @row-click="selectType">
          <el-table-column prop="dictName" label="名称" min-width="140" />
          <el-table-column prop="dictCode" label="编码" min-width="150" show-overflow-tooltip />
          <el-table-column label="状态" width="72">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openEditType(row)">编辑</el-button>
              <el-button link :type="row.status === 'ENABLED' ? 'warning' : 'success'" @click.stop="toggleType(row)">
                {{ row.status === 'ENABLED' ? '停用' : '启用' }}
              </el-button>
              <el-button link type="danger" @click.stop="removeType(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" class="dict-main">
        <template #header>
          <div class="card-header">
            <div>
              <span>字典项</span>
              <p class="muted">{{ activeType ? `${activeType.dictName}（${activeType.dictCode}）` : '请选择字典类型' }}</p>
            </div>
            <el-button type="primary" :disabled="!activeType" @click="openCreateItem">新增字典项</el-button>
          </div>
        </template>
        <el-form :inline="true" :model="itemQuery" class="search-form compact-search">
          <el-form-item label="关键字">
            <el-input v-model="itemQuery.keyword" clearable placeholder="字典值/显示名称" @keyup.enter="loadItems" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="itemQuery.enabled" clearable placeholder="全部">
              <el-option label="启用" value="true" />
              <el-option label="停用" value="false" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadItems">查询</el-button>
            <el-button @click="resetItemQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="items" stripe>
          <el-table-column prop="itemLabel" label="显示名称" min-width="160" fixed />
          <el-table-column prop="itemValue" label="字典值" min-width="170" />
          <el-table-column prop="parentValue" label="父级值" min-width="130" />
          <el-table-column prop="sortNo" label="排序" width="80" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
          <el-table-column prop="updatedAt" label="更新时间" min-width="160" />
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEditItem(row)">编辑</el-button>
              <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggleItem(row)">
                {{ row.enabled ? '停用' : '启用' }}
              </el-button>
              <el-button link type="danger" @click="removeItem(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <el-dialog v-model="typeDialogVisible" :title="editingTypeId ? '编辑字典类型' : '新增字典类型'" width="620px">
      <el-form :model="typeForm" label-width="100px" class="form-grid">
        <el-form-item label="字典编码" required><el-input v-model="typeForm.dictCode" placeholder="如 BUSINESS_CATEGORY" /></el-form-item>
        <el-form-item label="字典名称" required><el-input v-model="typeForm.dictName" placeholder="如 业务分类" /></el-form-item>
        <el-form-item label="使用场景"><el-input v-model="typeForm.usageScene" placeholder="如 配置向导" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="typeForm.status">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="typeForm.sortNo" :min="0" :max="9999" /></el-form-item>
        <el-form-item label="备注" class="wide"><el-input v-model="typeForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveType">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemDialogVisible" :title="editingItemId ? '编辑字典项' : '新增字典项'" width="720px">
      <el-form :model="itemForm" label-width="100px" class="form-grid">
        <el-form-item label="所属字典">
          <el-select v-model="itemForm.dictCode" filterable>
            <el-option v-for="type in types" :key="type.dictCode" :label="`${type.dictName} (${type.dictCode})`" :value="type.dictCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="字典值" required><el-input v-model="itemForm.itemValue" placeholder="保存到配置中的值" /></el-form-item>
        <el-form-item label="显示名称" required><el-input v-model="itemForm.itemLabel" placeholder="界面展示名称" /></el-form-item>
        <el-form-item label="父级值">
          <el-select v-model="itemForm.parentValue" filterable clearable allow-create placeholder="可选择或输入父级字典值">
            <el-option v-for="item in parentOptions" :key="item.id" :label="`${item.itemLabel} (${item.itemValue})`" :value="item.itemValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="itemForm.sortNo" :min="0" :max="9999" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="itemForm.enabled" /></el-form-item>
        <el-form-item label="扩展参数" class="wide"><el-input v-model="itemForm.extraJson" type="textarea" :rows="3" placeholder='可选 JSON，如 {"color":"blue"}' /></el-form-item>
        <el-form-item label="备注" class="wide"><el-input v-model="itemForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dict-layout {
  display: grid;
  grid-template-columns: 430px minmax(0, 1fr);
  gap: 12px;
}

.dict-sidebar,
.dict-main {
  min-width: 0;
}
</style>
