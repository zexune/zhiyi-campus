<template>
  <div class="tool-card card topic-card">
    <h3 class="tool-card__title">大事件专题</h3>
    <div class="field">
      <label>专题名称</label>
      <input v-model.trim="form.title" class="input" maxlength="100" placeholder="如：毕业季闲置循环" />
    </div>
    <div class="form-pair">
      <div class="field">
        <label>开始时间</label>
        <AppDateTimePicker v-model="form.startTime" placeholder="选择专题开始时间" aria-label="专题开始时间" />
      </div>
      <div class="field">
        <label>结束时间</label>
        <AppDateTimePicker v-model="form.endTime" :min="form.startTime" placeholder="选择专题结束时间" aria-label="专题结束时间" />
      </div>
    </div>
    <div class="form-pair">
      <div class="field">
        <label>商品类型</label>
        <AppSelect v-model="form.filterType" :options="TOPIC_TYPE_OPTIONS" placeholder="全部类型" />
      </div>
      <div class="field">
        <label>商品分类</label>
        <AppSelect v-model="form.filterCategoryId" :options="topicCategoryOptions" placeholder="全部分类" />
      </div>
    </div>
    <div class="field">
      <label>商品标签（可选，零到六个）</label>
      <TagInput v-model="form.filterTags" :suggestions="tagSuggestions" aria-label="专题筛选标签" placeholder="输入后回车添加，如：毕业季" />
    </div>
    <div class="field">
      <label>Banner 文案</label>
      <textarea v-model.trim="form.bannerText" class="textarea" maxlength="255" placeholder="展示给用户的专题文案"></textarea>
    </div>
    <label class="topic-enabled">
      <input v-model="form.enabled" type="checkbox" />
      启用专题
    </label>
    <div class="tool-card__actions">
      <button class="btn btn--sm btn--primary" :disabled="form.submitting" @click="saveTopic">{{ form.id ? '保存修改' : '创建专题' }}</button>
      <button v-if="form.id" class="btn btn--sm" @click="resetTopicForm">取消编辑</button>
    </div>
    <div class="topic-list">
      <div v-for="topic in topics" :key="topic.id" class="topic-row card card--flat">
        <div>
          <strong>{{ topic.title }}</strong>
          <div class="muted topic-time">{{ formatDateTime(topic.startTime) }} — {{ formatDateTime(topic.endTime) }}</div>
        </div>
        <div class="topic-actions">
          <span class="badge" :class="topic.enabled ? 'badge--ok' : 'badge--muted'">{{ topic.enabled ? '启用' : '停用' }}</span>
          <button class="btn btn--sm" @click="editTopic(topic)">编辑</button>
          <button class="btn btn--sm btn--danger" @click="removeTopic(topic)">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import AppDateTimePicker from '@/components/common/AppDateTimePicker.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import TagInput from '@/components/common/TagInput.vue'
import { createEventTopic, deleteEventTopic, getAdminItemTagSuggestions, getEventTopics, updateEventTopic } from '@/api/admin'
import type { EventTopicPayload } from '@/api/admin'
import { getCategories } from '@/api/item'
import type { Category, EventTopic } from '@/types/models'
import { ITEM_TYPE_OPTIONS } from '@/constants/domain'
import { formatDateTime } from '@/utils/format'

const TOPIC_TYPE_OPTIONS = ITEM_TYPE_OPTIONS
const topicCategories = ref<Category[]>([])
const topicCategoryOptions = computed(() => [{ label: '全部分类', value: '' }, ...topicCategories.value.map((c) => ({ label: c.name, value: c.id }))])
const topics = ref<EventTopic[]>([])

interface TopicFormState {
  id: number | null
  title: string
  startTime: string
  endTime: string
  filterType: string
  /** '' 表示未选分类（提交时转 null） */
  filterCategoryId: number | ''
  filterTags: string[]
  bannerText: string
  enabled: boolean
  submitting: boolean
}

const emptyTopic = (): TopicFormState => ({ id: null, title: '', startTime: '', endTime: '', filterType: '', filterCategoryId: '', filterTags: [], bannerText: '', enabled: true, submitting: false })
const form = reactive(emptyTopic())

/** 按专题名称生成候选标签（防抖），供管理员点选或无视后自定义 */
const tagSuggestions = ref<string[]>([])
let suggestTimer: number | undefined
watch(
  () => form.title,
  (title) => {
    window.clearTimeout(suggestTimer)
    const keyword = title.trim()
    if (keyword.length < 2) {
      tagSuggestions.value = []
      return
    }
    suggestTimer = window.setTimeout(async () => {
      try {
        const res = await getAdminItemTagSuggestions(keyword)
        tagSuggestions.value = res.data || []
      } catch {
        // 建议失败不影响手动输入
        tagSuggestions.value = []
      }
    }, 400)
  }
)

function resetTopicForm() {
  Object.assign(form, emptyTopic())
}
function editTopic(topic: EventTopic) {
  Object.assign(form, {
    ...topic,
    startTime: topic.startTime?.slice(0, 16) || '',
    endTime: topic.endTime?.slice(0, 16) || '',
    filterCategoryId: topic.filterCategoryId || '',
    filterTags: [...(topic.filterTags ?? [])],
    submitting: false
  })
}
async function loadTopics() {
  const res = await getEventTopics()
  topics.value = res.data || []
}
async function saveTopic() {
  if (!form.title || !form.startTime || !form.endTime || !form.bannerText) {
    ElMessage.warning('请填写专题名称、时间段和 Banner 文案')
    return
  }
  if (new Date(form.endTime) <= new Date(form.startTime)) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }
  form.submitting = true
  // 与表单状态同步的提交载荷（不含 id / submitting 等本地字段）
  const data: EventTopicPayload = {
    title: form.title,
    startTime: form.startTime,
    endTime: form.endTime,
    filterType: form.filterType || null,
    filterCategoryId: form.filterCategoryId || null,
    filterTags: form.filterTags.length ? form.filterTags : null,
    bannerText: form.bannerText,
    enabled: form.enabled
  }
  try {
    if (form.id) await updateEventTopic(form.id, data)
    else await createEventTopic(data)
    ElMessage.success(form.id ? '专题已更新' : '专题已创建')
    resetTopicForm()
    await loadTopics()
  } finally {
    form.submitting = false
  }
}
async function removeTopic(topic: EventTopic) {
  if (topic.id == null) return
  try {
    await ElMessageBox.confirm(`确认删除专题「${topic.title}」？`, '删除专题', { type: 'warning' })
  } catch {
    return
  }
  await deleteEventTopic(topic.id)
  ElMessage.success('专题已删除')
  await loadTopics()
}

onMounted(async () => {
  const [, categories] = await Promise.all([loadTopics(), getCategories()])
  topicCategories.value = categories.data || []
})
</script>

<style scoped>
.tool-card {
  padding: 24px;
}
.tool-card__title {
  font-family: var(--font-display);
  font-size: 20px;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}
.tool-card__actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  flex-wrap: wrap;
}
.field {
  margin-bottom: 14px;
}
.field > label {
  display: block;
  font-weight: 700;
  font-size: 13px;
  margin-bottom: 6px;
}
.form-pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.topic-enabled {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-weight: 700;
}
.topic-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 20px;
}
.topic-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 12px 14px;
}
.topic-time {
  font-size: 12px;
  margin-top: 3px;
}
.topic-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
@media (max-width: 768px) {
  .topic-row {
    align-items: flex-start;
    flex-direction: column;
  }
  .form-pair {
    grid-template-columns: 1fr;
  }
}
</style>
