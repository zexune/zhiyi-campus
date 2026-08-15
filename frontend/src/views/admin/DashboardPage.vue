<template>
  <AdminLayout>
    <div class="dashboard-page rise">
      <!-- 页面标题 -->
      <div class="page-title">
        📊 管理后台
        <span class="stamp">Admin</span>
      </div>

      <!-- 导航标签 -->
      <div class="nav-tabs">
        <span class="nav-tab active">📊 数据大盘</span>
        <router-link to="/admin/violations" class="nav-tab">⚖️ 内容治理</router-link>
        <router-link to="/admin/chat" class="nav-tab">💬 客服收件箱</router-link>
        <router-link to="/admin/manage" class="nav-tab">🔧 内容管理</router-link>
        <router-link to="/admin/schools" class="nav-tab">🏫 学校管理</router-link>
      </div>

      <!-- 学校切换（D2：多校大盘） -->
      <div v-if="schools.length > 0" class="school-bar">
        <span class="school-bar__label muted">🏫 学校视角：</span>
        <button v-for="s in schoolOptions" :key="s.value" class="school-chip" :class="{ active: selectedSchoolId === s.value }" @click="switchSchool(s.value)">
          {{ s.label }}
        </button>
      </div>

      <!-- 加载 / 错误 -->
      <div v-if="loading" class="card card--flat state-card">
        <span class="muted">加载中...</span>
      </div>
      <div v-else-if="loadError" class="card card--flat state-card">
        <span class="muted">数据加载失败</span>
        <button class="btn btn--sm" style="margin-top: 12px" @click="fetchDashboard">重新加载</button>
      </div>

      <template v-else>
        <!-- 统计卡片 -->
        <div class="stat-grid">
          <div class="stat-card card">
            <div class="stat-card__icon">👥</div>
            <div class="stat-card__num">{{ data.totalUsers }}</div>
            <div class="stat-card__label muted">用户总数</div>
          </div>
          <div class="stat-card card">
            <div class="stat-card__icon">📦</div>
            <div class="stat-card__num">{{ data.onSaleItems }}</div>
            <div class="stat-card__label muted">在售商品</div>
          </div>
          <div class="stat-card card">
            <div class="stat-card__icon">💰</div>
            <div class="stat-card__num">¥{{ data.todayTradeAmount }}</div>
            <div class="stat-card__label muted">今日交易额</div>
          </div>
          <router-link to="/admin/violations" class="stat-card card stat-card--link" :class="{ 'stat-card--alert': data.pendingViolations > 0 }">
            <div class="stat-card__icon">⚠️</div>
            <div class="stat-card__num">{{ data.pendingViolations }}</div>
            <div class="stat-card__label muted">待审核内容</div>
          </router-link>
        </div>

        <!-- 近 7 日交易趋势 -->
        <div class="section">
          <h3 class="section-title">📈 近 7 日交易趋势</h3>
          <div class="trend-card card">
            <div v-if="trendPoints.length > 0" class="trend-svg-wrap">
              <svg :viewBox="`0 0 ${SVG_W} ${SVG_H}`" class="trend-svg">
                <!-- 硬投影滤镜（悬停浮层用） -->
                <defs>
                  <filter id="tip-shadow" x="-10%" y="-10%" width="130%" height="140%">
                    <feDropShadow dx="3" dy="3" stdDeviation="0" flood-color="#26221C" flood-opacity="1" />
                  </filter>
                </defs>
                <!-- 水平网格线 -->
                <line
                  v-for="(_, i) in gridLines"
                  :key="'g' + i"
                  :x1="PAD_L"
                  :y1="yForGrid(i)"
                  :x2="PLOT_R"
                  :y2="yForGrid(i)"
                  stroke="#26221C"
                  stroke-opacity="0.1"
                  stroke-width="1"
                  stroke-dasharray="4 4"
                />
                <!-- Y 轴刻度 -->
                <text v-for="(_, i) in gridLines" :key="'gy' + i" :x="PAD_L - 8" :y="yForGrid(i) + 5" text-anchor="end" class="chart-label">{{ gridValue(i) }}</text>

                <!-- X 轴日期 -->
                <text v-for="(p, i) in trendPoints" :key="'gx' + i" :x="xFor(i)" :y="SVG_H - 6" text-anchor="middle" class="chart-label">{{ fmtDateShort(p.date) }}</text>

                <!-- 面积填充 -->
                <polygon :points="areaPoints" fill="#F5562E" fill-opacity="0.1" />

                <!-- 折线 -->
                <polyline :points="linePoints" fill="none" stroke="#F5562E" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />

                <!-- 数据点 + 悬停交互 -->
                <g v-for="(p, i) in trendPoints" :key="'dp' + i">
                  <!-- 悬停竖虚线（不拦截鼠标） -->
                  <line
                    v-if="hoveredIndex === i"
                    :x1="xFor(i)"
                    :y1="yFor(p.count) + 12"
                    :x2="xFor(i)"
                    :y2="SVG_H - PAD_B + 2"
                    stroke="#26221C"
                    stroke-opacity="0.25"
                    stroke-width="1.5"
                    stroke-dasharray="3 4"
                    pointer-events="none"
                  />

                  <!-- 外环（不拦截鼠标） -->
                  <circle
                    :cx="xFor(i)"
                    :cy="yFor(p.count)"
                    :r="hoveredIndex === i ? 10 : 6"
                    fill="#fff"
                    stroke="#26221C"
                    stroke-width="2"
                    :style="{ transition: 'r .15s ease' }"
                    pointer-events="none"
                  />
                  <!-- 内圆（不拦截鼠标） -->
                  <circle :cx="xFor(i)" :cy="yFor(p.count)" :r="hoveredIndex === i ? 4 : 3.5" fill="#F5562E" :style="{ transition: 'r .15s ease' }" pointer-events="none" />

                  <!-- 常态数值标签（不拦截鼠标） -->
                  <text v-if="hoveredIndex !== i" :x="xFor(i)" :y="yFor(p.count) - 12" text-anchor="middle" class="chart-point-label" pointer-events="none">{{ p.count }}</text>

                  <!-- 悬停浮层卡片（不拦截鼠标） -->
                  <g v-if="hoveredIndex === i" pointer-events="none">
                    <rect :x="tooltipX(i)" :y="tooltipY(i)" width="104" height="58" rx="8" fill="#fff" stroke="#26221C" stroke-width="2" filter="url(#tip-shadow)" />
                    <text :x="tooltipX(i) + 52" :y="tooltipY(i) + 17" text-anchor="middle" class="chart-tip-date">{{ fmtDateCN(p.date) }}</text>
                    <text :x="tooltipX(i) + 52" :y="tooltipY(i) + 34" text-anchor="middle" class="chart-tip-count">{{ p.count }} 笔交易</text>
                    <text :x="tooltipX(i) + 52" :y="tooltipY(i) + 50" text-anchor="middle" class="chart-tip-val">¥{{ p.totalAmount }}</text>
                  </g>

                  <!-- ★ 不可见大热区 —— 必须放在最后，渲染在最顶层，只有它拦截鼠标事件 -->
                  <circle :cx="xFor(i)" :cy="yFor(p.count)" r="18" fill="transparent" style="cursor: pointer" @mouseenter="hoveredIndex = i" @mouseleave="hoveredIndex = null" />
                </g>
              </svg>
            </div>
            <div v-else class="trend-empty muted">暂无交易数据</div>
          </div>
        </div>

        <!-- 交易热力图（D5） -->
        <div class="section">
          <h3 class="section-title">📍 交易热力图</h3>
          <div v-if="heatmapData.length === 0" class="card card--flat state-card">
            <span class="muted">暂无交易地点数据</span>
          </div>
          <div v-else class="heatmap-grid card">
            <div v-for="(h, i) in heatmapData" :key="i" class="heatmap-bar-row">
              <span class="heatmap-loc">{{ h.location }}</span>
              <div class="heatmap-bar-wrap">
                <div class="heatmap-bar" :style="{ width: heatmapWidth(h.count) + '%' }" :class="heatColor(i)"></div>
              </div>
              <span class="heatmap-count">{{ h.count }} 笔</span>
            </div>
          </div>
        </div>

        <!-- 最近违规 -->
        <div class="section">
          <h3 class="section-title">最近违规待审核</h3>
          <div v-if="data.recentViolations.length === 0" class="card card--flat state-card">
            <span class="muted">暂无待审核违规记录 🎉</span>
          </div>
          <div v-else class="violation-list">
            <div v-for="v in data.recentViolations" :key="v.id" class="violation-item card card--flat">
              <div class="violation-item__left">
                <span class="violation-type badge" :class="violationBadge(v.violationType)">
                  {{ v.violationType }}
                </span>
                <div class="violation-info">
                  <div class="violation-title">{{ v.originalTitle }}</div>
                  <div class="violation-meta muted">{{ v.reporterName }} · {{ fmtTime(v.createdAt) }}</div>
                </div>
              </div>
              <div class="violation-item__right">
                <span class="violation-reason muted">{{ v.violationReason }}</span>
                <router-link to="/admin/violations" class="btn btn--sm btn--primary">去处理</router-link>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import { getDashboard, getSchools, getTradeHeatmap } from '@/api/admin'

// ---- 学校选择（D2：多校大盘） ----
const schools = ref([])
const selectedSchoolId = ref(null)

const schoolOptions = computed(() => {
  const opts = [{ value: null, label: '🌐 全部学校' }]
  schools.value.forEach((s) => {
    opts.push({ value: s.id, label: s.name })
  })
  return opts
})

async function loadSchools() {
  try {
    const res = await getSchools({ status: 'ACTIVE' })
    schools.value = res.data || []
  } catch {
    /* ignore */
  }
}

// ---- 大盘数据 ----
const data = ref({
  totalUsers: 0,
  onSaleItems: 0,
  todayTradeAmount: '0.00',
  pendingViolations: 0,
  recentViolations: [],
  trend: []
})
const loading = ref(false)
const loadError = ref(false)
const hoveredIndex = ref(null)

// ---- 热力图（D5） ----
const heatmapData = ref([])
const heatmapMax = computed(() => Math.max(1, ...heatmapData.value.map((h) => h.count)))

async function fetchHeatmap() {
  try {
    const res = await getTradeHeatmap(selectedSchoolId.value)
    heatmapData.value = res.data || []
  } catch {
    /* ignore */
  }
}

function heatmapWidth(count) {
  return Math.round((count / heatmapMax.value) * 100)
}

const HEAT_COLORS = ['heat--1', 'heat--2', 'heat--3', 'heat--4', 'heat--5']
function heatColor(i) {
  return HEAT_COLORS[i % HEAT_COLORS.length]
}

function switchSchool(schoolId) {
  selectedSchoolId.value = schoolId
  fetchDashboard()
  fetchHeatmap()
}

async function fetchDashboard() {
  loading.value = true
  loadError.value = false
  try {
    const res = await getDashboard(selectedSchoolId.value)
    data.value = res.data
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

// ---- 趋势图计算 ----

const trendPoints = computed(() => data.value.trend || [])

// SVG 坐标常量
const SVG_W = 600
const SVG_H = 220
const PAD_L = 44
const PAD_R = 20
const PAD_T = 18
const PAD_B = 32

const PLOT_W = computed(() => SVG_W - PAD_L - PAD_R)
const PLOT_R = computed(() => SVG_W - PAD_R)
const PLOT_H = computed(() => SVG_H - PAD_T - PAD_B)

// Y 轴最大值（至少为 1，向上取整到舒适的值）
const maxY = computed(() => {
  const raw = Math.max(1, ...trendPoints.value.map((p) => p.count))
  // 向上取整到最近的 nice number
  if (raw <= 2) return raw
  if (raw <= 5) return Math.ceil(raw)
  const mag = Math.pow(10, Math.floor(Math.log10(raw)))
  const norm = raw / mag
  const nice = norm <= 2 ? 2 : norm <= 5 ? 5 : 10
  return nice * mag
})

// 4 条网格线（0%, 25%, 50%, 75%, 100% — 5 个刻度值）
const gridLines = [0, 1, 2, 3, 4]

function gridValue(i) {
  return Math.round((maxY.value * i) / 4)
}

function xFor(i) {
  if (trendPoints.value.length <= 1) return PAD_L + PLOT_W.value / 2
  return PAD_L + (PLOT_W.value * i) / (trendPoints.value.length - 1)
}

function yForGrid(i) {
  return PAD_T + PLOT_H.value * (1 - i / 4)
}

function yFor(v) {
  if (maxY.value === 0) return PAD_T + PLOT_H.value
  return PAD_T + PLOT_H.value * (1 - v / maxY.value)
}

const linePoints = computed(() => trendPoints.value.map((p, i) => `${xFor(i)},${yFor(p.count)}`).join(' '))

const areaPoints = computed(() => {
  if (trendPoints.value.length === 0) return ''
  const pts = trendPoints.value.map((p, i) => `${xFor(i)},${yFor(p.count)}`)
  const baseY = PAD_T + PLOT_H.value
  return `${PAD_L},${baseY} ${pts.join(' ')} ${xFor(trendPoints.value.length - 1)},${baseY}`
})

onMounted(() => {
  loadSchools()
  fetchDashboard()
  fetchHeatmap()
})

// ---- 工具函数 ----

function fmtDateShort(dateStr) {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  return `${parseInt(parts[1])}/${parseInt(parts[2])}`
}

function fmtDateCN(dateStr) {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  return `${parseInt(parts[1])}月${parseInt(parts[2])}日`
}

function fmtTime(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function tooltipX(i) {
  // 浮层居中于数据点，左右留边距
  const cx = xFor(i)
  const halfW = 52
  if (cx - halfW < PAD_L) return PAD_L + 2
  if (cx + halfW > PLOT_R.value) return PLOT_R.value - 104 - 2
  return cx - halfW
}

function tooltipY(i) {
  // 浮层在数据点上方；若顶部空间不足则放下面
  const py = yFor(trendPoints.value[i].count)
  if (py - 70 >= PAD_T) return py - 70
  return py + 16
}

function violationBadge(type) {
  if (!type) return ''
  const t = type.toLowerCase()
  if (t.includes('违禁') || t.includes('危险')) return 'badge--danger'
  if (t.includes('代考') || t.includes('代写')) return 'badge--warn'
  if (t.includes('攻击') || t.includes('辱骂')) return 'badge--danger'
  if (t.includes('虚假')) return 'badge--warn'
  return 'badge--muted'
}
</script>

<style scoped>
.dashboard-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

/* 导航标签 */
.nav-tabs {
  display: flex;
  gap: 4px;
  margin: 18px 0 28px;
  flex-wrap: wrap;
}
.nav-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  font-size: 15px;
  font-weight: 700;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  background: var(--paper-deep);
  color: var(--ink);
  cursor: pointer;
  text-decoration: none;
  transition: all 0.2s;
}
.nav-tab:hover {
  background: var(--white);
  box-shadow: var(--shadow-s);
}
.nav-tab.active {
  background: var(--ink);
  color: var(--paper);
}

/* 统计卡片网格 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 40px;
}
@media (max-width: 768px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .trend-svg-wrap {
    overflow-x: auto;
  }
}

.stat-card {
  padding: 28px 24px;
  text-align: center;
}
.stat-card__icon {
  font-size: 32px;
  margin-bottom: 8px;
}
.stat-card__num {
  font-family: var(--font-display);
  font-size: 36px;
  font-weight: 900;
  color: var(--ink);
  line-height: 1.2;
}
.stat-card__label {
  font-size: 14px;
  margin-top: 6px;
}
.stat-card--link {
  text-decoration: none;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
  cursor: pointer;
}
.stat-card--link:hover {
  transform: translate(-2px, -2px);
  box-shadow: var(--shadow-l);
}
.stat-card--alert .stat-card__num {
  color: var(--red);
}

/* 学校切换栏（D2） */
.school-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}
.school-bar__label {
  font-size: 14px;
  font-weight: 700;
}
.school-chip {
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 700;
  border: var(--bw) solid var(--ink);
  border-radius: 999px;
  background: var(--paper-deep);
  color: var(--ink);
  cursor: pointer;
  transition: all 0.15s;
}
.school-chip:hover {
  background: var(--white);
  box-shadow: 2px 2px 0 var(--ink);
}
.school-chip.active {
  background: var(--ink);
  color: var(--paper);
}

/* 热力图（D5） */
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
  border-bottom: 1px dashed rgba(38, 34, 28, 0.1);
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

.state-card {
  padding: 28px 24px;
  text-align: center;
}

/* 区块标题 */
.section {
  margin-bottom: 40px;
}
.section-title {
  font-family: var(--font-display);
  font-size: 22px;
  letter-spacing: 0.5px;
  margin-bottom: 16px;
}

/* 趋势图 */
.trend-card {
  padding: 24px;
}
.trend-svg-wrap {
  width: 100%;
}
.trend-svg {
  width: 100%;
  max-width: 640px;
  display: block;
  margin: 0 auto;
}
.chart-label {
  font-family: var(--font-body);
  font-size: 11px;
  fill: var(--ink-soft);
}
.chart-point-label {
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 700;
  fill: var(--ink);
}
.chart-tip-date {
  font-family: var(--font-body);
  font-size: 11px;
  fill: var(--ink-soft);
}
.chart-tip-count {
  font-family: var(--font-body);
  font-size: 12px;
  font-weight: 500;
  fill: var(--ink);
}
.chart-tip-val {
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 900;
  fill: var(--primary);
}
.trend-empty {
  text-align: center;
  padding: 40px 0;
  font-size: 15px;
}

/* 违规列表 */
.violation-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.violation-item {
  padding: 18px 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.violation-item__left {
  display: flex;
  align-items: center;
  gap: 14px;
  flex: 1;
  min-width: 0;
}
.violation-info {
  min-width: 0;
}
.violation-title {
  font-weight: 700;
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.violation-meta {
  font-size: 13px;
  margin-top: 2px;
}
.violation-item__right {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}
.violation-reason {
  font-size: 13px;
  max-width: 220px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
@media (max-width: 640px) {
  .violation-item__right {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
