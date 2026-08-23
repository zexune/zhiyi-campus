<template>
  <AdminLayout>
    <div class="topics-page rise">
      <div class="page-title">事件专题</div>
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
</style>
