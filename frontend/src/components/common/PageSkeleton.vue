<template>
  <!-- 场景化骨架：复刻真实布局的网格与卡片外框，只把内容换成占位块，
       避免 el-skeleton 通用文本行与真实布局严重不符导致的加载完成跳动（CLS） -->
  <div v-if="variant === 'goods'" class="page-skeleton sk-goods" aria-hidden="true">
    <div v-for="n in count" :key="n" class="sk-card">
      <div class="sk-img"></div>
      <div class="sk-body">
        <div class="sk-line sk-line--lg"></div>
        <div class="sk-row">
          <div class="sk-chip"></div>
          <div class="sk-chip"></div>
        </div>
        <div class="sk-line sk-line--price"></div>
        <div class="sk-line sk-line--sm"></div>
      </div>
    </div>
  </div>

  <div v-else-if="variant === 'detail'" class="page-skeleton sk-detail" aria-hidden="true">
    <div class="sk-gallery"></div>
    <div class="sk-info">
      <div class="sk-line sk-line--xl"></div>
      <div class="sk-price-strip"></div>
      <div class="sk-line"></div>
      <div class="sk-line"></div>
      <div class="sk-line sk-line--md"></div>
      <div class="sk-seller"></div>
      <div class="sk-desc">
        <div class="sk-line"></div>
        <div class="sk-line"></div>
        <div class="sk-line sk-line--md"></div>
      </div>
    </div>
  </div>

  <div v-else class="page-skeleton sk-ranking" aria-hidden="true">
    <div class="sk-trending">
      <div v-for="n in 5" :key="n" class="sk-chip sk-chip--tag"></div>
    </div>
    <div class="sk-podium">
      <div v-for="n in 3" :key="n" class="sk-card sk-card--podium">
        <div class="sk-img sk-img--podium"></div>
        <div class="sk-body">
          <div class="sk-line sk-line--lg"></div>
          <div class="sk-line sk-line--price"></div>
        </div>
      </div>
    </div>
    <div class="sk-board">
      <div v-for="n in 5" :key="n" class="sk-row-item">
        <div class="sk-thumb"></div>
        <div class="sk-lines">
          <div class="sk-line sk-line--md"></div>
          <div class="sk-line sk-line--sm"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    /** goods：首页 3 列卡片网格；detail：详情 2 列布局；ranking：热搜 + 领奖台 + 榜单表 */
    variant?: 'goods' | 'detail' | 'ranking'
    /** goods 变体的卡片数（对齐首页一页 12 张） */
    count?: number
  }>(),
  { variant: 'goods', count: 12 }
)
</script>

<style scoped>
.page-skeleton {
  --sk-base: var(--paper-deep);
  --sk-hi: var(--white);
}
.sk-line,
.sk-img,
.sk-chip,
.sk-thumb,
.sk-gallery,
.sk-seller,
.sk-price-strip,
.sk-desc {
  background: linear-gradient(100deg, var(--sk-base) 25%, var(--sk-hi) 40%, var(--sk-base) 55%);
  background-size: 220% 100%;
  animation: sk-shimmer 1.4s linear infinite;
  border-radius: 6px;
}
.sk-line {
  height: 14px;
}
.sk-line--sm {
  width: 45%;
}
.sk-line--md {
  width: 65%;
}
.sk-line--lg {
  width: 85%;
  height: 18px;
}
.sk-line--xl {
  width: 70%;
  height: 26px;
}
.sk-line--price {
  width: 35%;
  height: 22px;
}
.sk-row {
  display: flex;
  gap: 6px;
}
.sk-chip {
  width: 56px;
  height: 18px;
  border-radius: 999px;
}
.sk-chip--tag {
  width: 96px;
  height: 34px;
}

/* —— 首页网格：与 .goods-grid 同构 —— */
.sk-goods {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}
.sk-card {
  overflow: hidden;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-m);
  background: var(--white);
  box-shadow: var(--shadow-s);
}
.sk-img {
  aspect-ratio: 4 / 3;
  border-radius: 0;
}
.sk-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px 16px;
}
.sk-img--podium {
  aspect-ratio: 16 / 10;
}

/* —— 详情：与 .detail 的 460px + 1fr 同构 —— */
.sk-detail {
  display: grid;
  grid-template-columns: 460px 1fr;
  gap: 32px;
  align-items: start;
}
.sk-gallery {
  aspect-ratio: 4 / 3;
  border-radius: var(--r-m);
}
.sk-info {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.sk-price-strip {
  height: 74px;
  border-radius: var(--r-m);
}
.sk-seller {
  height: 92px;
  border-radius: var(--r-m);
}
.sk-desc {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 22px 24px;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-m);
}

/* —— 榜单：热搜条 + 领奖台 + 完整榜单 —— */
.sk-ranking {
  display: flex;
  flex-direction: column;
  gap: 26px;
}
.sk-trending {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  padding: 18px 20px;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-m);
  background: var(--white);
  box-shadow: var(--shadow-m);
}
.sk-podium {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}
.sk-card--podium .sk-body {
  padding-bottom: 22px;
}
.sk-board {
  overflow: hidden;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-m);
  background: var(--white);
  box-shadow: var(--shadow-m);
}
.sk-row-item {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 96px;
  padding: 11px 18px;
}
.sk-row-item + .sk-row-item {
  border-top: var(--bw) solid var(--line);
}
.sk-thumb {
  width: 62px;
  height: 62px;
  flex: 0 0 62px;
  border-radius: var(--r-s);
}
.sk-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

@keyframes sk-shimmer {
  to {
    background-position: -120% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .sk-line,
  .sk-img,
  .sk-chip,
  .sk-thumb,
  .sk-gallery,
  .sk-seller,
  .sk-price-strip,
  .sk-desc {
    animation: none;
  }
}

@media (max-width: 900px) {
  .sk-detail {
    grid-template-columns: 1fr;
  }
  .sk-podium {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 700px) {
  .sk-goods {
    grid-template-columns: repeat(2, 1fr);
    gap: 14px;
  }
}
</style>
