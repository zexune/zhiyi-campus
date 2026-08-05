<template>
  <span class="price" :style="{ fontSize }">
    <span class="rmb">¥</span>{{ formatted }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 价格展示 —— demo 设计系统 .price（柿子橙 + 展示字体）
 */
const props = defineProps({
  value: { type: [Number, String], default: 0 },
  fontSize: { type: String, default: '20px' },
})

const formatted = computed(() => {
  const n = Number(props.value)
  if (Number.isNaN(n)) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
})
</script>
