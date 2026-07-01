<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DataAnalysis,
  DocumentAdd,
  Files,
  Setting,
  Checked,
  Finished,
  Cpu,
  User
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const activeMenu = computed(() => route.path)

const go = (path: string) => router.push(path)
</script>

<template>
  <el-container class="app-shell">
    <el-aside width="216px" class="sidebar">
      <div class="brand">
        <div class="brand-mark">OCR</div>
        <div>
          <strong>智能要素提取平台</strong>
          <span>Prototype</span>
        </div>
      </div>
      <el-menu :default-active="activeMenu" router class="nav-menu">
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-sub-menu index="/documents">
          <template #title>
            <el-icon><DocumentAdd /></el-icon>
            <span>文档接入</span>
          </template>
          <el-menu-item index="/documents/upload">手工上传</el-menu-item>
          <el-menu-item index="/documents/records">接入记录</el-menu-item>
          <el-menu-item index="/documents/unmatched">待确认文档</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/tasks">
          <template #title>
            <el-icon><Files /></el-icon>
            <span>任务中心</span>
          </template>
          <el-menu-item index="/tasks">全部任务</el-menu-item>
          <el-menu-item index="/tasks/queue-dispatch">队列调度</el-menu-item>
          <el-menu-item index="/monitor/traces">全链路监控</el-menu-item>
          <el-menu-item index="/tasks/failed">失败任务</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/configs">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>配置中心</span>
          </template>
          <el-menu-item index="/configs/wizard">配置向导</el-menu-item>
          <el-menu-item index="/configs">配置列表</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/reviews">
          <el-icon><Checked /></el-icon>
          <span>复核中心</span>
        </el-menu-item>
        <el-sub-menu index="/results-menu">
          <template #title>
            <el-icon><Finished /></el-icon>
            <span>结果中心</span>
          </template>
          <el-menu-item index="/results">提取结果</el-menu-item>
          <el-menu-item index="/storage-data">落库数据查询</el-menu-item>
          <el-menu-item index="/push-records">推送记录</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/models">
          <template #title>
            <el-icon><Cpu /></el-icon>
            <span>模型中心</span>
          </template>
          <el-menu-item index="/models/ocr">OCR 引擎</el-menu-item>
          <el-menu-item index="/models/llm">LLM 配置</el-menu-item>
          <el-menu-item index="/models/logs">调用日志</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/system">
          <template #title>
            <el-icon><User /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/system/users">用户管理</el-menu-item>
          <el-menu-item index="/system/roles">角色权限</el-menu-item>
          <el-menu-item index="/system/data-permissions">数据权限</el-menu-item>
          <el-menu-item index="/system/integrations">集成管理</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>智能要素提取平台</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
          <h1>{{ route.meta.title }}</h1>
        </div>
        <div class="user-area">
          <el-button type="primary" @click="go('/documents/upload')">上传文档</el-button>
          <el-badge :value="17">
            <el-button>待复核</el-button>
          </el-badge>
          <el-avatar>王</el-avatar>
          <span>王老师 / 运营部</span>
        </div>
      </el-header>
      <el-main class="main-view">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
