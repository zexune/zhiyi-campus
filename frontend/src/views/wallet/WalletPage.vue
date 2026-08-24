<template>
  <DefaultLayout>
    <div class="wallet-page rise">
      <!-- 页面标题 -->
      <div class="page-title">我的钱包</div>

      <!-- 导航标签 -->
      <div class="nav-tabs">
        <span class="nav-tab active">我的钱包</span>
        <router-link :to="ROUTE_PATH.ORDERS_BOUGHT" class="nav-tab">我买的</router-link>
        <router-link :to="ROUTE_PATH.ORDERS_SOLD" class="nav-tab">我卖的</router-link>
      </div>

      <!-- 余额卡片 -->
      <div class="balance-card card">
        <div class="balance-card__info">
          <div class="balance-card__label">当前余额</div>
          <!-- 加载中 -->
          <div v-if="balanceLoading" class="balance-card__amount">
            <span class="price muted">加载中...</span>
          </div>
          <!-- 加载失败 -->
          <div v-else-if="balanceError" class="balance-card__error">
            <span class="muted">余额加载失败</span>
            <button class="btn btn--sm" @click="fetchBalance">重新加载</button>
          </div>
          <!-- 正常 -->
          <div v-else class="balance-card__amount">
            <span class="price">
              <span class="rmb">¥</span>
              {{ balanceText }}
            </span>
          </div>
        </div>
        <div class="balance-card__actions">
          <button class="btn btn--primary" @click="showRecharge = true">
            <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M12 5v14M5 12h14" /></svg>
            充值
          </button>
          <button class="btn" :disabled="loading" title="刷新流水" aria-label="刷新流水" @click="fetchLogs">
            <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
              <path d="M20 11a8 8 0 1 0-2.34 5.66" />
              <path d="M20 4v7h-7" />
            </svg>
            刷新流水
          </button>
        </div>
      </div>

      <!-- 充值弹窗 -->
      <el-dialog v-model="showRecharge" class="app-dialog" modal-class="app-modal" title="模拟充值" width="420px" append-to-body align-center :close-on-click-modal="false" destroy-on-close>
        <el-form ref="rechargeFormRef" class="recharge-form" :model="rechargeForm" :rules="rechargeRules" @submit.prevent="handleRecharge">
          <el-form-item prop="amount" class="field">
            <label>
              充值金额
              <span class="req">*</span>
            </label>
            <input v-model="rechargeForm.amount" type="number" class="input" placeholder="请输入充值金额（0.01 ~ 10,000.00）" min="0.01" max="10000" step="0.01" @keydown="blockInvalidKeys" />
            <div class="hint">单次充值范围：¥0.01 ~ ¥10,000.00</div>
          </el-form-item>
          <div v-if="rechargeAmountValue > 0" class="recharge-preview">
            充值后余额：
            <span class="price">
              <span class="rmb">¥</span>
              {{ formatPrice(balance + rechargeAmountValue) }}
            </span>
          </div>
        </el-form>
        <template #footer>
          <button class="btn" @click="showRecharge = false">取消</button>
          <button class="btn btn--primary" :disabled="!canRecharge || recharging" @click="handleRecharge">
            {{ recharging ? '充值中...' : '确认充值' }}
          </button>
        </template>
      </el-dialog>

      <!-- 资金流水 -->
      <div class="logs-section">
        <h3 class="logs-title">资金流水</h3>

        <div v-if="loading" class="card card--flat logs-empty">
          <div class="muted">加载中...</div>
        </div>

        <div v-else-if="logsError" class="card card--flat logs-empty">
          <div class="muted">流水加载失败</div>
          <button class="btn btn--sm" style="margin-top: 12px" @click="fetchLogs">重新加载</button>
        </div>

        <div v-else-if="logs.length === 0" class="empty-state">
          <span class="empty-state__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="6" width="18" height="13" rx="2" />
              <path d="M3 10h18M16 15h.01" />
            </svg>
          </span>
          <p>暂无资金变动记录</p>
        </div>

        <!-- 流水列表 -->
        <div v-if="logs.length > 0" class="log-list">
          <div v-for="log in logs" :key="log.id" class="log-item card card--flat">
            <div class="log-item__left">
              <span class="log-type-badge" :class="typeClass(log.type)">
                {{ typeLabel(log.type) }}
              </span>
              <span class="log-remark">{{ log.remark || '—' }}</span>
            </div>
            <div class="log-item__right">
              <span class="log-amount" :class="{ 'is-income': isIncome(log.type) }">{{ isIncome(log.type) ? '+' : '' }}¥{{ formatPrice(log.amount) }}</span>
              <span class="log-balance muted">余额 ¥{{ formatPrice(log.balanceAfter) }}</span>
              <span class="log-time muted">{{ formatDateTime(log.createdAt) }}</span>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div v-if="total > 0" class="logs-pagination">
          <el-pagination v-model:current-page="currentPage" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="fetchLogs" />
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import { getWalletBalance, rechargeWallet, getWalletLogs } from '@/api/wallet'
import { usePagedList } from '@/composables/usePagedList'
import { formatDateTime, formatPrice } from '@/utils/format'
import { validateForm } from '@/utils/formValidate'
import { ROUTE_PATH } from '@/constants/routes'
import { getPending, getOrCreatePending, clearPending } from '@/utils/idempotency'
import { ApiError } from '@/utils/request'

// ---- 余额 ----
const balance = ref(0)
const balanceText = computed(() => formatPrice(balance.value))
const balanceError = ref(false)
const balanceLoading = ref(false)

async function fetchBalance() {
  balanceLoading.value = true
  balanceError.value = false
  try {
    const res = await getWalletBalance()
    balance.value = Number(res.data.balance) || 0
  } catch {
    balanceError.value = true
  } finally {
    balanceLoading.value = false
  }
}

// ---- 充值（声明式校验：0.01 ~ 10000；幂等键闭环防重复入账） ----
const showRecharge = ref(false)
const rechargeFormRef = ref(null)
const rechargeForm = reactive({ amount: '' })
const recharging = ref(false)
/**
 * 充值客户端操作 ID（幂等槽位）：未决期间保持不变以复用原幂等键；
 * 明确成功或明确拒绝（CLEAR 白名单）后换新 UUID，下一次充值视为全新意图。
 */
const rechargeOpId = ref(crypto.randomUUID())

// 打开充值面板时检测未决充值（上次结果不明：超时/断网），提示用户将复用原单据重试
watch(showRecharge, (open) => {
  if (open && getPending('RECHARGE', rechargeOpId.value)) {
    ElMessage.info('存在未完成的充值，已为您恢复，提交时将沿用原单据')
  }
})

const rechargeAmountValue = computed(() => parseFloat(rechargeForm.amount) || 0)
const canRecharge = computed(() => {
  const v = rechargeAmountValue.value
  return v >= 0.01 && v <= 10000
})

const rechargeRules = {
  amount: [
    {
      validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
        const v = parseFloat(value)
        if (Number.isNaN(v) || v < 0.01 || v > 10000) return callback(new Error('充值金额须在 0.01 ~ 10,000.00 之间'))
        callback()
      },
      trigger: 'blur'
    }
  ]
}

/** 阻止科学计数法字符（e/E/+/-）和多余小数点 */
function blockInvalidKeys(e: KeyboardEvent) {
  const blocked = ['e', 'E', '+', '-']
  if (blocked.includes(e.key)) {
    e.preventDefault()
  }
}

async function handleRecharge() {
  // 同步互斥：在 await validateForm 之前置位，杜绝校验窗口内的重复提交
  if (recharging.value) return
  recharging.value = true
  try {
    if (!(await validateForm(rechargeFormRef)) || !canRecharge.value) return
    const amount = parseFloat(rechargeForm.amount)
    // 未决充值复用原幂等键；只有明确结束（成功/CLEAR）才清键
    const pending = getOrCreatePending('RECHARGE', rechargeOpId.value, { amount })
    const res = await rechargeWallet(amount, pending.idempotencyKey)
    balance.value = Number(res.data.balance) || 0
    balanceError.value = false
    clearPending('RECHARGE', rechargeOpId.value)
    rechargeOpId.value = crypto.randomUUID()
    ElMessage.success(`充值成功！当前余额 ¥${balanceText.value}`)
    showRecharge.value = false
    rechargeForm.amount = ''
    // 重置到第一页拉取最新流水
    goToFirstPage()
    fetchLogs()
  } catch (e) {
    // 明确业务拒绝（CLEAR 白名单）：清键换新；超时/网络错误等结果不明场景保留原键，下次复用
    if (e instanceof ApiError && e.idempotencyDisposition === 'CLEAR') {
      clearPending('RECHARGE', rechargeOpId.value)
      rechargeOpId.value = crypto.randomUUID()
    }
    // 其余错误已在拦截器提示
  } finally {
    recharging.value = false
  }
}

// ---- 流水（服务端分页状态机） ----
const { records: logs, currentPage, pageSize, total, loading, loadError: logsError, fetchList: fetchLogs, goToFirstPage } = usePagedList(getWalletLogs)

const TYPE_MAP: Record<string, { label: string; cls: string }> = {
  RECHARGE: { label: '充值', cls: 'badge--ok' },
  PAYMENT: { label: '支出', cls: 'badge--sell' },
  REFUND: { label: '退款', cls: 'badge--buy' },
  INCOME: { label: '收入', cls: 'badge--ok' }
}

function typeLabel(type: string) {
  return TYPE_MAP[type]?.label || type
}

function typeClass(type: string) {
  return TYPE_MAP[type]?.cls || 'badge--muted'
}

function isIncome(type: string) {
  return type === 'RECHARGE' || type === 'REFUND' || type === 'INCOME'
}

// ---- 初始化 ----
onMounted(() => {
  fetchBalance()
  fetchLogs()
})
</script>

<style scoped>
.wallet-page {
  max-width: 860px;
  margin: 0 auto;
}

/* 子导航（分段控件形态，与全站排序选项卡一致） */
.nav-tabs {
  display: inline-flex;
  gap: 2px;
  margin-top: var(--spacing-md);
  max-width: 100%;
  overflow-x: auto;
  padding: 3px;
  background: var(--paper-deep);
  border-radius: var(--r-s);
}

.nav-tab {
  padding: 7px 18px;
  border: none;
  border-radius: 6px;
  font-weight: 500;
  font-size: 14px;
  background: transparent;
  color: var(--ink-soft);
  cursor: pointer;
  text-decoration: none;
  white-space: nowrap;
  transition:
    color 0.15s,
    background-color 0.15s,
    box-shadow 0.15s;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.nav-tab:hover {
  color: var(--ink);
}

.nav-tab.active {
  background: var(--white);
  color: var(--ink);
  font-weight: 600;
  box-shadow: var(--shadow-s);
}

/* 余额卡片：信息在左、操作在右 */
.balance-card {
  margin-top: var(--spacing-lg);
  padding: var(--spacing-lg);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  flex-wrap: wrap;
  background: var(--white);
}

.balance-card__info {
  min-width: 0;
}

.balance-card__label {
  font-size: 13px;
  color: var(--ink-soft);
  font-weight: 500;
  margin-bottom: var(--spacing-xs);
}

.balance-card__amount .price {
  font-size: 40px;
}

.balance-card__error {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) 0;
}

.balance-card__actions {
  display: flex;
  gap: var(--spacing-sm);
}

/* 充值弹窗 */
.recharge-form {
  padding: var(--spacing-sm) 0;
}

.recharge-preview {
  margin-top: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--paper-deep);
  border-radius: var(--r-s);
  font-weight: 500;
}

.recharge-preview .price {
  font-size: 22px;
}

/* 流水区域 */
.logs-section {
  margin-top: var(--spacing-xl);
}

.logs-title {
  font-size: 17px;
  font-weight: 700;
  margin-bottom: var(--spacing-md);
}

.logs-empty {
  padding: var(--spacing-xl);
  text-align: center;
}

/* 流水条目 */
.log-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
}

.log-item__left {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.log-type-badge {
  display: inline-flex;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  border: var(--bw) solid var(--line);
  white-space: nowrap;
}

.log-remark {
  font-size: var(--font-sm);
}

.log-item__right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex-shrink: 0;
}

.log-amount {
  font-weight: 700;
  font-size: var(--font-md);
  min-width: 80px;
  text-align: right;
  color: var(--ink);
}

.log-amount.is-income {
  color: var(--green);
}

.log-balance {
  font-size: var(--font-sm);
  min-width: 100px;
  text-align: right;
}

.log-time {
  font-size: var(--font-sm);
  min-width: 130px;
  text-align: right;
}

/* 分页 */
.logs-pagination {
  display: flex;
  justify-content: center;
  margin-top: var(--spacing-lg);
}

/* 响应式 */
@media (max-width: 600px) {
  .balance-card__amount .price {
    font-size: 36px;
  }

  .log-item {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--spacing-sm);
  }

  .log-item__right {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
