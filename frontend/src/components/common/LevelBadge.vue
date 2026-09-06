<template>
  <span class="badge level-badge" :class="`badge--lv${clampedLevel}`" :aria-label="showTitle && title ? `Lv.${level} ${title}` : `Lv.${level}`">
    <span class="level-badge__level">Lv.{{ level }}</span>
    <span v-if="showTitle && title" class="level-badge__title">{{ title }}</span>
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

<style scoped>
.level-badge {
  gap: 5px;
  padding-inline: 8px;
  border: 1px solid color-mix(in srgb, currentColor 16%, transparent);
  letter-spacing: 0.01em;
}

.level-badge__level {
  font-variant-numeric: tabular-nums;
  font-weight: 800;
}

.level-badge__title {
  padding-left: 5px;
  border-left: 1px solid color-mix(in srgb, currentColor 26%, transparent);
  font-size: 0.92em;
  font-weight: 600;
  opacity: 0.85;
}
</style>
