<template>
  <AdminLayout>
    <div class="manage-page rise">
      <div class="page-title">
        🔧 内容管理
        <span class="stamp">Admin</span>
      </div>

      <div class="nav-tabs">
        <router-link :to="ROUTE_PATH.ADMIN_DASHBOARD" class="nav-tab">📊 数据大盘</router-link>
        <router-link :to="ROUTE_PATH.ADMIN_VIOLATIONS" class="nav-tab">⚖️ 内容治理</router-link>
        <router-link :to="ROUTE_PATH.ADMIN_CHAT" class="nav-tab">💬 客服收件箱</router-link>
        <span class="nav-tab active">🔧 内容管理</span>
        <router-link :to="ROUTE_PATH.ADMIN_SCHOOLS" class="nav-tab">🏫 学校管理</router-link>
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
import { ROUTE_PATH } from '@/constants/routes'

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

.nav-tabs {
  display: flex;
  gap: 4px;
  margin: 18px 0 28px;
  flex-wrap: wrap;
}
.nav-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  font-size: 15px;
  font-weight: 700;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  background: var(--paper-deep);
  color: var(--ink);
  cursor: pointer;
  text-decoration: none;
  transition: all 0.2s;
}
.nav-tab:hover {
  background: var(--white);
  box-shadow: var(--shadow-s);
}
.nav-tab.active {
  background: var(--ink);
  color: var(--paper);
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
