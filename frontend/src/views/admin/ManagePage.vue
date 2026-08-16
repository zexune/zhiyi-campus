<template>
  <AdminLayout>
    <div class="manage-page rise">
      <div class="page-title">
        🔧 内容管理
        <span class="stamp">Admin</span>
      </div>

      <div class="tool-grid">
        <ForceOffShelfCard />
        <ResetPasswordCard />
        <UserBanCard />
        <EventTopicCard />
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import AdminLayout from '@/components/layout/AdminLayout.vue'
import EventTopicCard from './manage/EventTopicCard.vue'
import ForceOffShelfCard from './manage/ForceOffShelfCard.vue'
import ResetPasswordCard from './manage/ResetPasswordCard.vue'
import UserBanCard from './manage/UserBanCard.vue'

/**
 * 内容管理页 —— 四个运营工具卡（下架/重置密码/封禁/专题）的编排层，
 * 各卡片自治（搜索、选择、提交与结果反馈），互不共享状态。
 */
</script>

<style scoped>
.manage-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.tool-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}
@media (max-width: 768px) {
  .tool-grid {
    grid-template-columns: 1fr;
  }
}
</style>
