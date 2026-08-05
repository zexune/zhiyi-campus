<template>
  <DefaultLayout>
    <div class="wallet-page rise">
      <!-- 页面标题 -->
      <div class="page-title">
        💰 我的钱包
        <span class="stamp">Wallet</span>
      </div>

      <!-- 导航标签 -->
      <div class="nav-tabs">
        <span class="nav-tab active">💰 我的钱包</span>
        <router-link to="/orders/bought" class="nav-tab">🛒 我买的</router-link>
        <router-link to="/orders/sold" class="nav-tab">📦 我卖的</router-link>
      </div>

      <!-- 余额卡片 -->
      <div class="balance-card card sticker-tilt">
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
            <span class="rmb">¥</span>{{ balanceText }}
          </span>
        </div>
        <div class="balance-card__actions">
          <button class="btn btn--primary" @click="showRecharge = true">
            <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
            充值
          </button>
          <button class="btn" @click="fetchLogs">
            <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>
            刷新流水
          </button>
          <router-link to="/orders/bought" class="btn btn--dark">📋 我的订单</router-link>
        </div>
      </div>

      <!-- 充值弹窗 -->
      <el-dialog
        v-model="showRecharge"
        class="app-dialog"
        modal-class="app-modal"
        title="模拟充值"
        width="420px"
        append-to-body
        align-center
        :close-on-click-modal="false"
        destroy-on-close
      >
        <div class="recharge-form">
          <div class="field">
            <label>充值金额 <span class="req">*</span></label>
            <input
              v-model="rechargeAmount"
              type="number"
              class="input"
              placeholder="请输入充值金额（0.01 ~ 10,000.00）"
              min="0.01"
              max="10000"
              step="0.01"
              @keydown="blockInvalidKeys"
              @keyup.enter="handleRecharge"
            />
            <div class="hint">单次充值范围：¥0.01 ~ ¥10,000.00</div>
          </div>
          <div class="recharge-preview" v-if="rechargeAmount > 0">
            充值后余额：<span class="price"><span class="rmb">¥</span>{{ previewBalance }}</span>
          </div>
        </div>
        <template #footer>
          <button class="btn" @click="showRecharge = false">取消</button>
          <button
            class="btn btn--primary"
            :disabled="!canRecharge || recharging"
            @click="handleRecharge"
          >
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
          <button class="btn btn--sm" style="margin-top:12px" @click="fetchLogs">重新加载</button>
        </div>

        <div v-else-if="logs.length === 0" class="card card--flat logs-empty">
          <div class="muted">暂无资金变动记录</div>
        </div>

        <!-- 流水列表 -->
        <div class="log-list" v-if="logs.length > 0">
          <div
            v-for="log in logs"
            :key="log.id"
            class="log-item card card--flat"
          >
            <div class="log-item__left">
              <span class="log-type-badge" :class="typeClass(log.type)">
                {{ typeLabel(log.type) }}
              </span>
              <span class="log-remark">{{ log.remark || '—' }}</span>
            </div>
            <div class="log-item__right">
              <span class="log-amount" :class="{ 'is-income': isIncome(log.type) }">
                {{ isIncome(log.type) ? '+' : '' }}¥{{ fmt(log.amount) }}
              </span>
              <span class="log-balance muted">余额 ¥{{ fmt(log.balanceAfter) }}</span>
              <span class="log-time muted">{{ fmtTime(log.createdAt) }}</span>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div class="logs-pagination" v-if="total > 0">
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="total"
            layout="prev, pager, next"
            background
            @current-change="fetchLogs"
          />
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import { getWalletBalance, rechargeWallet, getWalletLogs } from '@/api/wallet'

// ---- 余额 ----
const balance = ref(0)
const balanceText = computed(() => fmt(balance.value))
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

// ---- 充值 ----
const showRecharge = ref(false)
const rechargeAmount = ref('')
const recharging = ref(false)

const canRecharge = computed(() => {
  const v = parseFloat(rechargeAmount.value)
  return v >= 0.01 && v <= 10000
})

/** 阻止科学计数法字符（e/E/+/-）和多余小数点 */
function blockInvalidKeys(e) {
  const blocked = ['e', 'E', '+', '-']
  if (blocked.includes(e.key)) {
    e.preventDefault()
  }
}

const previewBalance = computed(() => {
  const v = parseFloat(rechargeAmount.value) || 0
  return fmt(balance.value + v)
})

async function handleRecharge() {
  if (!canRecharge.value) return
  recharging.value = true
  try {
    const amount = parseFloat(rechargeAmount.value)
    const res = await rechargeWallet(amount)
    balance.value = Number(res.data.balance) || 0
    balanceError.value = false
    ElMessage.success(`充值成功！当前余额 ¥${balanceText.value}`)
    showRecharge.value = false
    rechargeAmount.value = ''
    // 重置到第一页拉取最新流水
    currentPage.value = 1
    fetchLogs()
  } catch {
    // 错误已在拦截器提示
  } finally {
    recharging.value = false
  }
}

// ---- 流水 ----
const logs = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const logsError = ref(false)

async function fetchLogs() {
  loading.value = true
  logsError.value = false
  try {
    const res = await getWalletLogs({ page: currentPage.value, size: pageSize.value })
    logs.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    logsError.value = true
  } finally {
    loading.value = false
  }
}

// ---- 工具函数 ----
function fmt(val) {
  return Number(val || 0).toFixed(2)
}

function fmtTime(val) {
  if (!val) return ''
  const d = new Date(val)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const TYPE_MAP = {
  RECHARGE: { label: '充值', cls: 'badge--ok' },
  PAYMENT:  { label: '支出', cls: 'badge--sell' },
  REFUND:   { label: '退款', cls: 'badge--buy' },
  INCOME:   { label: '收入', cls: 'badge--ok' },
}

function typeLabel(type) {
  return TYPE_MAP[type]?.label || type
}

function typeClass(type) {
  return TYPE_MAP[type]?.cls || 'badge--muted'
}

function isIncome(type) {
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
  max-width: 720px;
  margin: 0 auto;
}

/* 子导航 */
.nav-tabs {
  display: flex;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-md);
  flex-wrap: wrap;
}

.nav-tab {
  padding: 8px 18px;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  font-weight: 700;
  font-size: 14px;
  background: var(--white);
  color: var(--ink);
  cursor: pointer;
  text-decoration: none;
  transition: all .15s;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.nav-tab:hover {
  background: var(--yellow);
}

.nav-tab.active {
  background: var(--ink);
  color: var(--paper);
}

/* 余额卡片 */
.balance-card {
  margin-top: var(--spacing-lg);
  padding: var(--spacing-xl) var(--spacing-lg);
  text-align: center;
  background: var(--white);
}

.balance-card__label {
  font-size: var(--font-md);
  color: var(--ink-soft);
  font-weight: 700;
  margin-bottom: var(--spacing-sm);
}

.balance-card__amount .price {
  font-size: 48px;
}

.balance-card__error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) 0;
}

.balance-card__actions {
  margin-top: var(--spacing-lg);
  display: flex;
  justify-content: center;
  gap: var(--spacing-md);
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
  font-family: var(--font-display);
  font-size: 22px;
  letter-spacing: .5px;
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
  border: var(--bw) solid var(--ink);
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
