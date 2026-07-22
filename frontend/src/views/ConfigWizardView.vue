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
  type ConfigSummary,
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
type DictConditionOperator = 'EQUALS' | 'CONTAINS' | 'REGEX' | 'RANGE' | 'EMPTY' | 'NOT_EMPTY'
type TransformConditionLogic = 'ALL' | 'ANY'
type TransformConditionOperator = 'NOT_EMPTY' | 'EMPTY' | 'EQUALS' | 'NOT_EQUALS' | 'CONTAINS' | 'NOT_CONTAINS' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'REGEX'
type SqlNoDataStrategy = 'SET_NULL' | 'REVIEW' | 'BLOCK'
type SqlMultiRowStrategy = 'FIRST' | 'REVIEW' | 'BLOCK'
type SqlNullStrategy = 'SET_NULL' | 'REVIEW'
type ValidationRuleType = 'REQUIRED' | 'FORMAT' | 'RANGE' | 'CROSS_FIELD' | 'UNIQUE' | 'MASTER_DATA'

interface DictItem {
  id: string
  ruleName: string
  enabled: boolean
  priority: number
  conditionLogic: TransformConditionLogic
  conditions: DictCondition[]
  target: string
}

interface DictCondition {
  id: string
  fieldId?: string
  fieldCode: string
  paramName: string
  operator: DictConditionOperator
  value: string
}

interface TransformInputField {
  fieldId: string
  fieldCode: string
  paramName: string
  required: boolean
  defaultValue: string
  sampleValue: string
}

interface TransformCondition {
  id: string
  fieldId: string
  fieldCode: string
  operator: TransformConditionOperator
  value: string
}

interface SqlParamCheckRow {
  paramName: string
  sourceField: string
  sampleValue: string
  status: 'OK' | 'WARN' | 'ERROR'
  message: string
}

interface TransformRule {
  id: string
  ruleName: string
  ruleType: TransformRuleType
  inputFields: TransformInputField[]
  outputFieldId?: string
  outputField: string
  outputMode?: TransformOutputMode
  conditionEnabled?: boolean
  conditionLogic?: TransformConditionLogic
  conditions: TransformCondition[]
  enabled: boolean
  onFail: 'KEEP_ORIGINAL' | 'SET_NULL' | 'BLOCK' | 'REVIEW'
  dictItems: DictItem[]
  apiEndpoint: string
  apiMethod: 'GET' | 'POST'
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
  sqlNoDataStrategy?: SqlNoDataStrategy
  sqlMultiRowStrategy?: SqlMultiRowStrategy
  sqlNullStrategy?: SqlNullStrategy
  sqlTimeoutSeconds?: number
}

interface ValidationRule {
  id: string
  ruleName: string
  ruleType: ValidationRuleType
  fieldId: string
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

interface ResultField {
  fieldId: string
  fieldCode: string
  fieldName: string
  sourceType: ResultFieldSourceType | string
  dataType: string
  fieldLength?: number
  fieldDescription?: string
  required?: boolean
  extractRequired?: boolean
  multiple?: boolean
  targetColumn?: string
  generatedByRuleId?: string
  [key: string]: any
}

const createFieldId = (fieldCode = '') => `fld_${String(fieldCode || Date.now()).trim().replace(/[^A-Za-z0-9_]/g, '_')}_${Math.random().toString(36).slice(2, 6)}`
const normalizeFieldId = (field: Partial<ResultField>, fallbackCode = '') => field.fieldId || `fld_${String(field.fieldCode || fallbackCode || Date.now()).trim().replace(/[^A-Za-z0-9_]/g, '_')}`
const normalizeResultField = (field: Partial<ResultField>, index = 0): ResultField => {
  const fieldCode = String(field.fieldCode || `field_${index + 1}`).trim()
  return {
    ...field,
    fieldId: normalizeFieldId(field, fieldCode),
    fieldCode,
    fieldName: field.fieldName || fieldCode,
    sourceType: field.sourceType || 'EXTRACTED',
    dataType: field.dataType || 'string',
    targetColumn: field.targetColumn ?? fieldCode
  } as ResultField
}

const initialConfigFields: ResultField[] = []

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
const fields = ref<ResultField[]>(initialConfigFields.map((item, index) => normalizeResultField({ ...item }, index)))
fields.value.forEach((field) => {
  const mutable = field as any
  mutable.fieldDescription = mutable.fieldDescription || ''
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
const toParamName = (fieldCode: string) => {
  const normalized = String(fieldCode || '').trim()
  if (!normalized) return 'param'
  return normalized.replace(/_([a-zA-Z0-9])/g, (_, char: string) => char.toUpperCase())
}
const fieldById = (fieldId = '') => fields.value.find((field) => field.fieldId === fieldId)
const fieldByCode = (fieldCode = '') => fields.value.find((field) => field.fieldCode === fieldCode)
const fieldIdByCode = (fieldCode = '') => fieldByCode(fieldCode)?.fieldId || ''
const fieldCodeById = (fieldId = '') => fieldById(fieldId)?.fieldCode || ''
const resolveField = (fieldId = '', fieldCode = '') => fieldById(fieldId) || fieldByCode(fieldCode)
const createTransformInputField = (fieldIdOrCode: string): TransformInputField => {
  const field = resolveField(fieldIdOrCode, fieldIdOrCode)
  const fieldId = field?.fieldId || fieldIdOrCode
  const fieldCode = field?.fieldCode || fieldIdOrCode
  return {
    fieldId,
    fieldCode,
    paramName: toParamName(fieldCode),
    required: true,
    defaultValue: '',
    sampleValue: ''
  }
}
const createTransformCondition = (fieldIdOrCode = ''): TransformCondition => {
  const field = resolveField(fieldIdOrCode, fieldIdOrCode)
  return {
    id: `cond-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    fieldId: field?.fieldId || fieldIdOrCode,
    fieldCode: field?.fieldCode || fieldIdOrCode,
    operator: 'NOT_EMPTY',
    value: ''
  }
}
const createDictCondition = (input?: TransformInputField): DictCondition => ({
  id: `dict-cond-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
  fieldId: input?.fieldId || '',
  fieldCode: input?.fieldCode || '',
  paramName: input?.paramName || toParamName(input?.fieldCode || ''),
  operator: 'EQUALS',
  value: ''
})
const normalizeBoolean = (value: any, fallback = false) => {
  if (value === true || value === 'true' || value === '1' || value === 1) return true
  if (value === false || value === 'false' || value === '0' || value === 0) return false
  return fallback
}
const createDictItem = (rule: TransformRule, seed?: Partial<DictItem>): DictItem => ({
  id: seed?.id || `dict-rule-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
  ruleName: seed?.ruleName || `映射规则${(rule.dictItems || []).length + 1}`,
  enabled: normalizeBoolean(seed?.enabled, true),
  priority: seed?.priority || (rule.dictItems || []).length + 1,
  conditionLogic: normalizeConditionLogic(seed?.conditionLogic),
  conditions: seed?.conditions?.length ? seed.conditions : [createDictCondition(rule.inputFields[0])],
  target: seed?.target || ''
})
const normalizeDictOperator = (operator: any): DictConditionOperator => {
  return ['EQUALS', 'CONTAINS', 'REGEX', 'RANGE', 'EMPTY', 'NOT_EMPTY'].includes(operator) ? operator : 'EQUALS'
}
const normalizeConditionLogic = (logic: any): TransformConditionLogic => logic === 'ANY' ? 'ANY' : 'ALL'
const normalizeDictCondition = (condition: any, rule: TransformRule): DictCondition => {
  const input = condition.paramName
    ? rule.inputFields.find((item) => item.paramName === condition.paramName)
    : rule.inputFields.find((item) => item.fieldId === condition.fieldId || item.fieldCode === condition.fieldCode)
  return {
    id: condition.id || `dict-cond-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    fieldId: condition.fieldId || input?.fieldId || fieldIdByCode(condition.fieldCode || '') || '',
    fieldCode: condition.fieldCode || input?.fieldCode || '',
    paramName: condition.paramName || input?.paramName || toParamName(condition.fieldCode || ''),
    operator: normalizeDictOperator(condition.operator || condition.matchMode),
    value: condition.value || ''
  }
}
const normalizeDictItem = (item: any, rule: TransformRule, index: number): DictItem => {
  const legacyConditions = item.sourceValues
    ? Object.entries(item.sourceValues)
      .filter(([, value]) => String(value ?? '').trim())
      .map(([paramName, value]) => {
        const input = rule.inputFields.find((field) => field.paramName === paramName)
        return {
          id: `dict-cond-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
          fieldId: input?.fieldId || fieldIdByCode(input?.fieldCode || '') || '',
          fieldCode: input?.fieldCode || '',
          paramName,
          operator: 'EQUALS' as DictConditionOperator,
          value: String(value ?? '')
        }
      })
    : []
  const conditions = Array.isArray(item.conditions) && item.conditions.length
    ? item.conditions.map((condition: any) => normalizeDictCondition(condition, rule))
    : legacyConditions
  return createDictItem(rule, {
    id: item.id,
    ruleName: item.ruleName || `映射规则${index + 1}`,
    enabled: normalizeBoolean(item.enabled, true),
    priority: Number(item.priority || index + 1),
    conditionLogic: normalizeConditionLogic(item.conditionLogic),
    conditions: conditions.length ? conditions : [createDictCondition(rule.inputFields[0])],
    target: item.target || ''
  })
}
const ensureDictItems = (rule: TransformRule) => {
  rule.dictItems = (rule.dictItems || [])
    .map((item: any, index) => normalizeDictItem(item, rule, index))
    .sort((a, b) => (a.priority || 0) - (b.priority || 0))
  normalizeDictItemPriorities(rule)
}
const normalizeDictItemPriorities = (rule?: TransformRule) => {
  if (!rule) return
  ;(rule.dictItems || []).forEach((item, index) => {
    item.priority = index + 1
  })
}
const normalizeTransformRule = (rule: any): TransformRule => {
  const inputFields = Array.isArray(rule.inputFields) ? rule.inputFields : []
  rule.inputFields = inputFields.map((input: any) => {
    const field = resolveField(input.fieldId, input.fieldCode)
    const fieldCode = field?.fieldCode || input.fieldCode || ''
    return {
      fieldId: field?.fieldId || input.fieldId || fieldIdByCode(fieldCode),
      fieldCode,
      paramName: input.paramName || toParamName(fieldCode),
      required: input.required !== false,
      defaultValue: input.defaultValue || '',
      sampleValue: input.sampleValue || ''
    }
  }).filter((input: TransformInputField) => input.fieldId || input.fieldCode)
  if (!rule.outputFieldId && rule.outputField) rule.outputFieldId = fieldIdByCode(rule.outputField)
  if (rule.outputFieldId && !rule.outputField) rule.outputField = fieldCodeById(rule.outputFieldId)
  rule.enabled = normalizeBoolean(rule.enabled, true)
  rule.conditionEnabled = normalizeBoolean(rule.conditionEnabled, false)
  rule.dictItems = rule.dictItems || []
  const legacyCondition = rule.conditionField ? [{
    id: `cond-${rule.id || Date.now()}-legacy`,
    fieldId: fieldIdByCode(rule.conditionField),
    fieldCode: rule.conditionField,
    operator: rule.conditionOperator || 'NOT_EMPTY',
    value: rule.conditionValue || ''
  }] : []
  rule.conditionLogic = rule.conditionLogic || 'ALL'
  rule.conditions = (Array.isArray(rule.conditions) && rule.conditions.length ? rule.conditions : legacyCondition).map((condition: any) => {
    const field = resolveField(condition.fieldId, condition.fieldCode)
    return {
      id: condition.id || `cond-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      fieldId: field?.fieldId || condition.fieldId || fieldIdByCode(condition.fieldCode || ''),
      fieldCode: field?.fieldCode || condition.fieldCode || '',
      operator: condition.operator || 'NOT_EMPTY',
      value: condition.value || ''
    }
  })
  delete rule.conditionField
  delete rule.conditionOperator
  delete rule.conditionValue
  ensureDictItems(rule)
  return rule as TransformRule
}
const transformRules = ref<TransformRule[]>([])
transformRules.value.forEach((rule) => {
  normalizeTransformRule(rule)
  rule.outputMode = rule.outputMode || 'DERIVE_FIELD'
  rule.conditionEnabled = normalizeBoolean(rule.conditionEnabled, false)
  rule.conditionLogic = rule.conditionLogic || 'ALL'
  rule.apiTimeout = rule.apiTimeout ?? 5
  rule.apiRetryCount = rule.apiRetryCount ?? 1
  rule.apiAuthMode = rule.apiAuthMode || 'SYSTEM'
  rule.apiSuccessRule = rule.apiSuccessRule || '$.code == 0'
  rule.sqlMaxRows = rule.sqlMaxRows ?? 1
  rule.sqlReadonlyChecked = rule.sqlReadonlyChecked ?? true
  rule.sqlNoDataStrategy = rule.sqlNoDataStrategy || 'REVIEW'
  rule.sqlMultiRowStrategy = rule.sqlMultiRowStrategy || 'FIRST'
  rule.sqlNullStrategy = rule.sqlNullStrategy || 'REVIEW'
  rule.sqlTimeoutSeconds = rule.sqlTimeoutSeconds ?? 5
})
const selectedRuleId = ref(transformRules.value[0]?.id || '')
const activeProcessTab = ref('transform')
const draggingDictItemId = ref('')
const validationRules = ref<ValidationRule[]>([])
const selectedExtractFieldCode = ref(fields.value[0]?.fieldCode || '')
const mappingProfileDrawerVisible = ref(false)
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
const previewOutput = ref('大额')
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
  storageMode: 'CREATE',
  mappingProfileName: '',
  targetTable: '',
  targetTableName: '',
  targetTableComment: '',
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
const targetTableColumns = ref<any[]>([])
const targetColumnOptions = computed(() => targetTableColumns.value.map((column) => column.columnName))
const uniqueConstraints = ref<any[]>([])
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
const formatTargetColumnLabel = (column: any) => `${column.columnCnName || column.columnName}（${column.columnName}${column.dbType ? `，${formatDbColumnType(column)}` : ''}）`
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
    constraint.uniqueColumns = constraint.uniqueColumns.filter((column: string) => validColumns.has(column))
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
    validationRule: column.validationRule || '',
    previousColumnName: column.columnName || '',
    previousColumnCnName: column.columnCnName || ''
  }))
const createResultFieldFromTargetColumn = (column: any): ResultField => {
  const columnName = String(column.columnName || '').trim()
  const columnCnName = String(column.columnCnName || columnName).trim()
  return normalizeResultField({
    fieldId: createFieldId(columnName),
    fieldCode: columnName,
    fieldName: columnCnName,
    sourceType: 'EXTRACTED',
    dataType: column.dbType === 'decimal' || column.dbType === 'number' ? 'amount' : column.dbType === 'date' ? 'date' : 'string',
    fieldDescription: '',
    required: true,
    extractRequired: true,
    multiple: false,
    targetColumn: columnName,
    autoGeneratedFromColumn: true,
    autoSyncColumnName: columnName,
    autoSyncColumnCnName: columnCnName,
    extractByRegex: false,
    traditionalRuleEnabled: false,
    traditionalRuleType: 'REGEX',
    traditionalRuleConfig: {},
    regexPattern: '',
    regexFlags: '',
    regexGroup: 1
  }, fields.value.length)
}
const ensureResultFieldForTargetColumn = (column: any) => {
  const columnName = String(column.columnName || '').trim()
  if (!columnName || fields.value.some((field) => field.targetColumn === columnName)) return
  const field = createResultFieldFromTargetColumn(column)
  fields.value.push(field)
  form.targetServices.forEach((serviceCode) => updateDownstreamField(serviceCode, field.fieldId, columnName))
}
const handleTargetColumnChange = (column: any) => {
  const previousName = column.previousColumnName || ''
  const previousCnName = column.previousColumnCnName || previousName
  const nextName = String(column.columnName || '').trim()
  const nextCnName = String(column.columnCnName || nextName).trim()
  if (!previousName) {
    column.previousColumnName = nextName
    column.previousColumnCnName = nextCnName
    ensureResultFieldForTargetColumn(column)
    return
  }
  fields.value.forEach((field: any) => {
    const canSyncCode = field.autoGeneratedFromColumn && field.targetColumn === previousName && field.fieldCode === previousName
    const canSyncName = field.autoGeneratedFromColumn && field.targetColumn === previousName && field.fieldName === previousCnName
    if (field.targetColumn === previousName) field.targetColumn = nextName
    if (canSyncCode) {
      field.fieldCode = nextName
      field.autoSyncColumnName = nextName
    }
    if (canSyncName) {
      field.fieldName = nextCnName
      field.autoSyncColumnCnName = nextCnName
    }
  })
  uniqueConstraints.value.forEach((constraint) => {
    constraint.uniqueColumns = constraint.uniqueColumns.map((name: string) => name === previousName ? nextName : name).filter(Boolean)
  })
  column.previousColumnName = nextName
  column.previousColumnCnName = nextCnName
  syncFieldReferences()
}
const handleTargetColumnCnNameChange = (column: any) => {
  const columnName = String(column.columnName || '').trim()
  const previousCnName = column.previousColumnCnName || columnName
  const nextCnName = String(column.columnCnName || columnName).trim()
  fields.value.forEach((field: any) => {
    if (field.autoGeneratedFromColumn && field.targetColumn === columnName && field.fieldName === previousCnName) {
      field.fieldName = nextCnName
      field.autoSyncColumnCnName = nextCnName
    }
  })
  column.previousColumnCnName = nextCnName
  syncFieldReferences()
}
const handleTargetTableChange = async (tableCode: string) => {
  const matchedTable = existingTables.value.find((table) => table.value === tableCode)
  if (matchedTable) {
    form.targetTableName = matchedTable.tableName
    form.targetTableComment = matchedTable.comment
  }
  if (!tableCode) {
    targetTableColumns.value = []
    cleanupTargetColumnReferences()
    return
  }
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
  if (storageMode === 'CREATE') {
    targetTableColumns.value = []
    uniqueConstraints.value = []
    cleanupTargetColumnReferences()
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
    const fieldKey = field.fieldId || field.fieldCode
    if (!downstreamFieldMap[serviceCode][fieldKey]) {
      downstreamFieldMap[serviceCode][fieldKey] = downstreamFieldMap[serviceCode][field.fieldCode] || defaultPushSourceField(field)
    }
  })
  return downstreamFieldMap[serviceCode]
}
const pushFieldMappingsForService = (serviceCode: string) => {
  const serviceMap = ensurePushServiceFieldMap(serviceCode)
  return fields.value.map((field: any) => ({
    fieldId: field.fieldId,
    fieldCode: field.fieldCode,
    resultFieldCode: field.fieldCode,
    sourceType: field.sourceType || 'EXTRACTED',
    sourceField: defaultPushSourceField(field),
    downstreamField: serviceMap[field.fieldId || field.fieldCode] || serviceMap[field.fieldCode] || defaultPushSourceField(field),
    fieldName: field.fieldName
  }))
}
const updateDownstreamField = (serviceCode: string, fieldIdOrCode: string, downstreamField: string) => {
  const serviceMap = ensurePushServiceFieldMap(serviceCode)
  serviceMap[fieldIdOrCode] = downstreamField
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
      const fieldId = mapping.fieldId || fieldIdByCode(fieldCode)
      const fieldKey = fieldId || fieldCode
      if (fieldKey) serviceMap[fieldKey] = mapping.downstreamField || mapping.targetField || mapping.sourceField || serviceMap[fieldKey] || fieldCode
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
const storageTargetColumnOptions = computed(() => targetTableColumns.value.map((column) => ({ label: formatTargetColumnLabel(column), value: column.columnName })))
const extractedResultFields = computed(() => fields.value.filter((field: any) => (field.sourceType || 'EXTRACTED') === 'EXTRACTED'))
const derivedResultFields = computed(() => fields.value.filter((field: any) => (field.sourceType || 'EXTRACTED') === 'DERIVED'))
const transformOutputFieldOptions = (rule: TransformRule) => {
  if (rule.outputMode === 'DERIVE_FIELD') return derivedResultFields.value
  return extractedResultFields.value
}
const transformOutputPlaceholder = (rule: TransformRule) => {
  if (rule.outputMode === 'DERIVE_FIELD') return '请选择加工衍生字段'
  if (rule.outputMode === 'OVERWRITE_INPUT') return '请选择要覆盖的提取字段'
  return '请选择要写入的提取字段'
}
const transformTypeLabel: Record<TransformRuleType, string> = {
  DICT: '字典转换',
  API: 'API 取数',
  SQL: 'SQL 查询'
}
const sqlNoDataStrategyLabel: Record<SqlNoDataStrategy, string> = {
  SET_NULL: '置空继续',
  REVIEW: '进入复核',
  BLOCK: '阻断任务'
}
const sqlMultiRowStrategyLabel: Record<SqlMultiRowStrategy, string> = {
  FIRST: '取第一行',
  REVIEW: '进入复核',
  BLOCK: '阻断任务'
}
const sqlNullStrategyLabel: Record<SqlNullStrategy, string> = {
  SET_NULL: '置空继续',
  REVIEW: '进入复核'
}
const dictConditionOperatorLabel: Record<DictConditionOperator, string> = {
  EQUALS: '等于',
  CONTAINS: '包含',
  REGEX: '正则匹配',
  RANGE: '区间',
  EMPTY: '为空',
  NOT_EMPTY: '不为空'
}
const transformRuleInputSummary = (rule: TransformRule) => {
  if (!rule.inputFields?.length) return '待配置依赖字段'
  return rule.inputFields.map((input) => fieldById(input.fieldId)?.fieldCode || input.fieldCode).join(' + ')
}
const transformRuleOutputSummary = (rule: TransformRule) => {
  const outputLabel = fieldLabelById(rule.outputFieldId || '', rule.outputField)
  if (rule.outputMode === 'OVERWRITE_INPUT') return rule.outputFieldId || rule.outputField ? `覆盖 ${outputLabel}` : '待选择覆盖字段'
  return rule.outputFieldId || rule.outputField ? outputLabel : '待配置输出字段'
}
const transformParamNameError = (rule: TransformRule, input: TransformInputField) => {
  const paramName = String(input.paramName || '').trim()
  if (!paramName) return '参数名不能为空'
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(paramName)) return '仅支持字母、数字、下划线，且不能以数字开头'
  const duplicateCount = (rule.inputFields || []).filter((item) => item.paramName === input.paramName).length
  if (duplicateCount > 1) return '参数名不能重复'
  return ''
}
const syncTransformOutputField = (rule: TransformRule) => {
  const validOptions = transformOutputFieldOptions(rule)
  const selected = validOptions.find((field: any) => field.fieldId === rule.outputFieldId || field.fieldCode === rule.outputField)
  if (selected) {
    rule.outputFieldId = selected.fieldId
    rule.outputField = selected.fieldCode
    return
  }
  if (rule.outputMode === 'OVERWRITE_INPUT' && rule.inputFields.length) {
    const inputField = extractedResultFields.value.find((field: any) => field.fieldId === rule.inputFields[0].fieldId || field.fieldCode === rule.inputFields[0].fieldCode)
    rule.outputFieldId = inputField?.fieldId || ''
    rule.outputField = inputField?.fieldCode || ''
    return
  }
  rule.outputFieldId = ''
  rule.outputField = ''
}
const updateTransformInputFields = (rule: TransformRule, fieldIds: string[]) => {
  const existing = new Map((rule.inputFields || []).map((input) => [input.fieldId || input.fieldCode, input]))
  rule.inputFields = fieldIds.map((fieldId) => {
    const field = fieldById(fieldId) || fieldByCode(fieldId)
    const existingInput = existing.get(field?.fieldId || fieldId) || existing.get(field?.fieldCode || fieldId)
    return existingInput ? { ...existingInput, fieldId: field?.fieldId || existingInput.fieldId, fieldCode: field?.fieldCode || existingInput.fieldCode } : createTransformInputField(field?.fieldId || fieldId)
  })
  if (rule.outputMode === 'OVERWRITE_INPUT' && !rule.outputFieldId && rule.inputFields.length) {
    rule.outputFieldId = rule.inputFields[0].fieldId
    rule.outputField = rule.inputFields[0].fieldCode
  }
  syncTransformOutputField(rule)
  ensureDictItems(rule)
}
const handleTransformOutputModeChange = (rule: TransformRule) => {
  syncTransformOutputField(rule)
}
const fieldLabelById = (fieldId: string, fallbackCode = '') => {
  const field = resolveField(fieldId, fallbackCode)
  return field ? `${field.fieldName}（${field.fieldCode}）` : fallbackCode || fieldId
}
const fieldLabelByCode = (fieldCode: string) => {
  const field = resolveField('', fieldCode)
  return field ? `${field.fieldName}（${field.fieldCode}）` : fieldCode
}
const transformRuleIndex = (rule: TransformRule) => transformRules.value.findIndex((item) => item.id === rule.id)
const conditionFieldGroups = (rule: TransformRule) => {
  const currentIndex = transformRuleIndex(rule)
  const previousOutputFields = new Set(
    transformRules.value
      .slice(0, currentIndex < 0 ? 0 : currentIndex)
      .filter((item) => item.enabled && item.outputField)
      .map((item) => item.outputFieldId || fieldIdByCode(item.outputField))
  )
  const baseFields = fields.value.filter((field: any) => !['DERIVED', 'SYSTEM'].includes(field.sourceType || 'EXTRACTED'))
  const systemFields = fields.value.filter((field: any) => (field.sourceType || 'EXTRACTED') === 'SYSTEM')
  const previousDerivedFields = fields.value.filter((field: any) => (field.sourceType || 'EXTRACTED') === 'DERIVED' && previousOutputFields.has(field.fieldId))
  return [
    { label: '提取字段', options: baseFields },
    { label: '前序加工字段', options: previousDerivedFields },
    { label: '系统字段', options: systemFields }
  ].filter((group) => group.options.length)
}
const firstAvailableConditionFieldId = (rule: TransformRule) => conditionFieldGroups(rule)[0]?.options[0]?.fieldId || ''
const sampleValueByInput = (input: TransformInputField) => input.sampleValue || input.defaultValue || ''
const buildTransformSampleMap = (rule: TransformRule) => {
  return (rule.inputFields || []).reduce<Record<string, string>>((result, input) => {
    result[input.paramName] = sampleValueByInput(input)
    result[input.fieldId] = sampleValueByInput(input)
    result[input.fieldCode] = sampleValueByInput(input)
    return result
  }, {})
}
const replaceParamPlaceholders = (text: string, values: Record<string, string>) => {
  return Object.entries(values).reduce((result, [paramName, value]) => result.replace(new RegExp(`\\{${paramName}\\}`, 'g'), value), text)
}
const extractSqlParamNames = (sqlText = '') => {
  const matches = Array.from(sqlText.matchAll(/:([A-Za-z_][A-Za-z0-9_]*)/g)).map((match) => match[1])
  return Array.from(new Set(matches))
}
const sqlSafetyIssues = (sqlText = '') => {
  const text = sqlText.trim()
  const normalized = text.toLowerCase()
  const issues: string[] = []
  if (!text) issues.push('SQL 模板不能为空')
  if (text && !normalized.startsWith('select')) issues.push('仅允许 SELECT 查询')
  if (/[;]/.test(text)) issues.push('SQL 模板不允许包含分号')
  if (/(--|\/\*)/.test(text)) issues.push('SQL 模板不允许包含注释')
  if (/\b(insert|update|delete|drop|truncate|alter|merge|call|execute|grant|revoke|create)\b/i.test(text)) issues.push('SQL 模板包含高风险关键字')
  return issues
}
const sqlParamRows = (rule: TransformRule): SqlParamCheckRow[] => {
  const inputMap = new Map((rule.inputFields || []).map((input) => [input.paramName, input]))
  return extractSqlParamNames(rule.sqlText).map((paramName) => {
    const input = inputMap.get(paramName)
    if (!input) {
      return { paramName, sourceField: '未映射', sampleValue: '', status: 'ERROR', message: 'SQL 参数未绑定依赖字段' }
    }
    const paramError = transformParamNameError(rule, input)
    if (paramError) {
      return { paramName, sourceField: fieldLabelByCode(input.fieldCode), sampleValue: sampleValueByInput(input), status: 'ERROR', message: paramError }
    }
    return { paramName, sourceField: fieldLabelByCode(input.fieldCode), sampleValue: sampleValueByInput(input), status: 'OK', message: '已绑定' }
  })
}
const unusedSqlInputs = (rule: TransformRule) => {
  const params = new Set(extractSqlParamNames(rule.sqlText))
  return (rule.inputFields || []).filter((input) => input.paramName && !params.has(input.paramName))
}
const sqlPreviewBlockingMessages = (rule: TransformRule) => [
  ...sqlSafetyIssues(rule.sqlText),
  ...sqlParamRows(rule).filter((row) => row.status === 'ERROR').map((row) => `${row.paramName}：${row.message}`)
]
const regexMatchesSample = (pattern: string, value: string) => {
  try {
    return new RegExp(pattern).test(value)
  } catch {
    return false
  }
}
const rangeMatchesSample = (range: string, value: string) => {
  const numericValue = Number(value)
  const [min, max] = range.split('-', 2).map((item) => Number(item.trim()))
  if (!Number.isFinite(numericValue) || !Number.isFinite(min) || !Number.isFinite(max)) return false
  return numericValue >= min && numericValue <= max
}
const transformValueMatches = (mode: DictConditionOperator | undefined, expected: string, actual: string) => {
  if (mode === 'EMPTY') return !actual
  if (mode === 'NOT_EMPTY') return Boolean(actual)
  if (mode === 'CONTAINS') return actual.includes(expected)
  if (mode === 'REGEX') return regexMatchesSample(expected, actual)
  if (mode === 'RANGE') return rangeMatchesSample(expected, actual)
  return actual === expected
}
const dictConditionActualValue = (condition: DictCondition, sampleValues: Record<string, string>) => {
  return String(sampleValues[condition.paramName] ?? sampleValues[condition.fieldId || ''] ?? sampleValues[condition.fieldCode] ?? '').trim()
}
const dictConditionMatchesSample = (condition: DictCondition, sampleValues: Record<string, string>) => {
  return transformValueMatches(condition.operator || 'EQUALS', String(condition.value || '').trim(), dictConditionActualValue(condition, sampleValues))
}
const dictItemConditionSummary = (item: DictItem) => {
  const conditions = item.conditions || []
  if (!conditions.length) return '待配置命中条件'
  const joiner = item.conditionLogic === 'ANY' ? ' 或 ' : ' 且 '
  return conditions.map((condition) => {
    const fieldName = fieldLabelById(condition.fieldId || '', condition.fieldCode)
    const operator = dictConditionOperatorLabel[condition.operator || 'EQUALS']
    const value = ['EMPTY', 'NOT_EMPTY'].includes(condition.operator) ? '' : ` ${condition.value || '待填写'}`
    return `${fieldName} ${operator}${value}`
  }).join(joiner)
}
const dictItemMatchStatus = (item: DictItem, sampleValues: Record<string, string>) => {
  if (!item.enabled) return '未启用'
  if (!item.conditions?.length) return '待配置'
  return dictItemMatchesSample(item, sampleValues) ? '命中' : '未命中'
}
const dictItemMatchStatusByRule = (item: DictItem, rule: TransformRule) => dictItemMatchStatus(item, buildTransformSampleMap(rule))
const dictItemMatchTagType = (item: DictItem, rule: TransformRule) => dictItemMatchStatusByRule(item, rule) === '命中' ? 'success' : 'info'
const dictInputByFieldId = (rule: TransformRule, fieldId: string, fieldCode = '') => rule.inputFields.find((input) => input.fieldId === fieldId || input.fieldCode === fieldCode)
const handleDictConditionFieldChange = (rule: TransformRule, condition: DictCondition) => {
  const input = dictInputByFieldId(rule, condition.fieldId || '', condition.fieldCode || '')
  condition.fieldId = input?.fieldId || condition.fieldId
  condition.fieldCode = input?.fieldCode || condition.fieldCode
  condition.paramName = input?.paramName || toParamName(condition.fieldCode)
}
const addDictCondition = (item: DictItem) => {
  if (!selectedTransformRule.value) return
  item.conditions.push(createDictCondition(selectedTransformRule.value.inputFields[0]))
}
const removeDictCondition = (item: DictItem, condition: DictCondition) => {
  item.conditions = item.conditions.filter((row) => row.id !== condition.id)
}
const conditionValueMatches = (condition: TransformCondition, sampleValues: Record<string, string>) => {
  const actual = String(sampleValues[condition.fieldId] ?? sampleValues[toParamName(condition.fieldCode)] ?? sampleValues[condition.fieldCode] ?? '').trim()
  const expected = String(condition.value || '').trim()
  const numericActual = Number(actual)
  const numericExpected = Number(expected)
  if (condition.operator === 'EMPTY') return !actual
  if (condition.operator === 'EQUALS') return actual === expected
  if (condition.operator === 'NOT_EQUALS') return actual !== expected
  if (condition.operator === 'CONTAINS') return actual.includes(expected)
  if (condition.operator === 'NOT_CONTAINS') return !actual.includes(expected)
  if (condition.operator === 'GT') return Number.isFinite(numericActual) && Number.isFinite(numericExpected) && numericActual > numericExpected
  if (condition.operator === 'GTE') return Number.isFinite(numericActual) && Number.isFinite(numericExpected) && numericActual >= numericExpected
  if (condition.operator === 'LT') return Number.isFinite(numericActual) && Number.isFinite(numericExpected) && numericActual < numericExpected
  if (condition.operator === 'LTE') return Number.isFinite(numericActual) && Number.isFinite(numericExpected) && numericActual <= numericExpected
  if (condition.operator === 'REGEX') return regexMatchesSample(expected, actual)
  return Boolean(actual)
}
const transformConditionsMatched = (rule: TransformRule, sampleValues: Record<string, string>) => {
  if (!rule.conditionEnabled) return true
  const conditions = rule.conditions || []
  if (!conditions.length) return false
  if (rule.conditionLogic === 'ANY') return conditions.some((condition) => conditionValueMatches(condition, sampleValues))
  return conditions.every((condition) => conditionValueMatches(condition, sampleValues))
}
const dictItemMatchesSample = (item: DictItem, sampleValues: Record<string, string>) => {
  if (!item.enabled || !item.conditions?.length) return false
  const conditions = item.conditions.filter((condition) => condition.operator === 'EMPTY' || condition.operator === 'NOT_EMPTY' || String(condition.value || '').trim())
  if (!conditions.length) return false
  if (item.conditionLogic === 'ANY') return conditions.some((condition) => dictConditionMatchesSample(condition, sampleValues))
  return conditions.every((condition) => dictConditionMatchesSample(condition, sampleValues))
}

const hasDuplicate = (values: string[]) => {
  const normalizedValues = values.map((value) => value?.trim()).filter(Boolean)
  return new Set(normalizedValues).size !== normalizedValues.length
}
const validateFieldStorageStep = () => {
  const errors: string[] = []
  if (!fields.value.length) errors.push('至少维护 1 个结果字段')
  if (hasDuplicate(fields.value.map((field) => field.fieldCode))) errors.push('结果字段编码不能重复')
  if (hasDuplicate(fields.value.map((field) => field.fieldId))) errors.push('结果字段内部ID不能重复，请刷新页面后重试')
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
  const mappedColumns = fields.value.map((field) => field.targetColumn).filter(Boolean) as string[]
  const duplicatedTargetColumns = Array.from(new Set(mappedColumns.filter((column, index) => mappedColumns.indexOf(column) !== index)))
  if (duplicatedTargetColumns.length) errors.push(`目标字段不能重复映射：${duplicatedTargetColumns.join('、')}。如需合并多个字段，请先通过加工规则生成一个结果字段后再映射。`)

  const enabledConstraints = uniqueConstraints.value.filter((constraint) => constraint.enabled)
  if (hasDuplicate(enabledConstraints.map((constraint) => constraint.constraintName))) errors.push('启用的唯一约束名称不能重复')
  enabledConstraints.forEach((constraint, index) => {
    const rowLabel = `唯一约束第 ${index + 1} 行`
    if (!constraint.constraintName?.trim()) errors.push(`${rowLabel}：约束名称不能为空`)
    if (!constraint.uniqueColumns.length) errors.push(`${rowLabel}：唯一字段组合至少选择 1 个字段`)
    constraint.uniqueColumns.forEach((column: string) => {
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
    syncFieldReferences()
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
const syncFieldReferences = () => {
  const existingFieldIds = new Set(fields.value.map((field) => field.fieldId))
  transformRules.value.forEach((rule) => {
    rule.inputFields = (rule.inputFields || []).map((input) => {
      const field = resolveField(input.fieldId, input.fieldCode)
      return { ...input, fieldId: field?.fieldId || input.fieldId, fieldCode: field?.fieldCode || input.fieldCode }
    }).filter((input) => input.fieldId ? existingFieldIds.has(input.fieldId) : Boolean(fieldByCode(input.fieldCode)))
    if (rule.outputFieldId) {
      const outputField = fieldById(rule.outputFieldId)
      rule.outputField = outputField?.fieldCode || rule.outputField
    } else if (rule.outputField) {
      rule.outputFieldId = fieldIdByCode(rule.outputField)
    }
    syncTransformOutputField(rule)
    rule.conditions = (rule.conditions || []).map((condition) => {
      const field = resolveField(condition.fieldId, condition.fieldCode)
      return { ...condition, fieldId: field?.fieldId || condition.fieldId, fieldCode: field?.fieldCode || condition.fieldCode }
    }).filter((condition) => condition.fieldId ? existingFieldIds.has(condition.fieldId) : Boolean(fieldByCode(condition.fieldCode)))
    ;(rule.dictItems || []).forEach((item) => {
      item.conditions = (item.conditions || []).map((condition) => {
        const field = resolveField(condition.fieldId || '', condition.fieldCode || '')
        const input = (rule.inputFields || []).find((row) => row.fieldId === field?.fieldId || row.fieldCode === field?.fieldCode)
        return {
          ...condition,
          fieldId: input?.fieldId || field?.fieldId || condition.fieldId,
          fieldCode: input?.fieldCode || field?.fieldCode || condition.fieldCode,
          paramName: input?.paramName || condition.paramName || toParamName(field?.fieldCode || condition.fieldCode || '')
        }
      }).filter((condition) => condition.fieldId ? existingFieldIds.has(condition.fieldId) : Boolean(fieldByCode(condition.fieldCode)))
    })
  })
  validationRules.value = validationRules.value.map((rule) => {
    const field = resolveField(rule.fieldId, rule.fieldCode)
    return { ...rule, fieldId: field?.fieldId || rule.fieldId, fieldCode: field?.fieldCode || rule.fieldCode }
  }).filter((rule) => rule.fieldId ? existingFieldIds.has(rule.fieldId) : Boolean(fieldByCode(rule.fieldCode)))
}
const transformRulesForPayload = () => transformRules.value.map((rule) => ({
  ...rule,
  outputField: fieldCodeById(rule.outputFieldId || '') || rule.outputField,
  inputFields: (rule.inputFields || []).map((input) => {
    const field = resolveField(input.fieldId, input.fieldCode)
    return { ...input, fieldId: field?.fieldId || input.fieldId, fieldCode: field?.fieldCode || input.fieldCode }
  }),
  conditions: (rule.conditions || []).map((condition) => {
    const field = resolveField(condition.fieldId, condition.fieldCode)
    return { ...condition, fieldId: field?.fieldId || condition.fieldId, fieldCode: field?.fieldCode || condition.fieldCode }
  }),
  dictItems: (rule.dictItems || []).map((item, index) => ({
    ...item,
    priority: index + 1,
    conditions: (item.conditions || []).map((condition) => {
      const field = resolveField(condition.fieldId || '', condition.fieldCode || '')
      return { ...condition, fieldId: field?.fieldId || condition.fieldId, fieldCode: field?.fieldCode || condition.fieldCode }
    })
  }))
}))
const buildWizardPayload = () => (syncFieldReferences(), {
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
  resultTableColumns: targetTableColumns.value.map((column: any) => ({
    columnName: column.columnName,
    columnCnName: column.columnCnName,
    dbType: column.dbType,
    length: column.length,
    precision: column.precision,
    scale: column.scale,
    required: column.required,
    defaultValue: column.defaultValue,
    validationRule: column.validationRule
  })),
  uniqueConstraints: uniqueConstraints.value,
  extractFields: fields.value.map((field: any) => ({
    fieldId: field.fieldId,
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
      fieldId: field.fieldId,
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
      fieldId: field.fieldId,
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
  transformRules: transformRulesForPayload(),
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
const addField = (sourceType: ResultFieldSourceType = 'EXTRACTED') => {
  const isDerived = sourceType === 'DERIVED'
  const sourceCount = fields.value.filter((field: any) => (field.sourceType || 'EXTRACTED') === sourceType).length + 1
  const fieldCode = isDerived ? `derived_field_${sourceCount}` : `field_${sourceCount}`
  const fieldId = createFieldId(fieldCode)
  const matchedColumn = targetColumnOptions.value.find((column) => column === fieldCode) || ''
  fields.value.push({
    fieldId,
    fieldCode,
    fieldName: isDerived ? `加工衍生字段${sourceCount}` : '新增提取字段',
    sourceType,
    fieldDescription: '',
    dataType: 'string',
    fieldLength: 100,
    required: !isDerived,
    extractRequired: !isDerived,
    multiple: false,
    targetColumn: matchedColumn,
    extractByRegex: false,
    traditionalRuleEnabled: false,
    traditionalRuleType: 'REGEX',
    traditionalRuleConfig: {},
    regexPattern: '',
    regexFlags: '',
    regexGroup: 1
  } as any)
  form.targetServices.forEach((serviceCode) => updateDownstreamField(serviceCode, fieldId, matchedColumn || fieldCode))
}
const handleResultFieldSourceChange = (row: ResultField) => {
  if ((row.sourceType || 'EXTRACTED') === 'DERIVED') {
    row.fieldDescription = ''
    row.extractRequired = false
    row.required = false
    row.multiple = false
    ;(row as any).extractByRegex = false
    ;(row as any).traditionalRuleEnabled = false
  }
  syncFieldReferences()
}
const autoMapFields = () => {
  fields.value.forEach((field) => {
    const matchedColumn = targetColumnOptions.value.find((column) => column === field.fieldCode || column === field.targetColumn)
    field.targetColumn = matchedColumn || ''
  })
  ElMessage.success('已按同名字段自动生成映射')
}
const addTargetColumn = () => {
  if (isReuseStorageMode.value) {
    ElMessage.warning('复用已有表时，目标字段来自结果表元数据台账，不能在当前配置中直接新增')
    return
  }
  const columnName = `column_${targetTableColumns.value.length + 1}`
  const column = {
    columnName,
    columnCnName: '新增目标字段',
    dbType: 'varchar',
    length: 100,
    precision: undefined,
    scale: undefined,
    required: false,
    defaultValue: '',
    validationRule: '',
    previousColumnName: columnName,
    previousColumnCnName: '新增目标字段'
  }
  targetTableColumns.value.push(column)
  ensureResultFieldForTargetColumn(column)
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
  const relatedFields = fields.value.filter((field: any) => field.targetColumn === row.columnName)
  const autoGeneratedFields = relatedFields.filter((field: any) => field.autoGeneratedFromColumn && field.fieldCode === row.columnName)
  const autoGeneratedIds = new Set(autoGeneratedFields.map((field) => field.fieldId))
  targetTableColumns.value = targetTableColumns.value.filter((column) => column.columnName !== row.columnName)
  uniqueConstraints.value.forEach((constraint) => {
    constraint.uniqueColumns = constraint.uniqueColumns.filter((column: string) => column !== row.columnName)
  })
  if (relatedFields.length > 0) {
    fields.value = fields.value.filter((field: any) => !autoGeneratedIds.has(field.fieldId))
    fields.value.forEach((field) => {
      if (field.targetColumn === row.columnName) field.targetColumn = ''
    })
    Object.values(downstreamFieldMap).forEach((serviceMap) => {
      autoGeneratedFields.forEach((field) => {
        delete serviceMap[field.fieldId]
        delete serviceMap[field.fieldCode]
      })
    })
    syncFieldReferences()
    const manualCount = relatedFields.length - autoGeneratedFields.length
    ElMessage.warning(`已删除目标字段，自动移除 ${autoGeneratedFields.length} 个默认结果字段${manualCount > 0 ? `，并清空 ${manualCount} 个手工结果字段映射` : ''}`)
  } else {
    ElMessage.success('已删除目标字段')
  }
}
const removeExtractField = (row: any) => {
  const removedFieldId = row.fieldId || fieldIdByCode(row.fieldCode)
  fields.value = fields.value.filter((field) => field.fieldId !== removedFieldId)
  Object.values(downstreamFieldMap).forEach((serviceMap) => delete serviceMap[row.fieldCode])
  delete regexPreviewMap.value[row.fieldCode]
  if (selectedExtractFieldCode.value === row.fieldCode) {
    selectedExtractFieldCode.value = fields.value[0]?.fieldCode || ''
  }
  syncFieldReferences()
  ElMessage.success('已删除结果字段')
}
const removeTransformRule = async (rule: TransformRule) => {
  const currentIndex = transformRules.value.findIndex((item) => item.id === rule.id)
  const referencedRules = transformRules.value
    .slice(currentIndex + 1)
    .filter((item) => (rule.outputFieldId || rule.outputField) && item.inputFields?.some((input) => input.fieldId === rule.outputFieldId || input.fieldCode === rule.outputField))
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
    inputFields: rule.inputFields.map((input) => ({ ...input })),
    conditions: rule.conditions.map((condition) => ({ ...condition, id: `cond-${Date.now()}-${Math.random().toString(36).slice(2, 8)}` })),
    dictItems: rule.dictItems.map((item) => createDictItem(rule, {
      ...item,
      id: `dict-rule-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      ruleName: `${item.ruleName}-副本`,
      conditions: item.conditions.map((condition) => ({ ...condition, id: `dict-cond-${Date.now()}-${Math.random().toString(36).slice(2, 8)}` }))
    }))
  }
  normalizeDictItemPriorities(copiedRule)
  const currentIndex = transformRules.value.findIndex((item) => item.id === rule.id)
  transformRules.value.splice(currentIndex + 1, 0, copiedRule)
  selectedRuleId.value = copiedRule.id
  ElMessage.success('已复制加工规则')
}
const addValidationRule = () => {
  const field = fields.value[0]
  validationRules.value.push({
    id: `valid-${Date.now()}`,
    ruleName: '新增校验规则',
    ruleType: 'REQUIRED',
    fieldId: field?.fieldId || '',
    fieldCode: field?.fieldCode || '',
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
const runValidationPreview = () => {
  ElMessage.success('已基于当前样本模拟执行校验规则')
}
const addTransformRule = (ruleType: TransformRuleType) => {
  const id = `rule-${Date.now()}`
  const defaultField = fields.value[0]
  const defaultFieldCode = defaultField?.fieldCode || ''
  const defaultParamName = toParamName(defaultFieldCode)
  transformRules.value.push({
    id,
    ruleName: `新增${transformTypeLabel[ruleType]}规则`,
    ruleType,
    inputFields: defaultField?.fieldId ? [createTransformInputField(defaultField.fieldId)] : [],
    outputFieldId: '',
    outputField: '',
    enabled: true,
    onFail: 'REVIEW',
    dictItems: [],
    apiEndpoint: ruleType === 'API' ? `/api/example/{${defaultParamName}}` : '',
    apiMethod: 'GET',
    apiResponsePath: '$.data.value',
    sqlDatasource: '只读数据源',
    sqlText: ruleType === 'SQL' ? `select target_value from table_name where source_value = :${defaultParamName}` : '',
    sqlResultColumn: 'target_value',
    outputMode: 'DERIVE_FIELD',
    conditionEnabled: false,
    conditionLogic: 'ALL',
    conditions: defaultField?.fieldId ? [createTransformCondition(defaultField.fieldId)] : [],
    apiTimeout: 5,
    apiRetryCount: 1,
    apiAuthMode: 'SYSTEM',
    apiSuccessRule: '$.code == 0',
    sqlMaxRows: 1,
    sqlReadonlyChecked: true,
    sqlNoDataStrategy: 'REVIEW',
    sqlMultiRowStrategy: 'FIRST',
    sqlNullStrategy: 'REVIEW',
    sqlTimeoutSeconds: 5
  })
  normalizeTransformRule(transformRules.value[transformRules.value.length - 1])
  selectedRuleId.value = id
}
const addTransformCondition = () => {
  if (!selectedTransformRule.value) return
  selectedTransformRule.value.conditions.push(createTransformCondition(firstAvailableConditionFieldId(selectedTransformRule.value)))
}
const removeTransformCondition = (condition: TransformCondition) => {
  if (!selectedTransformRule.value) return
  selectedTransformRule.value.conditions = selectedTransformRule.value.conditions.filter((item) => item.id !== condition.id)
}
const addDictItem = () => {
  if (!selectedTransformRule.value) return
  selectedTransformRule.value.dictItems.push(createDictItem(selectedTransformRule.value))
  normalizeDictItemPriorities(selectedTransformRule.value)
}
const copyDictItem = (row: DictItem) => {
  if (!selectedTransformRule.value) return
  const index = selectedTransformRule.value.dictItems.indexOf(row)
  selectedTransformRule.value.dictItems.splice(index + 1, 0, createDictItem(selectedTransformRule.value, {
    ...row,
    id: `dict-rule-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    ruleName: `${row.ruleName}-副本`,
    conditions: row.conditions.map((condition) => ({ ...condition, id: `dict-cond-${Date.now()}-${Math.random().toString(36).slice(2, 8)}` }))
  }))
  normalizeDictItemPriorities(selectedTransformRule.value)
}
const removeDictItem = (row: DictItem) => {
  if (!selectedTransformRule.value) return
  selectedTransformRule.value.dictItems = selectedTransformRule.value.dictItems.filter((item) => item !== row)
  normalizeDictItemPriorities(selectedTransformRule.value)
}
const dictItemIndex = (rule: TransformRule | undefined, row: DictItem) => rule?.dictItems?.indexOf(row) ?? -1
const moveDictItem = (row: DictItem, direction: -1 | 1) => {
  const rule = selectedTransformRule.value
  if (!rule) return
  const currentIndex = dictItemIndex(rule, row)
  const nextIndex = currentIndex + direction
  if (currentIndex < 0 || nextIndex < 0 || nextIndex >= rule.dictItems.length) return
  const [current] = rule.dictItems.splice(currentIndex, 1)
  rule.dictItems.splice(nextIndex, 0, current)
  normalizeDictItemPriorities(rule)
}
const moveDictItemTo = (row: DictItem, targetRow: DictItem) => {
  const rule = selectedTransformRule.value
  if (!rule || row === targetRow) return
  const fromIndex = dictItemIndex(rule, row)
  const toIndex = dictItemIndex(rule, targetRow)
  if (fromIndex < 0 || toIndex < 0) return
  const [current] = rule.dictItems.splice(fromIndex, 1)
  rule.dictItems.splice(toIndex, 0, current)
  normalizeDictItemPriorities(rule)
}
const handleDictDragStart = (row: DictItem, event: DragEvent) => {
  draggingDictItemId.value = row.id
  event.dataTransfer?.setData('text/plain', row.id)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}
const handleDictDrop = (targetRow: DictItem, event: DragEvent) => {
  event.preventDefault()
  const rule = selectedTransformRule.value
  const sourceId = draggingDictItemId.value || event.dataTransfer?.getData('text/plain')
  const sourceRow = rule?.dictItems.find((item) => item.id === sourceId)
  if (sourceRow) moveDictItemTo(sourceRow, targetRow)
  draggingDictItemId.value = ''
}
const handleDictDragEnd = () => {
  draggingDictItemId.value = ''
}
const runTransformPreview = () => {
  const rule = selectedTransformRule.value
  if (!rule) {
    ElMessage.warning('请先新增并选择一条加工规则')
    return
  }
  const sampleValues = buildTransformSampleMap(rule)
  if (!transformConditionsMatched(rule, sampleValues)) {
    previewOutput.value = `条件不满足，将跳过规则；测试参数 ${JSON.stringify(sampleValues)}`
    ElMessage.info('执行条件不满足，预览结果为跳过规则')
    return
  }
  if (rule.ruleType === 'DICT') {
    normalizeDictItemPriorities(rule)
    const matchedItem = rule.dictItems.find((item) => dictItemMatchesSample(item, sampleValues))
    previewOutput.value = matchedItem ? `命中${matchedItem.ruleName || '映射规则'}，输出 ${matchedItem.target || '未填写'}` : '未命中字典映射，按失败策略处理'
  } else if (rule.ruleType === 'API') {
    previewOutput.value = `模拟调用 ${replaceParamPlaceholders(rule.apiEndpoint, sampleValues)}；请求参数 ${JSON.stringify(sampleValues)}；响应取值 ${rule.apiResponsePath}`
  } else {
    const blockingMessages = sqlPreviewBlockingMessages(rule)
    if (blockingMessages.length) {
      previewOutput.value = `SQL 配置未通过检查：${blockingMessages.join('；')}`
      ElMessage.warning('SQL 配置未通过检查，请先修正参数或 SQL 模板')
      return
    }
    const boundParams = extractSqlParamNames(rule.sqlText).reduce<Record<string, string>>((result, paramName) => {
      result[paramName] = sampleValues[paramName] || ''
      return result
    }, {})
    previewOutput.value = `模拟执行只读 SQL；数据源 ${rule.sqlDatasource || '未选择'}；绑定参数 ${JSON.stringify(boundParams)}；返回字段 ${rule.sqlResultColumn || 'result'}；未查到${sqlNoDataStrategyLabel[rule.sqlNoDataStrategy || 'REVIEW']}，多行${sqlMultiRowStrategyLabel[rule.sqlMultiRowStrategy || 'FIRST']}，空值${sqlNullStrategyLabel[rule.sqlNullStrategy || 'REVIEW']}；超时 ${rule.sqlTimeoutSeconds || 5} 秒`
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
const resolveWizardPayload = (rawPayload: any) => {
  if (!rawPayload) return {}
  if (typeof rawPayload === 'string') {
    try {
      return JSON.parse(rawPayload)
    } catch {
      return {}
    }
  }
  if (rawPayload.payload || rawPayload.configPayload) return resolveWizardPayload(rawPayload.payload || rawPayload.configPayload)
  return rawPayload
}
const summaryToBaseInfo = (summary?: ConfigSummary) => ({
  configName: summary?.configName || '',
  category: summary?.category || '',
  subCategory: summary?.subCategory || '',
  templateType: summary?.templateType || '',
  documentType: summary?.documentType || '',
  departmentId: summary?.departmentId || '',
  ownerRole: summary?.ownerRole || '',
  defaultPriority: summary?.defaultPriority || form.defaultPriority
})
const applyWizardPayload = (rawPayload: any, summary?: ConfigSummary) => {
  const payload = resolveWizardPayload(rawPayload)
  const baseInfo = { ...summaryToBaseInfo(summary), ...(payload.baseInfo || {}) }
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
    mappingProfileName: storageConfig.mappingProfileName ?? form.mappingProfileName,
    targetTable: storageConfig.targetTable || summary?.targetTable || form.targetTable,
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
  targetTableColumns.value = Array.isArray(payload.resultTableColumns) ? normalizeResultTableColumns(payload.resultTableColumns) : []
  uniqueConstraints.value = Array.isArray(payload.uniqueConstraints) ? payload.uniqueConstraints : []
  if (payload.extractFields?.length) {
    fields.value = payload.extractFields.map((field: any, index: number) => {
      const targetColumn = targetTableColumns.value.find((column: any) => column.columnName === field.targetColumn)
      const isDefaultColumnMapping = Boolean(targetColumn && field.fieldCode === targetColumn.columnName && field.fieldName === targetColumn.columnCnName && (field.sourceType || 'EXTRACTED') === 'EXTRACTED')
      return normalizeResultField({
        ...field,
        sourceType: field.sourceType || 'EXTRACTED',
        generatedByRuleId: field.generatedByRuleId || '',
        fieldDescription: field.fieldDescription || '',
        required: field.extractRequired,
        targetColumn: field.targetColumn || '',
        autoGeneratedFromColumn: Boolean(field.autoGeneratedFromColumn ?? isDefaultColumnMapping),
        autoSyncColumnName: field.autoSyncColumnName || (isDefaultColumnMapping ? targetColumn?.columnName : ''),
        autoSyncColumnCnName: field.autoSyncColumnCnName || (isDefaultColumnMapping ? targetColumn?.columnCnName : ''),
        traditionalRuleEnabled: field.traditionalRuleEnabled ?? field.extractByRegex ?? false,
        traditionalRuleType: field.traditionalRuleType || 'REGEX',
        traditionalRuleConfig: field.traditionalRuleConfig || {}
      }, index)
    })
  } else {
    fields.value = []
  }
  if (payload.regexRules?.length) {
    payload.regexRules.forEach((rule: any) => {
      const field = resolveField(rule.fieldId, rule.fieldCode) as any
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
  transformRules.value = (payload.transformRules || []).map((rule: any) => normalizeTransformRule(rule))
  selectedRuleId.value = transformRules.value[0]?.id || ''
  validationRules.value = (payload.validationRules || []).map((rule: any) => {
    const field = resolveField(rule.fieldId, rule.fieldCode)
    return { ...rule, fieldId: field?.fieldId || rule.fieldId || fieldIdByCode(rule.fieldCode || ''), fieldCode: field?.fieldCode || rule.fieldCode || '' }
  })
  syncFieldReferences()
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
    applyWizardPayload(detail.payload, detail.summary)
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
    applyWizardPayload(detail.payload, detail.summary)
    router.replace({ path: '/configs/wizard', query: { id: detail.summary.id } })
    ElMessage.success(`已复制为 V${detail.summary.version} 草稿，可继续编辑`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '复制新版本失败')
  }
}
const loadWizardOptions = async () => {
  const isCreateMode = !route.query.id
  try {
    options.value = await getConfigOptions()
    normalizeRoleFields()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '配置选项加载失败')
  }
  try {
    llmModelOptions.value = await listLlmModelOptions()
    if (!form.llmModelCode && llmModelOptions.value.length) {
      form.llmModelCode = llmModelOptions.value.find((item) => item.defaultModel)?.modelCode || llmModelOptions.value[0].modelCode
    }
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : 'LLM 模型选项加载失败')
  }
  try {
    ocrEngineOptions.value = await listOcrEngineOptions()
    if (isCreateMode && !form.engineCode) {
      form.engineCode = ocrEngineOptions.value.find((item) => item.defaultEngine)?.engineCode || ''
    }
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : 'OCR 引擎选项加载失败')
  }
  try {
    promptTemplateDefaults.value = await getPromptTemplateDefaults()
    if (isCreateMode) {
      aiSystemPrompt.value = promptTemplateDefaults.value.systemTemplate || fallbackSystemPromptTemplate
      setAiUserPrompt(fieldDrivenUserPrompt.value, true)
    }
  } catch (error) {
    if (isCreateMode) setAiUserPrompt(fieldDrivenUserPrompt.value, true)
    ElMessage.warning(error instanceof Error ? error.message : '提示词模板加载失败')
  }
  if (isCreateMode && form.storageMode === 'REUSE' && form.targetTable) void handleTargetTableChange(form.targetTable)
}

onMounted(async () => {
  await loadWizardOptions()
  await loadConfigForEdit()
})
watch(() => route.query.id, async (nextId, previousId) => {
  if (nextId && nextId !== previousId) await loadConfigForEdit()
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
            <template #default="{ row }"><el-input v-model="row.columnName" :readonly="isReuseStorageMode" @change="handleTargetColumnChange(row)" /></template>
          </el-table-column>
          <el-table-column label="字段中文名" min-width="140">
            <template #default="{ row }"><el-input v-model="row.columnCnName" :readonly="isReuseStorageMode" @change="handleTargetColumnCnNameChange(row)" /></template>
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
            <el-button @click="addField('DERIVED')">添加加工衍生字段</el-button>
            <el-button type="primary" @click="addField('EXTRACTED')">添加提取字段</el-button>
          </div>
        </div>
        <el-table :data="fields">
          <el-table-column label="字段来源" width="110">
            <template #default="{ row }">
              <el-select v-model="row.sourceType" @change="handleResultFieldSourceChange(row)">
                <el-option label="提取字段" value="EXTRACTED" />
                <el-option label="加工衍生" value="DERIVED" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="结果字段编码" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.fieldCode" @change="syncFieldReferences" />
            </template>
          </el-table-column>
          <el-table-column label="结果字段名称" min-width="150">
            <template #default="{ row }"><el-input v-model="row.fieldName" @change="syncFieldReferences" /></template>
          </el-table-column>
          <el-table-column label="提取规则说明" min-width="240">
            <template #default="{ row }">
              <el-input v-if="(row.sourceType || 'EXTRACTED') === 'EXTRACTED'" v-model="row.fieldDescription" placeholder="如：右下方落款日期，转为 yyyyMMdd 格式，如 20260715" />
              <span v-else class="muted">加工规则生成，不参与 AI 提示词</span>
            </template>
          </el-table-column>
          <el-table-column label="提取必填" width="90">
            <template #default="{ row }"><el-switch v-if="(row.sourceType || 'EXTRACTED') === 'EXTRACTED'" v-model="row.extractRequired" /><span v-else class="muted">-</span></template>
          </el-table-column>
          <el-table-column label="多值" width="80">
            <template #default="{ row }"><el-switch v-if="(row.sourceType || 'EXTRACTED') === 'EXTRACTED'" v-model="row.multiple" /><span v-else class="muted">-</span></template>
          </el-table-column>
          <el-table-column v-if="isStorageEnabled" label="目标字段" min-width="260">
            <template #default="{ row }">
              <el-select v-model="row.targetColumn" filterable clearable placeholder="请选择目标字段">
                <el-option
                  v-for="column in storageTargetColumnOptions"
                  :key="column.value"
                  :label="column.label"
                  :value="column.value"
                />
              </el-select>
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
                <span>{{ transformRuleInputSummary(rule) }} -> {{ transformRuleOutputSummary(rule) }}</span>
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
              <el-form-item label="依赖字段" class="wide">
                <div class="field-hint-block">
                  <span class="muted">依赖字段是加工规则实际取数使用的字段；参数名用于 API 地址占位、SQL 参数和字典组合匹配。</span>
                <el-select
                  :model-value="selectedTransformRule.inputFields.map((input) => input.fieldId || fieldIdByCode(input.fieldCode))"
                  multiple
                  filterable
                  clearable
                  collapse-tags
                  collapse-tags-tooltip
                  placeholder="请选择该加工规则需要读取的字段"
                  @update:model-value="(value: string[]) => updateTransformInputFields(selectedTransformRule, value)"
                >
                  <el-option v-for="field in fields" :key="field.fieldId" :label="`${field.fieldName}（${field.fieldCode}）`" :value="field.fieldId" />
                </el-select>
                </div>
              </el-form-item>
              <el-form-item label="参数映射" class="wide">
                <el-table :data="selectedTransformRule.inputFields" size="small" class="transform-param-table">
                  <el-table-column label="输入字段" min-width="180">
                    <template #default="{ row }">{{ fieldLabelById(row.fieldId, row.fieldCode) }}</template>
                  </el-table-column>
                  <el-table-column label="参数名" min-width="160">
                    <template #default="{ row }">
                      <el-tooltip :content="transformParamNameError(selectedTransformRule, row)" :disabled="!transformParamNameError(selectedTransformRule, row)" placement="top">
                        <el-input
                          v-model="row.paramName"
                          :class="{ 'is-error-input': transformParamNameError(selectedTransformRule, row) }"
                          placeholder="如 productCode"
                          @change="ensureDictItems(selectedTransformRule)"
                        />
                      </el-tooltip>
                    </template>
                  </el-table-column>
                  <el-table-column label="必填" width="80">
                    <template #default="{ row }"><el-switch v-model="row.required" /></template>
                  </el-table-column>
                  <el-table-column label="默认值" min-width="150">
                    <template #default="{ row }"><el-input v-model="row.defaultValue" placeholder="缺失时使用" /></template>
                  </el-table-column>
                  <el-table-column label="测试值" min-width="150">
                    <template #default="{ row }"><el-input v-model="row.sampleValue" placeholder="预览用" /></template>
                  </el-table-column>
                </el-table>
              </el-form-item>
              <el-form-item label="输出方式">
                <el-select v-model="selectedTransformRule.outputMode" @change="handleTransformOutputModeChange(selectedTransformRule)">
                  <el-option label="覆盖已有字段" value="OVERWRITE_INPUT" />
                  <el-option label="写入已有提取字段" value="WRITE_TARGET" />
                  <el-option label="生成衍生字段" value="DERIVE_FIELD" />
                </el-select>
              </el-form-item>
              <el-form-item :label="selectedTransformRule.outputMode === 'OVERWRITE_INPUT' ? '覆盖字段' : '输出字段'">
                <div class="field-select-with-action">
                  <el-select
                    v-model="selectedTransformRule.outputFieldId"
                    filterable
                    clearable
                    :placeholder="transformOutputPlaceholder(selectedTransformRule)"
                    @change="selectedTransformRule.outputField = fieldCodeById(selectedTransformRule.outputFieldId || '')"
                  >
                    <el-option
                      v-for="field in transformOutputFieldOptions(selectedTransformRule)"
                      :key="field.fieldId"
                      :label="`${field.fieldName}（${field.fieldCode}）`"
                      :value="field.fieldId"
                    >
                      <span>{{ field.fieldName }}（{{ field.fieldCode }}）</span>
                      <el-tag class="option-tag" size="small" :type="resultFieldSourceTagType((field as any).sourceType)">{{ resultFieldSourceLabel((field as any).sourceType) }}</el-tag>
                    </el-option>
                  </el-select>
                </div>
                <span v-if="selectedTransformRule.outputMode === 'DERIVE_FIELD' && !derivedResultFields.length" class="muted block">请先在“字段与落库配置”中添加加工衍生字段，再回到这里选择输出字段。</span>
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
                <div class="field-hint-block">
                  <span class="muted">执行条件只判断本规则是否需要执行；不满足时跳过规则，不按失败策略处理。</span>
                <el-radio-group v-model="selectedTransformRule.conditionEnabled">
                  <el-radio-button :label="false">总是执行</el-radio-button>
                  <el-radio-button :label="true">满足条件时执行</el-radio-button>
                </el-radio-group>
                </div>
              </el-form-item>
              <template v-if="selectedTransformRule.conditionEnabled">
                <el-form-item label="条件关系" class="wide">
                  <div class="condition-toolbar">
                    <el-radio-group v-model="selectedTransformRule.conditionLogic">
                      <el-radio-button label="ALL">满足全部条件</el-radio-button>
                      <el-radio-button label="ANY">满足任一条件</el-radio-button>
                    </el-radio-group>
                    <el-button type="primary" @click="addTransformCondition">新增条件</el-button>
                  </div>
                </el-form-item>
                <el-form-item label="条件明细" class="wide">
                  <el-table :data="selectedTransformRule.conditions" size="small" class="condition-table">
                    <el-table-column label="条件字段" min-width="220">
                      <template #default="{ row }">
                        <el-select
                          v-model="row.fieldId"
                          filterable
                          clearable
                          placeholder="请选择当前规则执行前可用字段"
                          @change="row.fieldCode = fieldCodeById(row.fieldId || '')"
                        >
                          <el-option-group v-for="group in conditionFieldGroups(selectedTransformRule)" :key="group.label" :label="group.label">
                            <el-option v-for="field in group.options" :key="field.fieldId" :label="`${field.fieldName}（${field.fieldCode}）`" :value="field.fieldId" />
                          </el-option-group>
                        </el-select>
                      </template>
                    </el-table-column>
                    <el-table-column label="判断方式" min-width="150">
                      <template #default="{ row }">
                        <el-select v-model="row.operator" placeholder="判断方式">
                          <el-option label="不为空" value="NOT_EMPTY" />
                          <el-option label="为空" value="EMPTY" />
                          <el-option label="等于" value="EQUALS" />
                          <el-option label="不等于" value="NOT_EQUALS" />
                          <el-option label="包含" value="CONTAINS" />
                          <el-option label="不包含" value="NOT_CONTAINS" />
                          <el-option label="大于" value="GT" />
                          <el-option label="大于等于" value="GTE" />
                          <el-option label="小于" value="LT" />
                          <el-option label="小于等于" value="LTE" />
                          <el-option label="正则匹配" value="REGEX" />
                        </el-select>
                      </template>
                    </el-table-column>
                    <el-table-column label="条件值" min-width="220">
                      <template #default="{ row }">
                        <el-input v-model="row.value" :disabled="row.operator === 'NOT_EMPTY' || row.operator === 'EMPTY'" placeholder="为空/不为空无需填写" />
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="80" fixed="right">
                      <template #default="{ row }"><el-button link type="danger" @click="removeTransformCondition(row)">删除</el-button></template>
                    </el-table-column>
                  </el-table>
                </el-form-item>
              </template>
            </el-form>
            </section>

            <section v-if="selectedTransformRule.ruleType === 'DICT'" class="rule-section">
              <div class="card-header">
                <h3>类型配置：字典映射</h3>
                <el-button size="small" type="primary" @click="addDictItem">新增映射规则</el-button>
              </div>
              <p class="muted mb-12">每条映射规则独立维护命中条件，可配置“产品代码等于 + 摘要包含”等组合；系统按列表从上到下匹配，命中第一条后停止。新增规则默认追加到最后，可拖动或点击上移、下移调整顺序。</p>
              <el-table :data="selectedTransformRule.dictItems" row-key="id" border>
                <el-table-column type="expand">
                  <template #default="{ row }">
                    <div class="dict-condition-editor">
                      <div class="condition-toolbar mb-8">
                        <el-radio-group v-model="row.conditionLogic">
                          <el-radio-button label="ALL">满足全部条件</el-radio-button>
                          <el-radio-button label="ANY">满足任一条件</el-radio-button>
                        </el-radio-group>
                        <el-button size="small" @click="addDictCondition(row)">新增条件</el-button>
                      </div>
                      <div class="dict-condition-list">
                        <div v-for="condition in row.conditions" :key="condition.id" class="dict-condition-row">
                          <el-select v-model="condition.fieldId" filterable placeholder="选择依赖字段" @change="handleDictConditionFieldChange(selectedTransformRule, condition)">
                            <el-option v-for="input in selectedTransformRule.inputFields" :key="input.fieldId || input.fieldCode" :label="fieldLabelById(input.fieldId, input.fieldCode)" :value="input.fieldId" />
                          </el-select>
                          <el-select v-model="condition.operator" placeholder="匹配方式">
                            <el-option label="等于" value="EQUALS" />
                            <el-option label="包含" value="CONTAINS" />
                            <el-option label="正则匹配" value="REGEX" />
                            <el-option label="区间" value="RANGE" />
                            <el-option label="为空" value="EMPTY" />
                            <el-option label="不为空" value="NOT_EMPTY" />
                          </el-select>
                          <el-input v-model="condition.value" :disabled="condition.operator === 'EMPTY' || condition.operator === 'NOT_EMPTY'" placeholder="匹配值，区间示例 0-100000" />
                          <el-button link type="danger" @click="removeDictCondition(row, condition)">删除</el-button>
                        </div>
                      </div>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="启用" width="70">
                  <template #default="{ row }"><el-switch v-model="row.enabled" /></template>
                </el-table-column>
                <el-table-column label="顺序" width="88">
                  <template #default="{ row }">
                    <div
                      class="dict-order-cell"
                      :class="{ dragging: draggingDictItemId === row.id }"
                      draggable="true"
                      @dragstart="handleDictDragStart(row, $event)"
                      @dragover.prevent
                      @drop="handleDictDrop(row, $event)"
                      @dragend="handleDictDragEnd"
                    >
                      <span class="dict-drag-handle">拖动</span>
                      <strong>{{ dictItemIndex(selectedTransformRule, row) + 1 }}</strong>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="规则名称" min-width="170">
                  <template #default="{ row }"><el-input v-model="row.ruleName" placeholder="如 申购识别" /></template>
                </el-table-column>
                <el-table-column label="命中条件摘要" min-width="320" show-overflow-tooltip>
                  <template #default="{ row }">{{ dictItemConditionSummary(row) }}</template>
                </el-table-column>
                <el-table-column label="命中后输出" min-width="180">
                  <template #default="{ row }"><el-input v-model="row.target" placeholder="如：BUY、普通金额、产品名称" /></template>
                </el-table-column>
                <el-table-column label="样本状态" width="100">
                  <template #default="{ row }"><el-tag :type="dictItemMatchTagType(row, selectedTransformRule)">{{ dictItemMatchStatusByRule(row, selectedTransformRule) }}</el-tag></template>
                </el-table-column>
                <el-table-column label="操作" width="180" fixed="right">
                  <template #default="{ row }">
                    <el-button link :disabled="dictItemIndex(selectedTransformRule, row) <= 0" @click="moveDictItem(row, -1)">上移</el-button>
                    <el-button link :disabled="dictItemIndex(selectedTransformRule, row) >= selectedTransformRule.dictItems.length - 1" @click="moveDictItem(row, 1)">下移</el-button>
                    <el-button link @click="copyDictItem(row)">复制</el-button>
                    <el-button link type="danger" @click="removeDictItem(row)">删除</el-button>
                  </template>
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
                <el-form-item label="接口地址" class="wide">
                  <el-input v-model="selectedTransformRule.apiEndpoint" placeholder="/master-data/accounts/{productCode}?date={businessDate}" />
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
              <el-alert class="mb-12" title="SQL 仅支持参数化 SELECT 查询；运行预览和配置验证会检查只读、危险关键字和参数绑定。" type="warning" :closable="false" />
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
                <el-form-item label="超时秒数">
                  <el-input-number v-model="selectedTransformRule.sqlTimeoutSeconds" :min="1" :max="60" />
                </el-form-item>
                <el-form-item label="未查到数据">
                  <el-select v-model="selectedTransformRule.sqlNoDataStrategy">
                    <el-option label="置空继续" value="SET_NULL" />
                    <el-option label="进入复核" value="REVIEW" />
                    <el-option label="阻断任务" value="BLOCK" />
                  </el-select>
                </el-form-item>
                <el-form-item label="返回多行">
                  <el-select v-model="selectedTransformRule.sqlMultiRowStrategy">
                    <el-option label="取第一行" value="FIRST" />
                    <el-option label="进入复核" value="REVIEW" />
                    <el-option label="阻断任务" value="BLOCK" />
                  </el-select>
                </el-form-item>
                <el-form-item label="返回空值">
                  <el-select v-model="selectedTransformRule.sqlNullStrategy">
                    <el-option label="置空继续" value="SET_NULL" />
                    <el-option label="进入复核" value="REVIEW" />
                  </el-select>
                </el-form-item>
                <el-form-item label="只读校验">
                  <el-switch v-model="selectedTransformRule.sqlReadonlyChecked" active-text="已校验" inactive-text="待校验" />
                </el-form-item>
                <el-form-item label="SQL 模板" class="wide">
                  <el-input v-model="selectedTransformRule.sqlText" type="textarea" :rows="5" placeholder="select product_name from md_product where product_code = :productCode" />
                </el-form-item>
              </el-form>
              <div class="sql-check-panel">
                <div class="subsection-title">SQL 参数检查</div>
                <el-alert v-if="sqlSafetyIssues(selectedTransformRule.sqlText).length" class="mb-8" type="error" :closable="false" :title="sqlSafetyIssues(selectedTransformRule.sqlText).join('；')" />
                <el-table :data="sqlParamRows(selectedTransformRule)" size="small" empty-text="SQL 中未识别到 :paramName 参数" border>
                  <el-table-column prop="paramName" label="SQL参数" min-width="120" />
                  <el-table-column prop="sourceField" label="绑定字段" min-width="220" show-overflow-tooltip />
                  <el-table-column prop="sampleValue" label="测试值" min-width="150" show-overflow-tooltip />
                  <el-table-column label="状态" width="110">
                    <template #default="{ row }"><el-tag :type="row.status === 'ERROR' ? 'danger' : row.status === 'WARN' ? 'warning' : 'success'">{{ row.message }}</el-tag></template>
                  </el-table-column>
                </el-table>
                <el-alert v-if="unusedSqlInputs(selectedTransformRule).length" class="mt-8" type="warning" :closable="false" :title="`以下依赖字段未被 SQL 使用：${unusedSqlInputs(selectedTransformRule).map((input) => input.paramName).join('、')}`" />
              </div>
            </section>

            <section class="rule-preview">
              <div>
                <strong>转换预览</strong>
                <span class="muted">使用参数映射中的测试值生成模拟输出，并先判断执行条件。</span>
              </div>
              <el-button type="primary" @click="runTransformPreview">运行预览</el-button>
              <div class="preview-result">
                <el-tag :type="previewOutput.includes('未通过检查') ? 'danger' : previewOutput.includes('跳过规则') || previewOutput.includes('未命中') ? 'warning' : 'success'">{{ previewOutput.includes('未通过检查') ? '配置异常' : previewOutput.includes('跳过规则') ? '跳过规则' : '预览通过' }}</el-tag>
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
                  <el-select v-model="row.fieldId" filterable clearable @change="row.fieldCode = fieldCodeById(row.fieldId || '')">
                    <el-option v-for="field in fields" :key="field.fieldId" :label="`${field.fieldName}（${field.fieldCode}）`" :value="field.fieldId" />
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
                        @update:model-value="(value: string) => updateDownstreamField(service.serviceCode, row.fieldId || row.fieldCode, value)"
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

.field-hint-block {
  display: grid;
  gap: 6px;
  width: 100%;
}

.condition-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
}

.is-error-input :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
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
