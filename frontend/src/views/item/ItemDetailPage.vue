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
            <div class="gallery__main" :class="placeholderClass(item.id)">
              <img v-if="activeImage" :src="activeImage" :alt="item.title" />
              <span class="badge gallery-state" :class="item.type === ITEM_TYPE.BUY ? 'badge--buy' : 'badge--sell'">
                {{ itemTypeLabel(item.type) }}
              </span>
              <button v-if="(item.images?.length || 0) > 1" class="gallery__nav gallery__nav--prev" aria-label="上一张" @click="switchImage(-1)">‹</button>
              <button v-if="(item.images?.length || 0) > 1" class="gallery__nav gallery__nav--next" aria-label="下一张" @click="switchImage(1)">›</button>
              <span v-if="item.images?.length" class="gallery__count">{{ activeImageIndex + 1 }} / {{ item.images.length }}</span>
            </div>
            <div v-if="(item.images?.length || 0) > 1" class="gallery__thumbs">
              <button v-for="image in item.images" :key="image" class="th" :class="{ active: image === activeImage }" @click="activeImage = image">
                <img :src="image" :alt="item.title" />
              </button>
            </div>
          </div>

          <div class="info-panel rise rise-1">
            <div class="info-head">
              <span class="badge" :class="item.type === ITEM_TYPE.BUY ? 'badge--buy' : 'badge--sell'">
                {{ itemTypeLabel(item.type) }}
              </span>
              <h1>{{ item.title }}</h1>
              <span class="badge" :class="statusBadge(displayStatus)">{{ statusText(displayStatus) }}</span>
            </div>

            <div class="price-strip">
              <strong v-if="item.type === ITEM_TYPE.SWAP" class="price">以物换物</strong>
              <PriceTag v-else :value="item.price" font-size="40px" />
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
              <UserAvatar :nickname="item.publisherNickname || '同学'" :user-id="item.publisherId || 0" size="l" />
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
                <button class="btn btn--danger btn--icon" :disabled="reportForm.submitting" title="举报商品" aria-label="举报商品" @click="openReportDialog">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round"><path d="M4 21V4h13l-2.5 4L17 12H4" /></svg>
                </button>
              </template>
            </div>
          </div>
        </section>

        <section v-if="Number(item.categoryId) === 2" class="lineage-section" aria-labelledby="lineage-title">
          <div class="lineage-section__head">
            <span class="lineage-section__icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z" />
                <path d="M9 7h7M9 11h5" />
              </svg>
            </span>
            <div>
              <h2 id="lineage-title">教材传承时间轴</h2>
            </div>
          </div>

          <el-skeleton v-if="lineageLoading" :rows="3" animated />
          <ol v-else-if="lineage?.chain?.length" class="lineage-timeline">
            <li v-for="(node, index) in lineage.chain" :key="`${node.userId}-${node.time}-${index}`">
              <span class="lineage-timeline__dot">{{ index + 1 }}</span>
              <div class="lineage-timeline__content">
                <div>
                  <strong>{{ node.nickname || '校园同学' }}</strong>
                  <span>{{ node.role === 'PUBLISHER' ? '最初发布' : '完成接力' }}</span>
                </div>
                <small>
                  {{ formatDate(node.time) }}
                  <template v-if="node.price != null">· 成交 ¥{{ Number(node.price).toFixed(2) }}</template>
                </small>
              </div>
            </li>
          </ol>
          <p v-else class="lineage-section__empty">这本教材刚刚开始它的校园旅程。</p>
        </section>
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

      <el-dialog v-model="reportForm.visible" title="举报商品" width="min(520px, 92vw)" :close-on-click-modal="!reportForm.submitting">
        <div class="report-form">
          <label>
            <span>举报类型</span>
            <AppSelect v-model="reportForm.type" :options="REPORT_TYPE_OPTIONS" />
          </label>
          <label>
            <span>补充说明</span>
            <el-input v-model="reportForm.details" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="请说明具体问题；选择“其他”时必填" />
          </label>
        </div>
        <template #footer>
          <button class="btn" :disabled="reportForm.submitting" @click="reportForm.visible = false">取消</button>
          <button class="btn btn--danger" :disabled="reportForm.submitting" @click="submitReport">
            {{ reportForm.submitting ? '提交中...' : '提交举报' }}
          </button>
        </template>
      </el-dialog>
    </div>
  </DefaultLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, Star, StarFilled } from '@element-plus/icons-vue'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import LevelBadge from '@/components/common/LevelBadge.vue'
import PriceTag from '@/components/common/PriceTag.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import SellerDetailDialog from '@/components/user/SellerDetailDialog.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import { getItemDetail, getItemLineage, reportItem, toggleFavorite } from '@/api/item'
import { getSellerDetail, getUserRelation, getUserReputation } from '@/api/auth'
import type { ItemDetail, ItemLineage } from '@/types/models'
import { startItemConversation } from '@/api/chat'
import { createOrder } from '@/api/order'
import { ITEM_STATUS, ITEM_TYPE, ITEM_TYPE_LABELS, MODERATION_STATUS } from '@/constants/domain'
import type { ItemStatus, ItemType } from '@/constants/domain'
import { getUserId, isLoggedIn } from '@/utils/auth'
import type { ReputationVo } from '@/utils/reputation'
import { normalizeRelationTags } from '@/utils/relation'
import { itemStatusBadge, itemStatusLabel } from '@/utils/trade'
import { ROUTE_PATH } from '@/constants/routes'
import { formatDate, formatDateTime, placeholderClass } from '@/utils/format'

/**
 * 卖家档案弹窗数据：打开时的兜底摘要（id/nickname/level）与 getSellerDetail
 * 返回档案（Record<string, unknown>）的联合形状；弹窗内按字段可选消费，故带索引签名。
 */
interface SellerDetail {
  id?: number
  nickname?: string
  level?: number
  schoolName?: string
  [key: string]: unknown
}

const REPORT_TYPE_OPTIONS = [
  { label: '价格欺诈', value: 'PRICE_FRAUD' },
  { label: '违禁物品', value: 'PROHIBITED_ITEM' },
  { label: '图片违规', value: 'IMAGE_VIOLATION' },
  { label: '广告引流', value: 'ADVERTISING' },
  { label: '其他问题', value: 'OTHER' }
]

const route = useRoute()
const router = useRouter()
const item = ref<ItemDetail | null>(null)
const loading = ref(false)
const favoriteLoading = ref(false)
const chatLoading = ref(false)
const buyLoading = ref(false)
const favorite = ref(false)
const favoriteCount = ref(0)
const activeImage = ref('')
const sellerDialogVisible = ref(false)
const sellerDetailLoading = ref(false)
const sellerDetailError = ref(false)
const sellerDetail = ref<SellerDetail | null>(null)
const sellerReputation = ref<ReputationVo | null>(null)
const sellerRelations = ref<string[]>([])
const lineage = ref<ItemLineage | null>(null)
const lineageLoading = ref(false)
const reportForm = reactive({ visible: false, type: 'PRICE_FRAUD', details: '', submitting: false })

const isOwner = computed(() => String(item.value?.publisherId || '') === String(getUserId() || ''))
const canCompareSeller = computed(() => !!item.value?.publisherId && !isOwner.value && isLoggedIn())
const displayStatus = computed(() => (item.value?.moderationStatus === MODERATION_STATUS.PENDING ? ITEM_STATUS.REVIEWING : item.value?.status))
const isTradable = computed(() => item.value?.status === ITEM_STATUS.ON_SALE && item.value?.moderationStatus === MODERATION_STATUS.PASSED && !item.value?.reserved)
const activeImageIndex = computed(() => {
  const images = item.value?.images || []
  const index = images.indexOf(activeImage.value)
  return index >= 0 ? index : 0
})

function statusText(status: ItemStatus | string | undefined): string {
  // 模板仅在 item 已加载的分支调用；as 仅消除联合中的 undefined，运行时取值不变
  return itemStatusLabel(status as string)
}

function statusBadge(status: ItemStatus | string | undefined): string {
  return itemStatusBadge(status as string)
}

function itemTypeLabel(type: string): string {
  return ITEM_TYPE_LABELS[type as ItemType] || type
}

function switchImage(offset: number): void {
  const images = item.value?.images || []
  if (!images.length) return
  const nextIndex = (activeImageIndex.value + offset + images.length) % images.length
  activeImage.value = images[nextIndex]
}

async function fetchDetail(): Promise<void> {
  loading.value = true
  sellerRelations.value = []
  try {
    // 路由 /item/:id 的 param 恒为单值字符串
    const res = await getItemDetail(route.params.id as string)
    item.value = res.data
    activeImage.value = item.value.coverImage || item.value.images?.[0] || ''
    favorite.value = !!item.value.favoriteByCurrentUser
    favoriteCount.value = Number(item.value.favoriteCount || 0)
    loadSellerRelation(item.value.publisherId)
    if (Number(item.value.categoryId) === 2) {
      loadLineage()
    }
  } catch {
    item.value = null
  } finally {
    loading.value = false
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
      path: '/chat',
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
  try {
    await createOrder(item.value.id)
    ElMessage.success('下单成功！资金已冻结，请联系卖家线下见面')
    // 刷新商品状态
    fetchDetail()
  } catch {
    // 错误已在拦截器提示
  } finally {
    buyLoading.value = false
  }
}

function openReportDialog(): void {
  if (!requireLogin()) return
  reportForm.type = 'PRICE_FRAUD'
  reportForm.details = ''
  reportForm.visible = true
}

async function submitReport(): Promise<void> {
  if (!item.value) return // 举报按钮仅在 item 存在分支渲染，此行只为类型收窄
  const details = reportForm.details.trim()
  if (reportForm.type === 'OTHER' && !details) {
    ElMessage.warning('选择“其他问题”时请填写补充说明')
    return
  }
  reportForm.submitting = true
  try {
    await reportItem(item.value.id, { type: reportForm.type, details: details || null })
    reportForm.visible = false
    ElMessage.success('举报已提交，管理员核实前不会影响商品展示')
  } finally {
    reportForm.submitting = false
  }
}

function goTag(tag: string): void {
  router.push({ path: '/', query: { keyword: tag } })
}

onMounted(() => {
  if (!isLoggedIn()) {
    router.replace({ path: ROUTE_PATH.LOGIN, query: { redirect: route.fullPath } })
    return
  }
  fetchDetail()
})
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

.gallery__main {
  position: relative;
  aspect-ratio: 1 / 1;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-l);
  box-shadow: var(--shadow-m);
  display: grid;
  place-items: center;
  overflow: hidden;
}

.gallery__main img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.gallery-state {
  position: absolute;
  top: 14px;
  left: 14px;
}

.gallery__nav {
  position: absolute;
  top: 50%;
  translate: 0 -50%;
  width: 40px;
  height: 40px;
  border: var(--bw) solid var(--line);
  border-radius: 50%;
  background: var(--white);
  display: grid;
  place-items: center;
  cursor: pointer;
  box-shadow: var(--shadow-s);
  font-size: 26px;
  line-height: 1;
}

.gallery__nav:hover {
  background: var(--paper-deep);
}

.gallery__nav--prev {
  left: 14px;
}
.gallery__nav--next {
  right: 14px;
}

.gallery__count {
  position: absolute;
  bottom: 12px;
  right: 14px;
  padding: 3px 12px;
  background: var(--ink);
  color: var(--paper);
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 700;
}

.gallery__thumbs {
  display: flex;
  gap: 10px;
  margin-top: 14px;
  overflow-x: auto;
}

.th {
  width: 68px;
  height: 68px;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-s);
  overflow: hidden;
  background: var(--paper-deep);
  cursor: pointer;
  opacity: 0.55;
  transition:
    color 0.15s,
    background-color 0.15s,
    border-color 0.15s,
    box-shadow 0.15s,
    transform 0.15s;
}

.th:hover {
  opacity: 0.85;
}

.th.active {
  opacity: 1;
  border-color: var(--primary);
}

.th img {
  width: 100%;
  height: 100%;
  object-fit: cover;
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

.report-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.report-form label {
  display: flex;
  flex-direction: column;
  gap: 7px;
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

.lineage-section {
  margin-top: 28px;
  padding: 22px 24px 26px;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-m);
  background: var(--white);
  box-shadow: var(--shadow-m);
}

.lineage-section__head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 18px;
  border-bottom: var(--bw) solid var(--line);
}

.lineage-section__icon {
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
  display: grid;
  place-items: center;
  border-radius: var(--r-m);
  background: var(--paper-deep);
  color: var(--ink-soft);
  box-shadow: var(--shadow-s);
}

.lineage-section__icon svg {
  width: 27px;
  height: 27px;
}
.lineage-section__head h2 {
  font-size: 17px;
  font-weight: 700;
}

.lineage-timeline {
  display: flex;
  margin-top: 22px;
  padding: 0;
  list-style: none;
  overflow-x: auto;
}

.lineage-timeline li {
  position: relative;
  min-width: 190px;
  flex: 1 0 190px;
  padding-right: 22px;
}

.lineage-timeline li:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 17px;
  left: 34px;
  right: 0;
  border-top: var(--bw) solid var(--line);
}

.lineage-timeline__dot {
  position: relative;
  z-index: 1;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: var(--bw) solid var(--line);
  border-radius: 50%;
  background: var(--yellow);
  box-shadow: var(--shadow-s);
  font-family: var(--font-display);
}

.lineage-timeline__content {
  margin-top: 12px;
}
.lineage-timeline__content > div {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}
.lineage-timeline__content strong {
  font-size: 14px;
}
.lineage-timeline__content span {
  padding: 2px 6px;
  border: var(--bw) solid var(--line);
  border-radius: 5px;
  background: var(--paper-deep);
  font-size: 10px;
  font-weight: 800;
}
.lineage-timeline__content small {
  display: block;
  margin-top: 5px;
  color: var(--ink-soft);
  font-size: 11px;
}
.lineage-section__empty {
  margin: 22px 0 2px;
  color: var(--ink-soft);
  font-size: 13px;
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
