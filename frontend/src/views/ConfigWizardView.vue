<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  copyExtractConfig,
  createExtractConfigDraft,
  getConfigOptions,
  getExtractConfigDetail,
  getResultTableDetail,
  publishExtractConfig,
  updateExtractConfigDraft,
  validateExtractConfig,
  type ConfigOptions,
  type ConfigValidateResult
} from '../api/config'
import {
  getPromptTemplateDefaults,
  listLlmModelOptions,
  listOcrEngineOptions,
  type LlmModelOption,
  type OcrEngineOption,
  type PromptTemplateDefaults
} from '../api/model'

type TransformRuleType = 'DICT' | 'API' | 'SQL'
type PreprocessStepType = 'PAGE_RANGE' | 'KEYWORD_FILTER' | 'PDF_TO_IMAGE'
type TraditionalRuleType = 'REGEX' | 'TABLE_COLUMN' | 'FIXED_CELL' | 'KEY_VALUE' | 'METADATA'
type ImageQuality = 'FAST_150' | 'STANDARD_300' | 'HIGH_450'
type TransformOutputMode = 'OVERWRITE_INPUT' | 'WRITE_TARGET' | 'DERIVE_FIELD'
type ResultFieldSourceType = 'EXTRACTED' | 'DERIVED' | 'SYSTEM'
type DictMatchMode = 'EQUALS' | 'CONTAINS' | 'REGEX' | 'RANGE'
type ValidationRuleType = 'REQUIRED' | 'FORMAT' | 'RANGE' | 'CROSS_FIELD' | 'UNIQUE' | 'MASTER_DATA'

interface DictItem {
  source: string
  target: string
}

interface TransformRule {
  id: string
  ruleName: string
  ruleType: TransformRuleType
  inputField: string
  outputField: string
  outputMode?: TransformOutputMode
  conditionEnabled?: boolean
  conditionField?: string
  conditionOperator?: 'NOT_EMPTY' | 'EQUALS' | 'CONTAINS'
  conditionValue?: string
  enabled: boolean
  onFail: 'KEEP_ORIGINAL' | 'SET_NULL' | 'BLOCK' | 'REVIEW'
  dictMatchMode?: DictMatchMode
  dictItems: DictItem[]
  apiEndpoint: string
  apiMethod: 'GET' | 'POST'
  apiParamName: string
  apiResponsePath: string
  apiTimeout?: number
  apiRetryCount?: number
  apiAuthMode?: 'SYSTEM' | 'NONE'
  apiSuccessRule?: string
  sqlDatasource: string
  sqlText: string
  sqlResultColumn: string
  sqlMaxRows?: number
  sqlReadonlyChecked?: boolean
}

interface ValidationRule {
  id: string
  ruleName: string
  ruleType: ValidationRuleType
  fieldCode: string
  enabled: boolean
  severity: 'WARN' | 'REVIEW' | 'BLOCK'
  expression: string
  failMessage: string
}

interface PreprocessStep {
  id: string
  stepType: PreprocessStepType
  enabled: boolean
  pageRanges: string
  includeKeywords: string[]
  excludeKeywords: string[]
  imageQuality: ImageQuality
  dpi: number
  imageFormat: 'PNG' | 'JPEG'
}

type OptionItem = Record<string, any>

const initialConfigFields = [
  { fieldCode: 'payer_name', fieldName: '付款方名称', sourceType: 'EXTRACTED', dataType: 'string', fieldLength: 200, required: true, multiple: false, targetColumn: 'payer_name' },
  { fieldCode: 'payee_account', fieldName: '收款账号', sourceType: 'EXTRACTED', dataType: 'string', fieldLength: 64, required: true, multiple: false, targetColumn: 'payee_account' },
  { fieldCode: 'amount', fieldName: '划款金额', sourceType: 'EXTRACTED', dataType: 'amount', fieldLength: 18, required: true, multiple: false, targetColumn: 'amount' }
]

const activeStep = ref(0)
const route = useRoute()
const router = useRouter()
const currentConfigId = ref('')
const currentConfigCode = ref('')
const currentVersion = ref(1)
const currentStatus = ref('DRAFT')
const saving = ref(false)
const options = ref<ConfigOptions>({
  departments: [],
  roles: [],
  categories: [],
  documentTypes: [],
  ocrEngines: [],
  resultTables: [],
  downstreamServices: []
})
const llmModelOptions = ref<LlmModelOption[]>([])
const ocrEngineOptions = ref<OcrEngineOption[]>([])
const fields = ref(initialConfigFields.map((item) => ({ ...item })))
fields.value.forEach((field) => {
  const mutable = field as any
  mutable.fieldDescription = mutable.fieldDescription || `从文档中识别${field.fieldName}`
  mutable.extractRequired = field.required
  mutable.extractByRegex = field.fieldCode === 'amount' || field.fieldCode === 'payee_account'
  mutable.traditionalRuleEnabled = mutable.extractByRegex
  mutable.traditionalRuleType = 'REGEX'
  mutable.traditionalRuleConfig = {}
  mutable.regexPattern =
    field.fieldCode === 'amount'
      ? '(?:金额|付款金额|划款金额)[:：]?\\s*([0-9,]+(?:\\.\\d{1,2})?)'
      : field.fieldCode === 'payee_account'
        ? '(?:收款账号|账号)[:：]?\\s*([0-9*\\s]+)'
        : ''
  mutable.regexFlags = ''
  mutable.regexGroup = 1
})
const transformRules = ref<TransformRule[]>([])
transformRules.value.forEach((rule) => {
  rule.outputMode = rule.outputMode || 'DERIVE_FIELD'
  rule.conditionEnabled = rule.conditionEnabled ?? Boolean(rule.inputField)
  rule.conditionField = rule.conditionField || rule.inputField
  rule.conditionOperator = rule.conditionOperator || 'NOT_EMPTY'
  rule.conditionValue = rule.conditionValue || ''
  rule.dictMatchMode = rule.dictMatchMode || (rule.ruleType === 'DICT' ? 'EQUALS' : 'EQUALS')
  rule.apiTimeout = rule.apiTimeout ?? 5
  rule.apiRetryCount = rule.apiRetryCount ?? 1
  rule.apiAuthMode = rule.apiAuthMode || 'SYSTEM'
  rule.apiSuccessRule = rule.apiSuccessRule || '$.code == 0'
  rule.sqlMaxRows = rule.sqlMaxRows ?? 1
  rule.sqlReadonlyChecked = rule.sqlReadonlyChecked ?? true
})
const selectedRuleId = ref(transformRules.value[0]?.id || '')
const activeProcessTab = ref('transform')
const validationRules = ref<ValidationRule[]>([])
const selectedExtractFieldCode = ref(fields.value[0]?.fieldCode || '')
const mappingProfileDrawerVisible = ref(false)
const derivedFieldDialogVisible = ref(false)
const ddlPreviewVisible = ref(false)
const aiEnabled = ref(true)
const activeExtractTab = ref('ai')
const fallbackSystemPromptTemplate = '你是基金公司智能要素提取助手。请严格根据输入的文档内容提取信息，不允许编造。无法识别的字段返回null。请严格按用户提示词要求输出JSON，不输出解释性文字。'
const fallbackUserPromptTemplate = '请从文档内容中提取要素：${fields}。要求无法识别的字段返回null，严格JSON格式输出。'
const promptTemplateDefaults = ref<PromptTemplateDefaults | null>(null)
const aiSystemPrompt = ref(fallbackSystemPromptTemplate)
const aiUserPrompt = ref('')
const promptAutoSync = ref(true)
const promptOutdated = ref(false)
const suppressPromptInput = ref(false)
const regexSampleText = ref('付款方：示例基金管理有限公司\n收款账号：6222 **** 8910\n金额：100000.00\n划款日期：2026年6月28日')
const regexPreview = ref('100000.00')
const regexPreviewMap = ref<Record<string, string>>({
  amount: '100000.00',
  payee_account: '6222 **** 8910'
})
const validationReport = ref<ConfigValidateResult | null>(null)
const regexEnabledCount = computed(() => fields.value.filter((field) => (field as any).extractByRegex).length)
const regexConfiguredCount = computed(() =>
  fields.value.filter((field) => (field as any).extractByRegex && (field as any).regexPattern?.trim()).length
)
const regexValidatedCount = computed(() =>
  fields.value.filter((field) => {
    const result = regexPreviewMap.value[(field as any).fieldCode]
    return (field as any).extractByRegex && isExecutableTraditionalRule(field) && Boolean(result)
  }).length
)
const regexFailedCount = computed(() =>
  fields.value.filter((field) => {
    const result = regexPreviewMap.value[(field as any).fieldCode]
    return isExecutableTraditionalRule(field) && (result === '未匹配' || result === '正则错误')
  }).length
)
const traditionalRuleTypeOptions: Array<{ label: string; value: TraditionalRuleType; executable: boolean }> = [
  { label: '文本正则', value: 'REGEX', executable: true },
  { label: '表格列映射', value: 'TABLE_COLUMN', executable: false },
  { label: '固定单元格', value: 'FIXED_CELL', executable: false },
  { label: '关键字附近', value: 'KEY_VALUE', executable: false },
  { label: '文件元数据', value: 'METADATA', executable: false }
]
const traditionalRuleTypeLabel = (type?: TraditionalRuleType | string) =>
  traditionalRuleTypeOptions.find((item) => item.value === type)?.label || '文本正则'
const isExecutableTraditionalRule = (field: any) => (field.traditionalRuleType || 'REGEX') === 'REGEX'
const traditionalRuleStatusText = (field: any) =>
  isExecutableTraditionalRule(field) ? '已接入执行器' : '待接入解析器后执行'
const isRuleFirstStrategy = computed(() => form.defaultStrategy === 'RULE_FIRST_AI_FALLBACK')
const extractStrategyGuideText = computed(() =>
  isRuleFirstStrategy.value
    ? '当前为“传统规则优先，AI 兜底”：建议先维护字段级传统规则并验证样本文档；如担心传统规则运行异常，再维护 AI 提示词作为报错兜底。'
    : '当前为“AI 优先，传统规则兜底”：建议先维护 AI 提示词；如担心 AI 调用失败、返回非 JSON 或解析异常，再为关键字段补充传统规则兜底。'
)
const extractStrategyRuntimeText = computed(() =>
  isRuleFirstStrategy.value
    ? '执行时先按字段传统规则取数；P0 仅文本正则真实执行，其他类型先保存配置并提示待接入。仅当主规则执行异常时调用 AI 兜底。未匹配不视为报错，会进入复核或后续校验。'
    : '执行时先调用 AI 一次性提取全部字段；仅当 AI 调用失败、返回非 JSON 或解析异常时执行字段传统规则兜底。低置信度不自动触发传统规则，会进入复核。'
)
const regexStrategyHelpText = computed(() =>
  isRuleFirstStrategy.value
    ? '所有字段一次性展示。传统规则在当前策略下作为主取数规则；P0 仅文本正则执行，表格列映射、固定单元格等类型先作为配置入口保存。'
    : '所有字段一次性展示。传统规则在当前策略下作为 AI 报错后的兜底规则；AI 低置信度不会自动触发传统规则兜底。'
)
const fieldStrategyDescription = computed(() =>
  isRuleFirstStrategy.value
    ? '传统规则优先：先执行字段传统规则；仅主规则执行异常时调用 AI。未匹配字段进入复核/校验。'
    : 'AI 优先：先调用 AI；仅 AI 执行异常时执行字段传统规则。低置信度进入复核。'
)
const preprocessEnabled = ref(false)
const preprocessSteps = ref<PreprocessStep[]>([
  {
    id: 'pre-page',
    stepType: 'PAGE_RANGE',
    enabled: false,
    pageRanges: '',
    includeKeywords: [],
    excludeKeywords: [],
    imageQuality: 'STANDARD_300',
    dpi: 300,
    imageFormat: 'PNG'
  },
  {
    id: 'pre-keyword',
    stepType: 'KEYWORD_FILTER',
    enabled: false,
    pageRanges: '',
    includeKeywords: [],
    excludeKeywords: [],
    imageQuality: 'STANDARD_300',
    dpi: 300,
    imageFormat: 'PNG'
  },
  {
    id: 'pre-image',
    stepType: 'PDF_TO_IMAGE',
    enabled: false,
    pageRanges: '',
    includeKeywords: [],
    excludeKeywords: [],
    imageQuality: 'STANDARD_300',
    dpi: 300,
    imageFormat: 'PNG'
  }
])
const previewInput = ref('100000-1000000')
const previewOutput = ref('大额')
const derivedFieldForm = reactive({
  fieldCode: '',
  fieldName: '',
  fieldDescription: '',
  dataType: 'string',
  targetColumn: '',
  autoAddTargetColumn: true
})
const form = reactive({
  configName: '',
  category: '',
  subCategory: '',
  templateType: '',
  documentType: '',
  departmentId: '',
  visibleRoles: [] as string[],
  ownerRole: '',
  tags: [] as string[],
  defaultPriority: 'HIGH',
  engineCode: '',
  outputFormat: 'Markdown',
  parseMode: 'FULL',
  pageBatchSize: 10,
  storageEnabled: true,
  storageMode: 'REUSE',
  mappingProfileName: '划款指令-资金结果表映射',
  targetTable: 'ext_fund_business_result',
  targetTableName: '基金业务要素结果表',
  targetTableComment: '保存基金申购、赎回、划款、回单等业务场景的结构化要素结果',
  saveMode: 'SINGLE',
  outputMode: 'SINGLE',
  llmModelCode: '',
  defaultStrategy: 'AI_FIRST_RULE_FALLBACK',
  confidenceThreshold: 0.9,
  reviewerRole: '运营复核岗',
  transformEnabled: false,
  validationEnabled: false,
  pushEnabled: true,
  pushTrigger: 'REVIEW_APPROVED',
  targetServices: ['fund_ops_result_receive', 'dw_extract_result_topic'],
  pushScope: 'MAPPED_FIELDS',
  pushMode: 'SYNC',
  idempotentKey: '${traceId}-${taskId}-${serviceCode}',
  pushFailStrategy: 'RETRY_THEN_MANUAL'
})

const steps = ['基础信息', '解析配置', '字段与落库配置', '提取策略', '加工校验', '复核策略', '下游推送', '验证发布']
const existingTables = computed(() =>
  options.value.resultTables.map((table: OptionItem) => ({
    label: table.label || `${table.tableCode || table.value} - ${table.tableName || table.name || ''}`,
    value: table.tableCode || table.value,
    tableName: table.tableName || table.name || table.label || table.value,
    comment: table.comment || ''
  }))
)
const isReuseStorageMode = computed(() => form.storageMode === 'REUSE')
const isStorageEnabled = computed(() => form.storageEnabled)
const storageDisabledTip = '关闭后，平台仅完成解析、提取、加工、校验、复核和下游推送，不写入结果表，也不生成 DDL。'
const categoryOptions = computed<OptionItem[]>(() => options.value.categories || [])
const subCategoryOptions = computed<OptionItem[]>(() => {
  return (categoryOptions.value.find((item: OptionItem) => item.value === form.category)?.children || []) as OptionItem[]
})
const addConfigTag = (value: string) => {
  const tag = value.trim()
  if (!tag) return
  if (!form.tags.includes(tag)) form.tags.push(tag)
}
const templateTypeOptions = computed<string[]>(() => {
  return (subCategoryOptions.value.find((item: OptionItem) => item.value === form.subCategory)?.templates || []) as string[]
})
const preprocessStepTypeLabel: Record<PreprocessStepType, string> = {
  PAGE_RANGE: '指定页码/页范围',
  KEYWORD_FILTER: '包含/排除关键字',
  PDF_TO_IMAGE: 'PDF 转图片'
}
const imageQualityOptions: Array<{ label: string; value: ImageQuality; dpi: number }> = [
  { label: '快速（150 DPI）', value: 'FAST_150', dpi: 150 },
  { label: '标准（300 DPI，推荐）', value: 'STANDARD_300', dpi: 300 },
  { label: '高清（450 DPI）', value: 'HIGH_450', dpi: 450 }
]
const updateImageQuality = (row: PreprocessStep) => {
  row.dpi = imageQualityOptions.find((option) => option.value === row.imageQuality)?.dpi || 300
}
const preprocessPipelinePreview = computed(() => {
  if (!preprocessEnabled.value) return '全文直接进入 OCR/文档解析引擎，不执行预处理。'
  const enabledSteps = preprocessSteps.value.filter((step) => step.enabled)
  if (!enabledSteps.length) return '已启用预处理，但尚未启用任何处理步骤。'
  return enabledSteps
    .map((step, index) => {
      const stepName = preprocessStepTypeLabel[step.stepType]
      if (step.stepType === 'PAGE_RANGE') return `${index + 1}. ${stepName}: 解析页 ${step.pageRanges || '全文'}`
      if (step.stepType === 'KEYWORD_FILTER') return `${index + 1}. ${stepName}: 包含「${step.includeKeywords.join('、') || '未配置'}」，排除「${step.excludeKeywords.join('、') || '未配置'}」`
      return `${index + 1}. ${stepName}: ${imageQualityOptions.find((option) => option.value === step.imageQuality)?.label || `${step.dpi} DPI`}，${step.imageFormat}`
    })
    .join('\n')
})
const supportedPreprocessSteps = (steps: any[] = []) =>
  steps.filter((step) => ['PAGE_RANGE', 'KEYWORD_FILTER', 'PDF_TO_IMAGE'].includes(step?.stepType))
const targetTableColumns = ref([
  { columnName: 'biz_no', columnCnName: '业务编号', dbType: 'varchar', length: 64, precision: undefined, scale: undefined, required: false, defaultValue: '', validationRule: '唯一性校验' },
  { columnName: 'source_system', columnCnName: '来源系统', dbType: 'varchar', length: 32, precision: undefined, scale: undefined, required: true, defaultValue: 'OCR', validationRule: '非空' },
  { columnName: 'document_type', columnCnName: '文档类型', dbType: 'varchar', length: 64, precision: undefined, scale: undefined, required: true, defaultValue: '', validationRule: '非空' },
  { columnName: 'payer_name', columnCnName: '付款方名称', dbType: 'varchar', length: 200, precision: undefined, scale: undefined, required: true, defaultValue: '', validationRule: '非空' },
  { columnName: 'payee_account', columnCnName: '收款账号', dbType: 'varchar', length: 64, precision: undefined, scale: undefined, required: true, defaultValue: '', validationRule: '账号格式' },
  { columnName: 'amount', columnCnName: '业务金额', dbType: 'decimal', length: undefined, precision: 18, scale: 2, required: true, defaultValue: '', validationRule: '金额格式' },
  { columnName: 'counterparty_account', columnCnName: '交易对手账号', dbType: 'varchar', length: 64, precision: undefined, scale: undefined, required: false, defaultValue: '', validationRule: '账号格式' },
  { columnName: 'business_amount', columnCnName: '业务金额', dbType: 'decimal', length: undefined, precision: 18, scale: 2, required: true, defaultValue: '', validationRule: '金额格式' },
  { columnName: 'business_date', columnCnName: '业务日期', dbType: 'date', length: undefined, precision: undefined, scale: undefined, required: false, defaultValue: '', validationRule: '日期格式' },
  { columnName: 'product_code', columnCnName: '产品代码', dbType: 'varchar', length: 32, precision: undefined, scale: undefined, required: false, defaultValue: '', validationRule: '产品主数据校验' }
])
const targetColumnOptions = computed(() => targetTableColumns.value.map((column) => column.columnName))
const uniqueConstraints = ref([
  {
    id: 'uk-1',
    enabled: true,
    constraintName: 'uk_ext_fund_result_biz',
    uniqueColumns: ['document_type', 'payee_account', 'amount'],
    duplicateScope: 'TARGET_TABLE',
    duplicateStrategy: 'REVIEW',
    generateDbIndex: false
  }
])
const mappingProfiles = [
  {
    name: '划款指令-资金结果表映射',
    documentType: '划款指令',
    targetTable: 'ext_fund_business_result',
    mapping: 'payer_name -> payer_name, payee_account -> counterparty_account, amount -> business_amount'
  },
  {
    name: '银行回单-资金结果表映射',
    documentType: '银行回单',
    targetTable: 'ext_fund_business_result',
    mapping: 'payer_name -> counterparty_name, transaction_no -> biz_no, amount -> business_amount'
  }
]
const promptFieldItems = computed(() => fields.value
  .filter((field: any) => (field.fieldCode || field.fieldName) && (field.sourceType || 'EXTRACTED') === 'EXTRACTED')
  .map((field: any) => {
    const fieldCode = field.fieldCode || field.fieldName || '-'
    const fieldName = field.fieldName || field.fieldCode || '-'
    const fieldDescription = String(field.fieldDescription || '').trim()
    return {
      fieldCode,
      fieldName,
      fieldDescription,
      targetColumn: field.targetColumn || fieldCode,
      extractRequired: Boolean(field.extractRequired),
      multiple: Boolean(field.multiple),
      promptText: `${fieldCode}（${fieldName}${fieldDescription ? `：${fieldDescription}` : ''}）`
    }
  }))
const fieldDrivenUserPrompt = computed(() => {
  const fieldsText = promptFieldItems.value.map((field) => field.promptText).join('、') || '请先维护提取字段'
  const multipleFields = promptFieldItems.value.filter((field) => field.multiple).map((field) => field.fieldCode)
  const multipleRequirement = multipleFields.length ? `多值字段 ${multipleFields.join('、')} 返回数组。` : ''
  const template = promptTemplateDefaults.value?.userTemplate || fallbackUserPromptTemplate
  const renderedFields = `${fieldsText}${multipleRequirement ? `。${multipleRequirement}` : ''}`
  return template.split('${fields}').join(renderedFields)
})
const promptStatusLabel = computed(() => promptAutoSync.value ? '由字段配置自动生成' : promptOutdated.value ? '已人工调整，字段配置已变更' : '已人工调整')
const promptStatusType = computed(() => promptAutoSync.value ? 'success' : promptOutdated.value ? 'warning' : 'info')
const setAiUserPrompt = (value: string, autoSync: boolean) => {
  suppressPromptInput.value = true
  aiUserPrompt.value = value
  promptAutoSync.value = autoSync
  promptOutdated.value = false
  suppressPromptInput.value = false
}
const regenerateAiUserPrompt = () => {
  setAiUserPrompt(fieldDrivenUserPrompt.value, false)
  ElMessage.success('已按字段配置重新生成用户提示词')
}
const restoreAutoPrompt = () => {
  setAiUserPrompt(fieldDrivenUserPrompt.value, true)
  ElMessage.success('已恢复为字段配置自动生成')
}
const restoreDefaultSystemPrompt = () => {
  aiSystemPrompt.value = promptTemplateDefaults.value?.systemTemplate || fallbackSystemPromptTemplate
  ElMessage.success('已恢复默认系统提示词')
}
const handleAiUserPromptInput = (value: string) => {
  aiUserPrompt.value = value
  if (suppressPromptInput.value) return
  promptAutoSync.value = value.trim() === fieldDrivenUserPrompt.value.trim()
  promptOutdated.value = false
}
watch(fieldDrivenUserPrompt, (nextPrompt, previousPrompt) => {
  if (promptAutoSync.value) {
    setAiUserPrompt(nextPrompt, true)
    return
  }
  if (previousPrompt !== undefined && nextPrompt !== previousPrompt) {
    promptOutdated.value = true
  }
}, { immediate: true })
const normalizeDbTypeParams = (column: any) => {
  if (['varchar', 'char'].includes(column.dbType)) {
    column.length = column.length || 100
    column.precision = undefined
    column.scale = undefined
  } else if (['decimal', 'number'].includes(column.dbType)) {
    column.length = undefined
    column.precision = column.precision || 18
    column.scale = column.scale ?? 2
  } else {
    column.length = undefined
    column.precision = undefined
    column.scale = undefined
  }
}
const formatDbColumnType = (column: any) => {
  if (['varchar', 'char'].includes(column.dbType)) return `${column.dbType}(${column.length || 100})`
  if (['decimal', 'number'].includes(column.dbType)) return `${column.dbType}(${column.precision || 18},${column.scale ?? 2})`
  if (column.dbType === 'int') return 'int'
  return column.dbType
}
const storageDdlPreview = computed(() => {
  if (!form.storageEnabled) {
    return '-- 当前配置未启用结果落库\n-- 不生成 DDL，不写入结果表，仅保留提取、加工、复核和下游推送能力。'
  }
  if (form.storageMode === 'REUSE') {
    return `-- 复用已有表: ${form.targetTable}\n-- 目标表名称: ${form.targetTableName}\n-- 仅保存映射方案: ${form.mappingProfileName}\n-- 不执行 DDL，仅校验目标字段是否存在。`
  }
  const dbUniqueConstraints = uniqueConstraints.value
    .filter((constraint) => constraint.enabled && constraint.generateDbIndex && constraint.uniqueColumns.length > 0)
    .map((constraint) => `  UNIQUE KEY ${constraint.constraintName} (${constraint.uniqueColumns.join(', ')})`)
  const tableLines = [...targetTableColumns.value.map((column) => `  ${column.columnName} ${formatDbColumnType(column)}${column.required ? ' NOT NULL' : ''}`), ...dbUniqueConstraints]
  const businessRules = uniqueConstraints.value
    .filter((constraint) => constraint.enabled && !constraint.generateDbIndex)
    .map((constraint) => `-- 业务去重规则: ${constraint.constraintName}，字段组合 ${constraint.uniqueColumns.join(', ') || '未选择字段'}，命中后${constraint.duplicateStrategy === 'REVIEW' ? '转人工复核' : '按应用策略处理'}，不生成数据库唯一索引。`)
    .join('\n')
  return `-- 目标表名称: ${form.targetTableName}\n-- 表说明: ${form.targetTableComment || '无'}\nCREATE TABLE ${form.targetTable} (\n${tableLines.join(',\n')}\n);\n${businessRules ? `\n${businessRules}` : ''}`
})
const cleanupTargetColumnReferences = () => {
  const validColumns = new Set(targetColumnOptions.value)
  fields.value.forEach((field) => {
    if (field.targetColumn && !validColumns.has(field.targetColumn)) field.targetColumn = ''
  })
  uniqueConstraints.value.forEach((constraint) => {
    constraint.uniqueColumns = constraint.uniqueColumns.filter((column) => validColumns.has(column))
  })
}
const normalizeResultTableColumns = (columns: any[] = []) =>
  columns.map((column) => ({
    columnName: column.columnName || '',
    columnCnName: column.columnCnName || '',
    dbType: column.dbType || 'varchar',
    length: column.length,
    precision: column.precision,
    scale: column.scale,
    required: Boolean(column.required),
    defaultValue: column.defaultValue || '',
    validationRule: column.validationRule || ''
  }))
const handleTargetTableChange = async (tableCode: string) => {
  const matchedTable = existingTables.value.find((table) => table.value === tableCode)
  if (matchedTable) {
    form.targetTableName = matchedTable.tableName
    form.targetTableComment = matchedTable.comment
  }
  if (!tableCode) return
  try {
    const detail = await getResultTableDetail(tableCode)
    form.targetTableName = detail.tableName || form.targetTableName
    form.targetTableComment = detail.tableComment || ''
    if (detail.columns?.length) {
      targetTableColumns.value = normalizeResultTableColumns(detail.columns)
      cleanupTargetColumnReferences()
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '结果表字段加载失败')
  }
}
const handleStorageModeChange = (storageMode: string) => {
  if (storageMode === 'REUSE') {
    if (form.targetTable) void handleTargetTableChange(form.targetTable)
    return
  }
  if (storageMode === 'CREATE' && existingTables.value.some((table) => table.value === form.targetTable)) {
    form.targetTable = ''
    form.targetTableName = ''
    form.targetTableComment = ''
  }
}
const handleStorageEnabledChange = (enabled: string | number | boolean) => {
  if (!Boolean(enabled) && form.pushTrigger === 'STORED') {
    form.pushTrigger = 'REVIEW_APPROVED'
    ElMessage.info('未启用落库时，推送触发时机已调整为复核通过后')
  }
}
const enabledDownstreamServices = computed(() => options.value.downstreamServices.filter((service) => service.enabled !== false))
const selectedPushServices = computed(() => options.value.downstreamServices.filter((service) => form.targetServices.includes(service.serviceCode)))
const activePushServiceCode = ref('')
const downstreamFieldMap = reactive<Record<string, Record<string, string>>>({})
const defaultPushSourceField = (field: any) => form.pushScope === 'MAPPED_FIELDS' ? (field.targetColumn || field.fieldCode) : field.fieldCode
const ensurePushServiceFieldMap = (serviceCode: string) => {
  if (!serviceCode) return {}
  if (!downstreamFieldMap[serviceCode]) downstreamFieldMap[serviceCode] = {}
  fields.value.forEach((field: any) => {
    if (!downstreamFieldMap[serviceCode][field.fieldCode]) {
      downstreamFieldMap[serviceCode][field.fieldCode] = defaultPushSourceField(field)
    }
  })
  return downstreamFieldMap[serviceCode]
}
const pushFieldMappingsForService = (serviceCode: string) => {
  const serviceMap = ensurePushServiceFieldMap(serviceCode)
  return fields.value.map((field: any) => ({
    fieldCode: field.fieldCode,
    resultFieldCode: field.fieldCode,
    sourceType: field.sourceType || 'EXTRACTED',
    sourceField: defaultPushSourceField(field),
    downstreamField: serviceMap[field.fieldCode] || defaultPushSourceField(field),
    fieldName: field.fieldName
  }))
}
const updateDownstreamField = (serviceCode: string, fieldCode: string, downstreamField: string) => {
  const serviceMap = ensurePushServiceFieldMap(serviceCode)
  serviceMap[fieldCode] = downstreamField
}
const renderIdempotentKeyPreview = (serviceCode: string) => {
  return (form.idempotentKey || '${traceId}-${taskId}-${serviceCode}')
    .replace(/\$\{traceId\}/g, 'TRACE202607170001')
    .replace(/\$\{taskId\}/g, 'TASK202607170001')
    .replace(/\$\{documentId\}/g, 'DOC202607170001')
    .replace(/\$\{serviceCode\}/g, serviceCode)
    .replace(/\$\{resultVersion\}/g, '1')
}
const buildPushPayloadPreview = (serviceCode: string) => {
  if (!serviceCode) return ''
  const data = pushFieldMappingsForService(serviceCode).reduce<Record<string, string>>((result, mapping) => {
    const downstreamField = mapping.downstreamField?.trim() || mapping.sourceField
    result[downstreamField] = `\${result.${mapping.fieldCode}}`
    return result
  }, {})
  return JSON.stringify({
    traceId: 'TRACE202607170001',
    taskId: 'TASK202607170001',
    documentId: 'DOC202607170001',
    serviceCode,
    idempotentKey: renderIdempotentKeyPreview(serviceCode),
    data
  }, null, 2)
}
const initializePushServiceMaps = (pushRules: any[] = []) => {
  Object.keys(downstreamFieldMap).forEach((key) => delete downstreamFieldMap[key])
  form.targetServices.forEach((serviceCode) => {
    const serviceMap = ensurePushServiceFieldMap(serviceCode)
    const rule = pushRules.find((item) => item?.serviceCode === serviceCode) || (pushRules.length === 1 ? pushRules[0] : undefined)
    ;(rule?.fieldMappings || []).forEach((mapping: any) => {
      const fieldCode = mapping.fieldCode || mapping.resultFieldCode || mapping.extractFieldCode
      if (fieldCode) serviceMap[fieldCode] = mapping.downstreamField || mapping.targetField || mapping.sourceField || serviceMap[fieldCode] || fieldCode
    })
  })
  activePushServiceCode.value = form.targetServices.includes(activePushServiceCode.value) ? activePushServiceCode.value : (form.targetServices[0] || '')
}
watch(() => form.targetServices.slice(), (serviceCodes) => {
  serviceCodes.forEach((serviceCode) => ensurePushServiceFieldMap(serviceCode))
  Object.keys(downstreamFieldMap).forEach((serviceCode) => {
    if (!serviceCodes.includes(serviceCode)) delete downstreamFieldMap[serviceCode]
  })
  if (!serviceCodes.includes(activePushServiceCode.value)) activePushServiceCode.value = serviceCodes[0] || ''
}, { immediate: true })
const selectedTransformRule = computed(() => {
  return transformRules.value.find((rule) => rule.id === selectedRuleId.value) || transformRules.value[0]
})
const transformFeatureEnabled = computed(() => Boolean(form.transformEnabled))
const validationFeatureEnabled = computed(() => Boolean(form.validationEnabled))
const normalizeStatus = (status?: string) => (status || 'DRAFT').toUpperCase()
const canEditVersion = computed(() => !currentConfigId.value || ['DRAFT', 'TESTING'].includes(normalizeStatus(currentStatus.value)))
const isReadonlyVersion = computed(() => !canEditVersion.value)
const versionStatusLabel = computed(() => {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    TESTING: '验证中',
    PUBLISHED: '已发布',
    DISABLED: '已停用'
  }
  const status = normalizeStatus(currentStatus.value)
  return labels[status] || status
})
const validationSummary = computed(() => {
  const sections = validationReport.value?.sections || []
  return {
    total: sections.length,
    passed: sections.filter((section) => section.status === 'PASSED').length,
    warning: sections.filter((section) => section.status === 'WARNING').length,
    failed: sections.filter((section) => section.status === 'FAILED').length
  }
})
const validationSectionTagType = (status: string) => {
  if (status === 'PASSED') return 'success'
  if (status === 'WARNING') return 'warning'
  return 'danger'
}
const validationSectionLabel = (status: string) => {
  if (status === 'PASSED') return '通过'
  if (status === 'WARNING') return '提醒'
  return '阻断'
}
const validationIssueTagType = (level: string) => {
  if (level === 'INFO') return 'success'
  if (level === 'WARN') return 'warning'
  return 'danger'
}
const validationIssueLabel = (level: string) => {
  if (level === 'INFO') return '通过'
  if (level === 'WARN') return '提醒'
  return '错误'
}
const selectedExtractField = computed(() => {
  return fields.value.find((field) => field.fieldCode === selectedExtractFieldCode.value) || fields.value[0]
})
const resultFieldSourceLabel = (sourceType?: ResultFieldSourceType | string) => {
  if (sourceType === 'DERIVED') return '加工衍生'
  if (sourceType === 'SYSTEM') return '系统字段'
  return '提取字段'
}
const resultFieldSourceTagType = (sourceType?: ResultFieldSourceType | string) => {
  if (sourceType === 'DERIVED') return 'warning'
  if (sourceType === 'SYSTEM') return 'info'
  return 'success'
}
const storageTargetColumnOptions = computed(() => targetTableColumns.value.map((column) => ({ label: `${column.columnCnName || column.columnName}（${column.columnName}）`, value: column.columnName })))
const extractedResultFields = computed(() => fields.value.filter((field: any) => (field.sourceType || 'EXTRACTED') === 'EXTRACTED'))
const transformTypeLabel: Record<TransformRuleType, string> = {
  DICT: '字典转换',
  API: 'API 取数',
  SQL: 'SQL 查询'
}

const hasDuplicate = (values: string[]) => {
  const normalizedValues = values.map((value) => value?.trim()).filter(Boolean)
  return new Set(normalizedValues).size !== normalizedValues.length
}
const validateFieldStorageStep = () => {
  const errors: string[] = []
  if (!fields.value.length) errors.push('至少维护 1 个结果字段')
  if (hasDuplicate(fields.value.map((field) => field.fieldCode))) errors.push('结果字段编码不能重复')
  fields.value.forEach((field, index) => {
    const rowLabel = `结果字段第 ${index + 1} 行`
    if (!field.fieldCode?.trim()) errors.push(`${rowLabel}：字段编码不能为空`)
    if (!field.fieldName?.trim()) errors.push(`${rowLabel}：字段名称不能为空`)
  })
  if (!form.storageEnabled) return errors
  if (!form.storageMode) errors.push('落库模式不能为空')
  if (!form.targetTable?.trim()) errors.push('目标表编码不能为空')
  if (form.storageMode === 'CREATE' && form.targetTable && !/^[a-z][a-z0-9_]*$/.test(form.targetTable)) {
    errors.push('目标表编码只能包含小写字母、数字、下划线，并以小写字母开头')
  }
  if (!form.targetTableName?.trim()) errors.push('目标表名称不能为空')
  if (!form.mappingProfileName?.trim()) errors.push('映射方案名称不能为空')

  if (!targetTableColumns.value.length) errors.push('至少维护 1 个目标表字段')
  if (hasDuplicate(targetTableColumns.value.map((column) => column.columnName))) errors.push('目标表字段名不能重复')
  targetTableColumns.value.forEach((column, index) => {
    const rowLabel = `目标表字段第 ${index + 1} 行`
    if (!column.columnName?.trim()) errors.push(`${rowLabel}：字段名不能为空`)
    if (!column.columnCnName?.trim()) errors.push(`${rowLabel}：字段中文名不能为空`)
    if (!column.dbType) errors.push(`${rowLabel}：数据库类型不能为空`)
    if (['varchar', 'char'].includes(column.dbType) && (!column.length || column.length < 1)) errors.push(`${rowLabel}：字符型字段长度必须大于 0`)
    if (['decimal', 'number'].includes(column.dbType)) {
      if (!column.precision || column.precision < 1) errors.push(`${rowLabel}：数值型字段精度必须大于 0`)
      if (column.scale === undefined || column.scale === null || column.scale < 0) errors.push(`${rowLabel}：数值型字段小数位不能小于 0`)
    }
  })

  fields.value.forEach((field, index) => {
    const rowLabel = `结果字段第 ${index + 1} 行`
    if (!field.targetColumn?.trim()) errors.push(`${rowLabel}：目标字段不能为空`)
    if (field.targetColumn && !targetColumnOptions.value.includes(field.targetColumn)) errors.push(`${rowLabel}：目标字段 ${field.targetColumn} 不存在于目标表字段定义中`)
  })

  const enabledConstraints = uniqueConstraints.value.filter((constraint) => constraint.enabled)
  if (hasDuplicate(enabledConstraints.map((constraint) => constraint.constraintName))) errors.push('启用的唯一约束名称不能重复')
  enabledConstraints.forEach((constraint, index) => {
    const rowLabel = `唯一约束第 ${index + 1} 行`
    if (!constraint.constraintName?.trim()) errors.push(`${rowLabel}：约束名称不能为空`)
    if (!constraint.uniqueColumns.length) errors.push(`${rowLabel}：唯一字段组合至少选择 1 个字段`)
    constraint.uniqueColumns.forEach((column) => {
      if (!targetColumnOptions.value.includes(column)) errors.push(`${rowLabel}：唯一字段 ${column} 不存在于目标表字段定义中`)
    })
  })

  return errors
}

const validateBaseInfoStep = () => {
  const errors: string[] = []
  if (!form.configName?.trim()) errors.push('配置名称不能为空')
  if (!form.departmentId?.trim()) errors.push('所属部门不能为空')
  if (!form.ownerRole?.trim()) errors.push('配置负责人角色不能为空')
  if (!form.visibleRoles.length) errors.push('可见角色至少选择一个')
  return errors
}

const extractStrategyStepErrors = () => {
  const errors: string[] = []
  const hasEnabledTraditionalRule = fields.value.some((field: any) => field.extractByRegex && (field.traditionalRuleType || 'REGEX') === 'REGEX' && field.regexPattern?.trim())
  if (!aiEnabled.value && !hasEnabledTraditionalRule) errors.push('未启用 AI，也未配置可执行的文本正则规则')
  if (aiEnabled.value && !form.llmModelCode) errors.push('请选择 LLM 模型')
  return errors
}

const pushStepErrors = () => {
  if (!form.pushEnabled) return []
  if (!form.storageEnabled && form.pushTrigger === 'STORED') return ['未启用结果落库时，推送触发时机不能选择“落库成功后”']
  const errors: string[] = []
  if (!form.targetServices.length) errors.push('已启用推送，但未选择目标接口服务')
  const missingServices = form.targetServices.filter((serviceCode) => !selectedPushServices.value.some((service) => service.serviceCode === serviceCode))
  if (missingServices.length) errors.push(`目标接口服务不存在或已停用：${missingServices.join('、')}`)
  if (form.pushMode !== 'SYNC') errors.push('第一版真实验证仅支持 HTTP JSON 同步推送，请选择同步等待响应')
  const unsupportedServices = selectedPushServices.value.filter((service) => service.serviceType && service.serviceType !== 'HTTP')
  if (unsupportedServices.length) errors.push(`第一版真实验证仅支持 HTTP 接口，请移除：${unsupportedServices.map((service) => service.serviceName || service.serviceCode).join('、')}`)
  return errors
}

const stepErrors = (index: number) => {
  if (index === 0) return validateBaseInfoStep()
  if (index === 2) return validateFieldStorageStep()
  if (index === 3) return extractStrategyStepErrors()
  if (index === 6) return pushStepErrors()
  return []
}

const stepIssueCount = (index: number) => stepErrors(index).length

const wizardStepConfigured = (index: number) => {
  if (stepIssueCount(index)) return false
  if (index === 0) {
    return Boolean(form.configName?.trim() && form.departmentId?.trim() && form.ownerRole?.trim() && form.visibleRoles.length)
  }
  if (index === 1) return Boolean(form.engineCode && form.parseMode)
  if (index === 2) return validateFieldStorageStep().length === 0
  if (index === 3) return extractStrategyStepErrors().length === 0
  if (index === 4) {
    if (!form.transformEnabled && !form.validationEnabled) return true
    return (form.transformEnabled && transformRules.value.some((rule) => rule.enabled))
      || (form.validationEnabled && validationRules.value.some((rule) => rule.enabled))
  }
  if (index === 5) return Boolean(form.confidenceThreshold !== undefined && form.confidenceThreshold !== null && form.reviewerRole)
  if (index === 6) return pushStepErrors().length === 0
  if (index === 7) return Boolean(validationReport.value || ['TESTING', 'PUBLISHED'].includes(normalizeStatus(currentStatus.value)))
  return false
}

const wizardStepStatus = (index: number) => {
  if (stepIssueCount(index)) return 'error'
  if (activeStep.value === index) return 'process'
  return wizardStepConfigured(index) ? 'finish' : 'wait'
}

const showStepErrors = (index: number, title = '请先完善配置') => {
  const errors = stepErrors(index)
  if (!errors.length) return false
  ElMessageBox.alert(errors.map((error, errorIndex) => `${errorIndex + 1}. ${error}`).join('<br />'), title, {
    dangerouslyUseHTMLString: true,
    confirmButtonText: '我知道了'
  })
  return true
}

const goToStep = (index: number) => {
  if (index === activeStep.value) return
  if (!currentConfigId.value && index > 0 && showStepErrors(0, '请先完善基础信息')) return
  activeStep.value = index
}

const next = () => {
  if (activeStep.value === 0 && showStepErrors(0, '请先完善基础信息')) return
  if (activeStep.value === 2 && showStepErrors(2, '请完善字段与落库配置')) return
  if (activeStep.value === 2) {
    const errors = validateFieldStorageStep()
    if (errors.length) {
      ElMessageBox.alert(errors.map((error, index) => `${index + 1}. ${error}`).join('<br />'), '请完善字段与落库配置', {
        dangerouslyUseHTMLString: true,
        confirmButtonText: '我知道了'
      })
      return
    }
  }
  if (activeStep.value < steps.length - 1) activeStep.value += 1
}
const prev = () => {
  if (activeStep.value > 0) activeStep.value -= 1
}
const buildWizardPayload = () => ({
  baseInfo: {
    configName: form.configName,
    category: form.category,
    subCategory: form.subCategory,
    templateType: form.templateType,
    documentType: form.documentType,
    departmentId: form.departmentId,
    ownerRole: form.ownerRole,
    tags: form.tags,
    defaultPriority: form.defaultPriority
  },
  parseConfig: {
    engineCode: form.engineCode,
    outputFormat: 'Markdown',
    parseMode: form.parseMode,
    pageBatchSize: form.parseMode === 'PAGE_BATCH_MERGE' ? form.pageBatchSize : null,
    preprocessEnabled: preprocessEnabled.value
  },
  preprocessSteps: supportedPreprocessSteps(preprocessSteps.value),
  storageConfig: {
    storageEnabled: form.storageEnabled,
    storageMode: form.storageMode,
    mappingProfileName: form.mappingProfileName,
    targetTable: form.targetTable,
    targetTableName: form.targetTableName,
    targetTableComment: form.targetTableComment,
    saveMode: form.saveMode
  },
  resultTableColumns: targetTableColumns.value,
  uniqueConstraints: uniqueConstraints.value,
  extractFields: fields.value.map((field: any) => ({
    fieldCode: field.fieldCode,
    fieldName: field.fieldName,
    fieldDescription: field.fieldDescription,
    sourceType: field.sourceType || 'EXTRACTED',
    generatedByRuleId: field.generatedByRuleId || '',
    extractRequired: field.extractRequired,
    multiple: field.multiple,
    extractByRegex: field.extractByRegex,
    traditionalRuleEnabled: field.extractByRegex,
    traditionalRuleType: field.traditionalRuleType || 'REGEX',
    traditionalRuleConfig: field.traditionalRuleConfig || {},
    targetColumn: form.storageEnabled ? field.targetColumn : field.fieldCode
  })),
  fieldMappings: form.storageEnabled
    ? fields.value.map((field: any) => ({
      extractFieldCode: field.fieldCode,
      resultFieldCode: field.fieldCode,
      sourceType: field.sourceType || 'EXTRACTED',
      targetColumn: field.targetColumn,
      multiple: field.multiple,
      requiredForStorage: field.extractRequired
    }))
    : [],
  extractStrategy: {
    aiEnabled: aiEnabled.value,
    outputMode: form.outputMode,
    llmModelCode: form.llmModelCode,
    defaultStrategy: form.defaultStrategy,
    confidenceThreshold: form.confidenceThreshold,
    systemPrompt: aiSystemPrompt.value,
    userPrompt: aiUserPrompt.value,
    generatedPromptPreview: aiUserPrompt.value,
    outputJsonSchema: ''
  },
  processConfig: {
    transformEnabled: form.transformEnabled,
    validationEnabled: form.validationEnabled
  },
  regexRules: fields.value
    .filter((field: any) => field.extractByRegex || field.regexPattern || field.traditionalRuleType)
    .map((field: any) => ({
      fieldCode: field.fieldCode,
      ruleName: `${field.fieldName}${traditionalRuleTypeLabel(field.traditionalRuleType || 'REGEX')}取数`,
      ruleType: field.traditionalRuleType || 'REGEX',
      ruleConfig: field.traditionalRuleConfig || {},
      regexPattern: field.regexPattern,
      regexGroup: field.regexGroup,
      regexFlags: field.regexFlags,
      sampleText: regexSampleText.value,
      sampleResult: regexPreviewMap.value[field.fieldCode],
      validationStatus: regexPreviewMap.value[field.fieldCode] ? 'PASSED' : 'NOT_TESTED',
      enabled: field.extractByRegex
    })),
  transformRules: transformRules.value,
  validationRules: validationRules.value,
  reviewPolicy: {
    confidenceThreshold: form.confidenceThreshold,
    reviewerRole: form.reviewerRole,
    forceReviewFields: ['amount', 'payee_account']
  },
  pushRules: form.targetServices.map((serviceCode) => ({
    serviceCode,
    pushEnabled: form.pushEnabled,
    pushTrigger: form.pushTrigger,
    pushScope: form.pushScope,
    pushMode: form.pushMode,
    idempotentKey: form.idempotentKey,
    failStrategy: form.pushFailStrategy,
    fieldMappings: pushFieldMappingsForService(serviceCode)
  })),
  visibleRoles: form.visibleRoles,
  extension: {
    storageDdlPreview: storageDdlPreview.value
  }
})
const saveDraft = async () => {
  if (isReadonlyVersion.value) {
    ElMessage.warning('当前版本已发布或停用，请先复制为新版本草稿后再修改')
    return false
  }
  if (showStepErrors(0, '请先完善基础信息')) return false
  saving.value = true
  try {
    const payload = buildWizardPayload()
    const result = currentConfigId.value
      ? await updateExtractConfigDraft(currentConfigId.value, payload)
      : await createExtractConfigDraft(payload)
    currentConfigId.value = result.summary.id
    currentConfigCode.value = result.summary.configCode
    currentVersion.value = result.summary.version || currentVersion.value
    currentStatus.value = normalizeStatus(result.summary.status)
    ElMessage.success(`草稿已保存：${result.summary.configCode}`)
    return true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '草稿保存失败')
    return false
  } finally {
    saving.value = false
  }
}
const validate = async () => {
  if (isReadonlyVersion.value) {
    ElMessage.warning('当前版本不可直接验证，请复制为新版本草稿后再调整')
    return
  }
  try {
    const saved = await saveDraft()
    if (!saved || !currentConfigId.value) return
    const result = await validateExtractConfig(currentConfigId.value)
    validationReport.value = result
    if (result.passed) {
      ElMessage.success(result.message)
    } else {
      ElMessageBox.alert(result.errors.map((error, index) => `${index + 1}. ${error}`).join('<br />'), result.message, {
        dangerouslyUseHTMLString: true,
        confirmButtonText: '我知道了'
      })
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '验证失败')
  }
}
const publish = async () => {
  if (isReadonlyVersion.value) {
    ElMessage.warning('当前版本已发布或停用，无需重复发布')
    return
  }
  try {
    const saved = await saveDraft()
    if (!saved || !currentConfigId.value) return
    await ElMessageBox.confirm('发布后新任务将使用该配置版本，历史任务不受影响。确认发布？', '发布配置', {
      type: 'warning'
    })
    const result = await publishExtractConfig(currentConfigId.value)
    currentConfigId.value = result.summary.id
    currentConfigCode.value = result.summary.configCode
    currentVersion.value = result.summary.version || currentVersion.value
    currentStatus.value = normalizeStatus(result.summary.status)
    ElMessage.success(`配置已发布为 V${result.summary.version}`)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '发布失败')
  }
}
const addField = () => {
  const fieldCode = `field_${fields.value.length + 1}`
  fields.value.push({
    fieldCode,
    fieldName: '新增字段',
    sourceType: 'EXTRACTED',
    fieldDescription: '补充该字段的业务含义，用于生成 AI 提示词',
    dataType: 'string',
    fieldLength: 100,
    required: false,
    extractRequired: false,
    multiple: false,
    targetColumn: fieldCode,
    extractByRegex: false,
    traditionalRuleEnabled: false,
    traditionalRuleType: 'REGEX',
    traditionalRuleConfig: {},
    regexPattern: '',
    regexFlags: '',
    regexGroup: 1
  } as any)
  form.targetServices.forEach((serviceCode) => updateDownstreamField(serviceCode, fieldCode, fieldCode))
}
const autoMapFields = () => {
  fields.value.forEach((field) => {
    const matchedColumn = targetColumnOptions.value.find((column) => column === field.fieldCode || column === field.targetColumn)
    field.targetColumn = matchedColumn || field.targetColumn || field.fieldCode
  })
  ElMessage.success('已按同名字段自动生成映射')
}
const addTargetColumn = () => {
  if (isReuseStorageMode.value) {
    ElMessage.warning('复用已有表时，目标字段来自结果表元数据台账，不能在当前配置中直接新增')
    return
  }
  const columnName = `column_${targetTableColumns.value.length + 1}`
  targetTableColumns.value.push({
    columnName,
    columnCnName: '新增目标字段',
    dbType: 'varchar',
    length: 100,
    precision: undefined,
    scale: undefined,
    required: false,
    defaultValue: '',
    validationRule: ''
  })
}
const addUniqueConstraint = () => {
  uniqueConstraints.value.push({
    id: `uk-${Date.now()}`,
    enabled: true,
    constraintName: `uk_${form.targetTable}_${uniqueConstraints.value.length + 1}`,
    uniqueColumns: [],
    duplicateScope: 'TARGET_TABLE',
    duplicateStrategy: 'REVIEW',
    generateDbIndex: false
  })
}
const removeUniqueConstraint = (row: any) => {
  uniqueConstraints.value = uniqueConstraints.value.filter((constraint) => constraint.id !== row.id)
  ElMessage.success('已删除唯一约束')
}
const removeTargetColumn = (row: any) => {
  if (isReuseStorageMode.value) {
    ElMessage.warning('复用已有表时，不能在当前配置中删除目标字段')
    return
  }
  const usedCount = fields.value.filter((field) => field.targetColumn === row.columnName).length
  targetTableColumns.value = targetTableColumns.value.filter((column) => column.columnName !== row.columnName)
  uniqueConstraints.value.forEach((constraint) => {
    constraint.uniqueColumns = constraint.uniqueColumns.filter((column) => column !== row.columnName)
  })
  if (usedCount > 0) {
    fields.value.forEach((field) => {
      if (field.targetColumn === row.columnName) field.targetColumn = ''
    })
    ElMessage.warning(`已删除目标字段，并清空 ${usedCount} 个结果字段映射`)
  } else {
    ElMessage.success('已删除目标字段')
  }
}
const removeExtractField = (row: any) => {
  fields.value = fields.value.filter((field) => field.fieldCode !== row.fieldCode)
  Object.values(downstreamFieldMap).forEach((serviceMap) => delete serviceMap[row.fieldCode])
  delete regexPreviewMap.value[row.fieldCode]
  if (selectedExtractFieldCode.value === row.fieldCode) {
    selectedExtractFieldCode.value = fields.value[0]?.fieldCode || ''
  }
  ElMessage.success('已删除结果字段')
}
const removeTransformRule = async (rule: TransformRule) => {
  const currentIndex = transformRules.value.findIndex((item) => item.id === rule.id)
  const referencedRules = transformRules.value
    .slice(currentIndex + 1)
    .filter((item) => rule.outputField && item.inputField === rule.outputField)
  if (referencedRules.length) {
    ElMessage.warning(`该规则输出字段 ${rule.outputField} 被后续规则「${referencedRules.map((item) => item.ruleName).join('、')}」引用，请先调整后续规则`)
    return
  }
  try {
    await ElMessageBox.confirm('确认删除该加工规则？删除后不会影响已完成任务结果，但新任务将不再执行该规则。', '删除加工规则', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    transformRules.value = transformRules.value.filter((item) => item.id !== rule.id)
    if (selectedRuleId.value === rule.id) {
      selectedRuleId.value = transformRules.value[Math.max(0, currentIndex - 1)]?.id || transformRules.value[0]?.id || ''
    }
    if (!transformRules.value.length) selectedRuleId.value = ''
    ElMessage.success('已删除加工规则')
  } catch {
    // User canceled.
  }
}
const moveTransformRule = (rule: TransformRule, direction: -1 | 1) => {
  const currentIndex = transformRules.value.findIndex((item) => item.id === rule.id)
  const nextIndex = currentIndex + direction
  if (nextIndex < 0 || nextIndex >= transformRules.value.length) return
  const rows = [...transformRules.value]
  const [current] = rows.splice(currentIndex, 1)
  rows.splice(nextIndex, 0, current)
  transformRules.value = rows
}
const copyTransformRule = (rule: TransformRule) => {
  const copiedRule: TransformRule = {
    ...rule,
    id: `rule-${Date.now()}`,
    ruleName: `${rule.ruleName}-副本`,
    dictItems: rule.dictItems.map((item) => ({ ...item }))
  }
  const currentIndex = transformRules.value.findIndex((item) => item.id === rule.id)
  transformRules.value.splice(currentIndex + 1, 0, copiedRule)
  selectedRuleId.value = copiedRule.id
  ElMessage.success('已复制加工规则')
}
const addValidationRule = () => {
  validationRules.value.push({
    id: `valid-${Date.now()}`,
    ruleName: '新增校验规则',
    ruleType: 'REQUIRED',
    fieldCode: fields.value[0]?.fieldCode || '',
    enabled: true,
    severity: 'REVIEW',
    expression: '',
    failMessage: '校验不通过，请人工确认'
  })
}
const removeValidationRule = (row: ValidationRule) => {
  validationRules.value = validationRules.value.filter((rule) => rule.id !== row.id)
  ElMessage.success('已删除校验规则')
}
const openDerivedFieldDialog = () => {
  const rule = selectedTransformRule.value
  if (!rule) {
    ElMessage.warning('请先选择加工规则')
    return
  }
  const index = fields.value.filter((field: any) => field.sourceType === 'DERIVED').length + 1
  derivedFieldForm.fieldCode = rule.outputField || `derived_field_${index}`
  derivedFieldForm.fieldName = rule.outputField ? rule.outputField : `衍生字段${index}`
  derivedFieldForm.fieldDescription = `由加工规则「${rule.ruleName}」生成`
  derivedFieldForm.dataType = 'string'
  derivedFieldForm.targetColumn = rule.outputField || `derived_field_${index}`
  derivedFieldForm.autoAddTargetColumn = form.storageEnabled && form.storageMode === 'CREATE'
  derivedFieldDialogVisible.value = true
}
const confirmDerivedField = () => {
  const fieldCode = derivedFieldForm.fieldCode.trim()
  const fieldName = derivedFieldForm.fieldName.trim()
  if (!fieldCode || !fieldName) {
    ElMessage.warning('请维护衍生字段编码和名称')
    return
  }
  if (fields.value.some((field) => field.fieldCode === fieldCode)) {
    ElMessage.warning(`结果字段 ${fieldCode} 已存在，请直接选择已有字段或更换编码`)
    return
  }
  const targetColumn = form.storageEnabled ? (derivedFieldForm.targetColumn.trim() || fieldCode) : fieldCode
  if (form.storageEnabled && form.storageMode === 'REUSE' && targetColumn && !targetColumnOptions.value.includes(targetColumn)) {
    ElMessage.warning(`目标字段 ${targetColumn} 不存在于当前复用表，请先选择已有目标字段`)
    return
  }
  fields.value.push({
    fieldCode,
    fieldName,
    sourceType: 'DERIVED',
    generatedByRuleId: selectedTransformRule.value?.id || '',
    dataType: derivedFieldForm.dataType,
    fieldLength: derivedFieldForm.dataType === 'amount' ? 18 : 200,
    fieldDescription: derivedFieldForm.fieldDescription || `由加工规则生成 ${fieldName}`,
    required: false,
    extractRequired: false,
    multiple: false,
    targetColumn,
    extractByRegex: false,
    traditionalRuleEnabled: false,
    traditionalRuleType: 'REGEX',
    traditionalRuleConfig: {},
    regexPattern: '',
    regexFlags: '',
    regexGroup: 1
  } as any)
  if (form.storageEnabled && form.storageMode === 'CREATE' && derivedFieldForm.autoAddTargetColumn && !targetColumnOptions.value.includes(targetColumn)) {
    targetTableColumns.value.push({
      columnName: targetColumn,
      columnCnName: fieldName,
      dbType: derivedFieldForm.dataType === 'amount' ? 'decimal' : derivedFieldForm.dataType === 'date' ? 'date' : 'varchar',
      length: derivedFieldForm.dataType === 'string' ? 200 : undefined,
      precision: derivedFieldForm.dataType === 'amount' ? 18 : undefined,
      scale: derivedFieldForm.dataType === 'amount' ? 2 : undefined,
      required: false,
      defaultValue: '',
      validationRule: ''
    } as any)
  }
  if (selectedTransformRule.value) selectedTransformRule.value.outputField = fieldCode
  form.targetServices.forEach((serviceCode) => updateDownstreamField(serviceCode, fieldCode, targetColumn))
  derivedFieldDialogVisible.value = false
  ElMessage.success('已新增衍生结果字段，并自动回填到当前加工规则')
}
const runValidationPreview = () => {
  ElMessage.success('已基于当前样本模拟执行校验规则')
}
const addTransformRule = (ruleType: TransformRuleType) => {
  const id = `rule-${Date.now()}`
  transformRules.value.push({
    id,
    ruleName: `新增${transformTypeLabel[ruleType]}规则`,
    ruleType,
    inputField: fields.value[0]?.fieldCode || '',
    outputField: '',
    enabled: true,
    onFail: 'REVIEW',
    dictItems: ruleType === 'DICT' ? [{ source: '', target: '' }] : [],
    apiEndpoint: ruleType === 'API' ? '/api/example/{value}' : '',
    apiMethod: 'GET',
    apiParamName: 'value',
    apiResponsePath: '$.data.value',
    sqlDatasource: '只读数据源',
    sqlText: ruleType === 'SQL' ? 'select target_value from table_name where source_value = :source_value' : '',
    sqlResultColumn: 'target_value',
    outputMode: 'DERIVE_FIELD',
    conditionEnabled: true,
    conditionField: fields.value[0]?.fieldCode || '',
    conditionOperator: 'NOT_EMPTY',
    conditionValue: '',
    dictMatchMode: 'EQUALS',
    apiTimeout: 5,
    apiRetryCount: 1,
    apiAuthMode: 'SYSTEM',
    apiSuccessRule: '$.code == 0',
    sqlMaxRows: 1,
    sqlReadonlyChecked: true
  })
  selectedRuleId.value = id
}
const addDictItem = () => {
  if (!selectedTransformRule.value) return
  selectedTransformRule.value.dictItems.push({ source: '', target: '' })
}
const runTransformPreview = () => {
  const rule = selectedTransformRule.value
  if (!rule) {
    ElMessage.warning('请先新增并选择一条加工规则')
    return
  }
  if (rule.ruleType === 'DICT') {
    previewOutput.value = rule.dictItems.find((item) => item.source === previewInput.value)?.target || '未命中字典，按失败策略处理'
  } else if (rule.ruleType === 'API') {
    previewOutput.value = `模拟调用 ${rule.apiEndpoint.replace('{accountNo}', previewInput.value).replace('{value}', previewInput.value)}，读取 ${rule.apiResponsePath}`
  } else {
    previewOutput.value = `模拟执行只读 SQL，返回 ${rule.sqlResultColumn || 'result'}`
  }
  ElMessage.success('已生成加工预览')
}
const runRegexPreview = () => {
  const field = selectedExtractField.value as any
  regexPreview.value = runFieldRegexPreview(field)
  ElMessage.success('已运行正则测试')
}
const runFieldRegexPreview = (field: any) => {
  if (!isExecutableTraditionalRule(field)) {
    regexPreviewMap.value[field.fieldCode] = '待接入'
    return '待接入'
  }
  if (!field.regexPattern) {
    regexPreviewMap.value[field.fieldCode] = '未配置'
    return '未配置'
  }
  try {
    const match = regexSampleText.value.match(new RegExp(field.regexPattern, field.regexFlags || undefined))
    const group = Number(field.regexGroup ?? 1)
    const result = match?.[group] || match?.[0] || '未匹配'
    regexPreviewMap.value[field.fieldCode] = result
    return result
  } catch (error) {
    regexPreviewMap.value[field.fieldCode] = '正则错误'
    return '正则错误'
  }
}
const runAllRegexPreview = () => {
  if (!regexSampleText.value.trim()) {
    ElMessage.warning('请先粘贴或上传用于验证的样本文本')
    return
  }
  fields.value.forEach((field) => {
    if ((field as any).extractByRegex && isExecutableTraditionalRule(field)) runFieldRegexPreview(field)
  })
  ElMessage.success('已批量验证已启用的文本正则规则')
}
const normalizeRoleValue = (value: string) => {
  if (!value) return value
  const role = options.value.roles.find((item) =>
    item.value === value || item.roleCode === value || item.label === value || item.roleName === value
  )
  return String(role?.value || role?.roleCode || value)
}
const normalizeRoleFields = () => {
  form.ownerRole = normalizeRoleValue(form.ownerRole)
  form.visibleRoles = form.visibleRoles.map((role) => normalizeRoleValue(role))
}
const applyWizardPayload = (payload: any) => {
  if (!payload) return
  const baseInfo = payload.baseInfo || {}
  const parseConfig = payload.parseConfig || {}
  const storageConfig = payload.storageConfig || {}
  const extractStrategy = payload.extractStrategy || {}
  const reviewPolicy = payload.reviewPolicy || {}
  const firstPushRule = payload.pushRules?.[0] || {}

  Object.assign(form, {
    configName: baseInfo.configName ?? form.configName,
    category: baseInfo.category ?? form.category,
    subCategory: baseInfo.subCategory ?? form.subCategory,
    templateType: baseInfo.templateType ?? form.templateType,
    documentType: baseInfo.documentType ?? form.documentType,
    departmentId: baseInfo.departmentId ?? form.departmentId,
    visibleRoles: Array.isArray(payload.visibleRoles) ? payload.visibleRoles : form.visibleRoles,
    ownerRole: baseInfo.ownerRole ?? form.ownerRole,
    tags: Array.isArray(baseInfo.tags) ? baseInfo.tags : form.tags,
    defaultPriority: baseInfo.defaultPriority || form.defaultPriority,
    engineCode: parseConfig.engineCode || form.engineCode,
    outputFormat: 'Markdown',
    parseMode: parseConfig.parseMode || form.parseMode,
    pageBatchSize: parseConfig.pageBatchSize || form.pageBatchSize,
    storageEnabled: storageConfig.storageEnabled ?? form.storageEnabled,
    storageMode: storageConfig.storageMode || form.storageMode,
    mappingProfileName: storageConfig.mappingProfileName || form.mappingProfileName,
    targetTable: storageConfig.targetTable || form.targetTable,
    targetTableName: storageConfig.targetTableName || form.targetTableName,
    targetTableComment: storageConfig.targetTableComment || form.targetTableComment,
    saveMode: storageConfig.saveMode || form.saveMode,
    outputMode: extractStrategy.outputMode || form.outputMode,
    llmModelCode: extractStrategy.llmModelCode || form.llmModelCode,
    defaultStrategy: extractStrategy.defaultStrategy || form.defaultStrategy,
    confidenceThreshold: extractStrategy.confidenceThreshold ?? reviewPolicy.confidenceThreshold ?? form.confidenceThreshold,
    reviewerRole: reviewPolicy.reviewerRole || form.reviewerRole,
    pushEnabled: firstPushRule.pushEnabled ?? form.pushEnabled,
    pushTrigger: firstPushRule.pushTrigger || form.pushTrigger,
    targetServices: payload.pushRules?.map((rule: any) => rule.serviceCode).filter(Boolean) || form.targetServices,
    pushScope: ['MAPPED_FIELDS', 'ALL_EXTRACTED_FIELDS'].includes(firstPushRule.pushScope) ? firstPushRule.pushScope : form.pushScope,
    pushMode: firstPushRule.pushMode || form.pushMode,
    idempotentKey: firstPushRule.idempotentKey || form.idempotentKey,
    pushFailStrategy: firstPushRule.failStrategy || form.pushFailStrategy
  })

  const payloadPreprocessSteps = supportedPreprocessSteps(payload.preprocessSteps)
  preprocessEnabled.value = parseConfig.preprocessEnabled ?? payloadPreprocessSteps.some((step: any) => step.enabled)
  if (payloadPreprocessSteps.length) preprocessSteps.value = payloadPreprocessSteps
  if (payload.resultTableColumns?.length) targetTableColumns.value = payload.resultTableColumns
  if (payload.uniqueConstraints?.length) uniqueConstraints.value = payload.uniqueConstraints
  if (payload.extractFields?.length) {
    fields.value = payload.extractFields.map((field: any) => ({
      ...field,
      sourceType: field.sourceType || 'EXTRACTED',
      generatedByRuleId: field.generatedByRuleId || '',
      fieldDescription: field.fieldDescription || `从文档中识别${field.fieldName}`,
      required: field.extractRequired,
      targetColumn: field.targetColumn || '',
      traditionalRuleEnabled: field.traditionalRuleEnabled ?? field.extractByRegex ?? false,
      traditionalRuleType: field.traditionalRuleType || 'REGEX',
      traditionalRuleConfig: field.traditionalRuleConfig || {}
    }))
  }
  if (payload.regexRules?.length) {
    payload.regexRules.forEach((rule: any) => {
      const field = fields.value.find((item: any) => item.fieldCode === rule.fieldCode) as any
      if (!field) return
      field.extractByRegex = rule.enabled
      field.traditionalRuleEnabled = rule.enabled
      field.traditionalRuleType = rule.ruleType || field.traditionalRuleType || 'REGEX'
      field.traditionalRuleConfig = rule.ruleConfig || field.traditionalRuleConfig || {}
      field.regexPattern = rule.regexPattern
      field.regexGroup = rule.regexGroup
      field.regexFlags = rule.regexFlags
      if (rule.sampleResult) regexPreviewMap.value[rule.fieldCode] = rule.sampleResult
    })
  }
  transformRules.value = payload.transformRules || []
  selectedRuleId.value = transformRules.value[0]?.id || ''
  validationRules.value = payload.validationRules || []
  form.transformEnabled = payload.processConfig?.transformEnabled ?? transformRules.value.some((rule) => rule.enabled)
  form.validationEnabled = payload.processConfig?.validationEnabled ?? validationRules.value.some((rule) => rule.enabled)
  const savedUserPrompt = String(extractStrategy.userPrompt || '').trim()
  const generatedUserPrompt = fieldDrivenUserPrompt.value
  setAiUserPrompt(savedUserPrompt || generatedUserPrompt, !savedUserPrompt || savedUserPrompt === generatedUserPrompt)
  aiSystemPrompt.value = extractStrategy.systemPrompt || aiSystemPrompt.value
  aiEnabled.value = extractStrategy.aiEnabled ?? aiEnabled.value
  if (form.storageMode === 'REUSE' && form.targetTable) void handleTargetTableChange(form.targetTable)

  initializePushServiceMaps(payload.pushRules || [])
  normalizeRoleFields()
}
const loadConfigForEdit = async () => {
  const id = String(route.query.id || '')
  if (!id) return
  try {
    const detail = await getExtractConfigDetail(id)
    currentConfigId.value = detail.summary.id
    currentConfigCode.value = detail.summary.configCode
    currentVersion.value = detail.summary.version || 1
    currentStatus.value = normalizeStatus(detail.summary.status)
    applyWizardPayload(detail.payload)
    ElMessage.success(isReadonlyVersion.value ? '已加载历史版本，只读查看' : '已加载配置草稿')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '配置加载失败')
  }
}
const copyCurrentVersion = async () => {
  if (!currentConfigId.value) return
  try {
    const detail = await copyExtractConfig(currentConfigId.value)
    currentConfigId.value = detail.summary.id
    currentConfigCode.value = detail.summary.configCode
    currentVersion.value = detail.summary.version || 1
    currentStatus.value = normalizeStatus(detail.summary.status)
    applyWizardPayload(detail.payload)
    router.replace({ path: '/configs/wizard', query: { id: detail.summary.id } })
    ElMessage.success(`已复制为 V${detail.summary.version} 草稿，可继续编辑`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '复制新版本失败')
  }
}
const loadWizardOptions = async () => {
  try {
    options.value = await getConfigOptions()
    llmModelOptions.value = await listLlmModelOptions()
    ocrEngineOptions.value = await listOcrEngineOptions()
    promptTemplateDefaults.value = await getPromptTemplateDefaults()
    const isCreateMode = !route.query.id
    if (isCreateMode) {
      aiSystemPrompt.value = promptTemplateDefaults.value.systemTemplate || fallbackSystemPromptTemplate
      setAiUserPrompt(fieldDrivenUserPrompt.value, true)
    }
    if (isCreateMode && !form.engineCode) {
      form.engineCode = ocrEngineOptions.value.find((item) => item.defaultEngine)?.engineCode || ''
    }
    if (!form.llmModelCode && llmModelOptions.value.length) {
      form.llmModelCode = llmModelOptions.value.find((item) => item.defaultModel)?.modelCode || llmModelOptions.value[0].modelCode
    }
    normalizeRoleFields()
    if (isCreateMode && form.storageMode === 'REUSE' && form.targetTable) void handleTargetTableChange(form.targetTable)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '配置选项加载失败')
  }
}

onMounted(async () => {
  await loadWizardOptions()
  await loadConfigForEdit()
})
</script>

<template>
  <div class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>配置向导</span>
        </div>
      </template>
      <el-steps :active="activeStep" finish-status="success" align-center class="wizard-steps">
        <el-step
          v-for="(step, index) in steps"
          :key="step"
          :status="wizardStepStatus(index)"
        >
          <template #title>
            <button
              type="button"
              class="wizard-step-title"
              :class="{ active: activeStep === index, configured: wizardStepConfigured(index), 'has-issue': stepIssueCount(index) > 0 }"
              @click.stop="goToStep(index)"
            >
              <span>{{ step }}</span>
              <small v-if="stepIssueCount(index) > 0">需完善 {{ stepIssueCount(index) }}</small>
            </button>
          </template>
        </el-step>
      </el-steps>
      <el-alert
        v-if="currentConfigId"
        class="mt-12"
        :type="isReadonlyVersion ? 'warning' : 'info'"
        :closable="false"
        :title="`当前配置编码：${currentConfigCode || '-'}，版本：V${currentVersion}，状态：${versionStatusLabel}${isReadonlyVersion ? '。该版本只读，请复制为新版本后修改。' : '。草稿和验证中版本允许直接保存。'}`"
      />
    </el-card>

    <el-card shadow="never" class="wizard-card" :class="{ 'readonly-version': isReadonlyVersion }">
      <template v-if="activeStep === 0">
        <h2>基础信息</h2>
        <el-form :model="form" label-width="120px" class="form-grid">
          <el-form-item label="配置名称" required><el-input v-model="form.configName" clearable placeholder="请输入唯一配置名称" /></el-form-item>
          <el-form-item label="业务分类">
            <el-select v-model="form.category" filterable clearable placeholder="请选择业务分类" @change="form.subCategory = ''; form.templateType = ''">
              <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="业务子类">
            <el-select v-model="form.subCategory" filterable clearable placeholder="请选择业务子类" @change="form.templateType = ''; form.documentType = form.subCategory">
              <el-option v-for="item in subCategoryOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="模板/表单类型">
            <el-select v-model="form.templateType" filterable clearable allow-create placeholder="请选择或输入模板/表单类型">
              <el-option v-for="item in templateTypeOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="文档类型">
            <el-select v-model="form.documentType" filterable clearable allow-create placeholder="可由业务子类带出，也可手工维护">
              <el-option v-for="item in options.documentTypes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="所属部门" required>
            <el-select v-model="form.departmentId" filterable clearable placeholder="请选择所属部门">
              <el-option v-for="item in options.departments" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="配置负责人角色" required>
            <el-select v-model="form.ownerRole" filterable clearable placeholder="请选择负责维护该配置的角色">
              <el-option v-for="item in options.roles" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="可见角色" required>
            <el-select v-model="form.visibleRoles" multiple filterable clearable collapse-tags collapse-tags-tooltip placeholder="请选择可查看/使用该配置的角色">
              <el-option v-for="item in options.roles" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="标签" class="wide">
            <el-select
              v-model="form.tags"
              multiple
              filterable
              allow-create
              default-first-option
              clearable
              collapse-tags
              collapse-tags-tooltip
              placeholder="输入文字后按回车生成标签，可多次输入"
              @change="form.tags = Array.from(new Set(form.tags.map((tag) => tag.trim()).filter(Boolean)))"
              @blur="(event: FocusEvent) => addConfigTag((event.target as HTMLInputElement)?.value || '')"
            />
          </el-form-item>
          <el-form-item label="默认优先级">
            <el-radio-group v-model="form.defaultPriority">
              <el-radio-button label="HIGH">高</el-radio-button>
              <el-radio-button label="MEDIUM">中</el-radio-button>
              <el-radio-button label="LOW">低</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <el-alert
          title="权限绑定说明：所属部门只定义配置归属；用户还必须在该部门下拥有可见角色，并命中数据权限策略，才能查看该配置和相关任务结果。"
          type="info"
          :closable="false"
        />
      </template>

      <template v-if="activeStep === 1">
        <div class="card-header">
          <div>
            <h2>解析配置</h2>
            <p class="muted">默认全文进入 OCR/文档解析引擎；仅在需要裁剪页码、按关键字筛选或 PDF 转图片时启用预处理。</p>
          </div>
        </div>

        <el-card shadow="never" class="mb-12">
          <template #header>
            <div class="card-header">
              <div>
                <span>文档预处理</span>
                <p class="muted">默认不启用，全文直接解析；启用后可选择具体预处理动作。</p>
              </div>
              <div class="header-actions">
                <el-switch v-model="preprocessEnabled" active-text="启用预处理" inactive-text="全文解析" />
              </div>
            </div>
          </template>
          <el-table :data="preprocessSteps" height="280">
            <el-table-column label="启用" width="70">
              <template #default="{ row }"><el-switch v-model="row.enabled" :disabled="!preprocessEnabled" /></template>
            </el-table-column>
            <el-table-column label="处理方式" min-width="190">
              <template #default="{ row }">
                <strong>{{ preprocessStepTypeLabel[row.stepType as PreprocessStepType] }}</strong>
              </template>
            </el-table-column>
            <el-table-column label="参数配置" min-width="380">
              <template #default="{ row }">
                <div v-if="row.stepType === 'PAGE_RANGE'" class="inline-fields">
                  <span class="muted">页码</span>
                  <el-input v-model="row.pageRanges" :disabled="!preprocessEnabled" placeholder="如 1,3,5-8 或 1-3;8-10" />
                </div>
                <div v-else-if="row.stepType === 'KEYWORD_FILTER'" class="inline-fields">
                  <div class="keyword-config">
                    <div class="keyword-row">
                      <span class="keyword-label">包含关键字</span>
                      <el-select
                        v-model="row.includeKeywords"
                        :disabled="!preprocessEnabled"
                        multiple
                        filterable
                        allow-create
                        default-first-option
                        collapse-tags
                        collapse-tags-tooltip
                        placeholder="输入后回车添加"
                      />
                    </div>
                    <div class="keyword-row">
                      <span class="keyword-label">排除关键字</span>
                      <el-select
                        v-model="row.excludeKeywords"
                        :disabled="!preprocessEnabled"
                        multiple
                        filterable
                        allow-create
                        default-first-option
                        collapse-tags
                        collapse-tags-tooltip
                        placeholder="输入后回车添加"
                      />
                    </div>
                  </div>
                </div>
                <div v-else-if="row.stepType === 'PDF_TO_IMAGE'" class="inline-fields">
                  <el-select v-model="row.imageQuality" :disabled="!preprocessEnabled" placeholder="转换质量" @change="updateImageQuality(row)">
                    <el-option v-for="option in imageQualityOptions" :key="option.value" :label="option.label" :value="option.value" />
                  </el-select>
                  <el-select v-model="row.imageFormat" :disabled="!preprocessEnabled">
                    <el-option label="PNG（推荐，清晰度高）" value="PNG" />
                    <el-option label="JPEG（文件更小）" value="JPEG" />
                  </el-select>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <el-input class="mt-12" type="textarea" :rows="5" :model-value="preprocessPipelinePreview" readonly />
        </el-card>

        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <div>
                <span>OCR 引擎设置</span>
                <p class="muted">配置解析引擎、输出格式和引擎能力开关，文档预处理结果会作为 OCR/解析引擎输入。</p>
              </div>
            </div>
          </template>
          <el-form :model="form" label-width="130px" class="form-grid">
          <el-form-item label="OCR 引擎">
            <el-select v-model="form.engineCode" filterable placeholder="请选择启用中的 OCR 引擎">
              <el-option
                v-for="item in ocrEngineOptions"
                :key="item.engineCode"
                :label="`${item.engineName}（${item.provider}）`"
                :value="item.engineCode"
              />
            </el-select>
          </el-form-item>
            <el-form-item label="解析模式" class="wide">
              <el-radio-group v-model="form.parseMode">
                <el-radio-button label="FULL">全文解析</el-radio-button>
                <el-radio-button label="PAGE_BATCH_MERGE">每 N 页解析后拼接</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="form.parseMode === 'PAGE_BATCH_MERGE'" label="每批页数">
              <div class="inline-fields">
                <el-input-number v-model="form.pageBatchSize" :min="1" :step="1" step-strictly :controls="false" />
                <span class="muted">页，系统按原页码顺序分批解析并拼接 Markdown。</span>
              </div>
            </el-form-item>
            <el-form-item label="输出格式">
              <el-tag type="info">Markdown</el-tag>
              <span class="muted ml-8">固定输出 Markdown，供后续 AI 提取和传统规则取数统一消费。</span>
            </el-form-item>
          </el-form>
          <el-input
            type="textarea"
            :rows="5"
            :model-value="JSON.stringify({ outputFormat: 'Markdown', parseMode: form.parseMode, pageBatchSize: form.parseMode === 'PAGE_BATCH_MERGE' ? form.pageBatchSize : null }, null, 2)"
            readonly
          />
        </el-card>
      </template>

      <template v-if="activeStep === 2">
        <div class="card-header">
          <div>
            <h2>字段与落库配置</h2>
            <p class="muted">在同一个界面维护结果字段、目标表和字段映射，避免字段配置与落库配置来回切换。</p>
          </div>
        </div>

        <el-form :model="form" label-width="120px" class="form-grid">
          <el-form-item label="启用结果落库" class="wide">
            <div class="field-with-tip">
              <el-switch v-model="form.storageEnabled" active-text="启用" inactive-text="不落库" @change="handleStorageEnabledChange" />
              <span class="muted block">{{ storageDisabledTip }}</span>
            </div>
          </el-form-item>
          <el-form-item v-if="isStorageEnabled" label="落库模式" class="wide">
            <el-radio-group v-model="form.storageMode" @change="handleStorageModeChange">
              <el-radio-button label="REUSE">复用已有表</el-radio-button>
              <el-radio-button label="CREATE">新建结果表</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="isStorageEnabled" label="目标表编码">
            <el-select v-if="form.storageMode === 'REUSE'" v-model="form.targetTable" filterable clearable @change="handleTargetTableChange">
              <el-option v-for="table in existingTables" :key="table.value" :label="table.label" :value="table.value" />
            </el-select>
            <el-input v-else v-model="form.targetTable" placeholder="如 ext_new_extract_result" />
          </el-form-item>
          <el-form-item v-if="isStorageEnabled" label="目标表名称">
            <el-input v-model="form.targetTableName" :readonly="form.storageMode === 'REUSE'" placeholder="如 基金业务要素结果表" />
          </el-form-item>
          <el-form-item v-if="isStorageEnabled" label="目标表说明" class="wide">
            <el-input
              v-model="form.targetTableComment"
              :readonly="form.storageMode === 'REUSE'"
              type="textarea"
              :rows="2"
              placeholder="说明该结果表保存哪些业务场景和结构化结果"
            />
          </el-form-item>
          <el-form-item v-if="isStorageEnabled" label="映射方案名称" class="wide">
            <div class="field-with-tip">
              <el-input v-model="form.mappingProfileName" placeholder="如 大成基金申购单-基金业务要素结果表映射" />
              <span class="muted block">用于标识当前任务的结果字段如何映射到目标结果表；多个任务复用同一张表时便于区分。</span>
            </div>
          </el-form-item>
          <el-form-item v-if="isStorageEnabled" label="保存模式">
            <el-radio-group v-model="form.saveMode">
              <el-radio-button label="SINGLE">单对象</el-radio-button>
              <el-radio-button label="BATCH">数组批量</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>

        <div v-if="isStorageEnabled" class="card-header mt-16">
          <div>
            <h3 class="section-title">目标表字段定义</h3>
            <p class="muted">类型、长度、入库必填、默认值和入库校验属于目标表字段定义；复用已有表时只做字段存在性和约束校验。</p>
          </div>
          <el-button type="primary" :disabled="isReuseStorageMode" @click="addTargetColumn">添加目标字段</el-button>
        </div>
        <el-table v-if="isStorageEnabled" :data="targetTableColumns" class="mb-12" height="300">
          <el-table-column label="目标字段名" min-width="160" fixed>
            <template #default="{ row }"><el-input v-model="row.columnName" :readonly="isReuseStorageMode" /></template>
          </el-table-column>
          <el-table-column label="字段中文名" min-width="140">
            <template #default="{ row }"><el-input v-model="row.columnCnName" :readonly="isReuseStorageMode" /></template>
          </el-table-column>
          <el-table-column label="数据库类型" width="130">
            <template #default="{ row }">
              <el-select v-model="row.dbType" :disabled="isReuseStorageMode" @change="normalizeDbTypeParams(row)">
                <el-option label="varchar" value="varchar" />
                <el-option label="char" value="char" />
                <el-option label="decimal" value="decimal" />
                <el-option label="number" value="number" />
                <el-option label="int" value="int" />
                <el-option label="date" value="date" />
                <el-option label="datetime" value="datetime" />
                <el-option label="text" value="text" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="类型参数" min-width="210">
            <template #default="{ row }">
              <div v-if="['varchar', 'char'].includes(row.dbType)" class="db-param-cell">
                <span>{{ row.dbType }}(</span>
                <el-input-number v-model="row.length" :min="1" :controls="false" :disabled="isReuseStorageMode" placeholder="长度" />
                <span>)</span>
              </div>
              <div v-else-if="['decimal', 'number'].includes(row.dbType)" class="db-param-cell">
                <span>{{ row.dbType }}(</span>
                <el-input-number v-model="row.precision" :min="1" :controls="false" :disabled="isReuseStorageMode" placeholder="精度" />
                <span>,</span>
                <el-input-number v-model="row.scale" :min="0" :controls="false" :disabled="isReuseStorageMode" placeholder="小数位" />
                <span>)</span>
              </div>
              <span v-else class="muted">无需配置</span>
            </template>
          </el-table-column>
          <el-table-column label="入库必填" width="90">
            <template #default="{ row }"><el-switch v-model="row.required" :disabled="isReuseStorageMode" /></template>
          </el-table-column>
          <el-table-column label="默认值" min-width="120">
            <template #default="{ row }"><el-input v-model="row.defaultValue" :readonly="isReuseStorageMode" placeholder="可为空" /></template>
          </el-table-column>
          <el-table-column label="入库校验规则" min-width="180">
            <template #default="{ row }"><el-input v-model="row.validationRule" :readonly="isReuseStorageMode" placeholder="如非空、金额格式" /></template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" :disabled="isReuseStorageMode" @click="removeTargetColumn(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-card v-if="isStorageEnabled" shadow="never" class="mb-12">
          <template #header>
            <div class="card-header">
              <div>
                <span>唯一约束配置</span>
                <p class="muted">用于落库前判断业务数据是否重复，避免重复上传、重复推送或任务重试导致重复入库。</p>
              </div>
              <el-button type="primary" @click="addUniqueConstraint">新增唯一约束</el-button>
            </div>
          </template>
          <el-table :data="uniqueConstraints" empty-text="暂未配置唯一约束">
            <el-table-column label="启用" width="70">
              <template #default="{ row }"><el-switch v-model="row.enabled" /></template>
            </el-table-column>
            <el-table-column label="约束名称" min-width="190">
              <template #default="{ row }">
                <el-input v-model="row.constraintName" :disabled="!row.enabled" placeholder="如 uk_ext_fund_result_biz" />
              </template>
            </el-table-column>
            <el-table-column label="唯一字段组合" min-width="280">
              <template #default="{ row }">
                <el-select
                  v-model="row.uniqueColumns"
                  :disabled="!row.enabled"
                  multiple
                  filterable
                  clearable
                  collapse-tags
                  collapse-tags-tooltip
                  placeholder="请选择目标字段"
                >
                  <el-option v-for="column in targetTableColumns" :key="column.columnName" :label="`${column.columnCnName}（${column.columnName}）`" :value="column.columnName" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="判断范围" min-width="150">
              <template #default="{ row }">
                <el-select v-model="row.duplicateScope" :disabled="!row.enabled">
                  <el-option label="当前目标表" value="TARGET_TABLE" />
                  <el-option label="当前配置版本" value="CONFIG_VERSION" />
                  <el-option label="当前来源系统" value="SOURCE_SYSTEM" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="冲突策略" min-width="150">
              <template #default="{ row }">
                <el-select v-model="row.duplicateStrategy" :disabled="!row.enabled">
                  <el-option label="阻断入库" value="BLOCK" />
                  <el-option label="更新已有记录" value="UPDATE" />
                  <el-option label="忽略重复记录" value="IGNORE" />
                  <el-option label="转人工复核" value="REVIEW" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="生成唯一索引" width="120">
              <template #default="{ row }"><el-switch v-model="row.generateDbIndex" :disabled="!row.enabled" /></template>
            </el-table-column>
            <el-table-column label="约束预览" min-width="220">
              <template #default="{ row }">
                <el-tag v-if="row.enabled" type="primary">{{ row.constraintName }}({{ row.uniqueColumns.join(', ') || '未选择字段' }})</el-tag>
                <el-tag v-else type="info">未启用</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" @click="removeUniqueConstraint(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        <div v-if="isStorageEnabled" class="ddl-preview-panel mb-12">
          <div class="card-header">
            <div>
              <h3 class="section-title">表结构 DDL 预览</h3>
              <p class="muted">根据目标表字段定义和唯一约束生成；未开启“生成唯一索引”的约束仅作为业务去重规则展示。</p>
            </div>
            <el-button @click="ddlPreviewVisible = !ddlPreviewVisible">{{ ddlPreviewVisible ? '收起预览' : '查看建表语句预览' }}</el-button>
          </div>
          <el-input
            v-if="ddlPreviewVisible"
            type="textarea"
            :rows="8"
            :model-value="storageDdlPreview"
          />
        </div>

        <div class="card-header mt-16">
          <div>
            <h3 class="section-title">{{ isStorageEnabled ? '结果字段与目标字段映射' : '结果字段配置' }}</h3>
            <p class="muted">结果字段统一包含提取字段、加工衍生字段和系统字段；加工、校验、落库、复核和推送都基于这套字段目录。</p>
          </div>
          <div class="header-actions">
            <span v-if="isStorageEnabled" class="mapping-profile-hint">
              <el-tag size="small" type="info">已复用 {{ mappingProfiles.length }} 个方案</el-tag>
              <el-button link type="primary" @click="mappingProfileDrawerVisible = true">查看已有映射</el-button>
            </span>
            <el-button v-if="isStorageEnabled" @click="autoMapFields">自动生成映射</el-button>
            <el-button type="primary" @click="addField">添加结果字段</el-button>
          </div>
        </div>
        <el-table :data="fields">
          <el-table-column label="字段来源" width="110">
            <template #default="{ row }">
              <el-tag :type="resultFieldSourceTagType(row.sourceType)">{{ resultFieldSourceLabel(row.sourceType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="结果字段编码" min-width="150">
            <template #default="{ row }"><el-input v-model="row.fieldCode" /></template>
          </el-table-column>
          <el-table-column label="结果字段名称" min-width="150">
            <template #default="{ row }"><el-input v-model="row.fieldName" /></template>
          </el-table-column>
          <el-table-column label="字段描述" min-width="220">
            <template #default="{ row }"><el-input v-model="row.fieldDescription" placeholder="用于生成 AI 提示词" /></template>
          </el-table-column>
          <el-table-column label="提取必填" width="90">
            <template #default="{ row }"><el-switch v-model="row.extractRequired" /></template>
          </el-table-column>
          <el-table-column label="多值" width="80">
            <template #default="{ row }"><el-switch v-model="row.multiple" /></template>
          </el-table-column>
          <el-table-column v-if="isStorageEnabled" label="目标字段" min-width="260">
            <template #default="{ row }">
              <el-select v-if="form.storageMode === 'REUSE'" v-model="row.targetColumn" filterable clearable>
                <el-option v-for="column in targetColumnOptions" :key="column" :label="column" :value="column" />
              </el-select>
              <el-input v-else v-model="row.targetColumn" />
            </template>
          </el-table-column>
          <el-table-column v-if="isStorageEnabled" label="映射说明" min-width="160">
            <template #default="{ row }">
              <span class="muted">{{ row.fieldCode }} 写入 {{ row.targetColumn }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" @click="removeExtractField(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <template v-if="activeStep === 3">
        <div class="card-header">
          <div>
            <h2>提取策略</h2>
            <p class="muted">AI 适合非固定版式的一次性要素提取；传统规则适合结构化文档和格式稳定字段。P0 先真实执行文本正则，表格列映射、固定单元格等作为后续解析器的配置入口。</p>
          </div>
        </div>
        <el-form :model="form" label-width="130px" class="form-grid">
          <el-form-item label="输出模式"><el-radio-group v-model="form.outputMode"><el-radio-button label="SINGLE">单对象</el-radio-button><el-radio-button label="ARRAY">数组对象</el-radio-button></el-radio-group></el-form-item>
          <el-form-item label="LLM 模型">
            <el-select v-model="form.llmModelCode" filterable clearable placeholder="请选择启用中的模型">
              <el-option
                v-for="model in llmModelOptions"
                :key="model.modelCode"
                :label="model.defaultModel ? `${model.label} 默认` : model.label"
                :value="model.modelCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="默认策略"><el-select v-model="form.defaultStrategy"><el-option label="AI 优先，传统规则兜底" value="AI_FIRST_RULE_FALLBACK" /><el-option label="传统规则优先，AI 兜底" value="RULE_FIRST_AI_FALLBACK" /></el-select></el-form-item>
          <el-form-item label="置信度阈值"><el-input-number v-model="form.confidenceThreshold" :min="0" :max="1" :step="0.01" /></el-form-item>
        </el-form>
        <el-alert
          v-if="!llmModelOptions.length"
          class="mb-12"
          title="当前没有启用中的 LLM 配置。请先在“模型中心-LLM 配置”新增并启用模型。"
          type="warning"
          :closable="false"
        />

        <div class="strategy-overview">
          <el-alert
            :title="extractStrategyGuideText"
            type="info"
            :closable="false"
          />
          <el-alert
            class="mt-8"
            :title="extractStrategyRuntimeText"
            type="warning"
            :closable="false"
          />
          <div class="strategy-kpi">
            <span>AI 覆盖字段：<strong>{{ fields.length }}</strong></span>
            <span>已配置传统规则：<strong>{{ fields.filter((field) => (field as any).extractByRegex).length }}</strong></span>
            <span>低于 <strong>{{ Math.round(form.confidenceThreshold * 100) }}%</strong> 进入复核</span>
          </div>
        </div>

        <el-tabs v-model="activeExtractTab" type="border-card" class="extract-strategy-tabs">
          <el-tab-pane label="AI 一次性提取" name="ai">
          <el-card shadow="never">
            <template #header>
              <div class="card-header">
                <span>AI 一次性提取全部字段</span>
                <el-switch v-model="aiEnabled" active-text="启用" inactive-text="停用" />
              </div>
            </template>
            <el-form label-width="110px">
              <el-form-item label="系统提示词">
                <div class="prompt-editor wide">
                  <div class="prompt-editor-bar">
                    <span class="muted">默认从模型中心提示词模板加载，保存配置后会固定在当前版本中。</span>
                    <el-button size="small" @click="restoreDefaultSystemPrompt">恢复默认</el-button>
                  </div>
                  <el-input v-model="aiSystemPrompt" type="textarea" :rows="4" />
                </div>
              </el-form-item>
              <el-form-item label="用户提示词">
                <div class="prompt-editor wide">
                  <div class="prompt-editor-bar">
                    <div class="prompt-editor-status">
                      <el-tag size="small" :type="promptStatusType">{{ promptStatusLabel }}</el-tag>
                      <span class="muted">默认根据字段配置生成；执行时将拼接解析后的文档内容提交给 AI。</span>
                    </div>
                    <div class="header-actions">
                      <el-button size="small" @click="regenerateAiUserPrompt">按字段重新生成</el-button>
                      <el-button size="small" @click="restoreAutoPrompt">恢复自动生成</el-button>
                    </div>
                  </div>
                  <el-alert
                    v-if="promptOutdated"
                    class="mb-12"
                    title="字段配置已变更，当前用户提示词不会自动覆盖。可点击“按字段重新生成”同步最新字段。"
                    type="warning"
                    :closable="false"
                  />
                  <el-input :model-value="aiUserPrompt" type="textarea" :rows="5" :placeholder="fieldDrivenUserPrompt" @input="handleAiUserPromptInput" />
                </div>
              </el-form-item>
            </el-form>
          </el-card>
          </el-tab-pane>

          <el-tab-pane label="传统规则" name="regex">
          <el-card shadow="never">
            <template #header>
              <div class="card-header">
                <div>
                  <span>字段级传统取数规则</span>
                  <p class="muted">
                    已启用 {{ regexEnabledCount }}/{{ fields.length }} 个字段，文本正则已配置 {{ regexConfiguredCount }} 个，已验证 {{ regexValidatedCount }} 个，失败 {{ regexFailedCount }} 个
                  </p>
                </div>
                <el-button size="small" type="primary" @click="runAllRegexPreview">验证全部文本正则</el-button>
              </div>
            </template>
            <el-alert
              class="mb-12"
              :title="regexStrategyHelpText"
              type="success"
              :closable="false"
            />
            <el-input v-model="regexSampleText" class="mb-12" type="textarea" :rows="4" placeholder="输入统一测试文本，用于验证下方所有文本正则规则" />
            <el-table :data="extractedResultFields" class="regex-rule-table" height="520">
              <el-table-column label="字段" min-width="150" fixed>
                <template #default="{ row }">
                  <strong>{{ row.fieldName }}</strong>
                  <span class="muted block">{{ row.fieldCode }}</span>
                </template>
              </el-table-column>
              <el-table-column label="提取必填" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.extractRequired ? 'danger' : 'info'">{{ row.extractRequired ? '是' : '否' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="启用" width="70">
                <template #default="{ row }">
                  <el-switch v-model="row.extractByRegex" />
                </template>
              </el-table-column>
              <el-table-column label="规则类型" min-width="150">
                <template #default="{ row }">
                  <el-select v-model="row.traditionalRuleType" filterable>
                    <el-option v-for="item in traditionalRuleTypeOptions" :key="item.value" :label="item.label" :value="item.value">
                      <div class="option-row">
                        <span>{{ item.label }}</span>
                        <el-tag size="small" :type="item.executable ? 'success' : 'info'">{{ item.executable ? 'P0 可执行' : '待接入' }}</el-tag>
                      </div>
                    </el-option>
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="接入状态" min-width="130">
                <template #default="{ row }">
                  <el-tag :type="isExecutableTraditionalRule(row) ? 'success' : 'info'">{{ traditionalRuleStatusText(row) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="正则表达式" min-width="260">
                <template #default="{ row }">
                  <el-input v-if="isExecutableTraditionalRule(row)" v-model="row.regexPattern" type="textarea" :rows="2" placeholder="填写该字段的正则表达式" />
                  <span v-else class="muted">{{ traditionalRuleTypeLabel(row.traditionalRuleType) }} 的详细配置将在对应解析器接入后开放。</span>
                </template>
              </el-table-column>
              <el-table-column label="分组" width="86">
                <template #default="{ row }">
                  <el-input-number v-if="isExecutableTraditionalRule(row)" v-model="row.regexGroup" :min="0" />
                  <span v-else class="muted">-</span>
                </template>
              </el-table-column>
              <el-table-column label="flags" width="90">
                <template #default="{ row }">
                  <el-input v-if="isExecutableTraditionalRule(row)" v-model="row.regexFlags" placeholder="i/m" />
                  <span v-else class="muted">-</span>
                </template>
              </el-table-column>
              <el-table-column label="验证结果" min-width="140">
                <template #default="{ row }">
                  <el-tag
                    :type="regexPreviewMap[row.fieldCode] === '未匹配' || regexPreviewMap[row.fieldCode] === '正则错误' ? 'warning' : regexPreviewMap[row.fieldCode] === '未配置' || regexPreviewMap[row.fieldCode] === '待接入' ? 'info' : 'success'"
                  >
                    {{ regexPreviewMap[row.fieldCode] || '待验证' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="86" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" :disabled="!isExecutableTraditionalRule(row)" @click="runFieldRegexPreview(row)">验证此字段</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
          </el-tab-pane>
        </el-tabs>

        <el-table
          class="mt-16"
          :data="fields.map((field) => ({
            fieldName: field.fieldName,
            fieldCode: field.fieldCode,
            ai: aiEnabled ? '统一提示词覆盖' : '停用',
            regex: (field as any).extractByRegex ? `${traditionalRuleTypeLabel((field as any).traditionalRuleType)}已配置` : '未配置',
            strategy: fieldStrategyDescription
          }))"
        >
          <el-table-column prop="fieldName" label="字段" width="140" />
          <el-table-column prop="fieldCode" label="编码" width="150" />
          <el-table-column prop="ai" label="AI 取数" width="150" />
          <el-table-column prop="regex" label="传统规则" width="180" />
          <el-table-column prop="strategy" label="执行说明" min-width="260" />
        </el-table>
      </template>

      <template v-if="activeStep === 4">
        <div class="card-header">
          <div>
            <h2>加工校验</h2>
            <p class="muted">默认不启用。仅在需要字段标准化、主数据补全、格式校验或阻断/复核判断时开启。</p>
          </div>
          <div class="header-actions process-switches">
            <el-switch v-model="form.transformEnabled" active-text="启用加工规则" inactive-text="不加工" />
            <el-switch v-model="form.validationEnabled" active-text="启用校验规则" inactive-text="不校验" />
          </div>
        </div>
        <el-alert
          class="mb-12"
          title="加工规则和校验规则均为可选能力。关闭后任务执行会跳过对应环节；已维护规则会保留但不生效。"
          type="info"
          :closable="false"
        />

        <el-tabs v-model="activeProcessTab" type="border-card" class="process-tabs">
          <el-tab-pane label="加工规则" name="transform">
        <el-empty v-if="!transformFeatureEnabled" description="未启用加工规则，任务执行时会跳过字段标准化和衍生字段处理。" />
        <div v-else class="transform-designer">
          <el-card shadow="never" class="rule-list-card">
            <template #header>
              <div class="card-header compact-header">
                <span>加工规则流水线</span>
                <el-dropdown trigger="click">
                  <el-button type="primary">新增规则</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="addTransformRule('DICT')">字典转换</el-dropdown-item>
                      <el-dropdown-item @click="addTransformRule('API')">API 取数</el-dropdown-item>
                      <el-dropdown-item @click="addTransformRule('SQL')">SQL 查询</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
            <el-empty v-if="!transformRules.length" description="暂无加工规则，可按需新增字典转换、API取数或SQL查询。" />
            <div
              v-for="(rule, index) in transformRules"
              :key="rule.id"
              class="rule-item"
              :class="{ active: selectedRuleId === rule.id }"
              @click="selectedRuleId = rule.id"
            >
              <div class="rule-index">{{ index + 1 }}</div>
              <div class="rule-main">
                <div class="rule-title-row">
                  <strong>{{ rule.ruleName }}</strong>
                  <el-tag :type="rule.ruleType === 'DICT' ? 'success' : rule.ruleType === 'API' ? 'primary' : 'warning'">
                    {{ transformTypeLabel[rule.ruleType] }}
                  </el-tag>
                </div>
                <span>{{ rule.inputField }} -> {{ rule.outputMode === 'OVERWRITE_INPUT' ? '覆盖输入字段' : (rule.outputField || '待配置输出字段') }}</span>
              </div>
              <el-switch v-model="rule.enabled" @click.stop />
              <el-dropdown trigger="click" @click.stop>
                <el-button link @click.stop>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item :disabled="index === 0" @click="moveTransformRule(rule, -1)">上移</el-dropdown-item>
                    <el-dropdown-item :disabled="index === transformRules.length - 1" @click="moveTransformRule(rule, 1)">下移</el-dropdown-item>
                    <el-dropdown-item @click="copyTransformRule(rule)">复制</el-dropdown-item>
                    <el-dropdown-item divided @click="removeTransformRule(rule)">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </el-card>

          <el-card v-if="selectedTransformRule" shadow="never" class="rule-editor-card">
            <template #header>
              <div class="card-header">
                <span>规则编辑器</span>
                <el-tag :type="selectedTransformRule.enabled ? 'success' : 'info'">
                  {{ selectedTransformRule.enabled ? '启用' : '停用' }}
                </el-tag>
              </div>
            </template>

            <section class="config-section">
              <div class="config-section-title">基础信息</div>
            <el-form label-width="120px" class="form-grid">
              <el-form-item label="规则名称">
                <el-input v-model="selectedTransformRule.ruleName" />
              </el-form-item>
              <el-form-item label="转换方式">
                <el-radio-group v-model="selectedTransformRule.ruleType">
                  <el-radio-button label="DICT">字典转换</el-radio-button>
                  <el-radio-button label="API">API 取数</el-radio-button>
                  <el-radio-button label="SQL">SQL 查询</el-radio-button>
                </el-radio-group>
              </el-form-item>
            </el-form>
            </section>

            <section class="config-section">
              <div class="config-section-title">字段与输出</div>
              <el-form label-width="120px" class="form-grid">
              <el-form-item label="输入字段">
                <el-select v-model="selectedTransformRule.inputField" filterable allow-create>
                  <el-option v-for="field in fields" :key="field.fieldCode" :label="`${field.fieldName}（${field.fieldCode}）`" :value="field.fieldCode" />
                </el-select>
              </el-form-item>
              <el-form-item label="输出方式">
                <el-select v-model="selectedTransformRule.outputMode">
                  <el-option label="覆盖输入字段" value="OVERWRITE_INPUT" />
                  <el-option label="写入目标字段" value="WRITE_TARGET" />
                  <el-option label="生成衍生字段" value="DERIVE_FIELD" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="selectedTransformRule.outputMode !== 'OVERWRITE_INPUT'" label="输出字段">
                <div class="field-select-with-action">
                  <el-select v-model="selectedTransformRule.outputField" filterable clearable placeholder="请选择结果字段">
                    <el-option
                      v-for="field in fields"
                      :key="field.fieldCode"
                      :label="`${field.fieldName}（${field.fieldCode}）`"
                      :value="field.fieldCode"
                    >
                      <span>{{ field.fieldName }}（{{ field.fieldCode }}）</span>
                      <el-tag class="option-tag" size="small" :type="resultFieldSourceTagType((field as any).sourceType)">{{ resultFieldSourceLabel((field as any).sourceType) }}</el-tag>
                    </el-option>
                  </el-select>
                  <el-button v-if="selectedTransformRule.outputMode === 'DERIVE_FIELD'" @click="openDerivedFieldDialog">新增衍生字段</el-button>
                </div>
              </el-form-item>
              <el-form-item label="失败策略">
                <el-select v-model="selectedTransformRule.onFail">
                  <el-option label="保留原值" value="KEEP_ORIGINAL" />
                  <el-option label="置为空" value="SET_NULL" />
                  <el-option label="进入复核" value="REVIEW" />
                  <el-option label="阻断任务" value="BLOCK" />
                </el-select>
              </el-form-item>
              </el-form>
            </section>

            <section class="config-section">
              <div class="config-section-title">执行条件</div>
              <el-form label-width="120px" class="form-grid">
              <el-form-item label="执行方式" class="wide">
                <el-radio-group v-model="selectedTransformRule.conditionEnabled">
                  <el-radio-button :label="false">总是执行</el-radio-button>
                  <el-radio-button :label="true">满足条件时执行</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item v-if="selectedTransformRule.conditionEnabled" label="条件配置" class="wide">
                <div class="inline-fields">
                  <el-select v-model="selectedTransformRule.conditionField" filterable allow-create placeholder="条件字段">
                    <el-option v-for="field in fields" :key="field.fieldCode" :label="`${field.fieldName}（${field.fieldCode}）`" :value="field.fieldCode" />
                  </el-select>
                  <el-select v-model="selectedTransformRule.conditionOperator" placeholder="条件">
                    <el-option label="非空时" value="NOT_EMPTY" />
                    <el-option label="等于" value="EQUALS" />
                    <el-option label="包含" value="CONTAINS" />
                  </el-select>
                  <el-input v-model="selectedTransformRule.conditionValue" :disabled="selectedTransformRule.conditionOperator === 'NOT_EMPTY'" placeholder="条件值" />
                </div>
              </el-form-item>
            </el-form>
            </section>

            <section v-if="selectedTransformRule.ruleType === 'DICT'" class="rule-section">
              <div class="card-header">
                <h3>类型配置：字典映射</h3>
                <el-button size="small" @click="addDictItem">添加映射</el-button>
              </div>
              <el-form label-width="90px" class="form-grid mb-12">
                <el-form-item label="匹配方式">
                  <el-select v-model="selectedTransformRule.dictMatchMode">
                    <el-option label="等于" value="EQUALS" />
                    <el-option label="包含" value="CONTAINS" />
                    <el-option label="正则" value="REGEX" />
                    <el-option label="区间" value="RANGE" />
                  </el-select>
                </el-form-item>
              </el-form>
              <el-table :data="selectedTransformRule.dictItems">
                <el-table-column label="原始值/区间">
                  <template #default="{ row }"><el-input v-model="row.source" placeholder="如：买入、0-100000" /></template>
                </el-table-column>
                <el-table-column label="标准值">
                  <template #default="{ row }"><el-input v-model="row.target" placeholder="如：BUY、普通金额" /></template>
                </el-table-column>
              </el-table>
            </section>

            <section v-if="selectedTransformRule.ruleType === 'API'" class="rule-section">
              <h3>类型配置：API 取数</h3>
              <el-form label-width="130px" class="form-grid">
                <el-form-item label="请求方式">
                  <el-radio-group v-model="selectedTransformRule.apiMethod">
                    <el-radio-button label="GET">GET</el-radio-button>
                    <el-radio-button label="POST">POST</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="参数名">
                  <el-input v-model="selectedTransformRule.apiParamName" placeholder="如 accountNo" />
                </el-form-item>
                <el-form-item label="接口地址" class="wide">
                  <el-input v-model="selectedTransformRule.apiEndpoint" placeholder="/master-data/accounts/{accountNo}" />
                </el-form-item>
                <el-form-item label="响应取值路径" class="wide">
                  <el-input v-model="selectedTransformRule.apiResponsePath" placeholder="$.data.accountName" />
                </el-form-item>
                <el-form-item label="超时秒数">
                  <el-input-number v-model="selectedTransformRule.apiTimeout" :min="1" :max="60" />
                </el-form-item>
                <el-form-item label="重试次数">
                  <el-input-number v-model="selectedTransformRule.apiRetryCount" :min="0" :max="5" />
                </el-form-item>
                <el-form-item label="认证方式">
                  <el-select v-model="selectedTransformRule.apiAuthMode">
                    <el-option label="系统统一认证" value="SYSTEM" />
                    <el-option label="不认证" value="NONE" />
                  </el-select>
                </el-form-item>
                <el-form-item label="成功判断" class="wide">
                  <el-input v-model="selectedTransformRule.apiSuccessRule" placeholder="如 $.code == 0 && $.data != null" />
                </el-form-item>
              </el-form>
            </section>

            <section v-if="selectedTransformRule.ruleType === 'SQL'" class="rule-section">
              <h3>类型配置：SQL 查询</h3>
              <el-alert class="mb-12" title="SQL 仅支持 SELECT，只允许使用参数占位符，禁止拼接用户输入。" type="warning" :closable="false" />
              <el-form label-width="130px" class="form-grid">
                <el-form-item label="只读数据源">
                  <el-select v-model="selectedTransformRule.sqlDatasource">
                    <el-option label="产品主数据只读库" value="产品主数据只读库" />
                    <el-option label="客户主数据只读库" value="客户主数据只读库" />
                    <el-option label="主数据只读库" value="主数据只读库" />
                  </el-select>
                </el-form-item>
                <el-form-item label="结果字段">
                  <el-input v-model="selectedTransformRule.sqlResultColumn" placeholder="product_name" />
                </el-form-item>
                <el-form-item label="最大返回行">
                  <el-input-number v-model="selectedTransformRule.sqlMaxRows" :min="1" :max="10" />
                </el-form-item>
                <el-form-item label="只读校验">
                  <el-switch v-model="selectedTransformRule.sqlReadonlyChecked" active-text="已校验" inactive-text="待校验" />
                </el-form-item>
                <el-form-item label="SQL 模板" class="wide">
                  <el-input v-model="selectedTransformRule.sqlText" type="textarea" :rows="5" />
                </el-form-item>
              </el-form>
            </section>

            <section class="rule-preview">
              <div>
                <strong>转换预览</strong>
                <span class="muted">输入样例会按当前规则生成模拟输出。</span>
              </div>
              <el-input v-model="previewInput" placeholder="输入样例值" />
              <el-button type="primary" @click="runTransformPreview">运行预览</el-button>
              <div class="preview-result">
                <el-tag type="success">预览通过</el-tag>
                <strong>{{ previewOutput }}</strong>
              </div>
            </section>
          </el-card>
        </div>
          </el-tab-pane>

          <el-tab-pane label="校验规则" name="validation">
            <div class="card-header mb-12">
              <div>
                <h3 class="section-title">校验规则</h3>
                <p class="muted">校验只判断数据是否可继续落库、复核或推送，不直接改写字段值。</p>
              </div>
              <div class="header-actions">
                <el-button :disabled="!validationFeatureEnabled || !validationRules.length" @click="runValidationPreview">运行校验预览</el-button>
                <el-button type="primary" :disabled="!validationFeatureEnabled" @click="addValidationRule">新增校验规则</el-button>
              </div>
            </div>
            <el-alert
              class="mb-12"
              title="建议把必填、格式、跨字段、唯一性、主数据命中等校验与加工规则分开维护；阻断类校验会影响落库和下游推送。"
              type="info"
              :closable="false"
            />
            <el-empty v-if="!validationFeatureEnabled" description="未启用校验规则，任务执行时只按置信度和复核策略判断。" />
            <el-empty v-else-if="!validationRules.length" description="暂无校验规则，可按需新增必填、格式、范围、跨字段、唯一性或主数据校验。" />
            <el-table v-else :data="validationRules" height="520">
              <el-table-column label="启用" width="70">
                <template #default="{ row }"><el-switch v-model="row.enabled" /></template>
              </el-table-column>
              <el-table-column label="规则名称" min-width="170">
                <template #default="{ row }"><el-input v-model="row.ruleName" /></template>
              </el-table-column>
              <el-table-column label="校验类型" width="150">
                <template #default="{ row }">
                  <el-select v-model="row.ruleType">
                    <el-option label="必填" value="REQUIRED" />
                    <el-option label="格式" value="FORMAT" />
                    <el-option label="范围" value="RANGE" />
                    <el-option label="跨字段" value="CROSS_FIELD" />
                    <el-option label="唯一性" value="UNIQUE" />
                    <el-option label="主数据" value="MASTER_DATA" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="校验字段" min-width="150">
                <template #default="{ row }">
                  <el-select v-model="row.fieldCode" filterable allow-create>
                    <el-option v-for="field in fields" :key="field.fieldCode" :label="`${field.fieldName}（${field.fieldCode}）`" :value="field.fieldCode" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="失败级别" width="130">
                <template #default="{ row }">
                  <el-select v-model="row.severity">
                    <el-option label="提示" value="WARN" />
                    <el-option label="转复核" value="REVIEW" />
                    <el-option label="阻断" value="BLOCK" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="校验表达式" min-width="240">
                <template #default="{ row }"><el-input v-model="row.expression" placeholder="如 amount > 0、md_product.product_code exists" /></template>
              </el-table-column>
              <el-table-column label="失败提示" min-width="240">
                <template #default="{ row }"><el-input v-model="row.failMessage" /></template>
              </el-table-column>
              <el-table-column label="操作" width="80" fixed="right">
                <template #default="{ row }">
                  <el-button link type="danger" @click="removeValidationRule(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>

      <template v-if="activeStep === 5">
        <h2>复核策略</h2>
        <el-form :model="form" label-width="140px" class="form-grid">
          <el-form-item label="复核阈值"><el-input-number v-model="form.confidenceThreshold" :min="0" :max="1" :step="0.01" /></el-form-item>
          <el-form-item label="复核角色">
            <el-select v-model="form.reviewerRole" filterable>
              <el-option v-for="item in options.roles" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="强制复核字段" class="wide">
            <el-checkbox checked>划款金额</el-checkbox>
            <el-checkbox checked>收款账号</el-checkbox>
            <el-checkbox>付款方名称</el-checkbox>
          </el-form-item>
        </el-form>
      </template>

      <template v-if="activeStep === 6">
        <div class="card-header">
          <div>
            <h2>下游推送</h2>
            <p class="muted">这里只绑定当前配置的推送规则；下游系统和接口服务的地址、鉴权、重试等信息在“系统管理-集成管理”维护。</p>
          </div>
          <el-switch v-model="form.pushEnabled" active-text="启用推送" inactive-text="不推送" />
        </div>
        <el-alert
          class="mb-12"
          title="仅当提取、加工、校验、复核和落库均通过后，才允许自动推送下游；待复核或校验失败的数据不会自动推送。"
          type="info"
          :closable="false"
        />
        <el-alert
          class="mb-12"
          title="第一版真实验证先支持 HTTP JSON 同步推送；微服务、MQ 和异步队列可先维护服务档案，执行链路后续接入。"
          type="warning"
          :closable="false"
        />
        <el-form :model="form" label-width="130px" class="form-grid">
          <el-form-item label="触发时机">
            <el-select v-model="form.pushTrigger">
              <el-option label="复核通过后" value="REVIEW_APPROVED" />
              <el-option label="落库成功后" value="STORED" />
              <el-option label="人工确认后" value="MANUAL_CONFIRMED" />
              <el-option label="仅手工触发" value="MANUAL_ONLY" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标接口服务">
            <el-select v-model="form.targetServices" multiple filterable clearable collapse-tags collapse-tags-tooltip>
              <el-option
                v-for="service in enabledDownstreamServices"
                :key="service.serviceCode"
                :label="`${service.systemName} / ${service.serviceName}（${service.serviceType}）`"
                :value="service.serviceCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="推送字段范围">
            <el-select v-model="form.pushScope">
              <el-option label="按落库映射字段推送" value="MAPPED_FIELDS" />
              <el-option label="推送全部结果字段" value="ALL_EXTRACTED_FIELDS" />
            </el-select>
          </el-form-item>
          <el-form-item label="推送模式">
            <el-radio-group v-model="form.pushMode">
              <el-radio-button label="SYNC">同步等待响应</el-radio-button>
              <el-radio-button label="ASYNC" disabled>异步推送</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="幂等键" class="wide">
            <el-input v-model="form.idempotentKey" placeholder="${traceId}-${taskId}-${serviceCode}" />
            <div class="form-tip">支持变量：${traceId}、${taskId}、${documentId}、${serviceCode}、${resultVersion}</div>
          </el-form-item>
          <el-form-item label="失败策略">
            <el-select v-model="form.pushFailStrategy">
              <el-option label="自动重试后转人工处理" value="RETRY_THEN_MANUAL" />
              <el-option label="直接进入失败推送列表" value="MANUAL_ONLY" />
              <el-option label="阻断任务完成" value="BLOCK_TASK" />
            </el-select>
          </el-form-item>
        </el-form>

        <el-table :data="selectedPushServices" class="mb-12">
          <el-table-column prop="systemName" label="目标系统" min-width="130" />
          <el-table-column prop="serviceName" label="接口服务" min-width="160" />
          <el-table-column prop="purpose" label="用途" width="90" />
          <el-table-column label="方式" width="110">
            <template #default="{ row }">
              <el-tag :type="row.serviceType === 'HTTP' ? 'success' : 'warning'">{{ row.serviceType || 'HTTP' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="endpoint" label="地址/Topic/方法" min-width="260" />
          <el-table-column prop="responseSuccessRule" label="成功判断" min-width="220" />
          <el-table-column prop="retryCount" label="重试" width="70" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="form.pushEnabled && !selectedPushServices.length" description="请选择至少一个目标接口服务后维护字段映射" />
        <el-tabs v-else-if="form.pushEnabled" v-model="activePushServiceCode" class="push-service-tabs">
          <el-tab-pane
            v-for="service in selectedPushServices"
            :key="service.serviceCode"
            :label="service.serviceName || service.serviceCode"
            :name="service.serviceCode"
          >
            <div class="push-service-layout">
              <div>
                <div class="section-toolbar compact-toolbar">
                  <div>
                    <strong>字段映射</strong>
                    <p class="muted">每个接口服务独立维护下游字段名，适合同一份结果推送到多个系统但字段口径不同的场景。</p>
                  </div>
                  <el-tag :type="service.serviceType === 'HTTP' ? 'success' : 'warning'">{{ service.serviceType || 'HTTP' }}</el-tag>
                </div>
                <el-table :data="pushFieldMappingsForService(service.serviceCode)" height="360">
                  <el-table-column prop="fieldName" label="字段名称" width="150" />
                  <el-table-column prop="sourceField" label="推送来源字段" min-width="180" />
                  <el-table-column label="下游字段" min-width="220">
                    <template #default="{ row }">
                      <el-input
                        :model-value="row.downstreamField"
                        placeholder="填写下游接口接收字段名"
                        @update:model-value="(value: string) => updateDownstreamField(service.serviceCode, row.fieldCode, value)"
                      />
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <div>
                <div class="section-toolbar compact-toolbar">
                  <div>
                    <strong>推送报文预览</strong>
                    <p class="muted">预览按当前接口服务字段映射生成，正式执行时会替换为真实 traceId、taskId 和结果值。</p>
                  </div>
                </div>
                <el-input type="textarea" :rows="18" readonly :model-value="buildPushPayloadPreview(service.serviceCode)" />
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>

      <template v-if="activeStep === 7">
        <h2>验证发布</h2>
        <el-alert
          class="mb-12"
          type="info"
          :closable="false"
          title="第一版验证只做配置完整性、DDL 预检查、正则语法、加工校验和推送配置检查；样本文档 OCR/AI 全链路验证后续再接入。"
        />
        <div class="validation-grid">
          <el-card shadow="never">
            <template #header>
              <div class="card-header">
                <span>验证概览</span>
                <el-button type="primary" :loading="saving" :disabled="isReadonlyVersion" @click="validate">运行验证</el-button>
              </div>
            </template>
            <template v-if="validationReport">
              <div class="metric-grid compact-metrics">
                <div class="metric-card mini">
                  <span>检查项</span>
                  <strong>{{ validationSummary.total }}</strong>
                </div>
                <div class="metric-card mini">
                  <span>通过</span>
                  <strong>{{ validationSummary.passed }}</strong>
                </div>
                <div class="metric-card mini">
                  <span>提醒</span>
                  <strong>{{ validationSummary.warning }}</strong>
                </div>
                <div class="metric-card mini">
                  <span>阻断</span>
                  <strong>{{ validationSummary.failed }}</strong>
                </div>
              </div>
              <el-alert
                class="mt-12"
                :type="validationReport.passed ? 'success' : 'error'"
                :title="validationReport.message"
                :description="`检查时间：${validationReport.checkedAt}`"
                :closable="false"
              />
            </template>
            <el-empty v-else description="尚未运行验证。点击“运行验证”后生成发布前检查报告。" />
          </el-card>

          <el-card shadow="never">
            <template #header>DDL 预检查</template>
            <el-input
              type="textarea"
              :rows="12"
              readonly
              :model-value="validationReport?.ddlPreview || storageDdlPreview"
            />
          </el-card>
        </div>

        <el-card shadow="never" class="mt-12">
          <template #header>验证明细</template>
          <el-collapse v-if="validationReport?.sections?.length" accordion>
            <el-collapse-item v-for="section in validationReport.sections" :key="section.code" :name="section.code">
              <template #title>
                <div class="validation-section-title">
                  <span>{{ section.title }}</span>
                  <el-tag :type="validationSectionTagType(section.status)">
                    {{ validationSectionLabel(section.status) }}
                  </el-tag>
                </div>
              </template>
              <el-table :data="section.items" stripe>
                <el-table-column label="级别" width="90">
                  <template #default="{ row }">
                    <el-tag :type="validationIssueTagType(row.level)">
                      {{ validationIssueLabel(row.level) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="message" label="检查结果" min-width="320" />
              </el-table>
            </el-collapse-item>
          </el-collapse>
          <el-empty v-else description="暂无验证明细" />
        </el-card>
      </template>

      <el-drawer v-model="mappingProfileDrawerVisible" title="已有映射方案" size="720px">
        <el-alert
          class="mb-12"
          title="这里仅用于查看同一结果表的复用情况，不影响当前配置保存。"
          type="info"
          :closable="false"
        />
        <el-table :data="mappingProfiles">
          <el-table-column prop="name" label="映射方案" min-width="190" />
          <el-table-column prop="documentType" label="适用文档" width="110" />
          <el-table-column prop="targetTable" label="复用表" min-width="190" />
          <el-table-column prop="mapping" label="映射摘要" min-width="320" />
        </el-table>
      </el-drawer>

      <el-dialog v-model="derivedFieldDialogVisible" title="新增衍生结果字段" width="620px">
        <el-alert
          class="mb-12"
          title="衍生字段会加入结果字段目录，并可像普通字段一样映射目标字段、参与落库、复核和下游推送。"
          type="info"
          :closable="false"
        />
        <el-form :model="derivedFieldForm" label-width="120px" class="form-grid">
          <el-form-item label="字段编码">
            <el-input v-model="derivedFieldForm.fieldCode" placeholder="如 product_name" />
          </el-form-item>
          <el-form-item label="字段名称">
            <el-input v-model="derivedFieldForm.fieldName" placeholder="如 产品名称" />
          </el-form-item>
          <el-form-item label="字段类型">
            <el-select v-model="derivedFieldForm.dataType">
              <el-option label="字符串" value="string" />
              <el-option label="金额" value="amount" />
              <el-option label="日期" value="date" />
              <el-option label="数字" value="number" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="isStorageEnabled" label="目标字段">
            <el-select v-if="form.storageMode === 'REUSE'" v-model="derivedFieldForm.targetColumn" filterable clearable placeholder="请选择已有目标字段">
              <el-option v-for="column in storageTargetColumnOptions" :key="column.value" :label="column.label" :value="column.value" />
            </el-select>
            <el-input v-else v-model="derivedFieldForm.targetColumn" placeholder="默认同字段编码" />
          </el-form-item>
          <el-form-item v-if="isStorageEnabled && form.storageMode === 'CREATE'" label="同步目标字段" class="wide">
            <el-switch v-model="derivedFieldForm.autoAddTargetColumn" active-text="自动加入目标表字段定义" inactive-text="仅作为结果字段" />
          </el-form-item>
          <el-form-item label="字段说明" class="wide">
            <el-input v-model="derivedFieldForm.fieldDescription" type="textarea" :rows="3" placeholder="说明该字段如何由加工规则生成" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="derivedFieldDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmDerivedField">确认新增</el-button>
        </template>
      </el-dialog>

      <div class="wizard-actions">
        <el-button :disabled="activeStep === 0" @click="prev">上一步</el-button>
        <el-button v-if="activeStep < steps.length - 1" type="primary" @click="next">下一步</el-button>
        <el-button v-if="isReadonlyVersion" type="primary" @click="copyCurrentVersion">复制为新版本</el-button>
        <el-button :disabled="isReadonlyVersion" :loading="saving" @click="saveDraft">保存草稿</el-button>
        <el-button type="primary" :disabled="isReadonlyVersion" :loading="saving" @click="validate">验证</el-button>
        <el-button type="success" :disabled="isReadonlyVersion" :loading="saving" @click="publish">发布配置</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.wizard-steps {
  margin-top: 4px;
}

.wizard-step-title {
  display: inline-flex;
  min-height: 30px;
  max-width: 150px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border: 0;
  background: transparent;
  color: var(--el-text-color-regular);
  cursor: pointer;
  font: inherit;
  line-height: 1.2;
}

.wizard-step-title span {
  overflow: hidden;
  width: 100%;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wizard-step-title small {
  color: var(--el-color-danger);
  font-size: 11px;
  line-height: 1;
}

.wizard-step-title.configured {
  color: var(--el-color-success);
  font-weight: 600;
}

.wizard-step-title.has-issue {
  color: var(--el-color-danger);
}

.wizard-step-title.active {
  color: var(--el-color-primary);
  font-weight: 600;
}

.wizard-step-title:hover {
  color: var(--el-color-primary);
}

.wizard-step-title.has-issue:hover {
  color: var(--el-color-danger);
}

.wizard-steps :deep(.el-step__head.is-finish) {
  color: var(--el-color-success);
  border-color: var(--el-color-success);
}

.wizard-steps :deep(.el-step__head.is-finish .el-step__line) {
  background-color: var(--el-color-success);
}

.wizard-steps :deep(.el-step__head.is-process) {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

.wizard-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}

.form-tip {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.push-service-tabs {
  margin-top: 12px;
}

.push-service-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(360px, 0.75fr);
  gap: 12px;
}

.section-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.compact-toolbar strong {
  font-size: 13px;
}

.compact-toolbar p {
  margin: 2px 0 0;
}

.prompt-editor {
  width: 100%;
}

.prompt-editor-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.prompt-editor-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

@media (max-width: 900px) {
  .prompt-editor-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .push-service-layout {
    grid-template-columns: 1fr;
  }
}
</style>
