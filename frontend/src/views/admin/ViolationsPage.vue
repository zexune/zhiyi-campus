<template>
  <AdminLayout>
    <div class="review-page">
      <header class="page-head">
        <div>
          <p class="eyebrow">CONTENT GOVERNANCE</p>
          <h1 class="page-title">内容治理工作台</h1>
          <p class="muted">内容处罚与账号封禁完全分离；这里只处理商品内容、固定合规扣分及卖家申诉。</p>
        </div>
        <button class="btn" :disabled="loading" @click="refreshCurrent">刷新</button>
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
        <div class="filter-tabs" aria-label="内容审核状态">
          <button v-for="tab in REVIEW_STATUS_TABS" :key="tab.value" class="btn btn--sm" :class="{ 'btn--dark': reviewStatus === tab.value }" @click="changeReviewStatus(tab.value)">
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
              <div class="review-card__time">{{ formatDate(review.createdAt) }}</div>
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
        <div class="filter-tabs" aria-label="申诉状态">
          <button v-for="tab in APPEAL_STATUS_TABS" :key="tab.value" class="btn btn--sm" :class="{ 'btn--dark': appealStatus === tab.value }" @click="changeAppealStatus(tab.value)">
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
          <div class="warning-panel">确认后商品会下架，并按平台当前固定值扣除卖家合规分（默认 5 分）。该操作不会封禁账号，账号封禁只能在用户管理中执行。</div>
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

<script setup>
import { onMounted, reactive, ref } from 'vue'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import { approveAppeal, confirmViolation, dismissViolation, getAppeals, getViolations, rejectAppeal } from '@/api/admin'
import { APPEAL_STATUS, VIOLATION_STATUS } from '@/constants/domain'
import { itemStatusLabel } from '@/utils/trade'

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
const loading = ref(false)
const acting = ref(false)
const reviews = ref([])
const reviewPage = ref(1)
const reviewTotal = ref(0)
const reviewStatus = ref(VIOLATION_STATUS.PENDING)
const appeals = ref([])
const appealPage = ref(1)
const appealTotal = ref(0)
const appealStatus = ref(APPEAL_STATUS.PENDING)
const pendingReviewCount = ref(0)
const pendingAppealCount = ref(0)
const confirmForm = reactive({ visible: false, review: null, reason: '', handleNote: '', submitting: false })
const appealHandle = reactive({ visible: false, appeal: null, action: 'approve', handleNote: '', submitting: false })

function sourceMeta(source) {
  return (
    {
      LOCAL_RULE: { label: '本地规则命中', badge: 'badge--warn' },
      USER_REPORT: { label: '用户举报', badge: 'badge--buy' },
      CORRECTION: { label: '违规整改', badge: 'badge--sell' }
    }[source] || { label: source || '未知来源', badge: 'badge--muted' }
  )
}
function reviewStatusMeta(status) {
  return (
    {
      [VIOLATION_STATUS.PENDING]: { label: '待审核', badge: 'badge--warn' },
      [VIOLATION_STATUS.CONFIRMED]: { label: '已确认违规', badge: 'badge--danger' },
      [VIOLATION_STATUS.DISMISSED]: { label: '已放行', badge: 'badge--ok' },
      [VIOLATION_STATUS.OVERTURNED]: { label: '申诉已撤销', badge: 'badge--ok' }
    }[status] || { label: status, badge: 'badge--muted' }
  )
}
function appealStatusMeta(status) {
  return (
    {
      [APPEAL_STATUS.PENDING]: { label: '待复核', badge: 'badge--warn' },
      [APPEAL_STATUS.APPROVED]: { label: '已通过', badge: 'badge--ok' },
      [APPEAL_STATUS.REJECTED]: { label: '已驳回', badge: 'badge--muted' }
    }[status] || { label: status, badge: 'badge--muted' }
  )
}
function itemStatusText(status) {
  return itemStatusLabel(status)
}
function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}
function matchedRules(review) {
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

async function fetchReviews() {
  loading.value = true
  try {
    const res = await getViolations({ page: reviewPage.value, size: pageSize, status: reviewStatus.value })
    reviews.value = res.data?.records || []
    reviewTotal.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

async function fetchAppeals() {
  loading.value = true
  try {
    const res = await getAppeals({ page: appealPage.value, size: pageSize, status: appealStatus.value })
    appeals.value = res.data?.records || []
    appealTotal.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

function switchWorkspace(next) {
  if (workspace.value === next) return
  workspace.value = next
  if (next === 'reviews') fetchReviews()
  else fetchAppeals()
}
function changeReviewStatus(status) {
  reviewStatus.value = status
  reviewPage.value = 1
  fetchReviews()
}
function changeAppealStatus(status) {
  appealStatus.value = status
  appealPage.value = 1
  fetchAppeals()
}
function refreshCurrent() {
  fetchCounts()
  return workspace.value === 'reviews' ? fetchReviews() : fetchAppeals()
}

function openConfirmDialog(review) {
  confirmForm.review = review
  confirmForm.reason = review.violationReason || ''
  confirmForm.handleNote = ''
  confirmForm.visible = true
}

async function submitConfirm() {
  const reason = confirmForm.reason.trim()
  if (!reason) {
    ElMessage.warning('请填写明确的违规原因')
    return
  }
  confirmForm.submitting = true
  acting.value = true
  try {
    await confirmViolation(confirmForm.review.id, { reason, handleNote: confirmForm.handleNote.trim() || null })
    confirmForm.visible = false
    ElMessage.success('已确认违规，商品已下架并执行固定合规扣分')
    await Promise.all([fetchReviews(), fetchCounts()])
  } finally {
    confirmForm.submitting = false
    acting.value = false
  }
}

async function dismissReview(review) {
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

function openAppealHandle(appeal, action) {
  appealHandle.appeal = appeal
  appealHandle.action = action
  appealHandle.handleNote = ''
  appealHandle.visible = true
}

async function submitAppealHandle() {
  appealHandle.submitting = true
  acting.value = true
  const payload = { handleNote: appealHandle.handleNote.trim() || null }
  try {
    if (appealHandle.action === 'approve') await approveAppeal(appealHandle.appeal.id, payload)
    else await rejectAppeal(appealHandle.appeal.id, payload)
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
.eyebrow {
  color: var(--primary);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.16em;
}
.page-head .muted {
  margin-top: 7px;
  max-width: 760px;
}
.workspace-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-m);
  background: var(--white);
  overflow: hidden;
  box-shadow: var(--shadow-s);
}
.workspace-tab {
  padding: 15px 20px;
  border: 0;
  background: transparent;
  color: var(--ink);
  font-size: 16px;
  font-weight: 900;
  cursor: pointer;
}
.workspace-tab + .workspace-tab {
  border-left: var(--bw) solid var(--ink);
}
.workspace-tab.active {
  background: var(--ink);
  color: var(--white);
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
  border: 1.5px solid #d8cebb;
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
  border: 1.5px dashed #cfc3ad;
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
  border-top: 1.5px dashed #d8cebb;
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
