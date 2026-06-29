<script setup lang="ts">
const props = defineProps<{ value: number }>()
</script>

<template>
  <el-tag :type="props.value >= 0.9 ? 'success' : props.value >= 0.75 ? 'warning' : 'danger'" effect="light">
    {{ Math.round(props.value * 100) }}%
  </el-tag>
</template>
