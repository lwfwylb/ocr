export type TaskStatus = 'CREATED' | 'PARSING' | 'EXTRACTING' | 'VALIDATING' | 'WAIT_REVIEW' | 'COMPLETED' | 'FAILED'
export type Priority = 'HIGH' | 'MEDIUM' | 'LOW'
export type DepartmentName = '运营部' | '财务部' | '产品部'

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
  department: DepartmentName
  queueName: string
  queueLevel: Priority
  queueCapacity: number
  queuePosition: number
  waitingMinutes: number
  estimatedStartAt: string
  manualAccelerated: boolean
  dispatchReason?: string
}

export const departmentQueues = [
  { department: '运营部' as DepartmentName, queueCode: 'ops', maxCapacity: 60, description: '运营类文档量最大，保留最多并发与队列容量' },
  { department: '财务部' as DepartmentName, queueCode: 'finance', maxCapacity: 10, description: '财务类任务较少，独立小队列处理' },
  { department: '产品部' as DepartmentName, queueCode: 'product', maxCapacity: 20, description: '产品相关合同、协议、公告材料' }
]

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
    department: '运营部',
    queueName: '运营部-高优先级队列',
    queueLevel: 'HIGH',
    queueCapacity: 60,
    queuePosition: 0,
    waitingMinutes: 0,
    estimatedStartAt: '-',
    manualAccelerated: false
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
    department: '财务部',
    queueName: '财务部-中优先级队列',
    queueLevel: 'MEDIUM',
    queueCapacity: 10,
    queuePosition: 0,
    waitingMinutes: 0,
    estimatedStartAt: '-',
    manualAccelerated: false
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
    department: '运营部',
    queueName: '运营部-低优先级队列',
    queueLevel: 'LOW',
    queueCapacity: 60,
    queuePosition: 18,
    waitingMinutes: 26,
    estimatedStartAt: '11:48:00',
    manualAccelerated: false
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
    department: '产品部',
    queueName: '产品部-中优先级队列',
    queueLevel: 'MEDIUM',
    queueCapacity: 20,
    queuePosition: 6,
    waitingMinutes: 12,
    estimatedStartAt: '11:35:00',
    manualAccelerated: false
  }
]

export const queueWorkers = [
  { workerId: 'ops-worker-01', department: '运营部', queueName: '运营部-高优先级队列', queueLevel: 'HIGH', currentTask: 'TASK-20260628-0005', stage: 'EXTRACTING', load: 82, status: 'BUSY' },
  { workerId: 'ops-worker-02', department: '运营部', queueName: '运营部-低优先级队列', queueLevel: 'LOW', currentTask: 'TASK-20260628-0003', stage: 'PARSING', load: 64, status: 'BUSY' },
  { workerId: 'finance-worker-01', department: '财务部', queueName: '财务部-中优先级队列', queueLevel: 'MEDIUM', currentTask: 'TASK-20260628-0002', stage: 'COMPLETED', load: 28, status: 'IDLE' },
  { workerId: 'product-worker-01', department: '产品部', queueName: '产品部-中优先级队列', queueLevel: 'MEDIUM', currentTask: 'TASK-20260628-0004', stage: 'CLASSIFYING', load: 51, status: 'BUSY' }
]

export type PushMethod = 'HTTP' | 'MICROSERVICE' | 'MQ'
export type PushStatus = 'SUCCESS' | 'PENDING' | 'FAILED' | 'RETRYING'

export interface DownstreamSystem {
  systemCode: string
  systemName: string
  ownerDepartment: DepartmentName
  pushMethod: PushMethod
  endpoint: string
  authType: 'NONE' | 'TOKEN' | 'SIGN'
  timeoutSeconds: number
  retryCount: number
  enabled: boolean
  successRate: number
}

export interface DownstreamService {
  serviceCode: string
  serviceName: string
  systemCode: string
  systemName: string
  serviceType: PushMethod
  purpose: string
  endpoint: string
  httpMethod: 'GET' | 'POST' | '-'
  authType: 'INHERIT' | 'NONE' | 'TOKEN' | 'SIGN'
  timeoutSeconds: number
  retryCount: number
  idempotentRule: string
  responseSuccessRule: string
  enabled: boolean
  successRate: number
  boundConfigCount: number
  version: string
}

export interface PushRecord {
  pushId: string
  traceId: string
  taskId: string
  targetSystem: string
  serviceCode?: string
  serviceName?: string
  pushMethod: PushMethod
  triggerType: string
  idempotentKey: string
  status: PushStatus
  retryCount: number
  pushedAt: string
  responseMessage: string
}

export const downstreamSystems: DownstreamSystem[] = [
  {
    systemCode: 'fund_ops',
    systemName: '运营业务系统',
    ownerDepartment: '运营部',
    pushMethod: 'HTTP',
    endpoint: 'https://ops.example.com/api/extract-results',
    authType: 'TOKEN',
    timeoutSeconds: 30,
    retryCount: 3,
    enabled: true,
    successRate: 98
  },
  {
    systemCode: 'finance_core',
    systemName: '财务核算系统',
    ownerDepartment: '财务部',
    pushMethod: 'MICROSERVICE',
    endpoint: 'finance-result-service.receiveExtractResult',
    authType: 'SIGN',
    timeoutSeconds: 20,
    retryCount: 2,
    enabled: true,
    successRate: 96
  },
  {
    systemCode: 'data_warehouse',
    systemName: '数据仓库',
    ownerDepartment: '产品部',
    pushMethod: 'MQ',
    endpoint: 'topic.extract.result',
    authType: 'NONE',
    timeoutSeconds: 10,
    retryCount: 5,
    enabled: true,
    successRate: 99
  }
]

export const downstreamServices: DownstreamService[] = [
  {
    serviceCode: 'fund_ops_result_receive',
    serviceName: '接收提取结果服务',
    systemCode: 'fund_ops',
    systemName: '运营业务系统',
    serviceType: 'HTTP',
    purpose: '结果推送',
    endpoint: 'https://ops.example.com/api/extract-results',
    httpMethod: 'POST',
    authType: 'INHERIT',
    timeoutSeconds: 30,
    retryCount: 3,
    idempotentRule: 'traceId + taskId + resultVersion',
    responseSuccessRule: 'httpStatus in [200,202] && body.code == 0',
    enabled: true,
    successRate: 98,
    boundConfigCount: 6,
    version: 'v1'
  },
  {
    serviceCode: 'fund_ops_attachment_receive',
    serviceName: '接收附件服务',
    systemCode: 'fund_ops',
    systemName: '运营业务系统',
    serviceType: 'HTTP',
    purpose: '附件推送',
    endpoint: 'https://ops.example.com/api/document-attachments',
    httpMethod: 'POST',
    authType: 'INHERIT',
    timeoutSeconds: 45,
    retryCount: 2,
    idempotentRule: 'traceId + documentId + fileHash',
    responseSuccessRule: 'httpStatus == 200 && body.success == true',
    enabled: true,
    successRate: 97,
    boundConfigCount: 3,
    version: 'v1'
  },
  {
    serviceCode: 'finance_result_receive',
    serviceName: '核算结果接收服务',
    systemCode: 'finance_core',
    systemName: '财务核算系统',
    serviceType: 'MICROSERVICE',
    purpose: '结果推送',
    endpoint: 'finance-result-service.receiveExtractResult',
    httpMethod: '-',
    authType: 'INHERIT',
    timeoutSeconds: 20,
    retryCount: 2,
    idempotentRule: 'traceId + taskId + targetTable',
    responseSuccessRule: 'response.accepted == true',
    enabled: true,
    successRate: 96,
    boundConfigCount: 4,
    version: 'v2'
  },
  {
    serviceCode: 'finance_status_callback',
    serviceName: '处理状态回调服务',
    systemCode: 'finance_core',
    systemName: '财务核算系统',
    serviceType: 'MICROSERVICE',
    purpose: '状态回调',
    endpoint: 'finance-result-service.callbackTaskStatus',
    httpMethod: '-',
    authType: 'SIGN',
    timeoutSeconds: 15,
    retryCount: 1,
    idempotentRule: 'traceId + status + callbackTime',
    responseSuccessRule: 'response.code == SUCCESS',
    enabled: false,
    successRate: 92,
    boundConfigCount: 1,
    version: 'v1'
  },
  {
    serviceCode: 'dw_extract_result_topic',
    serviceName: '结果批量同步 Topic',
    systemCode: 'data_warehouse',
    systemName: '数据仓库',
    serviceType: 'MQ',
    purpose: '批量同步',
    endpoint: 'topic.extract.result',
    httpMethod: '-',
    authType: 'INHERIT',
    timeoutSeconds: 10,
    retryCount: 5,
    idempotentRule: 'traceId + taskId + resultVersion',
    responseSuccessRule: 'broker ack',
    enabled: true,
    successRate: 99,
    boundConfigCount: 8,
    version: 'v1'
  }
]

export const pushRecords: PushRecord[] = [
  {
    pushId: 'PUSH-20260628-0001',
    traceId: 'TRACE-20260628-0001',
    taskId: 'TASK-20260628-0001',
    targetSystem: '运营业务系统',
    serviceCode: 'fund_ops_result_receive',
    serviceName: '接收提取结果服务',
    pushMethod: 'HTTP',
    triggerType: '复核通过后',
    idempotentKey: 'TRACE-20260628-0001:TASK-20260628-0001:v1',
    status: 'SUCCESS',
    retryCount: 0,
    pushedAt: '2026-06-28 09:45:00',
    responseMessage: '200 OK'
  },
  {
    pushId: 'PUSH-20260628-0002',
    traceId: 'TRACE-20260628-0002',
    taskId: 'TASK-20260628-0002',
    targetSystem: '财务核算系统',
    serviceCode: 'finance_result_receive',
    serviceName: '核算结果接收服务',
    pushMethod: 'MICROSERVICE',
    triggerType: '落库成功后',
    idempotentKey: 'TRACE-20260628-0002:TASK-20260628-0002:v1',
    status: 'SUCCESS',
    retryCount: 0,
    pushedAt: '2026-06-28 10:19:00',
    responseMessage: 'accepted'
  },
  {
    pushId: 'PUSH-20260628-0003',
    traceId: 'TRACE-20260628-0006',
    taskId: 'TASK-20260628-0006',
    targetSystem: '数据仓库',
    serviceCode: 'dw_extract_result_topic',
    serviceName: '结果批量同步 Topic',
    pushMethod: 'MQ',
    triggerType: '手工触发',
    idempotentKey: 'TRACE-20260628-0006:TASK-20260628-0006:v1',
    status: 'FAILED',
    retryCount: 2,
    pushedAt: '2026-06-28 15:30:00',
    responseMessage: 'topic permission denied'
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
