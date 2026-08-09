<template>
  <DefaultLayout>
    <div class="detail-page">
      <nav class="crumb" aria-label="面包屑">
        <router-link to="/">交易大厅</router-link>
        <span>/</span>
        <button v-if="item?.categoryName" class="crumb-link" @click="goTag(item.categoryName)">{{ item.categoryName }}</button>
        <span v-if="item?.categoryName">/</span>
        <span>商品详情</span>
      </nav>

      <el-skeleton v-if="loading" :rows="10" animated />

      <template v-else-if="item">
        <section class="detail">
          <div class="gallery">
            <div class="gallery__main" :class="phClass(item.id)">
              <img v-if="activeImage" :src="activeImage" :alt="item.title" />
              <span class="badge gallery-state" :class="item.type === 'BUY' ? 'badge--buy' : 'badge--sell'">
                {{ itemTypeLabel(item.type) }}
              </span>
              <button v-if="item.images?.length > 1" class="gallery__nav gallery__nav--prev" aria-label="上一张" @click="switchImage(-1)">‹</button>
              <button v-if="item.images?.length > 1" class="gallery__nav gallery__nav--next" aria-label="下一张" @click="switchImage(1)">›</button>
              <span v-if="item.images?.length" class="gallery__count">{{ activeImageIndex + 1 }} / {{ item.images.length }}</span>
            </div>
            <div v-if="item.images?.length > 1" class="gallery__thumbs">
              <button
                v-for="image in item.images"
                :key="image"
                class="th"
                :class="{ active: image === activeImage }"
                @click="activeImage = image"
              >
                <img :src="image" :alt="item.title" />
              </button>
            </div>
          </div>

          <div class="info-panel rise rise-1">
            <div class="info-head">
              <span class="badge" :class="item.type === 'BUY' ? 'badge--buy' : 'badge--sell'">
                {{ itemTypeLabel(item.type) }}
              </span>
              <h1>{{ item.title }}</h1>
              <span class="badge" :class="statusBadge(displayStatus)">{{ statusText(displayStatus) }}</span>
            </div>

            <div class="price-strip">
              <strong v-if="item.type === 'SWAP'" class="price">以物换物</strong>
              <PriceTag v-else :value="item.price" font-size="40px" />
              <span class="escrow">{{ item.type === 'SELL' ? '平台担保 · 确认收货后打款' : '双方沟通后线下完成' }}</span>
            </div>

            <div class="meta-grid">
              <div class="meta-row">
                <span class="lab">商品标签</span>
                <div v-if="item.tags?.length" class="item-tags">
                  <button
                    v-for="tag in item.tags"
                    :key="tag"
                    class="tag"
                    @click="goTag(tag)"
                  >{{ tag }}</button>
                </div>
                <span v-else>暂无标签</span>
              </div>
              <div v-if="item.type !== 'ERRAND'" class="meta-row">
                <span class="lab">交易地点</span><strong>{{ item.tradeLocation || '待沟通' }}</strong>
              </div>
              <div v-if="item.type === 'ERRAND'" class="meta-row">
                <span class="lab">取送路线</span><strong>{{ item.pickupLocation }} → {{ item.deliveryLocation }}</strong>
              </div>
              <div class="meta-row">
                <span class="lab">发布时间</span><span>{{ formatDate(item.createdAt) }}</span>
              </div>
              <div class="meta-row">
                <span class="lab">浏览 / 收藏</span><span>{{ item.viewCount || 0 }} 次浏览 · {{ favoriteCount }} 人收藏</span>
              </div>
            </div>

            <div class="seller-card">
              <UserAvatar :nickname="item.publisherNickname || '同学'" :user-id="item.publisherId || 0" size="l" />
              <div class="seller-card__info">
                <div class="seller-card__name">
                  {{ item.publisherNickname || '同学' }}
                  <span v-if="item.publisherVerified" class="seller-card__verified" title="已填写本校邮箱">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12 4 4L19 6"/></svg>
                    已认证
                  </span>
                  <LevelBadge :level="item.publisherLevel || 1" show-title />
                  <template v-if="canCompareSeller">
                    <span
                      v-for="relation in sellerRelations"
                      :key="relation"
                      class="seller-card__relation-tag"
                      :aria-label="`校园关系：${relation}`"
                    >{{ relation }}</span>
                  </template>
                </div>
              </div>
              <button class="btn btn--sm seller-card__detail" type="button" @click="openSellerDetail">
                查看详情
                <span aria-hidden="true">↗</span>
              </button>
            </div>

            <div class="card card--flat desc-block">
              <h2>商品描述</h2>
              <p>{{ item.description }}</p>
            </div>

            <p v-if="item.reserved" class="reservation-note">
              该商品已有进行中的订单，订单取消前不会接受新的购买或商品变更。
            </p>

            <div class="action-bar">
              <template v-if="isOwner">
                <router-link to="/user/my-items" class="btn">管理我的发布</router-link>
              </template>
              <template v-else>
                <button class="btn" :disabled="!isTradable || favoriteLoading" @click="handleFavorite">
                  <el-icon><StarFilled v-if="favorite" /><Star v-else /></el-icon>
                  {{ favorite ? '已收藏' : '收藏' }}
                </button>
                <button
                  class="btn"
                  :class="item.type === 'BUY' ? 'btn--primary' : 'btn--green'"
                  :disabled="displayStatus === 'REVIEWING' || chatLoading"
                  @click="contactSeller"
                >
                  <el-icon><ChatDotRound /></el-icon>
                  {{ item.type === 'BUY' ? '我要出售' : '联系卖家' }}
                </button>
                <button
              v-if="item.type === 'SELL'"
                  class="btn btn--primary"
                  :disabled="!isTradable || buyLoading"
                  @click="handleBuy"
                >
                  {{ buyLoading ? '下单中...' : '立即购买' }}
                </button>
                <button class="btn btn--danger" :disabled="reportForm.submitting" @click="openReportDialog">
                  举报
                </button>
              </template>
            </div>
            <p class="muted escrow-note">
              <template v-if="item.type === 'BUY'">点击「我要出售」与发布者联系，双方沟通后确认交易细节</template>
              <template v-else>点击「立即购买」后货款将由平台托管，当面验货满意再确认收货</template>
            </p>
          </div>
        </section>

        <section v-if="Number(item.categoryId) === 2" class="lineage-section" aria-labelledby="lineage-title">
          <div class="lineage-section__head">
            <span class="lineage-section__icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z"/><path d="M9 7h7M9 11h5"/></svg>
            </span>
            <div>
              <small>BOOK LINEAGE</small>
              <h2 id="lineage-title">教材传承时间轴</h2>
              <p>一本书在校园里的每次交接，都值得被记住。</p>
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
                <small>{{ formatDate(node.time) }}<template v-if="node.price != null"> · 成交 ¥{{ Number(node.price).toFixed(2) }}</template></small>
              </div>
            </li>
          </ol>
          <p v-else class="lineage-section__empty">这本教材刚刚开始它的校园旅程。</p>
        </section>
      </template>

      <div v-else class="empty-panel">
        <p class="muted">商品不存在或已被删除</p>
        <router-link to="/" class="btn btn--primary">回到大厅</router-link>
      </div>

      <SellerDetailDialog
        :visible="sellerDialogVisible"
        :seller="sellerDetail"
        :reputation="sellerReputation"
        :loading="sellerDetailLoading"
        :error="sellerDetailError"
        @close="closeSellerDetail"
        @retry="loadSellerDetail"
      />

      <el-dialog
        v-model="reportForm.visible"
        title="举报商品"
        width="min(520px, 92vw)"
        :close-on-click-modal="!reportForm.submitting"
      >
        <div class="report-form">
          <p class="muted">举报不会自动下架商品，管理员核实后再处理，防止恶意举报影响正常交易。</p>
          <label>
            <span>举报类型</span>
            <AppSelect v-model="reportForm.type" :options="REPORT_TYPE_OPTIONS" />
          </label>
          <label>
            <span>补充说明</span>
            <el-input
              v-model="reportForm.details"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="请说明具体问题；选择“其他”时必填"
            />
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

<script setup>
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
import { startItemConversation } from '@/api/chat'
import { createOrder } from '@/api/order'
import { getUserId, isLoggedIn } from '@/utils/auth'
import { normalizeRelationTags } from '@/utils/relation'

const STATUS_TEXT = { ON_SALE: '在售中', REVIEWING: '审核中', SOLD: '已售出', OFF_SHELF: '已下架' }
const STATUS_BADGE = { ON_SALE: 'badge--ok', REVIEWING: 'badge--warn', SOLD: 'badge--muted', OFF_SHELF: 'badge--muted' }
const REPORT_TYPE_OPTIONS = [
  { label: '价格欺诈', value: 'PRICE_FRAUD' },
  { label: '违禁物品', value: 'PROHIBITED_ITEM' },
  { label: '图片违规', value: 'IMAGE_VIOLATION' },
  { label: '广告引流', value: 'ADVERTISING' },
  { label: '其他问题', value: 'OTHER' },
]
const PH = ['ph-a', 'ph-b', 'ph-c', 'ph-d', 'ph-e', 'ph-f']

const route = useRoute()
const router = useRouter()
const item = ref(null)
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
const sellerDetail = ref(null)
const sellerReputation = ref(null)
const sellerRelations = ref([])
const lineage = ref(null)
const lineageLoading = ref(false)
const reportForm = reactive({ visible: false, type: 'PRICE_FRAUD', details: '', submitting: false })

const isOwner = computed(() => String(item.value?.publisherId || '') === String(getUserId() || ''))
const canCompareSeller = computed(() => !!item.value?.publisherId && !isOwner.value && isLoggedIn())
const displayStatus = computed(() => item.value?.moderationStatus === 'PENDING' ? 'REVIEWING' : item.value?.status)
const isTradable = computed(() => (
  item.value?.status === 'ON_SALE'
  && item.value?.moderationStatus === 'PASSED'
  && !item.value?.reserved
))
const activeImageIndex = computed(() => {
  const images = item.value?.images || []
  const index = images.indexOf(activeImage.value)
  return index >= 0 ? index : 0
})

function phClass(id) {
  return PH[Number(id) % PH.length]
}

function statusText(status) {
  return STATUS_TEXT[status] || status
}

function statusBadge(status) {
  return STATUS_BADGE[status] || 'badge--muted'
}

function itemTypeLabel(type) {
  return { SELL: '出售', BUY: '求购', SWAP: '换物', ERRAND: '跑腿' }[type] || type
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

function switchImage(offset) {
  const images = item.value?.images || []
  if (!images.length) return
  const nextIndex = (activeImageIndex.value + offset + images.length) % images.length
  activeImage.value = images[nextIndex]
}

async function fetchDetail() {
  loading.value = true
  sellerRelations.value = []
  try {
    const res = await getItemDetail(route.params.id)
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

async function loadLineage() {
  lineageLoading.value = true
  try {
    const res = await getItemLineage(route.params.id)
    lineage.value = res.data
  } catch {
    lineage.value = null
  } finally {
    lineageLoading.value = false
  }
}

async function loadSellerRelation(sellerId) {
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

function requireLogin() {
  if (isLoggedIn()) return true
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

async function handleFavorite() {
  if (!requireLogin()) return
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

async function contactSeller() {
  if (!requireLogin()) return
  chatLoading.value = true
  try {
    const res = await startItemConversation(item.value.id)
    router.push({
      path: '/chat',
      query: {
        conversationId: res.data.conversationId,
        peerId: res.data.peer?.id,
        relatedItemId: res.data.relatedItem?.id,
      },
    })
  } finally {
    chatLoading.value = false
  }
}

async function loadSellerDetail() {
  const sellerId = item.value?.publisherId
  if (!sellerId) return

  sellerDetailLoading.value = true
  sellerDetailError.value = false
  try {
    const [detailResult, reputationResult] = await Promise.allSettled([
      getSellerDetail(sellerId),
      getUserReputation(sellerId),
    ])

    if (detailResult.status === 'fulfilled') {
      sellerDetail.value = detailResult.value.data
    } else {
      sellerDetailError.value = true
    }

    sellerReputation.value = reputationResult.status === 'fulfilled'
      ? reputationResult.value.data
      : null
  } finally {
    sellerDetailLoading.value = false
  }
}

function openSellerDetail() {
  if (!requireLogin()) return
  sellerDetail.value = {
    id: item.value.publisherId,
    nickname: item.value.publisherNickname,
    level: item.value.publisherLevel,
  }
  sellerReputation.value = null
  sellerDialogVisible.value = true
  loadSellerDetail()
}

function closeSellerDetail() {
  sellerDialogVisible.value = false
}

async function handleBuy() {
  if (!requireLogin()) return
  try {
    await ElMessageBox.confirm(
      `确认购买「${item.value.title}」？\n\n金额：¥${Number(item.value.price).toFixed(2)}\n确认后资金将由平台担保冻结，当面验货满意后再确认收货。`,
      '确认下单',
      { confirmButtonText: '确认购买', cancelButtonText: '再想想', type: 'warning' }
    )
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

function openReportDialog() {
  if (!requireLogin()) return
  reportForm.type = 'PRICE_FRAUD'
  reportForm.details = ''
  reportForm.visible = true
}

async function submitReport() {
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

function goTag(tag) {
  router.push({ path: '/', query: { keyword: tag } })
}

onMounted(() => {
  if (!isLoggedIn()) {
    router.replace({ path: '/login', query: { redirect: route.fullPath } })
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
  border: var(--bw) solid var(--ink);
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
  border: var(--bw) solid var(--ink);
  border-radius: 50%;
  background: var(--white);
  display: grid;
  place-items: center;
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--ink);
  font-size: 26px;
  line-height: 1;
}

.gallery__nav:hover {
  background: var(--yellow);
}

.gallery__nav--prev { left: 14px; }
.gallery__nav--next { right: 14px; }

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
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  overflow: hidden;
  background: var(--paper-deep);
  cursor: pointer;
  opacity: .55;
  transition: all .15s;
}

.th:hover {
  opacity: .85;
}

.th.active {
  opacity: 1;
  box-shadow: 3px 3px 0 var(--ink);
  transform: translate(-1px, -1px);
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
  font-size: 26px;
  font-weight: 900;
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
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-m);
  box-shadow: var(--shadow-s);
}

.escrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
  color: var(--green-deep);
  background: #D6F2DF;
  border: 1.5px solid var(--green);
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
  border: 1.5px solid #C88719;
  border-radius: var(--r-s);
  background: #FFF4CE;
  color: #6A4700;
  font-size: 13px;
  font-weight: 700;
}

.report-form { display: flex; flex-direction: column; gap: 18px; }
.report-form label { display: flex; flex-direction: column; gap: 7px; font-weight: 800; }

.seller-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  margin: 22px 0;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-m);
  background: var(--paper-deep);
}

.seller-card__info {
  flex: 1;
  min-width: 0;
}

.seller-card__name {
  font-weight: 900;
  font-size: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.seller-card__relation-tag {
  padding: 3px 9px;
  color: var(--ink);
  background: var(--yellow);
  border: 1.5px solid var(--ink);
  border-radius: 999px;
  box-shadow: 1px 1px 0 var(--ink);
  font-size: 11.5px;
  font-weight: 900;
  line-height: 1.3;
}

.seller-card__verified {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border: 1.5px solid var(--ink);
  border-radius: 6px;
  background: #D6F2DF;
  box-shadow: 1px 1px 0 var(--ink);
  font-size: 11px;
  font-weight: 900;
}

.seller-card__verified svg { width: 12px; height: 12px; }

.seller-card__relation-tag:nth-of-type(3n + 2) {
  background: #DCEEFF;
}

.seller-card__relation-tag:nth-of-type(3n) {
  background: #D6F2DF;
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
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-m);
  background: var(--white);
  box-shadow: var(--shadow-m);
}

.lineage-section__head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 18px;
  border-bottom: 1.5px dashed #D8CEBB;
}

.lineage-section__icon {
  width: 50px;
  height: 50px;
  flex: 0 0 50px;
  display: grid;
  place-items: center;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  background: var(--yellow);
  box-shadow: 3px 3px 0 var(--ink);
  transform: rotate(-4deg);
}

.lineage-section__icon svg { width: 27px; height: 27px; }
.lineage-section__head small { color: var(--primary); font-size: 10.5px; font-weight: 900; }
.lineage-section__head h2 { font-family: var(--font-display); font-size: 24px; }
.lineage-section__head p { margin-top: 2px; color: var(--ink-soft); font-size: 13px; }

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
  content: "";
  position: absolute;
  top: 17px;
  left: 34px;
  right: 0;
  border-top: 2px dashed var(--ink);
}

.lineage-timeline__dot {
  position: relative;
  z-index: 1;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: var(--bw) solid var(--ink);
  border-radius: 50%;
  background: var(--yellow);
  box-shadow: 2px 2px 0 var(--ink);
  font-family: var(--font-display);
}

.lineage-timeline__content { margin-top: 12px; }
.lineage-timeline__content > div { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; }
.lineage-timeline__content strong { font-size: 14px; }
.lineage-timeline__content span { padding: 2px 6px; border: 1px solid var(--ink); border-radius: 5px; background: var(--paper-deep); font-size: 10px; font-weight: 800; }
.lineage-timeline__content small { display: block; margin-top: 5px; color: var(--ink-soft); font-size: 11px; }
.lineage-section__empty { margin: 22px 0 2px; color: var(--ink-soft); font-size: 13px; }

.desc-block h2 {
  font-family: var(--font-display);
  font-size: 20px;
  letter-spacing: 1px;
  margin-bottom: 10px;
}

.desc-block p {
  font-size: 15px;
  line-height: 1.9;
  color: #3D372E;
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

.escrow-note {
  font-size: 12.5px;
  margin-top: 12px;
}

.empty-panel {
  min-height: 280px;
  display: grid;
  place-items: center;
  justify-content: center;
  gap: var(--spacing-md);
  background: var(--white);
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-m);
  box-shadow: var(--shadow-m);
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
