<template>
  <el-config-provider :locale="zhCn">
    <router-view />
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
