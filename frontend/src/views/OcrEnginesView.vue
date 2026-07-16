<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import {
  createOcrEngineConfig,
  disableOcrEngineConfig,
  enableOcrEngineConfig,
  listOcrEngineConfigs,
  setDefaultOcrEngineConfig,
  testOcrEngineParse,
  testOcrEngineConfig,
  updateOcrEngineConfig,
  type OcrEngineConfig,
  type OcrEnginePayload,
  type OcrEngineParseTestResult
} from '../api/model'
import { API_BASE_URL } from '../api/http'

const drawerVisible = ref(false)
const loading = ref(false)
const saving = ref(false)
const editingId = ref('')
const engines = ref<OcrEngineConfig[]>([])
const parseDialogVisible = ref(false)
const parseTesting = ref(false)
const parseTestEngine = ref<OcrEngineConfig | null>(null)
const parseTestFile = ref<File | null>(null)
const parseTestResult = ref<OcrEngineParseTestResult | null>(null)
const markdownViewMode = ref<'preview' | 'source'>('preview')
const compareDialogVisible = ref(false)
const compareFileUrl = ref('')
const markdownParser = new MarkdownIt({
  html: true,
  linkify: true,
  breaks: true
})
const query = reactive({
  keyword: '',
  provider: '',
  engineType: '',
  status: ''
})
const form = reactive<OcrEnginePayload>({
  engineCode: '',
  engineName: '',
  engineType: 'OCR/LAYOUT/TABLE',
  adapterType: 'PADDLE_OCR_VL',
  provider: 'PaddleOCR',
  baseUrl: '',
  authMode: 'NONE',
  apiKeySecretRef: '',
  defaultEngine: false,
  priority: 100,
  timeoutSeconds: 120,
  retryCount: 2,
  supportedFileTypes: 'pdf,jpg,jpeg,png,bmp,tif,tiff',
  outputFormat: 'Markdown',
  maxPagesPerCall: 20,
  engineParamsJson: '',
  status: 'ENABLED',
  description: ''
})

const enabledCount = computed(() => engines.value.filter((item) => item.status === 'ENABLED').length)
const defaultEngine = computed(() => engines.value.find((item) => item.defaultEngine))
const providerOptions = computed(() => Array.from(new Set(engines.value.map((item) => item.provider).filter(Boolean))))
const engineTypeOptions = computed(() => Array.from(new Set(engines.value.map((item) => item.engineType).filter(Boolean))))
const markdownCount = computed(() => engines.value.filter((item) => item.outputFormat === 'Markdown').length)
const adapterTypeOptions = [
  { label: 'PaddleOCR-VL', value: 'PADDLE_OCR_VL' },
  { label: 'MinerU', value: 'MINERU' }
]
const minerUSupportedExtensions = ['pdf', 'png', 'jpg', 'jpeg', 'jp2', 'webp', 'gif', 'bmp', 'tif', 'tiff', 'docx', 'pptx', 'xlsx']
const paddleSupportedExtensions = ['pdf', 'jpg', 'jpeg', 'png', 'bmp', 'tif', 'tiff']
const supportedFileTypesByAdapter = (adapterType?: string) =>
  adapterType === 'MINERU' ? minerUSupportedExtensions.join(',') : paddleSupportedExtensions.join(',')
const adapterTypeText = (value?: string) => adapterTypeOptions.find((item) => item.value === value)?.label || value || '-'
const parseStatusType = computed(() => parseTestResult.value?.passed ? 'success' : 'error')
const markdownSource = computed(() => parseTestResult.value?.markdownText || parseTestResult.value?.markdownPreview || '')
const renderedMarkdownHtml = computed(() => renderMarkdown(markdownSource.value))
const isMinerUParseTest = computed(() => parseTestEngine.value?.adapterType === 'MINERU')
const parseUploadAccept = computed(() => isMinerUParseTest.value
  ? minerUSupportedExtensions.map((item) => `.${item}`).join(',')
  : paddleSupportedExtensions.map((item) => `.${item}`).join(','))
const parseUploadTip = computed(() => isMinerUParseTest.value
  ? 'MinerU 试识别支持 PDF、常见图片、DOCX、PPTX、XLSX，测试不写入正式任务链路。'
  : 'PaddleOCR-VL 试识别支持 PDF、JPG、JPEG、PNG、BMP、TIF、TIFF，测试不写入正式任务链路。')
const compareFileName = computed(() => parseTestFile.value?.name || '-')
const compareFileKind = computed(() => {
  const file = parseTestFile.value
  const name = file?.name.toLowerCase() || ''
  const type = file?.type.toLowerCase() || ''
  if (name.endsWith('.pdf') || type.includes('pdf')) return 'pdf'
  if (/\.(png|jpg|jpeg|jp2|webp|gif|bmp|tif|tiff)$/i.test(name) || type.startsWith('image/')) return 'image'
  return 'unknown'
})

const resetForm = () => {
  editingId.value = ''
  Object.assign(form, {
    engineCode: '',
    engineName: '',
    engineType: 'OCR/LAYOUT/TABLE',
    adapterType: 'PADDLE_OCR_VL',
    provider: 'PaddleOCR',
    baseUrl: '',
    authMode: 'NONE',
    apiKeySecretRef: '',
    defaultEngine: false,
    priority: 100,
    timeoutSeconds: 120,
    retryCount: 2,
    supportedFileTypes: supportedFileTypesByAdapter('PADDLE_OCR_VL'),
    outputFormat: 'Markdown',
    maxPagesPerCall: 20,
    engineParamsJson: '',
    status: 'ENABLED',
    description: ''
  })
}

const loadEngines = async () => {
  loading.value = true
  try {
    engines.value = await listOcrEngineConfigs(query)
  } catch (error) {
    engines.value = []
    ElMessage.error(error instanceof Error ? error.message : 'OCR 引擎加载失败')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  resetForm()
  drawerVisible.value = true
}

const openEdit = (engine: OcrEngineConfig) => {
  editingId.value = engine.id
  Object.assign(form, {
    engineCode: engine.engineCode,
    engineName: engine.engineName,
    engineType: engine.engineType,
    adapterType: engine.adapterType || '',
    provider: engine.provider,
    baseUrl: engine.baseUrl,
    authMode: engine.authMode || 'NONE',
    apiKeySecretRef: engine.apiKeySecretRef || '',
    defaultEngine: engine.defaultEngine,
    priority: engine.priority,
    timeoutSeconds: engine.timeoutSeconds,
    retryCount: engine.retryCount,
    supportedFileTypes: engine.supportedFileTypes || supportedFileTypesByAdapter(engine.adapterType),
    outputFormat: 'Markdown',
    maxPagesPerCall: engine.maxPagesPerCall || 20,
    engineParamsJson: engine.engineParamsJson || '',
    status: engine.status,
    description: engine.description || ''
  })
  drawerVisible.value = true
}

const handleAdapterTypeChange = (adapterType: string) => {
  const current = form.supportedFileTypes || ''
  const paddleDefault = supportedFileTypesByAdapter('PADDLE_OCR_VL')
  const minerUDefault = supportedFileTypesByAdapter('MINERU')
  if (!current || current === paddleDefault || current === minerUDefault) {
    form.supportedFileTypes = supportedFileTypesByAdapter(adapterType)
  }
}

const saveEngine = async () => {
  saving.value = true
  try {
    const result = editingId.value
      ? await updateOcrEngineConfig(editingId.value, form)
      : await createOcrEngineConfig(form)
    ElMessage.success(`OCR 引擎已保存：${result.engineName}`)
    drawerVisible.value = false
    await loadEngines()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'OCR 引擎保存失败')
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (engine: OcrEngineConfig) => {
  try {
    if (engine.status === 'ENABLED') {
      await ElMessageBox.confirm('停用后，配置向导和任务执行不再选择该 OCR 引擎。确认停用？', '停用 OCR 引擎', { type: 'warning' })
      await disableOcrEngineConfig(engine.id)
      ElMessage.success('OCR 引擎已停用')
    } else {
      await enableOcrEngineConfig(engine.id)
      ElMessage.success('OCR 引擎已启用')
    }
    await loadEngines()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '状态更新失败')
  }
}

const setDefault = async (engine: OcrEngineConfig) => {
  try {
    await setDefaultOcrEngineConfig(engine.id)
    ElMessage.success(`已设置 ${engine.engineName} 为默认 OCR 引擎`)
    await loadEngines()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '设置默认 OCR 引擎失败')
  }
}

const testEngine = async (engine: OcrEngineConfig) => {
  try {
    const result = await testOcrEngineConfig(engine.id)
    ElMessage.success(result.message)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '测试连接失败')
  }
}

const openParseTest = (engine: OcrEngineConfig) => {
  parseTestEngine.value = engine
  parseTestFile.value = null
  parseTestResult.value = null
  markdownViewMode.value = 'preview'
  closeCompareDialog()
  parseDialogVisible.value = true
}

const fileExtension = (fileName?: string) => {
  const name = fileName || ''
  const index = name.lastIndexOf('.')
  return index >= 0 ? name.slice(index + 1).toLowerCase() : ''
}

const isMinerUSupportedFile = (fileName?: string) => minerUSupportedExtensions.includes(fileExtension(fileName))
const isPaddleSupportedFile = (fileName?: string) => paddleSupportedExtensions.includes(fileExtension(fileName))

const handleParseFileChange = (uploadFile: UploadFile) => {
  if (isMinerUParseTest.value && uploadFile.raw && !isMinerUSupportedFile(uploadFile.raw.name)) {
    parseTestFile.value = null
    parseTestResult.value = null
    closeCompareDialog()
    ElMessage.warning('MinerU 试识别支持 PDF、常见图片、DOCX、PPTX、XLSX，请上传支持的样本文档')
    return
  }
  if (!isMinerUParseTest.value && uploadFile.raw && !isPaddleSupportedFile(uploadFile.raw.name)) {
    parseTestFile.value = null
    parseTestResult.value = null
    closeCompareDialog()
    ElMessage.warning('PaddleOCR-VL 试识别支持 PDF、JPG、JPEG、PNG、BMP、TIF、TIFF，请上传支持的样本文档')
    return
  }
  parseTestFile.value = uploadFile.raw || null
  parseTestResult.value = null
  markdownViewMode.value = 'preview'
  closeCompareDialog()
}

const handleParseFileRemove = () => {
  parseTestFile.value = null
  parseTestResult.value = null
  markdownViewMode.value = 'preview'
  closeCompareDialog()
}

const runParseTest = async () => {
  if (!parseTestEngine.value) return
  if (!parseTestFile.value) {
    ElMessage.warning('请先上传样本文档')
    return
  }
  if (isMinerUParseTest.value && !isMinerUSupportedFile(parseTestFile.value.name)) {
    ElMessage.warning('MinerU 试识别支持 PDF、常见图片、DOCX、PPTX、XLSX，请上传支持的样本文档')
    return
  }
  if (!isMinerUParseTest.value && !isPaddleSupportedFile(parseTestFile.value.name)) {
    ElMessage.warning('PaddleOCR-VL 试识别支持 PDF、JPG、JPEG、PNG、BMP、TIF、TIFF，请上传支持的样本文档')
    return
  }
  parseTesting.value = true
  try {
    parseTestResult.value = await testOcrEngineParse(parseTestEngine.value.id, parseTestFile.value)
    markdownViewMode.value = 'preview'
    if (parseTestResult.value.passed) {
      ElMessage.success(parseTestResult.value.message)
    } else {
      ElMessage.error(parseTestResult.value.message || 'OCR 试识别失败')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'OCR 试识别失败')
  } finally {
    parseTesting.value = false
  }
}

const copyMarkdown = async () => {
  const text = markdownSource.value
  if (!text) return
  await navigator.clipboard.writeText(text)
  ElMessage.success('Markdown 已复制')
}

const openCompareDialog = () => {
  if (!parseTestFile.value) {
    ElMessage.warning('请先上传样本文档')
    return
  }
  if (!markdownSource.value) {
    ElMessage.warning('请先完成 OCR 试识别')
    return
  }
  revokeCompareFileUrl()
  compareFileUrl.value = URL.createObjectURL(parseTestFile.value)
  compareDialogVisible.value = true
}

const closeCompareDialog = () => {
  compareDialogVisible.value = false
  revokeCompareFileUrl()
}

const revokeCompareFileUrl = () => {
  if (compareFileUrl.value) {
    URL.revokeObjectURL(compareFileUrl.value)
    compareFileUrl.value = ''
  }
}

const resetQuery = () => {
  query.keyword = ''
  query.provider = ''
  query.engineType = ''
  query.status = ''
  loadEngines()
}

const legacyRenderMarkdown = (source: string) => {
  if (!source) return ''
  const lines = source.replace(/\r\n/g, '\n').split('\n')
  const html: string[] = []
  let paragraph: string[] = []
  let listItems: string[] = []
  let codeLines: string[] = []
  let inCode = false

  const flushParagraph = () => {
    if (paragraph.length) {
      html.push(`<p>${renderInline(paragraph.join(' '))}</p>`)
      paragraph = []
    }
  }
  const flushList = () => {
    if (listItems.length) {
      html.push(`<ul>${listItems.map((item) => `<li>${renderInline(item)}</li>`).join('')}</ul>`)
      listItems = []
    }
  }
  const flushCode = () => {
    html.push(`<pre><code>${escapeHtml(codeLines.join('\n'))}</code></pre>`)
    codeLines = []
  }

  for (let index = 0; index < lines.length; index++) {
    const line = lines[index]
    const trimmed = line.trim()
    if (trimmed.startsWith('```')) {
      if (inCode) {
        flushCode()
        inCode = false
      } else {
        flushParagraph()
        flushList()
        inCode = true
      }
      continue
    }
    if (inCode) {
      codeLines.push(line)
      continue
    }
    if (!trimmed) {
      flushParagraph()
      flushList()
      continue
    }
    if (/^---+$/.test(trimmed)) {
      flushParagraph()
      flushList()
      html.push('<hr>')
      continue
    }
    const heading = trimmed.match(/^(#{1,6})\s+(.+)$/)
    if (heading) {
      flushParagraph()
      flushList()
      const level = heading[1].length
      html.push(`<h${level}>${renderInline(heading[2])}</h${level}>`)
      continue
    }
    if (isTableStart(lines, index)) {
      flushParagraph()
      flushList()
      const parsed = parseTable(lines, index)
      html.push(parsed.html)
      index = parsed.nextIndex - 1
      continue
    }
    const list = trimmed.match(/^[-*+]\s+(.+)$/)
    if (list) {
      flushParagraph()
      listItems.push(list[1])
      continue
    }
    flushList()
    paragraph.push(trimmed)
  }

  if (inCode) flushCode()
  flushParagraph()
  flushList()
  return html.join('')
}

const isTableStart = (lines: string[], index: number) => {
  const current = lines[index]?.trim() || ''
  const next = lines[index + 1]?.trim() || ''
  return current.includes('|') && /^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$/.test(next)
}

const parseTable = (lines: string[], startIndex: number) => {
  const header = splitTableRow(lines[startIndex])
  const rows: string[][] = []
  let index = startIndex + 2
  while (index < lines.length && lines[index].includes('|') && lines[index].trim()) {
    rows.push(splitTableRow(lines[index]))
    index++
  }
  const thead = `<thead><tr>${header.map((cell) => `<th>${renderInline(cell)}</th>`).join('')}</tr></thead>`
  const tbody = `<tbody>${rows.map((row) => `<tr>${header.map((_, cellIndex) => `<td>${renderInline(row[cellIndex] || '')}</td>`).join('')}</tr>`).join('')}</tbody>`
  return { html: `<div class="markdown-table-wrap"><table>${thead}${tbody}</table></div>`, nextIndex: index }
}

const splitTableRow = (line: string) => line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((cell) => cell.trim())

const renderInline = (value: string) => escapeHtml(value)
  .replace(/!\[([^\]]*)\]\(([^)]+)\)/g, (_match, alt, url) => safeUrl(url) ? `<img src="${safeUrl(url)}" alt="${escapeAttribute(alt)}">` : escapeHtml(_match))
  .replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_match, text, url) => safeUrl(url) ? `<a href="${safeUrl(url)}" target="_blank" rel="noopener noreferrer">${text}</a>` : text)
  .replace(/`([^`]+)`/g, '<code>$1</code>')
  .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')

const safeUrl = (value: string) => {
  const url = String(value || '').trim()
  if (/^(https?:|data:image\/|\/api\/artifacts\/)/i.test(url)) return escapeAttribute(url)
  return ''
}

const escapeHtml = (value: string) => String(value || '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;')

const escapeAttribute = (value: string) => escapeHtml(value).replace(/`/g, '&#96;')

const renderMarkdown = (source: string) => {
  if (!source) return ''
  const html = markdownParser.render(resolveApiAssetUrls(source))
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    ADD_TAGS: ['table', 'thead', 'tbody', 'tr', 'th', 'td', 'div', 'span', 'img'],
    ADD_ATTR: ['target', 'rel', 'colspan', 'rowspan', 'align', 'src', 'alt', 'title', 'width', 'height', 'class'],
    FORBID_TAGS: ['script', 'style', 'iframe', 'object', 'embed', 'form', 'input', 'button'],
    FORBID_ATTR: ['style', 'srcdoc']
  }).replace(/<a /g, '<a target="_blank" rel="noopener noreferrer" ')
}

const resolveApiAssetUrls = (source: string) => {
  const baseUrl = API_BASE_URL.replace(/\/$/, '')
  return source
    .replace(/\]\(\/api\//g, `](${baseUrl}/api/`)
    .replace(/src=["']\/api\//g, (match) => `${match.slice(0, 5)}${baseUrl}/api/`)
}

onMounted(loadEngines)
onBeforeUnmount(revokeCompareFileUrl)
</script>

<template>
  <div class="page-stack">
    <section class="metric-grid config-summary">
      <el-card shadow="never" class="metric-card"><span>OCR 引擎</span><strong>{{ engines.length }}</strong><em>服务配置</em></el-card>
      <el-card shadow="never" class="metric-card"><span>启用中</span><strong>{{ enabledCount }}</strong><em>可被向导选择</em></el-card>
      <el-card shadow="never" class="metric-card"><span>默认引擎</span><strong>{{ defaultEngine ? 1 : 0 }}</strong><em>{{ defaultEngine?.engineName || '未设置' }}</em></el-card>
      <el-card shadow="never" class="metric-card"><span>Markdown 输出</span><strong>{{ markdownCount }}</strong><em>当前固定格式</em></el-card>
      <el-card shadow="never" class="metric-card"><span>测试调用</span><strong>真实</strong><em>支持样本文档试识别</em></el-card>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form compact-search">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="编码/名称/供应方/地址" clearable />
        </el-form-item>
        <el-form-item label="供应方">
          <el-select v-model="query.provider" clearable filterable placeholder="全部">
            <el-option v-for="provider in providerOptions" :key="provider" :label="provider" :value="provider" />
          </el-select>
        </el-form-item>
        <el-form-item label="能力类型">
          <el-select v-model="query.engineType" clearable filterable placeholder="全部">
            <el-option v-for="type in engineTypeOptions" :key="type" :label="type" :value="type" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadEngines">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>OCR 引擎配置</span>
          <el-button type="primary" @click="openCreate">新增引擎</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="engines" stripe>
        <el-table-column prop="engineCode" label="引擎编码" min-width="150" fixed />
        <el-table-column prop="engineName" label="引擎名称" min-width="170" />
        <el-table-column label="适配器" width="130"><template #default="{ row }">{{ adapterTypeText(row.adapterType) }}</template></el-table-column>
        <el-table-column prop="engineType" label="能力类型" min-width="150" />
        <el-table-column prop="provider" label="供应方" min-width="130" />
        <el-table-column prop="baseUrl" label="服务地址" min-width="260" />
        <el-table-column prop="supportedFileTypes" label="支持文件" min-width="170" />
        <el-table-column label="状态" width="86">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="默认" width="80">
          <template #default="{ row }"><el-tag v-if="row.defaultEngine" type="success">默认</el-tag><span v-else>-</span></template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column prop="timeoutSeconds" label="超时" width="80" />
        <el-table-column prop="retryCount" label="重试" width="70" />
        <el-table-column prop="maxPagesPerCall" label="单次页数" width="90" />
        <el-table-column label="输出" width="100">
          <template #default="{ row }"><el-tag>{{ row.outputFormat }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="310" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">配置</el-button>
            <el-button link @click="testEngine(row)">配置检查</el-button>
            <el-button link type="success" @click="openParseTest(row)">试识别</el-button>
            <el-button link @click="setDefault(row)">设默认</el-button>
            <el-button link :type="row.status === 'ENABLED' ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" :title="editingId ? '编辑 OCR 引擎' : '新增 OCR 引擎'" size="680px">
      <el-form :model="form" label-width="130px" class="form-grid">
        <el-form-item label="引擎编码"><el-input v-model="form.engineCode" placeholder="如 paddleocr_vl" /></el-form-item>
        <el-form-item label="引擎名称"><el-input v-model="form.engineName" placeholder="如 PaddleOCR-VL-1.6" /></el-form-item>
        <el-form-item label="适配器类型">
          <el-select v-model="form.adapterType" placeholder="请选择适配器类型" @change="handleAdapterTypeChange">
            <el-option v-for="item in adapterTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应方">
          <el-select v-model="form.provider" filterable allow-create>
            <el-option label="PaddleOCR" value="PaddleOCR" />
            <el-option label="MinerU" value="MinerU" />
            <el-option label="自研 OCR" value="自研 OCR" />
            <el-option label="第三方 OCR" value="第三方 OCR" />
          </el-select>
        </el-form-item>
        <el-form-item label="能力类型">
          <el-select v-model="form.engineType" filterable allow-create>
            <el-option label="OCR" value="OCR" />
            <el-option label="OCR/LAYOUT/TABLE" value="OCR/LAYOUT/TABLE" />
            <el-option label="PDF/PARSE" value="PDF/PARSE" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务地址" class="wide"><el-input v-model="form.baseUrl" placeholder="http://ocr-service/paddle/parse" /></el-form-item>
        <el-form-item label="认证方式">
          <el-select v-model="form.authMode">
            <el-option label="不认证" value="NONE" />
            <el-option label="API Key" value="API_KEY" />
            <el-option label="系统统一认证" value="SYSTEM" />
          </el-select>
        </el-form-item>
        <el-form-item label="密钥引用"><el-input v-model="form.apiKeySecretRef" placeholder="如 secret://ocr/paddle" /></el-form-item>
        <el-form-item label="支持文件" class="wide"><el-input v-model="form.supportedFileTypes" placeholder="pdf,jpg,jpeg,png,bmp,tif,tiff" /></el-form-item>
        <el-form-item label="输出格式"><el-input v-model="form.outputFormat" disabled /></el-form-item>
        <el-form-item label="单次最大页数"><el-input-number v-model="form.maxPagesPerCall" :min="1" :max="1000" /></el-form-item>
        <el-form-item label="调度优先级"><el-input-number v-model="form.priority" :min="0" :max="999" /></el-form-item>
        <el-form-item label="超时秒数"><el-input-number v-model="form.timeoutSeconds" :min="1" :max="3600" /></el-form-item>
        <el-form-item label="重试次数"><el-input-number v-model="form.retryCount" :min="0" :max="10" /></el-form-item>
        <el-form-item label="设为默认"><el-switch v-model="form.defaultEngine" active-text="默认" inactive-text="普通" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button label="ENABLED">启用</el-radio-button>
            <el-radio-button label="DISABLED">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="默认参数 JSON" class="wide"><el-input v-model="form.engineParamsJson" type="textarea" :rows="5" placeholder='如 {"fileType":0,"useSealRecognition":true}' /></el-form-item>
        <el-form-item label="说明" class="wide"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <el-alert
        class="mb-12"
        title="配置检查用于校验引擎基础配置；试识别会上传样本文档真实调用 OCR 服务，但不写入正式任务和结果表。"
        type="info"
        :closable="false"
      />
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEngine">保存</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="parseDialogVisible" title="OCR 文档试识别" width="92vw" class="ocr-parse-dialog" destroy-on-close>
      <div class="parse-test-layout">
        <section class="parse-test-side">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="引擎">{{ parseTestEngine?.engineName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="适配器">{{ adapterTypeText(parseTestEngine?.adapterType) }}</el-descriptions-item>
            <el-descriptions-item label="服务地址">{{ parseTestEngine?.baseUrl || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-upload
            class="sample-upload"
            drag
            :auto-upload="false"
            :limit="1"
            :accept="parseUploadAccept"
            :on-change="handleParseFileChange"
            :on-remove="handleParseFileRemove"
          >
            <span class="upload-title">上传样本文档</span>
            <template #tip><div class="el-upload__tip">{{ parseUploadTip }}</div></template>
          </el-upload>
          <el-button type="primary" class="full-button" :loading="parseTesting" @click="runParseTest">开始识别</el-button>
          <el-alert v-if="parseTestResult" :type="parseStatusType" :title="parseTestResult.message" :closable="false" show-icon />
          <el-descriptions v-if="parseTestResult" :column="1" border size="small">
            <el-descriptions-item label="耗时">{{ parseTestResult.durationMs ? `${parseTestResult.durationMs}ms` : '-' }}</el-descriptions-item>
            <el-descriptions-item label="页/文件数">{{ parseTestResult.pageCount || '-' }}</el-descriptions-item>
            <el-descriptions-item label="图片数">{{ parseTestResult.imageCount ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="错误码">{{ parseTestResult.errorCode || '-' }}</el-descriptions-item>
          </el-descriptions>
        </section>
        <section class="parse-test-main">
          <el-tabs v-if="parseTestResult" model-value="markdown">
            <el-tab-pane label="识别结果" name="markdown">
              <div class="preview-actions">
                <el-radio-group v-model="markdownViewMode" size="small">
                  <el-radio-button label="preview">渲染预览</el-radio-button>
                  <el-radio-button label="source">Markdown 原文</el-radio-button>
                </el-radio-group>
                <el-button size="small" :disabled="!markdownSource || !parseTestFile" @click="openCompareDialog">对比查看</el-button>
                <el-button size="small" :disabled="!markdownSource" @click="copyMarkdown">复制 Markdown</el-button>
              </div>
              <div v-if="markdownViewMode === 'preview'" class="ocr-markdown-rendered" v-html="renderedMarkdownHtml || '<p>暂无 Markdown 输出</p>'"></div>
              <pre v-else class="ocr-preview">{{ markdownSource || '暂无 Markdown 输出' }}</pre>
            </el-tab-pane>
            <el-tab-pane label="原始响应摘要" name="raw">
              <pre class="ocr-preview">{{ parseTestResult.rawResponsePreview || '暂无原始响应摘要' }}</pre>
            </el-tab-pane>
          </el-tabs>
          <el-empty v-else description="上传样本文档后点击开始识别" />
        </section>
      </div>
    </el-dialog>

    <el-dialog
      v-model="compareDialogVisible"
      title="OCR 识别对比查看"
      width="92vw"
      top="4vh"
      class="ocr-compare-dialog"
      destroy-on-close
      @closed="revokeCompareFileUrl"
    >
      <div class="compare-meta">
        <span>文件：<strong>{{ compareFileName }}</strong></span>
        <span>引擎：<strong>{{ parseTestEngine?.engineName || '-' }}</strong></span>
        <span>耗时：<strong>{{ parseTestResult?.durationMs ? `${parseTestResult.durationMs}ms` : '-' }}</strong></span>
        <span>图片：<strong>{{ parseTestResult?.imageCount ?? '-' }}</strong></span>
      </div>
      <div class="ocr-compare-layout">
        <section class="compare-pane">
          <div class="compare-pane-title">原始文档</div>
          <div class="compare-original-preview">
            <iframe v-if="compareFileKind === 'pdf' && compareFileUrl" :src="compareFileUrl" title="原始 PDF 预览"></iframe>
            <img v-else-if="compareFileKind === 'image' && compareFileUrl" :src="compareFileUrl" alt="原始图片预览" />
            <el-empty v-else description="当前文件类型暂不支持原文预览" />
          </div>
        </section>
        <section class="compare-pane">
          <div class="compare-pane-title">Markdown 渲染结果</div>
          <div class="ocr-markdown-rendered compare-markdown" v-html="renderedMarkdownHtml || '<p>暂无 Markdown 输出</p>'"></div>
        </section>
      </div>
    </el-dialog>
  </div>
</template>
