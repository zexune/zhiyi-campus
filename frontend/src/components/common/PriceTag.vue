<template>
  <span class="price price-tag" :style="{ fontSize }">
    <span class="rmb">¥</span>
    <span class="price-tag__value">{{ formatted }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 价格展示 —— demo 设计系统 .price（柿子橙 + 展示字体）
 */
const props = defineProps({
  value: { type: [Number, String], default: 0 },
  fontSize: { type: String, default: '20px' }
})

const formatted = computed(() => {
  const n = Number(props.value)
  if (Number.isNaN(n)) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
})
</script>

<style scoped>
.price-tag {
  display: inline-flex;
  align-items: baseline;
  gap: 1px;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.price-tag__value {
  font-weight: 800;
}
</style>
