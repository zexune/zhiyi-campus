<template>
  <DefaultLayout>
    <div class="violations-page rise">
      <!-- 页面标题 -->
      <div class="page-title">
        ⚖️ 违规审核
        <span class="stamp">Admin</span>
      </div>

      <!-- 导航标签 -->
      <div class="nav-tabs">
        <router-link to="/admin/dashboard" class="nav-tab">📊 数据大盘</router-link>
        <span class="nav-tab active">⚖️ 违规审核</span>
        <router-link to="/admin/chat" class="nav-tab">💬 客服收件箱</router-link>
        <router-link to="/admin/manage" class="nav-tab">🔧 内容管理</router-link>
        <router-link to="/admin/schools" class="nav-tab">🏫 学校管理</router-link>
      </div>

      <!-- 状态筛选 -->
      <div class="filter-bar">
        <button
          v-for="tab in statusTabs" :key="tab.key"
          class="filter-tab"
          :class="{ active: currentStatus === tab.key }"
          @click="switchTab(tab.key)"
        >
          {{ tab.label }}
          <span v-if="tab.key === 'PENDING' && pendingCount > 0" class="filter-count">{{ pendingCount }}</span>
        </button>
      </div>

      <!-- 加载 / 错误 -->
      <div v-if="loading" class="card card--flat state-card">
        <span class="muted">加载中...</span>
      </div>
      <div v-else-if="loadError" class="card card--flat state-card">
        <span class="muted">数据加载失败</span>
        <button class="btn btn--sm" style="margin-top:12px" @click="fetchList">重新加载</button>
      </div>

      <!-- 列表 -->
      <template v-else>
        <div v-if="list.length === 0" class="card card--flat state-card">
          <span class="muted">{{ emptyText }}</span>
        </div>

        <div v-else class="violation-list">
          <div
            v-for="v in list" :key="v.id"
            class="violation-card card"
            :class="{ 'card--flat': v.status !== 'PENDING' }"
          >
            <!-- 卡片头部 -->
            <div class="v-card__header">
              <div class="v-card__header-left">
                <span class="badge" :class="violationBadge(v.violationType)">
                  {{ v.violationType === 'CONTENT_VIOLATION' ? '内容违规' : v.violationType }}
                </span>
                <span class="badge" :class="statusBadge(v.status)">
                  {{ statusLabel(v.status) }}
                </span>
                <span v-if="v.aiReviewError" class="badge badge--warn">AI异常</span>
              </div>
              <span class="v-card__time muted">{{ fmtTime(v.createdAt) }}</span>
            </div>

            <!-- 卡片主体 -->
            <div class="v-card__body">
              <div class="v-card__title">{{ v.originalTitle }}</div>
              <div class="v-card__desc muted">{{ v.originalDescription }}</div>
              <div class="v-card__reason">
                <span class="reason-label">AI 判定：</span>
                {{ v.violationReason }}
              </div>
              <div class="v-card__meta muted">
                发布者：{{ v.reporterName }}
                <template v-if="v.itemId">
                  · 商品：<strong>#{{ v.itemId }}</strong>
                  <span v-if="v.itemStatus">（{{ itemStatusLabel(v.itemStatus) }}）</span>
                </template>
                <template v-if="v.handlerName">
                  · 处理人：{{ v.handlerName }}
                </template>
                <template v-if="v.handleNote">
                  · 备注：{{ v.handleNote }}
                </template>
              </div>
            </div>

            <!-- 卡片操作 -->
            <div v-if="v.status === 'PENDING' || canForceOff(v)" class="v-card__actions">
              <template v-if="v.status === 'PENDING'">
                <button class="btn btn--danger btn--sm" @click="openBanDialog(v)">
                  🚫 确认违规
                </button>
                <button class="btn btn--green btn--sm" @click="handleDismiss(v)">
                  ✅ 误判放行
                </button>
              </template>
              <button
                v-if="canForceOff(v)"
                class="btn btn--sm"
                @click="handleForceOff(v)"
              >
                📦 强制下架商品 #{{ v.itemId }}
              </button>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div v-if="total > pageSize" class="pagination-bar">
          <button
            class="btn btn--sm"
            :disabled="currentPage <= 1"
            @click="goPage(currentPage - 1)"
          >
            上一页
          </button>
          <span class="page-info muted">
            {{ currentPage }} / {{ totalPages }}
          </span>
          <button
            class="btn btn--sm"
            :disabled="currentPage >= totalPages"
            @click="goPage(currentPage + 1)"
          >
            下一页
          </button>
        </div>
      </template>
    </div>

    <!-- ========== 封禁弹窗 ========== -->
    <div v-if="banDialog.visible" class="modal-overlay" @click.self="closeBanDialog">
      <div class="modal-card card">
        <h3 class="modal-title">🚫 确认违规 · 处罚用户</h3>

        <!-- 违规信息摘要 -->
        <div class="ban-summary card card--flat">
          <div class="ban-summary__row">
            <span class="muted">违规用户：</span>
            <strong>{{ banDialog.report?.reporterName }}</strong>
          </div>
          <div class="ban-summary__row">
            <span class="muted">违规标题：</span>
            <span>{{ banDialog.report?.originalTitle }}</span>
          </div>
          <div class="ban-summary__row">
            <span class="muted">AI判定：</span>
            <span>{{ banDialog.report?.violationReason }}</span>
          </div>
        </div>

        <!-- 处罚类型 -->
        <div class="field">
          <label>处罚类型 <span class="req">*</span></label>
          <div class="radio-group">
            <label
              v-for="pt in punishTypes" :key="pt.key"
              class="radio-card"
              :class="{ active: banDialog.form.type === pt.key }"
            >
              <input
                type="radio"
                :value="pt.key"
                v-model="banDialog.form.type"
                style="display:none"
              />
              <span class="radio-card__icon">{{ pt.icon }}</span>
              <span class="radio-card__label">{{ pt.label }}</span>
            </label>
          </div>
        </div>

        <!-- 封禁天数（仅限时封禁） -->
        <div class="field" v-if="banDialog.form.type === 'BAN_TEMP'">
          <label>封禁天数 <span class="req">*</span></label>
          <input
            class="input"
            type="number"
            v-model.number="banDialog.form.banDays"
            min="1" max="365"
            placeholder="1-365 天"
          />
        </div>

        <!-- 处罚原因 -->
        <div class="field">
          <label>处罚原因 <span class="req">*</span></label>
          <textarea
            class="textarea"
            v-model="banDialog.form.reason"
            rows="3"
            maxlength="500"
            placeholder="填写处罚原因（最长500字）"
          ></textarea>
        </div>

        <!-- 处理备注 -->
        <div class="field">
          <label>处理备注</label>
          <input
            class="input"
            v-model="banDialog.form.handleNote"
            maxlength="500"
            placeholder="可选：补充说明"
          />
        </div>

        <!-- 按钮 -->
        <div class="modal-actions">
          <button class="btn" @click="closeBanDialog">取消</button>
          <button
            class="btn btn--danger"
            :disabled="banDialog.submitting"
            @click="handleConfirm"
          >
            {{ banDialog.submitting ? '处理中...' : '确认处罚' }}
          </button>
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import { getViolations, confirmViolation, dismissViolation, forceOffShelf } from '@/api/admin'

// ---- 状态筛选 ----
const statusTabs = [
  { key: '',      label: '全部' },
  { key: 'PENDING',   label: '待处理' },
  { key: 'CONFIRMED', label: '已确认' },
  { key: 'DISMISSED', label: '已驳回' },
]

const currentStatus = ref('PENDING')
const currentPage = ref(1)
const pageSize = 10
const total = ref(0)
const list = ref([])
const loading = ref(false)
const loadError = ref(false)
const pendingCount = ref(0)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const emptyText = computed(() => {
  const tab = statusTabs.find(t => t.key === currentStatus.value)
  return tab ? `暂无${tab.label}违规记录 🎉` : '暂无数据'
})

async function fetchList() {
  loading.value = true
  loadError.value = false
  try {
    const params = { page: currentPage.value, size: pageSize }
    if (currentStatus.value) params.status = currentStatus.value
    const res = await getViolations(params)
    const data = res.data
    list.value = data.records || []
    total.value = data.total || 0
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function fetchPendingCount() {
  try {
    const res = await getViolations({ page: 1, size: 1, status: 'PENDING' })
    pendingCount.value = res.data?.total || 0
  } catch { /* ignore */ }
}

function switchTab(key) {
  currentStatus.value = key
  currentPage.value = 1
  fetchList()
}

function goPage(p) {
  currentPage.value = p
  fetchList()
}

// ---- 封禁弹窗 ----
const punishTypes = [
  { key: 'WARNING',  icon: '⚠️', label: '警告' },
  { key: 'BAN_TEMP', icon: '⏳', label: '限时封禁' },
  { key: 'BAN_PERM', icon: '🚫', label: '永久封禁' },
]

const banDialog = reactive({
  visible: false,
  report: null,
  submitting: false,
  form: {
    type: 'WARNING',
    reason: '',
    banDays: 7,
    handleNote: '',
  },
})

function openBanDialog(report) {
  banDialog.report = report
  banDialog.form = {
    type: 'WARNING',
    reason: `AI判定：${report.violationReason}`,
    banDays: 7,
    handleNote: '',
  }
  banDialog.visible = true
}

function closeBanDialog() {
  banDialog.visible = false
  banDialog.report = null
}

async function handleConfirm() {
  const { type, reason, banDays, handleNote } = banDialog.form
  if (!reason.trim()) {
    ElMessage.warning('请填写处罚原因')
    return
  }
  if (type === 'BAN_TEMP' && (!banDays || banDays < 1 || banDays > 365)) {
    ElMessage.warning('封禁天数须为 1-365')
    return
  }

  banDialog.submitting = true
  try {
    await confirmViolation(banDialog.report.id, {
      type,
      reason: reason.trim(),
      banDays: type === 'BAN_TEMP' ? banDays : null,
      handleNote: handleNote.trim() || null,
    })
    ElMessage.success('处罚已生效')
    closeBanDialog()
    fetchList()
    fetchPendingCount()
  } catch {
    ElMessage.error('操作失败')
  } finally {
    banDialog.submitting = false
  }
}

// ---- 误判放行 ----
async function handleDismiss(report) {
  try {
    await ElMessageBox.confirm(
      `确认放行「${report.originalTitle}」？AI 判定将被撤销。`,
      '误判放行',
      { confirmButtonText: '确认放行', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // 用户取消
  }

  try {
    await dismissViolation(report.id)
    ElMessage.success('已放行，该违规记录已撤销')
    fetchList()
    fetchPendingCount()
  } catch {
    ElMessage.error('操作失败')
  }
}

// ---- 强制下架（关联商品仍在售时可直接下架） ----
function canForceOff(v) {
  return v.itemId && v.itemStatus && v.itemStatus !== 'OFF_SHELF'
}

function itemStatusLabel(s) {
  return { ON_SALE: '在售', OFF_SHELF: '已下架', SOLD: '已售出', PENDING: '交易中' }[s] || s
}

async function handleForceOff(v) {
  try {
    await ElMessageBox.confirm(
      `确认强制下架商品「${v.originalTitle}」(#${v.itemId})？卖家将被扣除 30 经验值。`,
      '强制下架',
      { confirmButtonText: '确认下架', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // 用户取消
  }

  try {
    await forceOffShelf(v.itemId)
    ElMessage.success('商品已强制下架')
    v.itemStatus = 'OFF_SHELF'
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

// ---- 工具函数 ----
function fmtTime(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function statusLabel(s) {
  return { PENDING: '待处理', CONFIRMED: '已确认', DISMISSED: '已驳回' }[s] || s
}

function statusBadge(s) {
  return { PENDING: 'badge--warn', CONFIRMED: 'badge--danger', DISMISSED: 'badge--ok' }[s] || 'badge--muted'
}

function violationBadge(type) {
  if (!type) return 'badge--muted'
  const t = type.toLowerCase()
  if (t.includes('violation') || t.includes('违禁')) return 'badge--danger'
  if (t.includes('ai_review_error') || t.includes('异常')) return 'badge--warn'
  return 'badge--muted'
}

onMounted(() => {
  fetchList()
  fetchPendingCount()
})
</script>

<style scoped>
.violations-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

/* 导航 */
.nav-tabs {
  display: flex; gap: 4px; margin: 18px 0 22px; flex-wrap: wrap;
}
.nav-tab {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 10px 20px; font-size: 15px; font-weight: 700;
  border: var(--bw) solid var(--ink); border-radius: var(--r-s);
  background: var(--paper-deep); color: var(--ink);
  cursor: pointer; text-decoration: none; transition: all .2s;
}
.nav-tab:hover { background: var(--white); box-shadow: var(--shadow-s); }
.nav-tab.active { background: var(--ink); color: var(--paper); }

/* 状态筛选 */
.filter-bar {
  display: flex; gap: 8px; margin-bottom: 24px; flex-wrap: wrap;
}
.filter-tab {
  padding: 8px 18px; font-size: 14px; font-weight: 700;
  border: var(--bw) solid var(--ink); border-radius: 999px;
  background: var(--paper-deep); color: var(--ink);
  cursor: pointer; transition: all .15s;
  display: inline-flex; align-items: center; gap: 6px;
}
.filter-tab:hover { background: var(--white); }
.filter-tab.active { background: var(--ink); color: var(--paper); }
.filter-count {
  min-width: 20px; height: 20px; padding: 0 6px; border-radius: 10px;
  background: var(--red); color: #fff; font-size: 11px; font-weight: 700;
  display: grid; place-items: center;
}
.filter-tab.active .filter-count { background: var(--yellow); color: var(--ink); }

/* 状态卡片 */
.state-card {
  padding: 40px 24px; text-align: center;
}

/* 违规列表 */
.violation-list {
  display: flex; flex-direction: column; gap: 14px;
}

/* 违规卡片 */
.violation-card {
  padding: 20px 24px;
}
.v-card__header {
  display: flex; align-items: center; justify-content: space-between;
  gap: 12px; margin-bottom: 14px; flex-wrap: wrap;
}
.v-card__header-left {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
}
.v-card__time { font-size: 13px; flex-shrink: 0; }
.v-card__body {
  margin-bottom: 16px;
}
.v-card__title {
  font-size: 17px; font-weight: 700; margin-bottom: 6px;
}
.v-card__desc {
  font-size: 14px; margin-bottom: 10px;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden;
}
.v-card__reason {
  font-size: 13px; padding: 10px 14px; margin-bottom: 8px;
  background: var(--paper-deep); border-radius: var(--r-s);
  border-left: 3px solid var(--red);
}
.reason-label { font-weight: 700; color: var(--red); }
.v-card__meta { font-size: 13px; }
.v-card__actions {
  display: flex; gap: 10px; padding-top: 14px;
  border-top: var(--bw) dashed var(--ink); border-color: rgba(38,34,28,.15);
}

/* 分页 */
.pagination-bar {
  display: flex; align-items: center; justify-content: center;
  gap: 16px; margin-top: 28px;
}
.page-info {
  font-size: 14px; font-weight: 700;
}

/* ===== 弹窗 ===== */
.modal-overlay {
  position: fixed; inset: 0; z-index: var(--z-modal);
  background: rgba(38,34,28,.45);
  display: grid; place-items: center;
  padding: 20px;
}
.modal-card {
  width: 100%; max-width: 560px; max-height: 90vh; overflow-y: auto;
  padding: 28px 28px 24px;
}
.modal-title {
  font-family: var(--font-display); font-size: 22px;
  letter-spacing: .5px; margin-bottom: 22px;
}
.modal-actions {
  display: flex; gap: 12px; justify-content: flex-end; margin-top: 22px;
}

/* 违规摘要 */
.ban-summary {
  padding: 16px; margin-bottom: 22px;
  background: var(--paper-deep);
}
.ban-summary__row {
  font-size: 14px; margin-bottom: 4px;
  display: flex; gap: 6px;
}
.ban-summary__row:last-child { margin-bottom: 0; }

/* 处罚类型单选卡片 */
.radio-group {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px;
}
.radio-card {
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  padding: 14px 10px;
  border: var(--bw) solid var(--ink); border-radius: var(--r-s);
  background: var(--paper-deep); cursor: pointer;
  transition: all .15s;
}
.radio-card:hover { background: var(--white); }
.radio-card.active {
  background: var(--ink); color: var(--paper);
  box-shadow: var(--shadow-s);
}
.radio-card__icon { font-size: 22px; }
.radio-card__label { font-size: 13px; font-weight: 700; }

@media (max-width: 640px) {
  .radio-group { grid-template-columns: 1fr; }
  .modal-card { padding: 20px; }
}
</style>
