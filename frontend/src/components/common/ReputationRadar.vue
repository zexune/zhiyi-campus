<template>
  <div class="radar">
    <svg class="radar__svg" :viewBox="`0 0 ${size} ${size}`" role="img" :aria-label="`信誉雷达图，综合分 ${overall} 分`">
      <!-- 背景同心网格（4 圈） -->
      <polygon v-for="ring in rings" :key="ring.k" :points="ring.points" class="radar__grid" />
      <!-- 轴线 -->
      <line v-for="(axis, i) in axes" :key="`ax-${i}`" :x1="center" :y1="center" :x2="axis.x" :y2="axis.y" class="radar__axis" />
      <!-- 数据多边形 -->
      <polygon :points="dataPolygon" class="radar__area" />
      <!-- 数据顶点 -->
      <circle v-for="(pt, i) in dataPoints" :key="`pt-${i}`" :cx="pt.x" :cy="pt.y" r="3.5" class="radar__dot" />
      <!-- 维度标签 -->
      <text v-for="(lbl, i) in labels" :key="`lb-${i}`" :x="lbl.x" :y="lbl.y" :text-anchor="lbl.anchor" class="radar__label">{{ lbl.text }}</text>
    </svg>

    <div class="radar__legend">
      <div class="radar__overall">
        <b>{{ overall }}</b>
        <span>综合信誉分</span>
      </div>
      <span class="badge radar__grade" :class="gradeClass">{{ grade }}</span>
      <span class="radar__count muted">{{ reviewCount }} 条评价</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { REPUTATION_DIMENSIONS, radarPoint, radarPolygon, reputationValues, overallScore, reputationGrade } from '@/utils/reputation.js'

/**
 * 信誉雷达图（A6）—— 六维纯 SVG 手绘风，不引入图表库，几何逻辑复用 utils/reputation.js
 */
const props = defineProps({
  reputation: { type: Object, default: () => ({}) },
  size: { type: Number, default: 260 }
})

const size = props.size
const center = computed(() => size / 2)
const radius = computed(() => size / 2 - 34) // 留出标签边距
const geom = computed(() => ({ radius: radius.value, cx: center.value, cy: center.value }))

const values = computed(() => reputationValues(props.reputation))
const overall = computed(() => overallScore(props.reputation))
const grade = computed(() => reputationGrade(overall.value))
const reviewCount = computed(() => Number(props.reputation?.reviewCount) || 0)

const gradeClass = computed(() => {
  if (overall.value >= 90) return 'badge--ok'
  if (overall.value >= 75) return 'badge--warn'
  if (overall.value >= 50) return 'badge--muted'
  return 'badge--danger'
})

// 4 圈网格：100/75/50/25
const rings = computed(() =>
  [100, 75, 50, 25].map((k) => ({
    k,
    points: radarPolygon(
      REPUTATION_DIMENSIONS.map(() => k),
      geom.value
    )
  }))
)

const axes = computed(() => REPUTATION_DIMENSIONS.map((_, i) => radarPoint(100, i, geom.value)))

const dataPolygon = computed(() => radarPolygon(values.value, geom.value))
const dataPoints = computed(() => values.value.map((v, i) => radarPoint(v, i, geom.value)))

const labels = computed(() =>
  REPUTATION_DIMENSIONS.map((d, i) => {
    const p = radarPoint(112, i, geom.value) // 略超出满圈放标签
    let anchor = 'middle'
    if (p.x < center.value - 4) anchor = 'end'
    else if (p.x > center.value + 4) anchor = 'start'
    return { text: d.label, x: p.x, y: p.y + 4, anchor }
  })
)
</script>

<style scoped>
.radar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.radar__svg {
  width: 100%;
  max-width: 300px;
  height: auto;
  overflow: visible;
}

.radar__grid {
  fill: none;
  stroke: var(--ink-soft);
  stroke-width: 1;
  opacity: 0.35;
}
.radar__axis {
  stroke: var(--ink-soft);
  stroke-width: 1;
  opacity: 0.4;
}
.radar__area {
  fill: var(--primary);
  fill-opacity: 0.18;
  stroke: var(--primary);
  stroke-width: 2.5;
  stroke-linejoin: round;
}
.radar__dot {
  fill: var(--white);
  stroke: var(--primary);
  stroke-width: 2;
}
.radar__label {
  font-family: var(--font-body);
  font-size: 12px;
  font-weight: 700;
  fill: var(--ink);
}

.radar__legend {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
}
.radar__overall {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.radar__overall b {
  font-family: var(--font-display);
  font-size: 30px;
  color: var(--primary);
  line-height: 1;
}
.radar__overall span {
  font-size: 13px;
  color: var(--ink-soft);
}
.radar__grade {
  font-size: 12px;
}
.radar__count {
  font-size: 12.5px;
}
</style>
