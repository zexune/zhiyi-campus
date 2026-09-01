import { ref } from 'vue'

/**
 * SPA 路由播报通道 —— router 守卫写入，App.vue 的视觉隐藏 live region 消费。
 * 独立成模块而非互相 import：router 不能依赖组件实例，组件也不该感知守卫细节。
 */
export const routeAnnouncement = ref('')

/** 写入一条播报；先清空再赋值，保证相同文案也能重新触发 live region */
export function announceRoute(text: string): void {
  if (!text) return
  routeAnnouncement.value = ''
  requestAnimationFrame(() => {
    routeAnnouncement.value = text
  })
}
