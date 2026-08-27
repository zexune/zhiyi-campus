<template>
  <span class="avatar" :class="[`avatar--${size}`, `avatar--${color}`]" :title="nickname">
    <template v-if="showImage">
      <!-- src 非空且未加载失败：圆形图片填充容器；加载失败回退文字头像 -->
      <img class="avatar__img" :src="src || ''" :alt="nickname" @error="onError" />
    </template>
    <template v-else>
      {{ initial }}
    </template>
  </span>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

/**
 * 头像 —— demo 设计系统 .avatar（昵称首字 + 按 id 取色）。
 *
 * 可选 src：非空且加载成功时渲染圆形图片（object-fit: cover）；
 * 为空或加载失败（onerror）回退到文字头像。src 变化（如上传新头像）时重置失败标记，
 * 使失败后可重试、换头像后立即恢复图片态。既有调用点零改动兼容（src 缺省即纯文字头像）。
 */
const props = withDefaults(
  defineProps<{
    nickname?: string
    userId?: number | string
    size?: string // s / m / l
    /** 头像相对路径（如 /uploads/avatars/xxx.png）；null/'' 表示未设置，走文字头像 */
    src?: string | null
  }>(),
  {
    nickname: '?',
    userId: 0,
    size: 's',
    src: null
  }
)

const COLORS = ['orange', 'green', 'blue', 'yellow', 'ink']

const initial = computed(() => (props.nickname || '?').trim().charAt(0).toUpperCase())
const color = computed(() => COLORS[Number(props.userId) % COLORS.length])

// 图片加载失败标记：仅在 src 非空且未失败时展示图片态
const failed = ref(false)
watch(
  () => props.src,
  () => {
    // 换头像/重试：重置失败标记，允许重新加载新 URL
    failed.value = false
  }
)
const showImage = computed(() => !!props.src && !failed.value)

function onError() {
  failed.value = true
}
</script>

<style scoped>
.avatar {
  /* 尺寸/颜色来自 demo 设计系统 .avatar--* 类（s/m/l + 五种取色）；
     这里补充图片态所需的作用域样式，避免污染全局。 */
  position: relative;
  overflow: hidden;
}

.avatar__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
</style>
