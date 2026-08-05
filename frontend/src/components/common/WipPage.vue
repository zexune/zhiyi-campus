<template>
  <DefaultLayout>
    <div class="wip card">
      <span class="badge badge--warn">建设中</span>
      <h2>{{ title }}</h2>
      <p class="muted">该页面由成员 {{ owner }} 负责开发，敬请期待</p>
      <router-link to="/" class="btn btn--primary btn--sm">回到大厅</router-link>
    </div>
  </DefaultLayout>
</template>

<script setup>
import DefaultLayout from '@/components/layout/DefaultLayout.vue'

/**
 * 占位页 —— 非 A 负责的页面统一用它占位，保证路由完整、应用可运行。
 * 各成员实现自己的页面后直接替换路由指向即可。
 */
defineProps({
  title: { type: String, default: '页面建设中' },
  owner: { type: String, default: 'B/C/D' },
})
</script>

<style scoped>
.wip {
  max-width: 520px;
  margin: 60px auto;
  padding: 48px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-items: center;
}
.wip h2 { font-family: var(--font-display); font-size: 26px; letter-spacing: 1px; }
</style>
