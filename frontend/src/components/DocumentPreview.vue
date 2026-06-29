<script setup lang="ts">
const props = defineProps<{ activeField?: string }>()
</script>

<template>
  <div class="document-preview">
    <div class="doc-toolbar">
      <span>划款指令_001.pdf</span>
      <div>
        <el-button size="small">-</el-button>
        <el-button size="small">适应宽度</el-button>
        <el-button size="small">+</el-button>
      </div>
    </div>
    <div class="paper">
      <h3>划款指令</h3>
      <p>付款方：示例基金管理有限公司</p>
      <p :class="{ highlight: props.activeField === 'payee_account' }">收款账号：6222 **** 8910</p>
      <p :class="{ highlight: props.activeField === 'amount' }">金额：100000.00</p>
      <p>划款日期：2026年6月28日</p>
      <div class="seal">印章识别区域</div>
    </div>
  </div>
</template>
