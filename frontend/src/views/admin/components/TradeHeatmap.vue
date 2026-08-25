<template>
  <div v-if="entries.length === 0" class="card card--flat state-card">
    <span class="muted">暂无交易地点数据</span>
  </div>
  <div v-else class="heatmap-grid card">
    <div v-for="(h, i) in entries" :key="i" class="heatmap-bar-row">
      <span class="heatmap-loc">{{ h.location }}</span>
      <div class="heatmap-bar-wrap">
        <div class="heatmap-bar" :style="{ width: heatmapWidth(h.count) + '%' }" :class="heatColor(i)"></div>
      </div>
      <span class="heatmap-count">{{ h.count }} 笔</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TradeHeatEntry } from '@/types/models'

/** 交易地点热力图（D5）：条目、宽度比例与配色自包含 */
const props = defineProps<{
  entries: TradeHeatEntry[]
}>()

const heatmapMax = computed(() => Math.max(1, ...props.entries.map((h) => h.count)))

function heatmapWidth(count: number) {
  return Math.round((count / heatmapMax.value) * 100)
}

const HEAT_COLORS = ['heat--1', 'heat--2', 'heat--3', 'heat--4', 'heat--5']
function heatColor(i: number) {
  return HEAT_COLORS[i % HEAT_COLORS.length]
}
</script>

<style scoped>
.state-card {
  padding: 28px 24px;
  text-align: center;
}
.heatmap-grid {
  padding: 20px 24px;
}
.heatmap-bar-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}
.heatmap-bar-row:not(:last-child) {
  border-bottom: var(--bw) solid var(--line);
}
.heatmap-loc {
  width: 120px;
  flex-shrink: 0;
  font-weight: 700;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.heatmap-bar-wrap {
  flex: 1;
  height: 22px;
  background: var(--paper-deep);
  border-radius: 4px;
  overflow: hidden;
}
.heatmap-bar {
  height: 100%;
  border-radius: 4px;
  min-width: 4px;
  transition: width 0.4s ease;
}
.heatmap-count {
  width: 50px;
  flex-shrink: 0;
  text-align: right;
  font-size: 13px;
  font-weight: 700;
  font-family: var(--font-display);
}
.heat--1 {
  background: var(--primary);
}
.heat--2 {
  background: #e8852e;
}
.heat--3 {
  background: var(--yellow);
}
.heat--4 {
  background: var(--green);
}
.heat--5 {
  background: var(--blue);
}
</style>
