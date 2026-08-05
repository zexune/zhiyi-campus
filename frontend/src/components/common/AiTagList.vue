<template>
  <div v-if="visibleTags.length" class="ai-tag-list" :class="{ 'ai-tag-list--compact': compact }" aria-label="AI 动态标签">
    <button
      v-for="tag in visibleTags"
      :key="tag"
      type="button"
      class="ai-tag"
      :title="`搜索标签：${tag}`"
      @click.stop="emit('select', tag)"
    >#{{ tag }}</button>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  tags: { type: Array, default: () => [] },
  limit: { type: Number, default: 3 },
  compact: { type: Boolean, default: false },
})
const emit = defineEmits(['select'])

const visibleTags = computed(() => {
  const source = Array.isArray(props.tags) ? props.tags : []
  return [...new Set(
    source.map(tag => String(tag || '').trim()).filter(Boolean),
  )].slice(0, Math.max(1, props.limit))
})
</script>

<style scoped>
.ai-tag-list {
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
}

.ai-tag {
  max-width: 100%;
  min-width: 0;
  padding: 2px 8px;
  overflow-wrap: anywhere;
  border: 1.5px dashed var(--ink-soft);
  border-radius: 999px;
  background: var(--paper-deep);
  color: var(--ink-soft);
  font-size: 11px;
  font-weight: 700;
  line-height: 1.45;
  text-align: left;
  white-space: normal;
  cursor: pointer;
  transition: background-color .15s, border-color .15s, color .15s;
}

.ai-tag:hover,
.ai-tag:focus-visible {
  outline: none;
  border-style: solid;
  border-color: var(--ink);
  background: var(--yellow);
  color: var(--ink);
}

.ai-tag-list--compact { gap: 4px; margin-top: 4px; }
.ai-tag-list--compact .ai-tag { padding: 1px 7px; font-size: 10.5px; }
</style>
