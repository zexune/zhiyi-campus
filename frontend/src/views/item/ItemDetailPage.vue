<template>
  <DefaultLayout>
    <div class="detail-page">
      <nav class="crumb" aria-label="面包屑">
        <router-link :to="ROUTE_PATH.HOME">交易大厅</router-link>
        <span>/</span>
        <button v-if="item?.categoryName" class="crumb-link" @click="goTag(item.categoryName)">{{ item.categoryName }}</button>
      </nav>

      <el-skeleton v-if="loading" :rows="10" animated />

      <template v-else-if="item">
        <section class="detail">
          <div class="gallery">
            <GalleryBlock :images="itemImages" :cover-image="item?.coverImage || ''" :alt="item?.title || ''" :placeholder="placeholderClass(item.id)">
              <span class="badge gallery-state" :class="typeBadgeClass(item.type)">
                {{ itemTypeLabel(item.type) }}
              </span>
            </GalleryBlock>
          </div>

          <div class="info-panel rise rise-1">
            <div class="info-head">
              <span class="badge" :class="typeBadgeClass(item.type)">
                {{ itemTypeLabel(item.type) }}
              </span>
              <h1>{{ item.title }}</h1>
              <span class="badge" :class="statusBadge(displayStatus)">{{ statusText(displayStatus) }}</span>
            </div>

            <div class="price-strip">
              <ItemPrice :type="item.type" :price="item.price" font-size="40px" />
              <span class="escrow">{{ item.type === ITEM_TYPE.SELL ? '平台担保 · 确认收货后打款' : '双方沟通后线下完成' }}</span>
            </div>

            <div class="meta-grid">
              <div class="meta-row">
                <span class="lab">商品标签</span>
                <div v-if="item.tags?.length" class="item-tags">
                  <button v-for="tag in item.tags" :key="tag" class="tag" @click="goTag(tag)">{{ tag }}</button>
                </div>
                <span v-else class="muted">暂无标签</span>
              </div>
              <div v-if="item.type !== ITEM_TYPE.ERRAND" class="meta-row">
                <span class="lab">交易地点</span>
                <strong>{{ item.tradeLocation || '待沟通' }}</strong>
              </div>
              <div v-if="item.type === ITEM_TYPE.ERRAND" class="meta-row">
                <span class="lab">取送路线</span>
                <strong>{{ item.pickupLocation }} → {{ item.deliveryLocation }}</strong>
              </div>
              <div class="meta-row">
                <span class="lab">发布时间</span>
                <span>{{ formatDateTime(item.createdAt) }}</span>
              </div>
              <div class="meta-row stat-row" aria-label="浏览与收藏数">
                <span class="stat-chip" title="浏览次数">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" />
                    <circle cx="12" cy="12" r="3" />
                  </svg>
                  {{ item.viewCount || 0 }}
                </span>
                <span class="stat-chip" title="收藏人数">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2C10.5 3.5 9.3 3 7.5 3A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z" />
                  </svg>
                  {{ favoriteCount }}
                </span>
              </div>
            </div>

            <div class="seller-card">
              <UserAvatar :nickname="item.publisherNickname || '同学'" :user-id="item.publisherId || 0" size="l" :src="item.publisherAvatar || null" />
              <div class="seller-card__info">
                <div class="seller-card__name">
                  {{ item.publisherNickname || '同学' }}
                  <span v-if="item.publisherVerified" class="seller-card__verified" title="已填写本校邮箱">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12 4 4L19 6" /></svg>
                    已认证
                  </span>
                  <LevelBadge :level="item.publisherLevel || 1" show-title />
                  <template v-if="canCompareSeller">
                    <span v-for="relation in sellerRelations" :key="relation" class="seller-card__relation-tag" :aria-label="`校园关系：${relation}`">{{ relation }}</span>
                  </template>
                </div>
              </div>
              <button class="btn btn--sm seller-card__detail" type="button" @click="openSellerDetail">
                查看详情
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"><path d="M7 17 17 7M9 7h8v8" /></svg>
              </button>
            </div>

            <div class="card card--flat desc-block">
              <h2>商品描述</h2>
              <p>{{ item.description }}</p>
            </div>

            <p v-if="item.reserved" class="reservation-note">该商品已有进行中的订单，订单取消前不会接受新的购买或商品变更。</p>

            <div class="action-bar">
              <template v-if="isOwner">
                <router-link :to="ROUTE_PATH.MY_ITEMS" class="btn">管理我的发布</router-link>
              </template>
              <template v-else>
                <button class="btn" :disabled="!isTradable || favoriteLoading" @click="handleFavorite">
                  <el-icon>
                    <StarFilled v-if="favorite" />
                    <Star v-else />
                  </el-icon>
                  {{ favorite ? '已收藏' : '收藏' }}
                </button>
                <button class="btn" :class="item.type === ITEM_TYPE.BUY ? 'btn--primary' : 'btn--green'" :disabled="displayStatus === ITEM_STATUS.REVIEWING || chatLoading" @click="contactSeller">
                  <el-icon><ChatDotRound /></el-icon>
                  {{ item.type === ITEM_TYPE.BUY ? '我要出售' : '联系卖家' }}
                </button>
                <button v-if="item.type === ITEM_TYPE.SELL" class="btn btn--primary" :disabled="!isTradable || buyLoading" @click="handleBuy">
                  {{ buyLoading ? '下单中...' : '立即购买' }}
                </button>
                <button class="btn btn--danger btn--icon" title="举报商品" aria-label="举报商品" @click="openReportDialog">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round"><path d="M4 21V4h13l-2.5 4L17 12H4" /></svg>
                </button>
              </template>
            </div>
          </div>
        </section>

        <ProvenanceTimeline v-if="Number(item.categoryId) === 2" :chain="lineage?.chain" :loading="lineageLoading" />
      </template>

      <div v-else class="empty-state">
        <span class="empty-state__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z" />
            <path d="M3 6h18M16 10a4 4 0 0 1-8 0" />
          </svg>
        </span>
        <p>商品不存在或已被删除</p>
        <router-link :to="ROUTE_PATH.HOME" class="btn btn--primary">回到大厅</router-link>
      </div>

      <SellerDetailDialog
        :visible="sellerDialogVisible"
        :seller="sellerDetail ?? undefined"
        :reputation="sellerReputation ?? undefined"
        :loading="sellerDetailLoading"
        :error="sellerDetailError"
        @close="closeSellerDetail"
        @retry="loadSellerDetail"
      />

      <ReportDialog v-model:visible="reportVisible" :item-id="item?.id || 0" />
    </div>
  </DefaultLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, Star, StarFilled } from '@element-plus/icons-vue'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import LevelBadge from '@/components/common/LevelBadge.vue'
import ItemPrice from '@/components/common/ItemPrice.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import SellerDetailDialog from '@/components/user/SellerDetailDialog.vue'
import GalleryBlock from './components/GalleryBlock.vue'
import ProvenanceTimeline from './components/ProvenanceTimeline.vue'
import ReportDialog from './components/ReportDialog.vue'
import { getItemDetail, getItemLineage, toggleFavorite } from '@/api/item'
import { getSellerDetail, getUserRelation, getUserReputation } from '@/api/auth'
import type { ItemDetail, ItemLineage } from '@/types/models'
import { startItemConversation } from '@/api/chat'
import { createOrder } from '@/api/order'
import { getOrCreatePending, clearPending } from '@/utils/idempotency'
import { ApiError } from '@/utils/request'
import { ITEM_STATUS, ITEM_TYPE, MODERATION_STATUS } from '@/constants/domain'
import type { ItemStatus } from '@/constants/domain'
import { getUserId, isLoggedIn } from '@/utils/auth'
import type { ReputationVo } from '@/utils/reputation'
import { normalizeRelationTags } from '@/utils/relation'
import { itemStatusBadge, itemStatusLabel, itemTypeLabel, typeBadgeClass } from '@/utils/trade'
import { ROUTE_PATH } from '@/constants/routes'
import { formatDateTime, placeholderClass } from '@/utils/format'

/**
 * 卖家档案弹窗数据：打开时的兜底摘要（id/nickname/level）与 getSellerDetail
 * 返回档案（Record<string, unknown>）的联合形状；弹窗内按字段可选消费，故带索引签名。
 */
interface SellerDetail {
  id?: number
  nickname?: string
  level?: number
  schoolName?: string
  /** 卖家头像（SellerDetailVO 新增字段），弹窗展示用 */
  avatar?: string | null
  [key: string]: unknown
}

const route = useRoute()
const router = useRouter()
const item = ref<ItemDetail | null>(null)
const loading = ref(false)
/** 详情请求代数：路由参数复用组件实例时作废旧响应 */
let detailGen = 0
const favoriteLoading = ref(false)
const chatLoading = ref(false)
const buyLoading = ref(false)
const favorite = ref(false)
const favoriteCount = ref(0)
const sellerDialogVisible = ref(false)
const sellerDetailLoading = ref(false)
const sellerDetailError = ref(false)
const sellerDetail = ref<SellerDetail | null>(null)
const sellerReputation = ref<ReputationVo | null>(null)
const sellerRelations = ref<string[]>([])
const lineage = ref<ItemLineage | null>(null)
const lineageLoading = ref(false)
/** 举报弹窗开关：表单状态与提交逻辑由 ReportDialog 自持 */
const reportVisible = ref(false)

const isOwner = computed(() => String(item.value?.publisherId || '') === String(getUserId() || ''))
const canCompareSeller = computed(() => !!item.value?.publisherId && !isOwner.value && isLoggedIn())
const displayStatus = computed(() => (item.value?.moderationStatus === MODERATION_STATUS.PENDING ? ITEM_STATUS.REVIEWING : item.value?.status))
const isTradable = computed(() => item.value?.status === ITEM_STATUS.ON_SALE && item.value?.moderationStatus === MODERATION_STATUS.PASSED && !item.value?.reserved)
/** 传给 GalleryBlock 的稳定图片集引用：item 为空时回退空数组（避免内联数组每次渲染变化触发子组件重置） */
const itemImages = computed(() => item.value?.images || [])

function statusText(status: ItemStatus | string | undefined): string {
  // 模板仅在 item 已加载的分支调用；as 仅消除联合中的 undefined，运行时取值不变
  return itemStatusLabel(status as string)
}

function statusBadge(status: ItemStatus | string | undefined): string {
  return itemStatusBadge(status as string)
}

async function fetchDetail(): Promise<void> {
  const gen = ++detailGen
  loading.value = true
  sellerRelations.value = []
  try {
    // 路由 /item/:id 的 param 恒为单值字符串
    const res = await getItemDetail(route.params.id as string)
    if (gen !== detailGen) return
    item.value = res.data
    favorite.value = !!item.value.favoriteByCurrentUser
    favoriteCount.value = Number(item.value.favoriteCount || 0)
    loadSellerRelation(item.value.publisherId)
    if (Number(item.value.categoryId) === 2) {
      loadLineage()
    }
  } catch {
    if (gen !== detailGen) return
    item.value = null
  } finally {
    if (gen === detailGen) loading.value = false
  }
}

async function loadLineage(): Promise<void> {
  lineageLoading.value = true
  try {
    const res = await getItemLineage(route.params.id as string)
    lineage.value = res.data
  } catch {
    lineage.value = null
  } finally {
    lineageLoading.value = false
  }
}

async function loadSellerRelation(sellerId: number | undefined): Promise<void> {
  if (!sellerId || !canCompareSeller.value) {
    sellerRelations.value = []
    return
  }

  try {
    const res = await getUserRelation(sellerId)
    sellerRelations.value = normalizeRelationTags(res.data)
  } catch {
    sellerRelations.value = []
  }
}

function requireLogin(): boolean {
  if (isLoggedIn()) return true
  router.push({ path: ROUTE_PATH.LOGIN, query: { redirect: route.fullPath } })
  return false
}

async function handleFavorite(): Promise<void> {
  if (!requireLogin()) return
  if (!item.value) return // 触发按钮仅在 item 已加载的分支渲染，此行只为类型收窄
  favoriteLoading.value = true
  try {
    const res = await toggleFavorite(item.value.id)
    favorite.value = res.data.favorite
    favoriteCount.value = res.data.favoriteCount
    ElMessage.success(favorite.value ? '已收藏' : '已取消收藏')
  } finally {
    favoriteLoading.value = false
  }
}

async function contactSeller(): Promise<void> {
  if (!requireLogin()) return
  if (!item.value) return // 同上：仅类型收窄
  chatLoading.value = true
  try {
    const res = await startItemConversation(item.value.id)
    router.push({
      path: ROUTE_PATH.CHAT,
      query: {
        conversationId: res.data.conversationId,
        peerId: res.data.peer?.id,
        relatedItemId: res.data.relatedItem?.id
      }
    })
  } finally {
    chatLoading.value = false
  }
}

async function loadSellerDetail(): Promise<void> {
  const sellerId = item.value?.publisherId
  if (!sellerId) return

  sellerDetailLoading.value = true
  sellerDetailError.value = false
  try {
    const [detailResult, reputationResult] = await Promise.allSettled([getSellerDetail(sellerId), getUserReputation(sellerId)])

    if (detailResult.status === 'fulfilled') {
      // getSellerDetail 的返回为动态档案对象（Record<string, unknown>），单点收窄到弹窗形状
      sellerDetail.value = detailResult.value.data as SellerDetail
    } else {
      sellerDetailError.value = true
    }

    sellerReputation.value = reputationResult.status === 'fulfilled' ? reputationResult.value.data : null
  } finally {
    sellerDetailLoading.value = false
  }
}

function openSellerDetail(): void {
  if (!requireLogin()) return
  if (!item.value) return // 同上：仅类型收窄
  sellerDetail.value = {
    id: item.value.publisherId,
    nickname: item.value.publisherNickname,
    level: item.value.publisherLevel
  }
  sellerReputation.value = null
  sellerDialogVisible.value = true
  loadSellerDetail()
}

function closeSellerDetail(): void {
  sellerDialogVisible.value = false
}

async function handleBuy(): Promise<void> {
  if (buyLoading.value) return
  if (!requireLogin()) return
  if (!item.value) return // 同上：仅类型收窄
  try {
    await ElMessageBox.confirm(`确认购买「${item.value.title}」？\n\n金额：¥${Number(item.value.price).toFixed(2)}\n确认后资金将由平台担保冻结，当面验货满意后再确认收货。`, '确认下单', {
      confirmButtonText: '确认购买',
      cancelButtonText: '再想想',
      type: 'warning'
    })
  } catch {
    return // 用户取消
  }
  buyLoading.value = true
  // 下单幂等键（B6）：超时/结果不明时复用原键重试，服务端复返同一结果
  const pending = getOrCreatePending('ORDER_CREATE', item.value.id, { itemId: item.value.id })
  try {
    await createOrder(item.value.id, pending.idempotencyKey)
    clearPending('ORDER_CREATE', item.value.id)
    ElMessage.success('下单成功！资金已冻结，请联系卖家线下见面')
    // 刷新商品状态
    fetchDetail()
  } catch (error) {
    // 明确业务拒绝（已售/余额不足等 CLEAR）才清除幂等键；繁忙/超时保留原键供重试
    if (error instanceof ApiError && error.idempotencyDisposition === 'CLEAR') {
      clearPending('ORDER_CREATE', item.value.id)
    }
  } finally {
    buyLoading.value = false
  }
}

function openReportDialog(): void {
  if (!requireLogin()) return
  reportVisible.value = true
}

function goTag(tag: string): void {
  router.push({ path: ROUTE_PATH.HOME, query: { keyword: tag } })
}

onMounted(() => {
  if (!isLoggedIn()) {
    router.replace({ path: ROUTE_PATH.LOGIN, query: { redirect: route.fullPath } })
    return
  }
  fetchDetail()
})

// 低危修复：路由 /item/:id 仅参数变化时组件实例被复用，onMounted 不会再次触发；
// 此处监听参数变化重置页面状态并重新拉取，避免串显旧商品数据
watch(
  () => route.params.id,
  (newId, oldId) => {
    if (newId === oldId || !isLoggedIn()) return
    detailGen += 1
    item.value = null
    lineage.value = null
    sellerDialogVisible.value = false
    reportVisible.value = false
    fetchDetail()
  }
)
</script>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.crumb {
  margin: 0 0 18px;
  font-size: 13.5px;
  color: var(--ink-soft);
  display: flex;
  gap: 8px;
  align-items: center;
}

.crumb a:hover,
.crumb-link:hover {
  color: var(--primary);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.crumb-link {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  padding: 0;
}

.detail {
  display: grid;
  grid-template-columns: 460px 1fr;
  gap: 32px;
  align-items: start;
}

.gallery {
  position: sticky;
  top: 84px;
}

.gallery-state {
  position: absolute;
  top: 14px;
  left: 14px;
}

.info-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.info-head h1 {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.4;
  flex: 1;
}

.price-strip {
  margin: 18px 0;
  padding: 16px 22px;
  display: flex;
  align-items: baseline;
  gap: 16px;
  flex-wrap: wrap;
  background: var(--white);
  border: var(--bw) solid var(--line);
  border-radius: var(--r-m);
  box-shadow: var(--shadow-s);
}

.escrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--green-deep);
  background: var(--green-bg);
  padding: 4px 12px;
  border-radius: 999px;
}

.meta-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 14.5px;
  margin-bottom: 20px;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.meta-row .lab {
  color: var(--ink-soft);
  min-width: 68px;
}

.item-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.reservation-note {
  margin: -8px 0 18px;
  padding: 10px 14px;
  border-radius: var(--r-s);
  background: var(--yellow-bg);
  color: #8a5a00;
  font-size: 13px;
  font-weight: 600;
}

.seller-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  margin: 22px 0;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-m);
  background: var(--paper-deep);
}

.seller-card__info {
  flex: 1;
  min-width: 0;
}

.seller-card__name {
  font-weight: 700;
  font-size: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.seller-card__relation-tag {
  padding: 2px 9px;
  color: #8a5a00;
  background: var(--yellow-bg);
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 600;
  line-height: 1.6;
}

.seller-card__verified {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--green-bg);
  color: var(--green-deep);
  font-size: 11px;
  font-weight: 600;
}

.seller-card__verified svg {
  width: 12px;
  height: 12px;
}

.seller-card__relation-tag:nth-of-type(3n + 2) {
  background: var(--blue-bg);
}

.seller-card__relation-tag:nth-of-type(3n) {
  background: var(--green-bg);
}

.seller-card__detail {
  flex: 0 0 auto;
}

.desc-block {
  padding: 22px 24px;
  margin-bottom: 24px;
}

.desc-block h2 {
  font-family: var(--font-display);
  font-size: 20px;
  letter-spacing: 1px;
  margin-bottom: 10px;
}

.desc-block p {
  font-size: 15px;
  line-height: 1.9;
  color: #3d372e;
  white-space: pre-wrap;
}

.action-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.action-bar .btn {
  flex: 1;
  min-width: 150px;
}

/* 图标型举报按钮：不随操作主按钮拉伸 */
.action-bar .btn--icon {
  flex: 0 0 48px;
  min-width: 48px;
  padding: 10px 0;
}
.action-bar .btn--icon svg {
  width: 19px;
  height: 19px;
}

/* 浏览 / 收藏数据小胶囊 */
.stat-row {
  gap: 8px;
}
.stat-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 11px;
  border: var(--bw) solid var(--line);
  border-radius: 999px;
  background: var(--paper-deep);
  color: var(--ink-soft);
  font-size: 12.5px;
  font-weight: 700;
}
.stat-chip svg {
  width: 14px;
  height: 14px;
}

@media (max-width: 860px) {
  .detail {
    grid-template-columns: 1fr;
  }

  .gallery {
    position: static;
  }
}

@media (max-width: 520px) {
  .seller-card {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .seller-card__detail {
    width: calc(100% - 78px);
    margin-left: 78px;
  }
}
</style>
