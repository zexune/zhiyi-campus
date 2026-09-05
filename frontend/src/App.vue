<template>
  <el-config-provider :locale="zhCn">
    <!-- 页面切换过渡：懒加载 chunk 就绪后内容硬切会显得"闪现"，
         用 180ms 淡入 + 轻上移软化；mode="out-in" 让旧页先让位避免重叠抖动。
         出场刻意极短（0.05s）不拖慢导航；prefers-reduced-motion 下全局规则会把
         时长压到 0.01ms，动画自动退化为直切 -->
    <router-view v-slot="{ Component }">
      <transition name="page" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
    <!-- 路由切换进度条：懒加载 chunk 下载期间的可视反馈 -->
    <RouteProgressBar />
    <!-- 读屏路由播报区：afterEach 写入标题，让用户感知 SPA 已切换页面 -->
    <div class="visually-hidden" role="status" aria-live="polite">{{ routeAnnouncement }}</div>
  </el-config-provider>
</template>

<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn.mjs'
import RouteProgressBar from '@/components/common/RouteProgressBar.vue'
import { routeAnnouncement } from '@/utils/routeAnnouncer'
</script>

<style scoped>
.page-enter-active {
  transition:
    opacity 0.18s ease,
    translate 0.18s cubic-bezier(0.2, 0.7, 0.3, 1);
}
.page-leave-active {
  transition: opacity 0.05s ease;
}
.page-enter-from {
  opacity: 0;
  translate: 0 8px;
}
.page-leave-to {
  opacity: 0;
}
</style>
