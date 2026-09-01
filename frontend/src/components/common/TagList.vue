<template>
  <ul v-if="visibleTags.length" class="tag-list" :class="{ 'tag-list--compact': compact }" aria-label="商品标签">
    <li v-for="tag in visibleTags" :key="tag" class="tag-list__entry">
      <!-- stop+prevent：标签按钮常嵌在 router-link 卡片内，仅 stop 挡不住浏览器对祖先锚点的默认导航 -->
      <button type="button" class="tag-list__item" :title="`搜索标签：${tag}`" @click.stop.prevent="emit('select', tag)">#{{ tag }}</button>
    </li>
  </ul>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps({
  tags: { type: Array, default: () => [] },
  limit: { type: Number, default: 3 },
  compact: { type: Boolean, default: false }
})
const emit = defineEmits(['select'])

const visibleTags = computed(() => {
  const source = Array.isArray(props.tags) ? props.tags : []
  return [...new Set(source.map((tag) => String(tag || '').trim()).filter(Boolean))].slice(0, Math.max(1, props.limit))
})
</script>

<style scoped>
/* ul/li：读屏按列表播报条目数；此前是无角色 div，aria-label 会被直接丢弃 */
.tag-list {
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
}
.tag-list__entry {
  min-width: 0;
  max-width: 100%;
  display: flex;
}
.tag-list__item {
  max-width: 100%;
  min-width: 0;
  padding: 2px 8px;
  overflow-wrap: anywhere;
  border: var(--bw) solid var(--line);
  border-radius: 999px;
  background: var(--paper-deep);
  color: var(--ink-soft);
  font-size: 11px;
  font-weight: 700;
  line-height: 1.45;
  text-align: left;
  white-space: normal;
  cursor: pointer;
  transition:
    background-color 0.15s,
    border-color 0.15s,
    color 0.15s;
}
/* 不覆盖全局焦点环：1px 边框变色不足以标识焦点位置（WCAG 2.4.7） */
.tag-list__item:hover,
.tag-list__item:focus-visible {
  border-color: var(--ink);
  background: var(--yellow);
  color: var(--ink);
}
.tag-list--compact {
  gap: 4px;
  margin-top: 4px;
}
.tag-list--compact .tag-list__item {
  padding: 1px 7px;
  font-size: 10.5px;
}
</style>
