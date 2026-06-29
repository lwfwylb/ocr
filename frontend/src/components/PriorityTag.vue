<script setup lang="ts">
const props = defineProps<{ priority: string }>()

const priorityMap: Record<string, { label: string; type: 'danger' | 'warning' | 'info' }> = {
  HIGH: { label: '高', type: 'danger' },
  MEDIUM: { label: '中', type: 'warning' },
  LOW: { label: '低', type: 'info' }
}
</script>

<template>
  <el-tag :type="priorityMap[props.priority]?.type || 'info'" effect="light">
    {{ priorityMap[props.priority]?.label || props.priority }}
  </el-tag>
</template>
