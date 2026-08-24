<template>
  <AdminLayout>
    <div class="review-page">
      <header class="page-head">
        <div>
          <h1 class="page-title">内容治理工作台</h1>
        </div>
        <button class="btn" :disabled="loading" @click="refreshCurrent">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20 11a8 8 0 1 0-2.34 5.66" />
            <path d="M20 4v7h-7" />
          </svg>
          刷新
        </button>
      </header>

      <div class="workspace-tabs" role="tablist">
        <button class="workspace-tab" :class="{ active: workspace === 'reviews' }" @click="switchWorkspace('reviews')">
          内容审核
          <span v-if="pendingReviewCount" class="count">{{ pendingReviewCount }}</span>
        </button>
        <button class="workspace-tab" :class="{ active: workspace === 'appeals' }" @click="switchWorkspace('appeals')">
          申诉复核
          <span v-if="pendingAppealCount" class="count">{{ pendingAppealCount }}</span>
        </button>
      </div>

      <template v-if="workspace === 'reviews'">
        <div class="filter-tabs seg-tabs" aria-label="内容审核状态">
          <button v-for="tab in REVIEW_STATUS_TABS" :key="tab.value" type="button" :class="{ active: reviewStatus === tab.value }" @click="changeReviewStatus(tab.value)">
            {{ tab.label }}
          </button>
        </div>

        <el-skeleton v-if="loading" :rows="7" animated />
        <div v-else-if="reviews.length" class="review-list">
          <article v-for="review in reviews" :key="review.id" class="card review-card">
            <div class="review-card__head">
              <div>
                <div class="badge-row">
                  <span class="badge" :class="sourceMeta(review.source).badge">{{ sourceMeta(review.source).label }}</span>
                  <span class="badge" :class="reviewStatusMeta(review.status).badge">{{ reviewStatusMeta(review.status).label }}</span>
                  <span v-if="review.ruleVersion" class="rule-version">规则 {{ review.ruleVersion }}</span>
                </div>
                <h2>{{ review.originalTitle || '已删除商品' }}</h2>
              </div>
              <div class="review-card__time">{{ formatDateTime(review.createdAt) }}</div>
            </div>

            <div class="party-grid">
              <div>
                <span>卖家</span>
                <strong>{{ review.sellerName || `用户 #${review.userId}` }}</strong>
              </div>
              <div v-if="review.reporterId">
                <span>举报人</span>
                <strong>{{ review.reporterName || `用户 #${review.reporterId}` }}</strong>
              </div>
              <div>
                <span>商品状态</span>
                <strong>{{ itemStatusText(review.itemStatus) }}</strong>
              </div>
              <div v-if="review.handlerName">
                <span>处理人</span>
                <strong>{{ review.handlerName }}</strong>
              </div>
            </div>

            <div class="content-box">
              <p class="content-box__label">待核实内容</p>
              <p>{{ review.originalDescription || '暂无商品描述' }}</p>
            </div>

            <div class="reason-box">
              <strong>{{ review.source === 'USER_REPORT' ? '举报说明' : '检测依据' }}</strong>
              <p>{{ review.violationReason || '未提供说明' }}</p>
              <div v-if="matchedRules(review).length" class="matched-rules">
                <span v-for="rule in matchedRules(review)" :key="rule" class="tag">{{ rule }}</span>
              </div>
            </div>

            <div v-if="review.handleNote" class="handle-note">
              <strong>处理备注：</strong>
              {{ review.handleNote }}
            </div>

            <div v-if="review.status === VIOLATION_STATUS.PENDING" class="review-card__actions">
              <button class="btn btn--green" :disabled="acting" @click="dismissReview(review)">核实无违规，放行</button>
              <button class="btn btn--danger" :disabled="acting" @click="openConfirmDialog(review)">确认内容违规</button>
            </div>
          </article>
        </div>
        <div v-else class="card empty-card"><p class="muted">当前状态下没有内容审核记录</p></div>

        <el-pagination v-if="reviewTotal > pageSize" v-model:current-page="reviewPage" :page-size="pageSize" :total="reviewTotal" layout="prev, pager, next" @current-change="fetchReviews" />
      </template>

      <template v-else>
        <div class="filter-tabs seg-tabs" aria-label="申诉状态">
          <button v-for="tab in APPEAL_STATUS_TABS" :key="tab.value" type="button" :class="{ active: appealStatus === tab.value }" @click="changeAppealStatus(tab.value)">
            {{ tab.label }}
          </button>
        </div>

        <el-skeleton v-if="loading" :rows="6" animated />
        <div v-else-if="appeals.length" class="review-list">
          <article v-for="appeal in appeals" :key="appeal.id" class="card appeal-card">
            <div class="review-card__head">
              <div>
                <span class="badge" :class="appealStatusMeta(appeal.status).badge">{{ appealStatusMeta(appeal.status).label }}</span>
                <h2>{{ appeal.itemTitle || `商品 #${appeal.itemId}` }}</h2>
                <p class="muted">卖家：{{ appeal.sellerName || `用户 #${appeal.userId}` }} · 提交于 {{ formatDate(appeal.createdAt) }}</p>
              </div>
            </div>

            <div class="appeal-compare">
              <div>
                <span>原违规依据</span>
                <p>{{ appeal.violationReason }}</p>
              </div>
              <div>
                <span>卖家申诉理由</span>
                <p>{{ appeal.reason }}</p>
              </div>
            </div>

            <div v-if="appeal.handleNote" class="handle-note">
              <strong>复核说明：</strong>
              {{ appeal.handleNote }}
              <span v-if="appeal.handlerName">· {{ appeal.handlerName }} · {{ formatDate(appeal.handledAt) }}</span>
            </div>

            <div v-if="appeal.status === APPEAL_STATUS.PENDING" class="review-card__actions">
              <button class="btn btn--danger" :disabled="acting" @click="openAppealHandle(appeal, 'reject')">驳回申诉</button>
              <button class="btn btn--green" :disabled="acting" @click="openAppealHandle(appeal, 'approve')">通过并撤销扣分</button>
            </div>
          </article>
        </div>
        <div v-else class="card empty-card"><p class="muted">当前状态下没有申诉记录</p></div>

        <el-pagination v-if="appealTotal > pageSize" v-model:current-page="appealPage" :page-size="pageSize" :total="appealTotal" layout="prev, pager, next" @current-change="fetchAppeals" />
      </template>

      <el-dialog v-model="confirmForm.visible" title="确认内容违规" width="min(560px, 92vw)" :close-on-click-modal="!confirmForm.submitting">
        <div class="dialog-form">
          <div class="warning-panel">确认后商品将下架，并扣除卖家合规分。</div>
          <label>
            <span>
              违规原因
              <b>*</b>
            </span>
            <el-input v-model="confirmForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
          </label>
          <label>
            <span>内部处理备注</span>
            <el-input v-model="confirmForm.handleNote" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="选填" />
          </label>
        </div>
        <template #footer>
          <button class="btn" :disabled="confirmForm.submitting" @click="confirmForm.visible = false">取消</button>
          <button class="btn btn--danger" :disabled="confirmForm.submitting" @click="submitConfirm">
            {{ confirmForm.submitting ? '处理中...' : '确认违规并下架' }}
          </button>
        </template>
      </el-dialog>

      <el-dialog v-model="appealHandle.visible" :title="appealHandle.action === 'approve' ? '通过申诉' : '驳回申诉'" width="min(520px, 92vw)" :close-on-click-modal="!appealHandle.submitting">
        <div class="dialog-form">
          <div v-if="appealHandle.action === 'approve'" class="success-panel">通过后将幂等撤销该违规记录对应的扣分；如果商品没有其他已确认违规，会自动重新上架。</div>
          <label>
            <span>复核说明</span>
            <el-input v-model="appealHandle.handleNote" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="选填，建议说明判断依据" />
          </label>
        </div>
        <template #footer>
          <button class="btn" :disabled="appealHandle.submitting" @click="appealHandle.visible = false">取消</button>
          <button class="btn" :class="appealHandle.action === 'approve' ? 'btn--green' : 'btn--danger'" :disabled="appealHandle.submitting" @click="submitAppealHandle">
            {{ appealHandle.submitting ? '处理中...' : '确认提交' }}
          </button>
        </template>
      </el-dialog>
    </div>
  </AdminLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import { approveAppeal, confirmViolation, dismissViolation, getAppeals, getViolations, rejectAppeal } from '@/api/admin'
import { APPEAL_STATUS, VIOLATION_STATUS } from '@/constants/domain'
import type { AppealStatus, ViolationStatus } from '@/constants/domain'
import type { ViolationAppeal, ViolationReview } from '@/types/models'
import { itemStatusLabel } from '@/utils/trade'
import { formatDate, formatDateTime } from '@/utils/format'
import { usePagedList } from '@/composables/usePagedList'

const REVIEW_STATUS_TABS = [
  { label: '待审核', value: VIOLATION_STATUS.PENDING },
  { label: '已确认违规', value: VIOLATION_STATUS.CONFIRMED },
  { label: '已放行', value: VIOLATION_STATUS.DISMISSED },
  { label: '申诉撤销', value: VIOLATION_STATUS.OVERTURNED }
]
const APPEAL_STATUS_TABS = [
  { label: '待复核', value: APPEAL_STATUS.PENDING },
  { label: '已通过', value: APPEAL_STATUS.APPROVED },
  { label: '已驳回', value: APPEAL_STATUS.REJECTED }
]

const pageSize = 10
const workspace = ref('reviews')
const acting = ref(false)
const reviewStatus = ref<ViolationStatus>(VIOLATION_STATUS.PENDING)
const appealStatus = ref<AppealStatus>(APPEAL_STATUS.PENDING)
const pendingReviewCount = ref(0)
const pendingAppealCount = ref(0)

// 审核与申诉各自一份独立分页状态机：records / loading / 翻页互不串扰，
// 快速切筛选或切工作区时由 usePagedList 的代数守卫丢弃乱序返回的旧响应（F1）
const {
  records: reviews,
  currentPage: reviewPage,
  total: reviewTotal,
  loading: reviewLoading,
  fetchList: fetchReviews,
  goToFirstPage: resetReviewPage
} = usePagedList<ViolationReview, { status: ViolationStatus }>(getViolations, {
  size: pageSize,
  params: () => ({ status: reviewStatus.value })
})
const {
  records: appeals,
  currentPage: appealPage,
  total: appealTotal,
  loading: appealLoading,
  fetchList: fetchAppeals,
  goToFirstPage: resetAppealPage
} = usePagedList<ViolationAppeal, { status: AppealStatus }>(getAppeals, {
  size: pageSize,
  params: () => ({ status: appealStatus.value })
})

// 骨架屏与刷新按钮始终取当前工作区实例的 loading，两路在途请求互不复位
const loading = computed(() => (workspace.value === 'reviews' ? reviewLoading.value : appealLoading.value))
const confirmForm = reactive({ visible: false, review: null as ViolationReview | null, reason: '', handleNote: '', submitting: false })
const appealHandle = reactive({ visible: false, appeal: null as ViolationAppeal | null, action: 'approve', handleNote: '', submitting: false })

interface BadgeMeta {
  label: string
  badge: string
}

const SOURCE_META: Record<string, BadgeMeta> = {
  LOCAL_RULE: { label: '本地规则命中', badge: 'badge--warn' },
  USER_REPORT: { label: '用户举报', badge: 'badge--buy' },
  CORRECTION: { label: '违规整改', badge: 'badge--sell' }
}

function sourceMeta(source: string) {
  return SOURCE_META[source] || { label: source || '未知来源', badge: 'badge--muted' }
}
function reviewStatusMeta(status: string) {
  const meta: Record<string, BadgeMeta> = {
    [VIOLATION_STATUS.PENDING]: { label: '待审核', badge: 'badge--warn' },
    [VIOLATION_STATUS.CONFIRMED]: { label: '已确认违规', badge: 'badge--danger' },
    [VIOLATION_STATUS.DISMISSED]: { label: '已放行', badge: 'badge--ok' },
    [VIOLATION_STATUS.OVERTURNED]: { label: '申诉已撤销', badge: 'badge--ok' }
  }
  return meta[status] || { label: status, badge: 'badge--muted' }
}
function appealStatusMeta(status: string) {
  const meta: Record<string, BadgeMeta> = {
    [APPEAL_STATUS.PENDING]: { label: '待复核', badge: 'badge--warn' },
    [APPEAL_STATUS.APPROVED]: { label: '已通过', badge: 'badge--ok' },
    [APPEAL_STATUS.REJECTED]: { label: '已驳回', badge: 'badge--muted' }
  }
  return meta[status] || { label: status, badge: 'badge--muted' }
}
function itemStatusText(status: string | undefined) {
  return itemStatusLabel(status || '')
}
function matchedRules(review: ViolationReview) {
  return Array.isArray(review.matchedRules) ? review.matchedRules : []
}

async function fetchCounts() {
  const [reviewResult, appealResult] = await Promise.allSettled([
    getViolations({ page: 1, size: 1, status: VIOLATION_STATUS.PENDING }),
    getAppeals({ page: 1, size: 1, status: APPEAL_STATUS.PENDING })
  ])
  pendingReviewCount.value = reviewResult.status === 'fulfilled' ? Number(reviewResult.value.data?.total || 0) : 0
  pendingAppealCount.value = appealResult.status === 'fulfilled' ? Number(appealResult.value.data?.total || 0) : 0
}

/** 已加载过的工作区：首次打开才拉取，之后切换沿用已有数据（刷新按钮手动更新） */
const loadedWorkspaces = new Set(['reviews'])

function switchWorkspace(next: string) {
  if (workspace.value === next) return
  workspace.value = next
  if (loadedWorkspaces.has(next)) return
  loadedWorkspaces.add(next)
  void (next === 'reviews' ? fetchReviews() : fetchAppeals())
}
function changeReviewStatus(status: ViolationStatus) {
  reviewStatus.value = status
  // params 是函数，fetchList 时取到的永远是最新筛选；回第一页并作废在途旧响应
  resetReviewPage()
  void fetchReviews()
}
function changeAppealStatus(status: AppealStatus) {
  appealStatus.value = status
  resetAppealPage()
  void fetchAppeals()
}
function refreshCurrent() {
  fetchCounts()
  return workspace.value === 'reviews' ? fetchReviews() : fetchAppeals()
}

function openConfirmDialog(review: ViolationReview) {
  confirmForm.review = review
  confirmForm.reason = review.violationReason || ''
  confirmForm.handleNote = ''
  confirmForm.visible = true
}

async function submitConfirm() {
  const review = confirmForm.review
  if (!review) return
  const reason = confirmForm.reason.trim()
  if (!reason) {
    ElMessage.warning('请填写明确的违规原因')
    return
  }
  confirmForm.submitting = true
  acting.value = true
  try {
    await confirmViolation(review.id, { reason, handleNote: confirmForm.handleNote.trim() || null })
    confirmForm.visible = false
    ElMessage.success('已确认违规，商品已下架并执行固定合规扣分')
    await Promise.all([fetchReviews(), fetchCounts()])
  } finally {
    confirmForm.submitting = false
    acting.value = false
  }
}

async function dismissReview(review: ViolationReview) {
  try {
    await ElMessageBox.confirm(`确认放行「${review.originalTitle}」？本次审核记录会标记为已放行。`, '放行内容', {
      confirmButtonText: '确认放行',
      cancelButtonText: '取消',
      type: 'info'
    })
  } catch {
    return
  }
  acting.value = true
  try {
    await dismissViolation(review.id)
    ElMessage.success('已放行该内容')
    await Promise.all([fetchReviews(), fetchCounts()])
  } finally {
    acting.value = false
  }
}

function openAppealHandle(appeal: ViolationAppeal, action: string) {
  appealHandle.appeal = appeal
  appealHandle.action = action
  appealHandle.handleNote = ''
  appealHandle.visible = true
}

async function submitAppealHandle() {
  const appeal = appealHandle.appeal
  if (!appeal) return
  appealHandle.submitting = true
  acting.value = true
  const payload = { handleNote: appealHandle.handleNote.trim() || null }
  try {
    if (appealHandle.action === 'approve') await approveAppeal(appeal.id, payload)
    else await rejectAppeal(appeal.id, payload)
    appealHandle.visible = false
    ElMessage.success(appealHandle.action === 'approve' ? '申诉已通过，关联扣分已撤销' : '申诉已驳回')
    await Promise.all([fetchAppeals(), fetchCounts()])
  } finally {
    appealHandle.submitting = false
    acting.value = false
  }
}

onMounted(async () => {
  await Promise.all([fetchReviews(), fetchCounts()])
})
</script>

<style scoped>
.review-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 18px;
}
.page-head .page-title {
  margin-bottom: 0;
}
.page-head .muted {
  margin-top: 7px;
  max-width: 760px;
}
.workspace-tabs {
  display: inline-flex;
  gap: 2px;
  margin-top: var(--spacing-md);
  padding: 3px;
  background: var(--paper-deep);
  border-radius: var(--r-s);
}
.workspace-tab {
  padding: 9px 26px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ink-soft);
  font-size: 14.5px;
  font-weight: 500;
  cursor: pointer;
  transition:
    color 0.15s,
    background-color 0.15s,
    box-shadow 0.15s;
}
.workspace-tab:hover {
  color: var(--ink);
}
.workspace-tab.active {
  background: var(--white);
  color: var(--ink);
  font-weight: 600;
  box-shadow: var(--shadow-s);
}
.count {
  display: inline-grid;
  place-items: center;
  min-width: 22px;
  height: 22px;
  margin-left: 7px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--primary);
  color: var(--white);
  font-size: 11px;
}
.filter-tabs,
.badge-row,
.matched-rules {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
}
.filter-tabs {
  flex-wrap: wrap;
}
.page-head .btn .ic {
  width: 16px;
  height: 16px;
}
.review-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.review-card,
.appeal-card {
  padding: 22px 24px;
}
.review-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.review-card__head h2 {
  margin-top: 9px;
  font-family: var(--font-display);
  font-size: 20px;
}
.review-card__time,
.rule-version {
  color: var(--ink-soft);
  font-size: 12px;
}
.party-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 16px 0;
}
.party-grid > div {
  padding: 10px 12px;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-s);
  background: var(--paper-deep);
}
.party-grid span,
.appeal-compare span {
  display: block;
  color: var(--ink-soft);
  font-size: 11px;
  font-weight: 800;
}
.party-grid strong {
  display: block;
  margin-top: 3px;
  font-size: 13px;
}
.content-box,
.reason-box,
.appeal-compare > div {
  padding: 14px 16px;
  border-radius: var(--r-s);
}
.content-box {
  border: var(--bw) dashed var(--line-strong);
  background: #faf7f0;
}
.content-box__label {
  margin-bottom: 5px;
  color: var(--ink-soft);
  font-size: 11px;
  font-weight: 900;
}
.content-box p:last-child {
  white-space: pre-wrap;
  line-height: 1.7;
}
.reason-box {
  margin-top: 10px;
  border-left: 4px solid var(--primary);
  background: #fff1e9;
}
.reason-box p {
  margin: 5px 0 9px;
}
.handle-note {
  margin-top: 12px;
  color: var(--ink-soft);
  font-size: 13px;
}
.review-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: var(--bw) solid var(--line);
}
.appeal-compare {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 16px;
}
.appeal-compare > div:first-child {
  background: #fff1e9;
  border-left: 4px solid var(--primary);
}
.appeal-compare > div:last-child {
  background: #eef8f1;
  border-left: 4px solid var(--green);
}
.appeal-compare p {
  margin-top: 6px;
  line-height: 1.7;
  white-space: pre-wrap;
}
.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.dialog-form label {
  display: flex;
  flex-direction: column;
  gap: 7px;
  font-weight: 800;
}
.dialog-form label b {
  color: var(--red);
}
.warning-panel,
.success-panel {
  padding: 12px 14px;
  border: 1.5px solid;
  border-radius: var(--r-s);
  line-height: 1.65;
  font-size: 13px;
}
.warning-panel {
  border-color: #c88719;
  background: #fff4ce;
  color: #6a4700;
}
.success-panel {
  border-color: var(--green);
  background: #e4f6ea;
  color: #1d6b42;
}
.empty-card {
  padding: 54px 24px;
  text-align: center;
}
@media (max-width: 760px) {
  .page-head {
    align-items: stretch;
    flex-direction: column;
  }
  .page-head > .btn {
    align-self: flex-start;
  }
  .party-grid {
    grid-template-columns: 1fr 1fr;
  }
  .appeal-compare {
    grid-template-columns: 1fr;
  }
  .review-card__actions {
    justify-content: stretch;
    flex-direction: column;
  }
}
</style>
