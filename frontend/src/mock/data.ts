export type TaskStatus = 'CREATED' | 'PARSING' | 'EXTRACTING' | 'VALIDATING' | 'WAIT_REVIEW' | 'COMPLETED' | 'FAILED'
export type Priority = 'HIGH' | 'MEDIUM' | 'LOW'

export interface TaskItem {
  traceId: string
  taskId: string
  fileName: string
  sourceType: string
  documentType: string
  priority: Priority
  status: TaskStatus
  currentStage: string
  progress: number
  confidence: number
  createdAt: string
  department: string
}

export interface FieldResult {
  fieldCode: string
  fieldName: string
  extractedValue: string
  finalValue: string
  confidence: number
  sourcePage: number
  evidence: string
  reviewRequired: boolean
  issue?: string
}

export const tasks: TaskItem[] = [
  {
    traceId: 'TRACE-20260628-0001',
    taskId: 'TASK-20260628-0001',
    fileName: '划款指令_001.pdf',
    sourceType: 'MANUAL_UPLOAD',
    documentType: '划款指令',
    priority: 'HIGH',
    status: 'WAIT_REVIEW',
    currentStage: 'VALIDATING',
    progress: 86,
    confidence: 0.88,
    createdAt: '2026-06-28 09:30:00',
    department: '运营部'
  },
  {
    traceId: 'TRACE-20260628-0002',
    taskId: 'TASK-20260628-0002',
    fileName: '银行回单_002.png',
    sourceType: 'API',
    documentType: '银行回单',
    priority: 'MEDIUM',
    status: 'COMPLETED',
    currentStage: 'COMPLETED',
    progress: 100,
    confidence: 0.95,
    createdAt: '2026-06-28 10:15:00',
    department: '财务部'
  },
  {
    traceId: 'TRACE-20260628-0003',
    taskId: 'TASK-20260628-0003',
    fileName: '开户资料_客户A.pdf',
    sourceType: 'EMAIL',
    documentType: '开户资料',
    priority: 'LOW',
    status: 'PARSING',
    currentStage: 'PARSING',
    progress: 42,
    confidence: 0,
    createdAt: '2026-06-28 11:02:00',
    department: '运营部'
  },
  {
    traceId: 'TRACE-20260628-0004',
    taskId: 'TASK-20260628-0004',
    fileName: '未知附件.zip',
    sourceType: 'FILE_DISPATCH',
    documentType: '未匹配',
    priority: 'MEDIUM',
    status: 'FAILED',
    currentStage: 'CLASSIFYING',
    progress: 18,
    confidence: 0,
    createdAt: '2026-06-28 11:20:00',
    department: '产品部'
  }
]

export const fieldResults: FieldResult[] = [
  {
    fieldCode: 'payer_name',
    fieldName: '付款方名称',
    extractedValue: '示例基金管理有限公司',
    finalValue: '示例基金管理有限公司',
    confidence: 0.96,
    sourcePage: 1,
    evidence: '付款方：示例基金管理有限公司',
    reviewRequired: false
  },
  {
    fieldCode: 'payee_account',
    fieldName: '收款账号',
    extractedValue: '6222 **** 8910',
    finalValue: '6222 **** 8910',
    confidence: 0.87,
    sourcePage: 1,
    evidence: '收款账号：6222 **** 8910',
    reviewRequired: true,
    issue: '低于 90% 置信度'
  },
  {
    fieldCode: 'amount',
    fieldName: '划款金额',
    extractedValue: '100000.00',
    finalValue: '100000.00',
    confidence: 0.82,
    sourcePage: 1,
    evidence: '金额：100000.00',
    reviewRequired: true,
    issue: '金额为高风险字段'
  },
  {
    fieldCode: 'payment_date',
    fieldName: '划款日期',
    extractedValue: '2026-06-28',
    finalValue: '2026-06-28',
    confidence: 0.93,
    sourcePage: 1,
    evidence: '划款日期：2026年6月28日',
    reviewRequired: false
  }
]

export const timeline = [
  { stage: '接入', time: '09:30:00', status: 'SUCCESS', duration: '0.8s', description: '用户手工上传划款指令，生成 traceId。' },
  { stage: '规则匹配', time: '09:30:02', status: 'SUCCESS', duration: '0.2s', description: '命中划款指令-运营部-V1.3 配置。' },
  { stage: '解析', time: '09:30:08', status: 'SUCCESS', duration: '8.1s', description: '使用 PaddleOCR-VL 解析 PDF。' },
  { stage: '提取', time: '09:30:16', status: 'SUCCESS', duration: '4.3s', description: '调用 Qwen3.6-27B 生成结构化 JSON。' },
  { stage: '加工校验', time: '09:30:21', status: 'WARNING', duration: '1.4s', description: '金额、账号字段触发复核策略。' },
  { stage: '复核', time: '09:30:25', status: 'WAITING', duration: '-', description: '低置信度字段等待人工确认。' }
]

export const traceRecords = [
  {
    traceId: 'TRACE-20260628-0001',
    taskId: 'TASK-20260628-0001',
    documentId: 'DOC-20260628-0001',
    businessNo: 'BIZ-20260628-001',
    fileName: '划款指令_001.pdf',
    sourceType: 'MANUAL_UPLOAD',
    documentType: '划款指令',
    status: 'WAIT_REVIEW',
    owner: '运营部',
    startedAt: '2026-06-28 09:30:00',
    duration: '25s',
    currentStage: '复核'
  },
  {
    traceId: 'TRACE-20260628-0002',
    taskId: 'TASK-20260628-0002',
    documentId: 'DOC-20260628-0002',
    businessNo: 'BANK-20260628-7788',
    fileName: '银行回单_002.png',
    sourceType: 'API',
    documentType: '银行回单',
    status: 'PUSHED',
    owner: '财务部',
    startedAt: '2026-06-28 10:15:00',
    duration: '19s',
    currentStage: '推送下游'
  },
  {
    traceId: 'TRACE-20260628-0004',
    taskId: 'TASK-20260628-0004',
    documentId: 'DOC-20260628-0004',
    businessNo: 'DISPATCH-UNKNOWN-004',
    fileName: '未知附件.zip',
    sourceType: 'FILE_DISPATCH',
    documentType: '未匹配',
    status: 'FAILED',
    owner: '产品部',
    startedAt: '2026-06-28 11:20:00',
    duration: '7s',
    currentStage: '规则匹配'
  }
]

export const traceStages = [
  { stage: '文档接入', status: 'SUCCESS', startedAt: '09:30:00', durationMs: 820, operator: '王老师', input: '划款指令_001.pdf', output: 'DOC-20260628-0001' },
  { stage: '规则匹配', status: 'SUCCESS', startedAt: '09:30:02', durationMs: 210, operator: 'task-service', input: 'sourceType=MANUAL_UPLOAD, documentType=划款指令', output: 'CFG-20260628-001 / V1.3' },
  { stage: '优先级入队', status: 'SUCCESS', startedAt: '09:30:03', durationMs: 60, operator: 'task-service', input: 'priority=HIGH', output: 'stream:extract:high' },
  { stage: '文档解析', status: 'SUCCESS', startedAt: '09:30:08', durationMs: 8120, operator: 'parse-service', input: 'PaddleOCR-VL-1.6', output: 'Markdown + blocks + tables' },
  { stage: '要素提取', status: 'SUCCESS', startedAt: '09:30:16', durationMs: 4320, operator: 'extract-service', input: 'Qwen3.6-27B + JSON Schema', output: '8 fields' },
  { stage: '加工校验', status: 'WARNING', startedAt: '09:30:21', durationMs: 1410, operator: 'validation-service', input: '字段结果 + 加工规则', output: '2 fields need review' },
  { stage: '落库预览', status: 'SUCCESS', startedAt: '09:30:23', durationMs: 330, operator: 'result-service', input: 'ext_fund_business_result mapping', output: 'storage preview generated' },
  { stage: '人工复核', status: 'WAITING', startedAt: '09:30:25', durationMs: 0, operator: 'review-service', input: 'amount, payee_account', output: 'waiting reviewer' },
  { stage: '推送下游', status: 'PENDING', startedAt: '-', durationMs: 0, operator: 'result-service', input: '-', output: '-' }
]

export const dashboardStats = [
  { label: '今日接入', value: 128, trend: '+12%', type: 'primary' },
  { label: '处理中', value: 23, trend: '队列正常', type: 'warning' },
  { label: '待复核', value: 17, trend: '6 个高优先级', type: 'danger' },
  { label: '失败任务', value: 4, trend: '可重试 3 个', type: 'info' },
  { label: '平均耗时', value: '18s', trend: '较昨日 -8%', type: 'success' }
]

export const configFields = [
  { fieldCode: 'payer_name', fieldName: '付款方名称', dataType: 'string', fieldLength: 200, required: true, multiple: false, targetColumn: 'payer_name' },
  { fieldCode: 'payee_account', fieldName: '收款账号', dataType: 'string', fieldLength: 64, required: true, multiple: false, targetColumn: 'payee_account' },
  { fieldCode: 'amount', fieldName: '划款金额', dataType: 'amount', fieldLength: 18, required: true, multiple: false, targetColumn: 'amount' }
]
