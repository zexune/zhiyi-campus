<template>
  <span class="badge" :class="`badge--lv${clampedLevel}`">
    Lv.{{ level }}<template v-if="showTitle"> {{ title }}</template>
  </span>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 等级徽章（模块一成长体系）—— demo 设计系统 .badge--lv1~lv5
 * 展示位置：商品详情卖家旁 / 聊天头像旁 / 个人主页
 */
const props = defineProps({
  level: { type: Number, default: 1 },
  showTitle: { type: Boolean, default: false },
})

const LEVEL_TITLES = ['初来乍到', '小有信誉', '值得信赖', '交易达人', '校园传奇']

const clampedLevel = computed(() => Math.min(Math.max(props.level, 1), 5))
const title = computed(() => LEVEL_TITLES[clampedLevel.value - 1])
</script>
