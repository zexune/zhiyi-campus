<template>
  <div class="gallery__main" :class="placeholder">
    <img v-if="activeImage" :src="activeImage" :alt="alt" />
    <!-- 类型徽标等内容由父级经作用域插槽注入（绝对定位于主图左上角） -->
    <slot name="overlay" />
    <button v-if="images.length > 1" class="gallery__nav gallery__nav--prev" aria-label="上一张" @click="switchImage(-1)">‹</button>
    <button v-if="images.length > 1" class="gallery__nav gallery__nav--next" aria-label="下一张" @click="switchImage(1)">›</button>
    <span v-if="images.length" class="gallery__count">{{ activeImageIndex + 1 }} / {{ images.length }}</span>
  </div>
  <div v-if="images.length > 1" class="gallery__thumbs">
    <button v-for="image in images" :key="image" class="th" :class="{ active: image === activeImage }" @click="activeImage = image">
      <img :src="image" :alt="alt" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

/**
 * 商品图集：主图 + 缩略图与切换状态自持。
 * 图片集/封面引用变化（路由切换商品、重新拉取详情）时按「封面优先」重置当前图；
 * 父级须以 computed 传 images，模板内联 `item?.images || []` 会因每次渲染的新数组
 * 引用反复触发本重置、冲掉用户已选图片。
 */
const props = defineProps<{
  images: string[]
  coverImage: string
  alt: string
  /** 主区占位底色类名（placeholderClass(item.id)），由父级计算 */
  placeholder: string
}>()

const activeImage = ref(props.coverImage || props.images[0] || '')

watch(
  () => [props.images, props.coverImage] as const,
  () => {
    activeImage.value = props.coverImage || props.images[0] || ''
  }
)

const activeImageIndex = computed(() => {
  const index = props.images.indexOf(activeImage.value)
  return index >= 0 ? index : 0
})

function switchImage(offset: number): void {
  if (!props.images.length) return
  const nextIndex = (activeImageIndex.value + offset + props.images.length) % props.images.length
  activeImage.value = props.images[nextIndex]
}
</script>

<style scoped>
.gallery__main {
  position: relative;
  aspect-ratio: 1 / 1;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-l);
  box-shadow: var(--shadow-m);
  display: grid;
  place-items: center;
  overflow: hidden;
}

.gallery__main img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.gallery__nav {
  position: absolute;
  top: 50%;
  translate: 0 -50%;
  width: 40px;
  height: 40px;
  border: var(--bw) solid var(--line);
  border-radius: 50%;
  background: var(--white);
  display: grid;
  place-items: center;
  cursor: pointer;
  box-shadow: var(--shadow-s);
  font-size: 26px;
  line-height: 1;
}

.gallery__nav:hover {
  background: var(--paper-deep);
}

.gallery__nav--prev {
  left: 14px;
}

.gallery__nav--next {
  right: 14px;
}

.gallery__count {
  position: absolute;
  bottom: 12px;
  right: 14px;
  padding: 3px 12px;
  background: var(--ink);
  color: var(--paper);
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 700;
}

.gallery__thumbs {
  display: flex;
  gap: 10px;
  margin-top: 14px;
  overflow-x: auto;
}

.th {
  width: 68px;
  height: 68px;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-s);
  overflow: hidden;
  background: var(--paper-deep);
  cursor: pointer;
  opacity: 0.55;
  transition:
    color 0.15s,
    background-color 0.15s,
    border-color 0.15s,
    box-shadow 0.15s,
    transform 0.15s;
}

.th:hover {
  opacity: 0.85;
}

.th.active {
  opacity: 1;
  border-color: var(--primary);
}

.th img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
