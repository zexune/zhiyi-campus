<template>
  <span class="avatar" :class="[`avatar--${size}`, `avatar--${color}`]" :title="nickname">
    {{ initial }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 文字头像 —— demo 设计系统 .avatar（昵称首字 + 按 id 取色）
 */
const props = defineProps({
  nickname: { type: String, default: '?' },
  userId: { type: [Number, String], default: 0 },
  size: { type: String, default: 's' }, // s / m / l
})

const COLORS = ['orange', 'green', 'blue', 'yellow', 'ink']

const initial = computed(() => (props.nickname || '?').trim().charAt(0).toUpperCase())
const color = computed(() => COLORS[Number(props.userId) % COLORS.length])
</script>
