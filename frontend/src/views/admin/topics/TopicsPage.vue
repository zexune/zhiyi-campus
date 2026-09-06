<template>
  <AdminLayout>
    <div class="topics-page rise">
      <header class="topics-head">
        <div>
          <h1 class="page-title">事件专题</h1>
          <p>把校园节奏转成清晰的主题入口，让阶段性需求被更快看见。</p>
        </div>
        <span class="topics-head__badge" aria-hidden="true">CAMPUS / 2026</span>
      </header>
      <EventTopicCard />
    </div>
  </AdminLayout>
</template>

<script setup lang="ts">
import AdminLayout from '@/components/layout/AdminLayout.vue'
import EventTopicCard from './EventTopicCard.vue'

/**
 * 事件专题页 —— 大事件专题的创建、编辑、启停与删除。
 * 专题卡片自治（表单校验、提交与结果反馈），本页只负责布局编排。
 */
</script>

<style scoped>
.topics-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px;
}
.topics-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
}
.topics-head .page-title {
  margin-bottom: 6px;
}
.topics-head p {
  color: var(--ink-soft);
  font-size: 13.5px;
}
.topics-head__badge {
  padding: 7px 10px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--white);
  color: var(--primary);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  white-space: nowrap;
}
@media (max-width: 620px) {
  .topics-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }
  .topics-page {
    padding-inline: 0;
  }
}
</style>
