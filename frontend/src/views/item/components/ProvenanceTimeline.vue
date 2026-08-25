<template>
  <section class="lineage-section" aria-labelledby="lineage-title">
    <div class="lineage-section__head">
      <span class="lineage-section__icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
          <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
          <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z" />
          <path d="M9 7h7M9 11h5" />
        </svg>
      </span>
      <div>
        <h2 id="lineage-title">教材传承时间轴</h2>
      </div>
    </div>

    <el-skeleton v-if="loading" :rows="3" animated />
    <ol v-else-if="chain?.length" class="lineage-timeline">
      <li v-for="(node, index) in chain" :key="`${node.userId}-${node.time}-${index}`">
        <span class="lineage-timeline__dot">{{ index + 1 }}</span>
        <div class="lineage-timeline__content">
          <div>
            <strong>{{ node.nickname || '校园同学' }}</strong>
            <span>{{ node.role === 'PUBLISHER' ? '最初发布' : '完成接力' }}</span>
          </div>
          <small>
            {{ formatDate(node.time) }}
            <template v-if="node.price != null">· 成交 ¥{{ Number(node.price).toFixed(2) }}</template>
          </small>
        </div>
      </li>
    </ol>
    <p v-else class="lineage-section__empty">这本教材刚刚开始它的校园旅程。</p>
  </section>
</template>

<script setup lang="ts">
import type { LineageNode } from '@/types/models'
import { formatDate } from '@/utils/format'

/** 教材传承链时间轴：纯展示，链数据与加载态由详情页传入 */
defineProps<{
  chain?: LineageNode[] | null
  loading?: boolean
}>()
</script>

<style scoped>
.lineage-section {
  margin-top: 28px;
  padding: 22px 24px 26px;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-m);
  background: var(--white);
  box-shadow: var(--shadow-m);
}

.lineage-section__head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 18px;
  border-bottom: var(--bw) solid var(--line);
}

.lineage-section__icon {
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
  display: grid;
  place-items: center;
  border-radius: var(--r-m);
  background: var(--paper-deep);
  color: var(--ink-soft);
  box-shadow: var(--shadow-s);
}

.lineage-section__icon svg {
  width: 27px;
  height: 27px;
}
.lineage-section__head h2 {
  font-size: 17px;
  font-weight: 700;
}

.lineage-timeline {
  display: flex;
  margin-top: 22px;
  padding: 0;
  list-style: none;
  overflow-x: auto;
}

.lineage-timeline li {
  position: relative;
  min-width: 190px;
  flex: 1 0 190px;
  padding-right: 22px;
}

.lineage-timeline li:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 17px;
  left: 34px;
  right: 0;
  border-top: var(--bw) solid var(--line);
}

.lineage-timeline__dot {
  position: relative;
  z-index: 1;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: var(--bw) solid var(--line);
  border-radius: 50%;
  background: var(--yellow);
  box-shadow: var(--shadow-s);
  font-family: var(--font-display);
}

.lineage-timeline__content {
  margin-top: 12px;
}
.lineage-timeline__content > div {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}
.lineage-timeline__content strong {
  font-size: 14px;
}
.lineage-timeline__content span {
  padding: 2px 6px;
  border: var(--bw) solid var(--line);
  border-radius: 5px;
  background: var(--paper-deep);
  font-size: 10px;
  font-weight: 800;
}
.lineage-timeline__content small {
  display: block;
  margin-top: 5px;
  color: var(--ink-soft);
  font-size: 11px;
}
.lineage-section__empty {
  margin: 22px 0 2px;
  color: var(--ink-soft);
  font-size: 13px;
}
</style>
