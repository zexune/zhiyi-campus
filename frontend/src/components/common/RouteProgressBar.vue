<template>
  <Transition name="route-progress-fade">
    <div v-if="visible" class="route-progress" aria-hidden="true">
      <div class="route-progress__bar" :style="{ width: `${progress}%` }"></div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import router from '@/router'

/**
 * 路由切换进度条 —— 路由全量懒加载，chunk 下载期间界面无任何反馈时，
 * 慢网用户会重复点击导航（router.onError 的 chunk 重试兜底正说明此问题真实存在）。
 * 挂在根组件上自行注册 beforeEach / afterEach / onError：导航开始 200ms 内
 * 完成（缓存命中）不显示，避免每次切换闪条。
 */
const visible = ref(false)
const progress = ref(0)

let showTimer: number | undefined
let creepTimer: number | undefined
let hideTimer: number | undefined

function begin(): void {
  window.clearTimeout(showTimer)
  window.clearTimeout(hideTimer)
  showTimer = window.setTimeout(() => {
    progress.value = 30
    visible.value = true
    // 蠕动到 80% 封顶：真正的完成时机由 afterEach 决定
    window.clearInterval(creepTimer)
    creepTimer = window.setInterval(() => {
      if (progress.value < 80) progress.value = Math.min(80, progress.value + 4)
    }, 200)
  }, 200)
}

function end(): void {
  window.clearTimeout(showTimer)
  window.clearInterval(creepTimer)
  if (!visible.value) return
  progress.value = 100
  hideTimer = window.setTimeout(() => {
    visible.value = false
    progress.value = 0
  }, 300)
}

const offBeforeEach = router.beforeEach(() => {
  begin()
})
const offAfterEach = router.afterEach(() => {
  end()
})
const offOnError = router.onError(() => {
  end()
})

onBeforeUnmount(() => {
  offBeforeEach()
  offAfterEach()
  offOnError()
  window.clearTimeout(showTimer)
  window.clearInterval(creepTimer)
  window.clearTimeout(hideTimer)
})
</script>

<style scoped>
.route-progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: var(--z-progress);
  height: 3px;
  pointer-events: none;
}
.route-progress__bar {
  height: 100%;
  background: var(--primary);
  box-shadow: 0 1px 4px rgba(194, 65, 12, 0.4);
  transition: width 0.2s ease;
}
.route-progress-fade-leave-active {
  transition: opacity 0.25s ease;
}
.route-progress-fade-leave-to {
  opacity: 0;
}
</style>
