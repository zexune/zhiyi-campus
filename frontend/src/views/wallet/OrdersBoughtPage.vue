<template>
  <DefaultLayout>
    <div class="orders-page rise">
      <!-- 页面标题 -->
      <div class="page-title">
        🛒 我买的
        <span class="stamp">Orders</span>
      </div>

      <!-- 导航标签 -->
      <div class="nav-tabs">
        <router-link to="/wallet" class="nav-tab">💰 我的钱包</router-link>
        <span class="nav-tab active">🛒 我买的</span>
        <router-link to="/orders/sold" class="nav-tab">📦 我卖的</router-link>
      </div>

      <!-- 状态筛选 -->
      <div class="filter-bar">
        <button
          v-for="f in filters"
          :key="f.value"
          class="btn btn--sm"
          :class="currentFilter === f.value ? 'btn--dark' : 'btn--ghost'"
          @click="switchFilter(f.value)"
        >{{ f.label }}</button>
      </div>

      <!-- 加载 / 空 / 错误 -->
      <div v-if="loading" class="card card--flat state-card">
        <span class="muted">加载中...</span>
      </div>

      <div v-else-if="loadError" class="card card--flat state-card">
        <span class="muted">订单加载失败</span>
        <button class="btn btn--sm" style="margin-top:12px" @click="fetchOrders">重新加载</button>
      </div>

      <div v-else-if="orders.length === 0" class="card card--flat state-card">
        <span class="muted">暂无订单记录</span>
      </div>

      <!-- 订单列表 -->
      <div v-else class="order-list">
        <div
          v-for="o in orders"
          :key="o.id"
          class="order-item card"
        >
          <!-- 商品封面 -->
          <router-link :to="`/item/${o.itemId}`" class="order-cover">
            <div v-if="o.itemCover" class="order-cover__img">
              <img :src="o.itemCover" :alt="o.itemTitle" />
            </div>
            <div v-else class="order-cover__ph" :class="phClass(o.itemId)">
              <span class="muted">暂无图片</span>
            </div>
          </router-link>

          <!-- 信息区 -->
          <div class="order-info">
            <router-link :to="`/item/${o.itemId}`" class="order-title">
              {{ o.itemTitle }}
            </router-link>
            <div class="order-meta">
              <span class="price"><span class="rmb">¥</span>{{ fmt(o.price) }}</span>
              <span class="muted">·</span>
              <span class="muted">卖家：{{ o.peerNickname || '—' }}</span>
            </div>
            <div class="order-time muted">{{ fmtTime(o.createdAt) }}</div>
          </div>

          <!-- 状态 & 操作 -->
          <div class="order-actions">
            <span class="badge" :class="statusBadge(o.status)">{{ statusLabel(o.status) }}</span>

            <template v-if="o.status === 'WAITING_MEET'">
              <button
                class="btn btn--green btn--sm"
                :disabled="actingId === o.id"
                @click="handleConfirm(o)"
              >
                {{ actingId === o.id ? '处理中...' : '确认收货' }}
              </button>
              <button
                class="btn btn--sm"
                :disabled="actingId === o.id"
                @click="handleCancel(o)"
              >
                取消订单
              </button>
            </template>

            <template v-else-if="o.status === 'COMPLETED'">
              <button
                v-if="o.reviewed === false"
                class="btn btn--yellow btn--sm"
                @click="openReview(o)"
              >
                ⭐ 评价卖家
              </button>
              <span v-else-if="o.reviewed" class="muted order-extra">已评价</span>
              <div class="order-extra muted">
                {{ fmtTime(o.completedAt) }} 完成
              </div>
            </template>
            <div v-else-if="o.status === 'CANCELLED'" class="order-extra muted">
              {{ fmtTime(o.cancelledAt) }} 取消
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div class="logs-pagination" v-if="total > pageSize">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="fetchOrders"
        />
      </div>

    </div>

    <OrderReviewDialog
      :visible="reviewVisible"
      :order="reviewingOrder"
      :submitting="submittingReview"
      @close="reviewVisible = false"
      @submit="handleSubmitReview"
    />
  </DefaultLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import OrderReviewDialog from '@/components/trade/OrderReviewDialog.vue'
import { getBoughtOrders, confirmReceipt, cancelOrder, reviewOrder } from '@/api/order'

// ---- 评价弹窗（A7）----
const reviewVisible = ref(false)
const reviewingOrder = ref(null)
const submittingReview = ref(false)

function openReview(order) {
  reviewingOrder.value = order
  reviewVisible.value = true
}

async function handleSubmitReview(reviewForm) {
  submittingReview.value = true
  try {
    await reviewOrder(reviewingOrder.value.id, reviewForm)
    ElMessage.success('评价成功！')
    reviewVisible.value = false
    fetchOrders()
  } catch (e) {
    // 提示由 request.js 处理
  } finally {
    submittingReview.value = false
  }
}

// ---- 筛选 ----
const filters = [
  { label: '全部', value: '' },
  { label: '待见面', value: 'WAITING_MEET' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' },
]
const currentFilter = ref('')

function switchFilter(val) {
  currentFilter.value = val
  currentPage.value = 1
  fetchOrders()
}

// ---- 订单列表 ----
const orders = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const loadError = ref(false)
const actingId = ref(null) // 正在操作的订单 ID，防重复点击

async function fetchOrders() {
  loading.value = true
  loadError.value = false
  try {
    const params = { page: currentPage.value, size: pageSize.value }
    if (currentFilter.value) params.status = currentFilter.value
    const res = await getBoughtOrders(params)
    orders.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

// ---- 确认收货 ----
async function handleConfirm(order) {
  try {
    await ElMessageBox.confirm(
      `确认已收到「${order.itemTitle}」？确认后钱款将打给卖家，不可撤销。`,
      '确认收货',
      { confirmButtonText: '确认收货', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // 用户取消
  }

  actingId.value = order.id
  try {
    await confirmReceipt(order.id)
    ElMessage.success('收货确认成功！')
    fetchOrders()
  } catch {
    // 错误已在拦截器提示
  } finally {
    actingId.value = null
  }
}

// ---- 取消订单 ----
async function handleCancel(order) {
  try {
    await ElMessageBox.confirm(
      `确定取消「${order.itemTitle}」的订单？取消后钱款将退回你的钱包，商品将重新上架。`,
      '取消订单',
      { confirmButtonText: '确认取消', cancelButtonText: '返回', type: 'warning' }
    )
  } catch {
    return
  }

  actingId.value = order.id
  try {
    await cancelOrder(order.id)
    ElMessage.success('订单已取消，退款已到账')
    fetchOrders()
  } catch {
    // 错误已在拦截器提示
  } finally {
    actingId.value = null
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

const STATUS_MAP = {
  WAITING_MEET: { label: '待见面', cls: 'badge--warn' },
  COMPLETED:    { label: '已完成', cls: 'badge--ok' },
  CANCELLED:    { label: '已取消', cls: 'badge--muted' },
}

function statusLabel(s) {
  return STATUS_MAP[s]?.label || s
}

function statusBadge(s) {
  return STATUS_MAP[s]?.cls || 'badge--muted'
}

function phClass(id) {
  const map = ['ph-a', 'ph-b', 'ph-c', 'ph-d', 'ph-e', 'ph-f']
  return map[(id || 0) % map.length]
}

// ---- 初始化 ----
onMounted(() => {
  fetchOrders()
})
</script>

<style scoped>
.orders-page {
  max-width: 820px;
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

/* 筛选栏 */
.filter-bar {
  display: flex;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-md);
  flex-wrap: wrap;
}

/* 状态卡片 */
.state-card {
  margin-top: var(--spacing-lg);
  padding: var(--spacing-xl);
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
}

/* 订单列表 */
.order-list {
  margin-top: var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

/* 订单条目 */
.order-item {
  display: flex;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  align-items: center;
}

/* 封面 */
.order-cover {
  flex-shrink: 0;
}

.order-cover__img {
  width: 80px;
  height: 80px;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  overflow: hidden;
}

.order-cover__img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.order-cover__ph {
  width: 80px;
  height: 80px;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  display: grid;
  place-items: center;
  font-size: 11px;
}

/* 信息 */
.order-info {
  flex: 1;
  min-width: 0;
}

.order-title {
  font-weight: 900;
  font-size: 16px;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.order-title:hover {
  color: var(--primary);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.order-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}

.order-meta .price {
  font-size: 17px;
}

.order-time {
  font-size: 12.5px;
  margin-top: 2px;
}

/* 操作区 */
.order-actions {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.order-actions .badge {
  font-size: 12px;
}

.order-extra {
  font-size: 12px;
  text-align: right;
  line-height: 1.5;
}

/* 分页 */
.logs-pagination {
  display: flex;
  justify-content: center;
  margin-top: var(--spacing-lg);
}

/* 响应式 */
@media (max-width: 640px) {
  .order-item {
    flex-wrap: wrap;
  }

  .order-cover__img,
  .order-cover__ph {
    width: 64px;
    height: 64px;
  }

  .order-actions {
    width: 100%;
    flex-direction: row;
    justify-content: flex-end;
    align-items: center;
  }
}

</style>
