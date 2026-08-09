<template>
  <DefaultLayout>
    <div class="my-items-page">
      <div class="page-head">
        <div>
          <h1 class="page-title">我的发布 <span class="stamp">MY POSTS</span></h1>
          <p class="muted">商品状态、内容审核与订单占用分别管理，避免互相混淆。</p>
        </div>
        <router-link to="/publish" class="btn btn--primary">发布商品</router-link>
      </div>

      <div class="status-tabs" aria-label="商品状态筛选">
        <button
          v-for="tab in STATUS_TABS"
          :key="tab.value"
          class="btn btn--sm"
          :class="{ 'btn--dark': statusFilter === tab.value }"
          @click="handleStatusChange(tab.value)"
        >{{ tab.label }}</button>
      </div>

      <template v-if="items.length">
        <div class="item-list">
          <article v-for="item in items" :key="item.id" class="card item-row">
            <router-link :to="`/item/${item.id}`" class="item-row__thumb" :class="phClass(item.id)">
              <img v-if="mainImage(item)" :src="mainImage(item)" :alt="item.title" />
            </router-link>

            <div class="item-row__body">
              <div class="item-row__title">
                <span class="badge" :class="item.type === 'BUY' ? 'badge--buy' : 'badge--sell'">
                  {{ itemTypeLabel(item.type) }}
                </span>
                <router-link :to="`/item/${item.id}`">{{ item.title }}</router-link>
              </div>
              <div class="item-row__meta muted">
                <span>浏览 {{ item.viewCount ?? 0 }}</span>
                <span>发布于 {{ formatDate(item.createdAt) }}</span>
              </div>
              <div class="state-notes">
                <span v-if="item.reserved" class="badge badge--warn">订单进行中</span>
                <span v-if="item.appealStatus" class="badge" :class="appealBadge(item.appealStatus)">
                  {{ appealStatusText(item.appealStatus) }}
                </span>
                <span v-if="item.moderationStatus === 'REJECTED'" class="muted">已确认内容违规，可整改或申诉</span>
              </div>
            </div>

            <div class="item-row__price"><PriceTag :value="item.price" /></div>
            <div class="item-row__status">
              <span class="badge" :class="statusBadge(displayStatus(item))">{{ statusText(displayStatus(item)) }}</span>
            </div>

            <div class="item-row__actions">
              <router-link
                v-if="canEdit(item)"
                :to="`/item/${item.id}/edit`"
                class="btn btn--sm btn--yellow edit-button"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>
                {{ item.moderationStatus === 'REJECTED' ? '整改' : '编辑' }}
              </router-link>
              <button v-if="canOffShelf(item)" class="btn btn--sm" :disabled="acting" @click="handleOffShelf(item)">下架</button>
              <button v-if="canRelist(item)" class="btn btn--sm btn--green" :disabled="acting" @click="handleRelist(item)">重新上架</button>
              <button v-if="item.appealable" class="btn btn--sm btn--primary" :disabled="acting" @click="openAppeal(item)">申诉</button>
              <button v-if="canDelete(item)" class="btn btn--sm btn--danger" :disabled="acting" @click="handleDelete(item)">删除</button>
              <span v-if="!hasActions(item)" class="muted action-hint">当前不可操作</span>
            </div>
          </article>
        </div>

        <el-pagination
          v-if="total > pageSize"
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="fetchItems"
        />
      </template>

      <div v-else class="card empty-card">
        <p v-if="loadError" class="muted">{{ loadError }}</p>
        <template v-else>
          <p class="muted">该筛选条件下暂无商品</p>
          <router-link to="/publish" class="btn btn--primary">去发布第一件闲置 →</router-link>
        </template>
      </div>

      <el-dialog
        v-model="appealForm.visible"
        title="提交内容违规申诉"
        width="min(540px, 92vw)"
        :close-on-click-modal="!appealForm.submitting"
      >
        <div class="appeal-form">
          <p>商品：<strong>{{ appealForm.item?.title }}</strong></p>
          <p class="muted">每次已确认的违规记录仅能申诉一次，请在确认违规后的 7 天内说明理由。申诉成功后会撤销本次扣分；不存在其他已确认违规时，商品将重新上架。</p>
          <el-input
            v-model="appealForm.reason"
            type="textarea"
            :rows="5"
            minlength="10"
            maxlength="500"
            show-word-limit
            placeholder="请填写 10-500 字的申诉理由"
          />
        </div>
        <template #footer>
          <button class="btn" :disabled="appealForm.submitting" @click="appealForm.visible = false">取消</button>
          <button class="btn btn--primary" :disabled="appealForm.submitting" @click="submitAppeal">
            {{ appealForm.submitting ? '提交中...' : '确认提交' }}
          </button>
        </template>
      </el-dialog>
    </div>
  </DefaultLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import PriceTag from '@/components/common/PriceTag.vue'
import { deleteItem, getMyItems, offShelfItem, relistItem, submitItemAppeal } from '@/api/item'
import { buildMyItemsParams } from './myItemsQuery.js'

const STATUS_TABS = [
  { label: '全部', value: '' },
  { label: '在售中', value: 'ON_SALE' },
  { label: '审核中', value: 'REVIEWING' },
  { label: '已售出', value: 'SOLD' },
  { label: '已下架', value: 'OFF_SHELF' },
]
const STATUS_TEXT = { ON_SALE: '在售中', REVIEWING: '审核中', SOLD: '已售出', OFF_SHELF: '已下架' }
const STATUS_BADGE = { ON_SALE: 'badge--ok', REVIEWING: 'badge--warn', SOLD: 'badge--muted', OFF_SHELF: 'badge--muted' }
const PH = ['ph-a', 'ph-b', 'ph-c', 'ph-d', 'ph-e', 'ph-f']

const items = ref([])
const page = ref(1)
const pageSize = 10
const total = ref(0)
const statusFilter = ref('')
const acting = ref(false)
const loadError = ref('')
const appealForm = reactive({ visible: false, item: null, reason: '', submitting: false })

function displayStatus(item) { return item.moderationStatus === 'PENDING' ? 'REVIEWING' : item.status }
function statusText(status) { return STATUS_TEXT[status] || status }
function statusBadge(status) { return STATUS_BADGE[status] || 'badge--muted' }
function phClass(id) { return PH[Number(id) % PH.length] }
function formatDate(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '' }
function itemTypeLabel(type) { return { SELL: '出售', BUY: '求购', SWAP: '换物', ERRAND: '跑腿' }[type] || type }
function appealStatusText(status) { return { PENDING: '申诉审核中', APPROVED: '申诉已通过', REJECTED: '申诉未通过' }[status] || status }
function appealBadge(status) { return status === 'APPROVED' ? 'badge--ok' : status === 'PENDING' ? 'badge--warn' : 'badge--muted' }

function mainImage(item) {
  try {
    const images = typeof item.images === 'string' ? JSON.parse(item.images) : item.images
    return Array.isArray(images) && images.length ? images[0] : ''
  } catch { return '' }
}

function canEdit(item) {
  return ['ON_SALE', 'OFF_SHELF'].includes(item.status) && item.moderationStatus !== 'PENDING' && !item.reserved
}
function canOffShelf(item) { return item.status === 'ON_SALE' && item.moderationStatus === 'PASSED' && !item.reserved }
function canRelist(item) { return item.status === 'OFF_SHELF' && item.moderationStatus === 'PASSED' && !item.reserved }
function canDelete(item) { return ['ON_SALE', 'OFF_SHELF'].includes(item.status) && item.moderationStatus === 'PASSED' && !item.reserved }
function hasActions(item) { return canEdit(item) || canOffShelf(item) || canRelist(item) || canDelete(item) || item.appealable }

async function fetchItems() {
  try {
    const res = await getMyItems(buildMyItemsParams(page.value, pageSize, statusFilter.value))
    items.value = res.data?.records || res.data || []
    total.value = Number(res.data?.total ?? items.value.length)
    loadError.value = ''
  } catch {
    loadError.value = '商品列表加载失败，请稍后重试'
  }
}

function handleStatusChange(status) {
  if (statusFilter.value === status) return
  statusFilter.value = status
  page.value = 1
  fetchItems()
}

async function handleOffShelf(item) {
  acting.value = true
  try {
    await offShelfItem(item.id)
    ElMessage.success('商品已下架')
    await fetchItems()
  } finally { acting.value = false }
}

async function handleRelist(item) {
  try {
    await ElMessageBox.confirm(`重新上架「${item.title}」前将执行本地合规检测，命中风险会转入人工审核。`, '重新上架', {
      confirmButtonText: '检测并上架', cancelButtonText: '取消', type: 'info',
    })
  } catch { return }
  acting.value = true
  try {
    const res = await relistItem(item.id)
    if (res.data?.moderationStatus === 'PENDING') ElMessage.warning('检测到风险内容，已提交管理员审核')
    else ElMessage.success('检测通过，商品已重新上架')
    await fetchItems()
  } finally { acting.value = false }
}

function openAppeal(item) {
  appealForm.item = item
  appealForm.reason = ''
  appealForm.visible = true
}

async function submitAppeal() {
  const reason = appealForm.reason.trim()
  if (reason.length < 10) {
    ElMessage.warning('申诉理由至少需要 10 个字')
    return
  }
  appealForm.submitting = true
  try {
    await submitItemAppeal(appealForm.item.id, { reason })
    appealForm.visible = false
    ElMessage.success('申诉已提交，请等待管理员复核')
    await fetchItems()
  } finally { appealForm.submitting = false }
}

async function handleDelete(item) {
  try {
    await ElMessageBox.confirm(`确认删除「${item.title}」吗？删除后不可恢复。`, '删除商品', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    })
  } catch { return }
  acting.value = true
  try {
    await deleteItem(item.id)
    ElMessage.success('商品已删除')
    await fetchItems()
  } finally { acting.value = false }
}

onMounted(fetchItems)
</script>

<style scoped>
.my-items-page { display: flex; flex-direction: column; gap: var(--spacing-lg); }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 18px; }
.page-head .muted { margin-top: 6px; }
.status-tabs { display: flex; gap: 10px; flex-wrap: wrap; }
.item-list { display: flex; flex-direction: column; gap: 14px; }
.item-row { display: grid; grid-template-columns: 84px minmax(220px, 1fr) auto auto minmax(170px, auto); gap: 16px; align-items: center; padding: 14px 18px; }
.item-row__thumb { width: 84px; height: 84px; border: var(--bw) solid var(--ink); border-radius: var(--r-s); overflow: hidden; }
.item-row__thumb img { width: 100%; height: 100%; object-fit: cover; }
.item-row__title { display: flex; align-items: center; gap: 8px; font-weight: 700; font-size: 15px; }
.item-row__title a:hover { color: var(--primary); }
.item-row__meta, .state-notes { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; font-size: 12.5px; margin-top: 6px; }
.item-row__actions { display: flex; gap: 8px; justify-content: flex-end; flex-wrap: wrap; }
.edit-button svg { width: 15px; height: 15px; flex: 0 0 15px; }
.action-hint { font-size: 12px; }
.appeal-form { display: flex; flex-direction: column; gap: 14px; }
.empty-card { padding: 48px; text-align: center; display: flex; flex-direction: column; gap: 16px; align-items: center; }
@media (max-width: 900px) {
  .item-row { grid-template-columns: 72px 1fr auto; }
  .item-row__thumb { width: 72px; height: 72px; }
  .item-row__price { grid-column: 3; grid-row: 1; }
  .item-row__status, .item-row__actions { grid-column: 2 / -1; justify-self: start; }
  .item-row__actions { justify-content: flex-start; }
}
@media (max-width: 600px) {
  .page-head { align-items: stretch; flex-direction: column; }
  .page-head > .btn { align-self: flex-start; }
  .item-row { grid-template-columns: 64px 1fr; }
  .item-row__thumb { width: 64px; height: 64px; }
  .item-row__price, .item-row__status, .item-row__actions { grid-column: 2; grid-row: auto; }
}
</style>
