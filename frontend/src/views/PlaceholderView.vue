<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <el-empty :description="String(route.meta.hint || '该页面已预留，后续迭代完善。')">
    <el-button type="primary" @click="$router.push('/dashboard')">返回工作台</el-button>
  </el-empty>
</template>
