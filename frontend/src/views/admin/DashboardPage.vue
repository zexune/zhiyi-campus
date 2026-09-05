<template>
  <AdminLayout>
    <div class="dashboard-page rise">
      <!-- 页面标题 -->
      <div class="page-title">数据大盘</div>

      <!-- 学校切换（D2：多校大盘） -->
      <div v-if="schools.length > 0" class="school-bar">
        <span class="school-bar__label muted">学校视角</span>
        <button v-for="s in schoolOptions" :key="s.value ?? 'all'" class="school-chip" :class="{ active: selectedSchoolId === s.value }" @click="switchSchool(s.value)">
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
            <div class="stat-card__icon stat-card__icon--blue" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="9" cy="8" r="3.5" />
                <path d="M2.5 20c0-3.5 2.8-5.5 6.5-5.5s6.5 2 6.5 5.5" />
                <circle cx="17" cy="9" r="2.6" />
                <path d="M17 14.7c2.8.3 4.5 2 4.5 4.3" />
              </svg>
            </div>
            <div class="stat-card__num">{{ data.totalUsers }}</div>
            <div class="stat-card__label muted">用户总数</div>
          </div>
          <div class="stat-card card">
            <div class="stat-card__icon stat-card__icon--yellow" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z" />
                <path d="M3 6h18M16 10a4 4 0 0 1-8 0" />
              </svg>
            </div>
            <div class="stat-card__num">{{ data.onSaleItems }}</div>
            <div class="stat-card__label muted">在售商品</div>
          </div>
          <div class="stat-card card">
            <div class="stat-card__icon stat-card__icon--green" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="6" width="18" height="13" rx="2" />
                <path d="M3 10h18M16 15h.01" />
              </svg>
            </div>
            <div class="stat-card__num">¥{{ data.todayTradeAmount }}</div>
            <div class="stat-card__label muted">今日交易额</div>
          </div>
          <router-link :to="ROUTE_PATH.ADMIN_VIOLATIONS" class="stat-card card stat-card--link" :class="{ 'stat-card--alert': data.pendingViolations > 0 }">
            <div class="stat-card__icon stat-card__icon--red" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 3 2.5 20h19Z" />
                <path d="M12 10v4M12 17.5h.01" />
              </svg>
            </div>
            <div class="stat-card__num">{{ data.pendingViolations }}</div>
            <div class="stat-card__label muted">待审核内容</div>
          </router-link>
        </div>

        <!-- 近 7 日交易趋势 -->
        <div class="section">
          <h3 class="section-title">近 7 日交易趋势</h3>
          <div class="trend-card card">
            <div v-if="trendPoints.length > 0" class="trend-svg-wrap">
              <svg :viewBox="`0 0 ${SVG_W} ${SVG_H}`" class="trend-svg">
                <!-- 硬投影滤镜（悬停浮层用） -->
                <defs>
                  <filter id="tip-shadow" x="-10%" y="-10%" width="130%" height="140%">
                    <feDropShadow dx="3" dy="3" stdDeviation="0" flood-color="#1f1b16" flood-opacity="1" />
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
                  stroke="#1f1b16"
                  stroke-opacity="0.1"
                  stroke-width="1"
                  stroke-dasharray="4 4"
                />
                <!-- Y 轴刻度 -->
                <text v-for="(_, i) in gridLines" :key="'gy' + i" :x="PAD_L - 8" :y="yForGrid(i) + 5" text-anchor="end" class="chart-label">{{ gridValue(i) }}</text>

                <!-- X 轴日期 -->
                <text v-for="(p, i) in trendPoints" :key="'gx' + i" :x="xFor(i)" :y="SVG_H - 6" text-anchor="middle" class="chart-label">{{ fmtDateShort(p.date) }}</text>

                <!-- 面积填充 -->
                <polygon :points="areaPoints" fill="#c2410c" fill-opacity="0.1" />

                <!-- 折线 -->
                <polyline :points="linePoints" fill="none" stroke="var(--primary)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />

                <!-- 数据点 + 悬停交互 -->
                <g v-for="(p, i) in trendPoints" :key="'dp' + i">
                  <!-- 悬停竖虚线（不拦截鼠标） -->
                  <line
                    v-if="hoveredIndex === i"
                    :x1="xFor(i)"
                    :y1="yFor(p.count) + 12"
                    :x2="xFor(i)"
                    :y2="SVG_H - PAD_B + 2"
                    stroke="#1f1b16"
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
                    stroke="#1f1b16"
                    stroke-width="2"
                    :style="{ transition: 'r .15s ease' }"
                    pointer-events="none"
                  />
                  <!-- 内圆（不拦截鼠标） -->
                  <circle :cx="xFor(i)" :cy="yFor(p.count)" :r="hoveredIndex === i ? 4 : 3.5" fill="#c2410c" :style="{ transition: 'r .15s ease' }" pointer-events="none" />

                  <!-- 常态数值标签（不拦截鼠标） -->
                  <text v-if="hoveredIndex !== i" :x="xFor(i)" :y="yFor(p.count) - 12" text-anchor="middle" class="chart-point-label" pointer-events="none">{{ p.count }}</text>

                  <!-- 悬停浮层卡片（不拦截鼠标） -->
                  <g v-if="hoveredIndex === i" pointer-events="none">
                    <rect :x="tooltipX(i)" :y="tooltipY(i)" width="104" height="58" rx="8" fill="#fff" stroke="#1f1b16" stroke-width="2" filter="url(#tip-shadow)" />
                    <text :x="tooltipX(i) + 52" :y="tooltipY(i) + 17" text-anchor="middle" class="chart-tip-date">{{ fmtDateCN(p.date) }}</text>
                    <text :x="tooltipX(i) + 52" :y="tooltipY(i) + 34" text-anchor="middle" class="chart-tip-count">{{ p.count }} 笔交易</text>
                    <text :x="tooltipX(i) + 52" :y="tooltipY(i) + 50" text-anchor="middle" class="chart-tip-val">¥{{ p.totalAmount }}</text>
                  </g>

                  <!-- ★ 不可见大热区 —— 必须放在最后，渲染在最顶层，只有它拦截事件。
                      同时是键盘可达的数据点：Tab 聚焦/失焦复用悬停浮层，
                      aria-label 给出完整数据（日期/笔数/金额），读屏不依赖图形 -->
                  <circle
                    :cx="xFor(i)"
                    :cy="yFor(p.count)"
                    r="18"
                    fill="transparent"
                    tabindex="0"
                    role="img"
                    :aria-label="`${fmtDateCN(p.date)}，${p.count} 笔交易，金额 ¥${p.totalAmount}`"
                    style="cursor: pointer"
                    @mouseenter="hoveredIndex = i"
                    @mouseleave="hoveredIndex = null"
                    @focus="hoveredIndex = i"
                    @blur="hoveredIndex = null"
                  />
                </g>
              </svg>
            </div>
            <div v-else class="trend-empty muted">暂无交易数据</div>

            <!-- 图表数据的等价文本形式（读屏/键盘用户的非图形替代） -->
            <table v-if="trendPoints.length" class="visually-hidden">
              <caption>近 7 日交易趋势数据</caption>
              <thead>
                <tr>
                  <th scope="col">日期</th>
                  <th scope="col">交易笔数</th>
                  <th scope="col">交易金额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="p in trendPoints" :key="p.date">
                  <th scope="row">{{ fmtDateCN(p.date) }}</th>
                  <td>{{ p.count }}</td>
                  <td>¥{{ p.totalAmount }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 交易热力图（D5） -->
        <div class="section">
          <h3 class="section-title">交易热力图</h3>
          <TradeHeatmap :entries="heatmapData" />
        </div>

        <!-- 最近违规 -->
        <div class="section">
          <h3 class="section-title">最近违规待审核</h3>
          <div v-if="data.recentViolations.length === 0" class="card card--flat state-card">
            <span class="muted">暂无待审核违规记录</span>
          </div>
          <div v-else class="violation-list">
            <div v-for="v in data.recentViolations" :key="v.id" class="violation-item card card--flat">
              <div class="violation-item__left">
                <span class="violation-type badge" :class="violationBadge(v.violationType)">
                  {{ v.violationType }}
                </span>
                <div class="violation-info">
                  <div class="violation-title">{{ v.originalTitle }}</div>
                  <div class="violation-meta muted">{{ v.reporterName }} · {{ formatDateTime(v.createdAt) }}</div>
                </div>
              </div>
              <div class="violation-item__right">
                <span class="violation-reason muted">{{ v.violationReason }}</span>
                <router-link :to="ROUTE_PATH.ADMIN_VIOLATIONS" class="btn btn--sm btn--primary">去处理</router-link>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </AdminLayout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import TradeHeatmap from './components/TradeHeatmap.vue'
import { getDashboard, getSchools, getTradeHeatmap } from '@/api/admin'
import type { DashboardStats, School, TradeHeatEntry } from '@/types/models'
import { ROUTE_PATH } from '@/constants/routes'
import { formatDateTime } from '@/utils/format'
import { useLatestWins } from '@/composables/useLatestWins'

// ---- 学校选择（D2：多校大盘） ----
const schools = ref<School[]>([])
const selectedSchoolId = ref<number | null>(null)

interface SchoolOption {
  value: number | null
  label: string
}

const schoolOptions = computed(() => {
  const opts: SchoolOption[] = [{ value: null, label: '全部学校' }]
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
// 缺省骨架同时用于初始值与响应合并：模板直接访问 data.recentViolations.length 等字段，
// 后端若缺字段，直接整包替换会让渲染抛 TypeError 白屏。
const EMPTY_DASHBOARD: DashboardStats = {
  totalUsers: 0,
  onSaleItems: 0,
  todayTradeAmount: '0.00',
  pendingViolations: 0,
  recentViolations: [],
  trend: []
}
const data = ref<DashboardStats>({ ...EMPTY_DASHBOARD })
const loading = ref(false)
const loadError = ref(false)
const hoveredIndex = ref<number | null>(null)

// ---- 热力图（D5） ----
const heatmapData = ref<TradeHeatEntry[]>([])

/**
 * 学校切换 latest-wins 守卫：fetchDashboard 入口推进代数，作废上一学校
 * 仍在途的大盘/热力图两路响应，防止快速切换时旧学校数据覆盖新学校。
 */
const schoolGuard = useLatestWins()

async function fetchHeatmap() {
  // 快照当前代数（不推进）：与 fetchDashboard 同批发起的请求共享同一代
  const gen = schoolGuard.generation.value
  try {
    const res = await getTradeHeatmap(selectedSchoolId.value)
    if (!schoolGuard.isCurrent(gen)) return
    heatmapData.value = res.data || []
  } catch {
    /* ignore */
  }
}

function switchSchool(schoolId: number | null) {
  selectedSchoolId.value = schoolId
  fetchDashboard()
  fetchHeatmap()
}

async function fetchDashboard() {
  // 入口推进学校代数：切换学校/重试后，上一轮在途响应（含热力图）一律作废
  const gen = schoolGuard.begin()
  loading.value = true
  loadError.value = false
  try {
    const res = await getDashboard(selectedSchoolId.value)
    if (!schoolGuard.isCurrent(gen)) return
    data.value = { ...EMPTY_DASHBOARD, ...res.data }
  } catch {
    if (schoolGuard.isCurrent(gen)) loadError.value = true
  } finally {
    // 只有最新一代允许复位 loading，避免旧代 finally 提前结束新一轮的加载态
    if (schoolGuard.isCurrent(gen)) loading.value = false
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

// Y 轴最大值：向上取整到 4 的倍数，保证 4 等分后的刻度值全是整数且互不重复
// （此前 raw=1 时四等分刻度 round 后为 0/1/1/1，网格标签连排三个"1"）
const maxY = computed(() => {
  const raw = Math.max(1, ...trendPoints.value.map((p) => p.count))
  return Math.max(4, Math.ceil(raw / 4) * 4)
})

// 4 条网格线（0%, 25%, 50%, 75%, 100% — 5 个刻度值）
const gridLines = [0, 1, 2, 3, 4]

function gridValue(i: number) {
  return (maxY.value * i) / 4
}

function xFor(i: number) {
  if (trendPoints.value.length <= 1) return PAD_L + PLOT_W.value / 2
  return PAD_L + (PLOT_W.value * i) / (trendPoints.value.length - 1)
}

function yForGrid(i: number) {
  return PAD_T + PLOT_H.value * (1 - i / 4)
}

function yFor(v: number) {
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

function fmtDateShort(dateStr: string) {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  return `${parseInt(parts[1])}/${parseInt(parts[2])}`
}

function fmtDateCN(dateStr: string) {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  return `${parseInt(parts[1])}月${parseInt(parts[2])}日`
}

function tooltipX(i: number) {
  // 浮层居中于数据点，左右留边距
  const cx = xFor(i)
  const halfW = 52
  if (cx - halfW < PAD_L) return PAD_L + 2
  if (cx + halfW > PLOT_R.value) return PLOT_R.value - 104 - 2
  return cx - halfW
}

function tooltipY(i: number) {
  // 浮层在数据点上方；若顶部空间不足则放下面
  const py = yFor(trendPoints.value[i].count)
  if (py - 70 >= PAD_T) return py - 70
  return py + 16
}

function violationBadge(type: string | undefined) {
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
  width: 48px;
  height: 48px;
  margin: 0 auto 10px;
  display: grid;
  place-items: center;
  border-radius: var(--r-m);
}
.stat-card__icon svg {
  width: 26px;
  height: 26px;
}
.stat-card__icon--blue {
  background: var(--blue-bg);
  color: var(--blue);
}
.stat-card__icon--yellow {
  background: var(--yellow-bg);
  color: var(--yellow-ink);
}
.stat-card__icon--green {
  background: var(--green-bg);
  color: var(--green-deep);
}
.stat-card__icon--red {
  background: var(--red-bg);
  color: var(--red-deep);
}
.stat-card__num {
  font-size: 32px;
  font-weight: 700;
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
  gap: 10px;
  margin-bottom: var(--spacing-lg);
  flex-wrap: wrap;
}
.school-bar__label {
  margin-right: 8px;
  font-weight: 600;
  font-size: 13.5px;
  font-weight: 700;
}
.school-chip {
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 700;
  border: var(--bw) solid var(--line);
  border-radius: 999px;
  background: var(--paper-deep);
  color: var(--ink);
  cursor: pointer;
  transition:
    color 0.15s,
    background-color 0.15s,
    border-color 0.15s,
    box-shadow 0.15s,
    transform 0.15s;
}
.school-chip:hover {
  background: var(--white);
  box-shadow: var(--shadow-s);
}
.school-chip.active {
  background: var(--ink);
  color: var(--paper);
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
