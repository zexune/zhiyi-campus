<template>
  <DefaultLayout>
    <div class="my-items-page">
      <div class="page-head">
        <div>
          <h1 class="page-title">我的发布</h1>
        </div>
        <router-link :to="ROUTE_PATH.PUBLISH" class="btn btn--primary">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M12 5v14M5 12h14" /></svg>
          发布商品
        </router-link>
      </div>

      <div class="status-tabs seg-tabs" aria-label="商品状态筛选">
        <button v-for="tab in STATUS_TABS" :key="tab.value" type="button" :class="{ active: statusFilter === tab.value }" @click="handleStatusChange(tab.value)">{{ tab.label }}</button>
      </div>

      <template v-if="items.length">
        <div class="item-list">
          <article v-for="item in items" :key="item.id" class="card item-row">
            <router-link :to="ROUTE_PATH.item(item.id)" class="item-row__thumb" :class="placeholderClass(item.id)">
              <img v-if="mainImage(item)" :src="mainImage(item)" :alt="item.title" />
            </router-link>

            <div class="item-row__body">
              <div class="item-row__title">
                <span class="badge" :class="item.type === ITEM_TYPE.BUY ? 'badge--buy' : 'badge--sell'">
                  {{ itemTypeLabel(item.type) }}
                </span>
                <router-link :to="ROUTE_PATH.item(item.id)">{{ item.title }}</router-link>
              </div>
              <div class="item-row__meta muted">
                <span>浏览 {{ item.viewCount ?? 0 }}</span>
                <span>发布于 {{ formatDateTime(item.createdAt) }}</span>
              </div>
              <div class="state-notes">
                <span v-if="item.reserved" class="badge badge--warn">订单进行中</span>
                <span v-if="item.appealStatus" class="badge" :class="appealBadge(item.appealStatus)">
                  {{ appealStatusText(item.appealStatus) }}
                </span>
                <span v-if="item.moderationStatus === MODERATION_STATUS.REJECTED" class="muted">已确认内容违规，可整改或申诉</span>
              </div>
            </div>

            <div class="item-row__price"><PriceTag :value="item.price" /></div>
            <div class="item-row__status">
              <span class="badge" :class="statusBadge(displayStatus(item))">{{ statusText(displayStatus(item)) }}</span>
            </div>

            <div class="item-row__actions">
              <router-link v-if="canEdit(item)" :to="ROUTE_PATH.editItem(item.id)" class="btn btn--sm edit-button">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 20h9" />
                  <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
                </svg>
                {{ item.moderationStatus === MODERATION_STATUS.REJECTED ? '整改' : '编辑' }}
              </router-link>
              <button v-if="canOffShelf(item)" class="btn btn--sm" :disabled="acting" @click="handleOffShelf(item)">下架</button>
              <button v-if="canRelist(item)" class="btn btn--sm btn--green" :disabled="acting" @click="handleRelist(item)">重新上架</button>
              <button v-if="item.appealable" class="btn btn--sm btn--primary" :disabled="acting" @click="openAppeal(item)">申诉</button>
              <button v-if="canDelete(item)" class="btn btn--sm btn-danger-soft" :disabled="acting" @click="handleDelete(item)">删除</button>
              <span v-if="!hasActions(item)" class="muted action-hint">当前不可操作</span>
            </div>
          </article>
        </div>

        <el-pagination v-if="total > pageSize" v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="fetchItems" />
      </template>

      <div v-else class="empty-state">
        <p v-if="loadError">{{ loadError }}</p>
        <template v-else>
          <span class="empty-state__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z" />
              <path d="M3 6h18M16 10a4 4 0 0 1-8 0" />
            </svg>
          </span>
          <p>该筛选条件下暂无商品</p>
          <router-link :to="ROUTE_PATH.PUBLISH" class="btn btn--primary">去发布第一件闲置</router-link>
        </template>
      </div>

      <el-dialog v-model="appealForm.visible" title="提交内容违规申诉" width="min(540px, 92vw)" :close-on-click-modal="!appealForm.submitting">
        <div class="appeal-form">
          <p>
            商品：
            <strong>{{ appealForm.item?.title }}</strong>
          </p>
          <p class="muted">违规确认后 7 天内可申诉一次，成功后将撤销扣分并重新上架。</p>
          <el-input v-model="appealForm.reason" type="textarea" :rows="5" minlength="10" maxlength="500" show-word-limit placeholder="请填写 10-500 字的申诉理由" />
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

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import PriceTag from '@/components/common/PriceTag.vue'
import { deleteItem, getMyItems, offShelfItem, relistItem, submitItemAppeal } from '@/api/item'
import { APPEAL_STATUS, APPEAL_STATUS_LABELS, ITEM_STATUS, ITEM_STATUS_OPTIONS, ITEM_TYPE, MODERATION_STATUS } from '@/constants/domain'
import type { AppealStatus } from '@/constants/domain'
import type { Item } from '@/types/models'
import { itemStatusBadge, itemStatusLabel, itemTypeLabel } from '@/utils/trade'
import { buildMyItemsParams } from './myItemsQuery'
import { usePagedList } from '@/composables/usePagedList'
import { formatDateTime, placeholderClass } from '@/utils/format'
import { ROUTE_PATH } from '@/constants/routes'

/** 我的发布行数据：Item 之外，后端还会带回“当前是否可申诉”标记 */
interface MyItemRow extends Item {
  appealable?: boolean
}

/** 申诉弹窗状态（item 为当前申诉的商品行） */
interface AppealFormState {
  visible: boolean
  item: MyItemRow | null
  reason: string
  submitting: boolean
}

const STATUS_TABS = [{ label: '全部', value: '' }, ...ITEM_STATUS_OPTIONS]

const statusFilter = ref('')
const acting = ref(false)
const {
  records: items,
  currentPage: page,
  pageSize,
  total,
  loadError,
  fetchList: fetchItems,
  goToFirstPage
} = usePagedList<MyItemRow>(({ page, size }) => getMyItems(buildMyItemsParams(page, size, statusFilter.value)))
const appealForm = reactive<AppealFormState>({ visible: false, item: null, reason: '', submitting: false })

function displayStatus(item: MyItemRow) {
  return item.moderationStatus === MODERATION_STATUS.PENDING ? ITEM_STATUS.REVIEWING : item.status
}
function statusText(status: string) {
  return itemStatusLabel(status)
}
function statusBadge(status: string) {
  return itemStatusBadge(status)
}
function appealStatusText(status: string) {
  return APPEAL_STATUS_LABELS[status as AppealStatus] || status
}
function appealBadge(status: string) {
  return status === APPEAL_STATUS.APPROVED ? 'badge--ok' : status === APPEAL_STATUS.PENDING ? 'badge--warn' : 'badge--muted'
}

function mainImage(item: MyItemRow) {
  return Array.isArray(item.images) ? item.images[0] || '' : ''
}

function canEdit(item: MyItemRow) {
  const editableStatuses: readonly string[] = [ITEM_STATUS.ON_SALE, ITEM_STATUS.OFF_SHELF]
  return editableStatuses.includes(item.status) && item.moderationStatus !== MODERATION_STATUS.PENDING && !item.reserved
}
function canOffShelf(item: MyItemRow) {
  return item.status === ITEM_STATUS.ON_SALE && item.moderationStatus === MODERATION_STATUS.PASSED && !item.reserved
}
function canRelist(item: MyItemRow) {
  return item.status === ITEM_STATUS.OFF_SHELF && item.moderationStatus === MODERATION_STATUS.PASSED && !item.reserved
}
function canDelete(item: MyItemRow) {
  const deletableStatuses: readonly string[] = [ITEM_STATUS.ON_SALE, ITEM_STATUS.OFF_SHELF]
  return deletableStatuses.includes(item.status) && item.moderationStatus === MODERATION_STATUS.PASSED && !item.reserved
}
function hasActions(item: MyItemRow) {
  return canEdit(item) || canOffShelf(item) || canRelist(item) || canDelete(item) || item.appealable
}

function handleStatusChange(status: string) {
  if (statusFilter.value === status) return
  statusFilter.value = status
  goToFirstPage()
  fetchItems()
}

async function handleOffShelf(item: MyItemRow) {
  acting.value = true
  try {
    await offShelfItem(item.id)
    ElMessage.success('商品已下架')
    await fetchItems()
  } finally {
    acting.value = false
  }
}

async function handleRelist(item: MyItemRow) {
  try {
    await ElMessageBox.confirm(`重新上架「${item.title}」前将执行本地合规检测，命中风险会转入人工审核。`, '重新上架', {
      confirmButtonText: '检测并上架',
      cancelButtonText: '取消',
      type: 'info'
    })
  } catch {
    return
  }
  acting.value = true
  try {
    const res = await relistItem(item.id)
    if (res.data?.moderationStatus === MODERATION_STATUS.PENDING) ElMessage.warning('检测到风险内容，已提交管理员审核')
    else ElMessage.success('检测通过，商品已重新上架')
    await fetchItems()
  } finally {
    acting.value = false
  }
}

function openAppeal(item: MyItemRow) {
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
  // 弹窗只能经 openAppeal 打开，此时 item 必有值；判空仅为类型收窄
  if (!appealForm.item) return
  appealForm.submitting = true
  try {
    await submitItemAppeal(appealForm.item.id, { reason })
    appealForm.visible = false
    ElMessage.success('申诉已提交，请等待管理员复核')
    await fetchItems()
  } finally {
    appealForm.submitting = false
  }
}

async function handleDelete(item: MyItemRow) {
  try {
    await ElMessageBox.confirm(`确认删除「${item.title}」吗？删除后不可恢复。`, '删除商品', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  acting.value = true
  try {
    await deleteItem(item.id)
    ElMessage.success('商品已删除')
    await fetchItems()
  } finally {
    acting.value = false
  }
}

onMounted(fetchItems)
</script>

<style scoped>
.my-items-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 18px;
}
.page-head .muted {
  margin-top: 6px;
}
.item-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.item-row {
  display: grid;
  grid-template-columns: 84px minmax(220px, 1fr) auto auto minmax(170px, auto);
  gap: 16px;
  align-items: center;
  padding: 14px 18px;
}
.item-row__thumb {
  width: 84px;
  height: 84px;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-s);
  overflow: hidden;
}
.item-row__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.item-row__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  font-size: 15px;
}
.item-row__title a:hover {
  color: var(--primary);
}
.item-row__meta,
.state-notes {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  font-size: 12.5px;
  margin-top: 6px;
}
.item-row__actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  flex-wrap: wrap;
}
.edit-button svg {
  width: 15px;
  height: 15px;
  flex: 0 0 15px;
}
.action-hint {
  font-size: 12px;
}
.page-head .btn .ic {
  width: 16px;
  height: 16px;
}
.appeal-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
@media (max-width: 900px) {
  .item-row {
    grid-template-columns: 72px 1fr auto;
  }
  .item-row__thumb {
    width: 72px;
    height: 72px;
  }
  .item-row__price {
    grid-column: 3;
    grid-row: 1;
  }
  .item-row__status,
  .item-row__actions {
    grid-column: 2 / -1;
    justify-self: start;
  }
  .item-row__actions {
    justify-content: flex-start;
  }
}
@media (max-width: 600px) {
  .page-head {
    align-items: stretch;
    flex-direction: column;
  }
  .page-head > .btn {
    align-self: flex-start;
  }
  .item-row {
    grid-template-columns: 64px 1fr;
  }
  .item-row__thumb {
    width: 64px;
    height: 64px;
  }
  .item-row__price,
  .item-row__status,
  .item-row__actions {
    grid-column: 2;
    grid-row: auto;
  }
}
.btn-danger-soft {
  background: var(--white);
  border-color: var(--red-bg);
  color: var(--red-deep);
}
.btn-danger-soft:hover {
  background: var(--red-bg);
  border-color: var(--red);
  color: var(--red-deep);
}
</style>
