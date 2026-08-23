<template>
  <div class="tag-input" role="group" :aria-label="ariaLabel">
    <div v-if="modelValue.length" class="tag-input__selected">
      <span v-for="tag in modelValue" :key="tag" class="tag-input__chip">
        {{ tag }}
        <button type="button" class="tag-input__remove" :aria-label="`移除标签 ${tag}`" @click="removeTag(tag)">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="m6 6 12 12M18 6 6 18" /></svg>
        </button>
      </span>
    </div>

    <input
      v-model.trim="draft"
      class="tag-input__field"
      type="text"
      :maxlength="maxLength"
      :placeholder="atMax ? `最多 ${max} 个标签` : placeholder"
      :disabled="atMax"
      :aria-label="`添加标签，最多 ${max} 个`"
      @keydown.enter.prevent="commitDraft"
      @keydown.,="commitDraft"
      @keydown.backspace="removeLastOnEmpty"
      @blur="commitDraft"
    />

    <div v-if="availableSuggestions.length && !atMax" class="tag-input__suggestions">
      <span class="tag-input__suggestions-label">可选：</span>
      <button v-for="suggestion in availableSuggestions" :key="suggestion" type="button" class="tag-input__suggestion" :title="`添加标签 ${suggestion}`" @click="addTag(suggestion)">
        {{ suggestion }}
      </button>
    </div>
    <p v-if="atMax" class="tag-input__hint">标签已达数量上限</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

/**
 * 标签输入组件 —— 发布页与专题配置共用。
 * 支持回车/逗号/失焦提交自定义标签、点击建议一键选用、删除与上限控制。
 * 提交前做 trim 与去重（忽略大小写），超长输入由 maxlength 截断。
 */
const props = withDefaults(
  defineProps<{
    modelValue: string[]
    suggestions?: string[]
    max?: number
    maxLength?: number
    placeholder?: string
    ariaLabel?: string
  }>(),
  { suggestions: () => [], max: 6, maxLength: 12, placeholder: '输入后回车添加', ariaLabel: '商品标签' }
)

const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()

const draft = ref('')

const atMax = computed(() => props.modelValue.length >= props.max)

/** 尚未被选用的建议（已选的不重复展示） */
const availableSuggestions = computed(() => props.suggestions.filter((s) => !props.modelValue.some((t) => t.toLowerCase() === s.toLowerCase())))

function emitValue(value: string[]): void {
  emit('update:modelValue', value)
}

function addTag(raw: string): void {
  const tag = raw.trim()
  if (!tag || tag.length < 2 || tag.length > props.maxLength || atMax.value) return
  const exists = props.modelValue.some((t) => t.toLowerCase() === tag.toLowerCase())
  if (exists) return
  emitValue([...props.modelValue, tag])
}

function commitDraft(): void {
  if (!draft.value) return
  addTag(draft.value)
  draft.value = ''
}

function removeTag(tag: string): void {
  emitValue(props.modelValue.filter((t) => t !== tag))
}

function removeLastOnEmpty(): void {
  if (!draft.value && props.modelValue.length) {
    emitValue(props.modelValue.slice(0, -1))
  }
}
</script>

<style scoped>
.tag-input {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.tag-input__selected {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tag-input__chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 4px 2px 10px;
  border-radius: 999px;
  background: var(--primary-bg);
  color: var(--primary-deep);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.7;
}
.tag-input__remove {
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: inherit;
  cursor: pointer;
  transition: background-color 0.15s;
}
.tag-input__remove:hover {
  background: rgba(154, 52, 18, 0.15);
}
.tag-input__remove svg {
  width: 11px;
  height: 11px;
}
.tag-input__field {
  width: 100%;
  padding: 9px 12px;
  font-family: inherit;
  font-size: 14px;
  color: var(--ink);
  background: var(--white);
  border: var(--bw) solid var(--line-strong);
  border-radius: var(--r-s);
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}
.tag-input__field:hover:not(:disabled) {
  border-color: var(--ink-soft);
}
.tag-input__field:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-bg);
}
.tag-input__field:disabled {
  background: var(--paper-deep);
  color: var(--ink-faint);
  cursor: not-allowed;
}
.tag-input__suggestions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.tag-input__suggestions-label {
  font-size: 12px;
  color: var(--ink-soft);
}
.tag-input__suggestion {
  padding: 3px 11px;
  border: var(--bw) solid var(--line);
  border-radius: 999px;
  background: var(--white);
  color: var(--ink-soft);
  font-size: 12.5px;
  cursor: pointer;
  transition:
    color 0.15s,
    background-color 0.15s,
    border-color 0.15s;
}
.tag-input__suggestion:hover {
  background: var(--primary-bg);
  border-color: var(--primary-bg);
  color: var(--primary-deep);
}
.tag-input__hint {
  margin: 0;
  font-size: 12px;
  color: var(--ink-faint);
}
</style>
