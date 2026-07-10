<script setup lang="ts">
const props = defineProps<{ status: string }>()

const statusMap: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' }> = {
  CREATED: { label: '已创建', type: 'info' },
  QUEUED: { label: '排队中', type: 'info' },
  PARSING: { label: '解析中', type: 'primary' },
  EXTRACTING: { label: '提取中', type: 'primary' },
  VALIDATING: { label: '校验中', type: 'warning' },
  WAIT_REVIEW: { label: '待复核', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  STORED: { label: '已落库', type: 'success' },
  PUSHED: { label: '已推送', type: 'success' },
  FAILED: { label: '失败', type: 'danger' }
}
</script>

<template>
  <el-tag :type="statusMap[props.status]?.type || 'info'" round>
    {{ statusMap[props.status]?.label || props.status }}
  </el-tag>
</template>
