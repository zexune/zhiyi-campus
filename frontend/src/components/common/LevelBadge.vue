<template>
  <span class="badge" :class="`badge--lv${clampedLevel}`">
    Lv.{{ level }}
    <template v-if="showTitle && title">{{ title }}</template>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 等级徽章（模块一成长体系）—— demo 设计系统 .badge--lv1~lv5
 * 展示位置：商品详情卖家旁 / 聊天头像旁 / 个人主页
 * 称号文案由服务端下发（levelTitle / publisherLevelTitle），
 * 本组件不做等级→称号的本地映射，避免与后端 LevelRule 形成第二份真相。
 */
const props = defineProps({
  level: { type: Number, default: 1 },
  showTitle: { type: Boolean, default: false },
  /** 服务端下发的等级称号；缺失时不渲染文案（不回退到本地映射） */
  title: { type: String, default: '' }
})

const clampedLevel = computed(() => Math.min(Math.max(props.level, 1), 5))
</script>
