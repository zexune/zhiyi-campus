<template>
  <DefaultLayout>
    <div class="home-page">
      <section class="hero">
        <div class="hero__inner">
          <div class="float-sticker fs-1" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="#F5562E" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>
            吉他 ¥260
          </div>
          <div class="float-sticker fs-2" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="#3B7BD8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 7h8M8 11h8M8 15h5"/></svg>
            高数教材 ¥15
          </div>
          <div class="float-sticker fs-3" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="#26221C" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="2" width="14" height="20" rx="2"/><path d="M12 18h.01"/></svg>
            iPad Air5 ¥2000
          </div>

          <h1 class="rise">校园里的好东西<br>都在<span class="hl">这块布告栏</span>上</h1>
          <p class="sub rise rise-1">AI 智能审核 · 平台担保交易 · 当面验货，放心买卖</p>

          <form class="searchbar rise rise-2" role="search" @submit.prevent="handleSearch">
            <span class="searchbar__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg></span>
            <input v-model="filters.keyword" type="search" placeholder="搜一搜：iPad、高数教材、吉他、瑜伽垫…" aria-label="搜索商品">
            <button
              v-if="filters.keyword"
              class="searchbar__clear"
              type="button"
              title="清空搜索"
              aria-label="清空搜索"
              @click="clearKeyword"
            ><svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round"><path d="m6 6 12 12M18 6 6 18"/></svg></button>
            <button type="submit" :disabled="loading">
              <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>
              搜索
            </button>
          </form>

          <div class="hot-words rise rise-3">
            <span class="lab">热搜：</span>
            <button class="tag" type="button" @click="quickSearch('iPad')">iPad</button>
            <button class="tag" type="button" @click="quickSearch('四级真题')">四级真题</button>
            <button class="tag" type="button" @click="quickSearch('小米充电宝')">小米充电宝</button>
            <button class="tag" type="button" @click="quickSearch('Switch')">Switch</button>
            <button class="tag" type="button" @click="quickSearch('考研政治')">考研政治</button>
          </div>
        </div>
      </section>

      <section v-if="!loggedIn" class="login-gate">
        <span class="login-gate__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 11 12 4l9 7"/><path d="M5 10v10h14V10"/><path d="M9 20v-6h6v6"/></svg>
        </span>
        <div>
          <h2>先登录，再逛本校交易大厅</h2>
          <p>登录后只会展示你所在学校的商品、榜单和聊天内容。</p>
        </div>
        <router-link :to="{ path: '/login', query: { redirect: '/' } }" class="btn btn--primary">登录并进入</router-link>
      </section>

      <section v-if="loggedIn && activeTopic" class="topic-banner">
        <span class="topic-banner__stamp">{{ activeTopic.stamp }}</span>
        <div class="topic-banner__copy">
          <small>{{ activeTopic.dateLabel }}</small>
          <h2>{{ activeTopic.title }}</h2>
          <p>{{ activeTopic.description }}</p>
        </div>
        <button class="btn btn--yellow" type="button" @click="applyTopic(activeTopic)">
          {{ activeTopic.action }}
          <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6"/></svg>
        </button>
      </section>

      <div v-if="loggedIn" class="cat-row" role="tablist" aria-label="商品大类筛选">
          <button
            class="cat-chip"
            :class="{ active: !filters.categoryId }"
            @click="selectCategory('')"
          ><CategoryIcon name="全部" />全部</button>
          <button
            v-for="category in categories"
            :key="category.id"
            class="cat-chip"
            :class="{ active: filters.categoryId === category.id }"
            @click="selectCategory(category.id)"
          >
            <CategoryIcon :name="category.name" />{{ category.name }}
          </button>
      </div>

      <section v-if="loggedIn" class="filter-panel">
        <div class="filter-panel__bar">
          <button class="filter-panel__title" type="button" :aria-expanded="showTagCloud" @click="toggleTagCloud">
            <span class="filter-panel__stamp"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 5h16l-6 7v5l-4 2v-7Z"/></svg></span>
            <strong>精细筛选</strong>
            <span class="filter-panel__chevron" :class="{ open: showTagCloud }"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="m6 9 6 6 6-6"/></svg></span>
          </button>
          <div class="advanced-row">
            <label class="filter-field">
              <span>发布类型</span>
              <AppSelect
                v-model="filters.type"
                :options="TYPE_OPTIONS"
                aria-label="发布类型"
              />
            </label>
            <fieldset class="filter-field price-field">
              <legend>价格区间</legend>
              <div class="price-range">
                <span>¥</span><input v-model.number="filters.minPrice" type="number" min="0" step="1" placeholder="最低价" @blur="applyPriceFilterNow" @keyup.enter="applyPriceFilterNow">
                <i>—</i>
                <span>¥</span><input v-model.number="filters.maxPrice" type="number" min="0" step="1" placeholder="最高价" @blur="applyPriceFilterNow" @keyup.enter="applyPriceFilterNow">
              </div>
            </fieldset>
            <button class="btn filter-reset" type="button" :disabled="loading" @click="resetFilters">
              <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 11a8 8 0 1 0-2.34 5.66"/><path d="M20 4v7h-7"/></svg>
              重置
            </button>
          </div>
        </div>
        <div class="tag-cloud-wrap" :class="{ open: showTagCloud && allTags.length }">
          <div class="tag-cloud-wrap__inner">
            <div class="tag-cloud">
              <div v-for="group in allTags" :key="group.categoryId" class="tag-group">
                <span class="tag-group__label">{{ group.categoryName }}</span>
                <div class="tag-group__chips">
                  <button
                    v-for="tag in group.tags"
                    :key="tag.name"
                    class="tag-cloud__chip"
                    :class="{ active: activeTag === tag.name }"
                    @click="filterByTag(tag.name, group.categoryId)"
                  >{{ tag.name }}<span class="tag-cloud__count">{{ tag.count }}</span></button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div v-if="loggedIn" class="hall">
        <main aria-label="商品列表">
          <div class="sort-row">
            <div class="sort-tabs" role="tablist" aria-label="排序方式">
              <button :class="{ active: filters.sort === 'random' }" @click="filters.sort = 'random'">智能推荐</button>
              <button :class="{ active: filters.sort === 'latest' }" @click="filters.sort = 'latest'">最新发布</button>
              <button :class="{ active: filters.sort === 'priceAsc' }" @click="filters.sort = 'priceAsc'">价格 ↑</button>
              <button :class="{ active: filters.sort === 'priceDesc' }" @click="filters.sort = 'priceDesc'">价格 ↓</button>
            </div>
            <span class="muted goods-total">共 <strong>{{ total }}</strong> 件在售好物</span>
          </div>

          <el-skeleton v-if="loading && !items.length" :rows="8" animated />

          <div v-else-if="items.length" class="goods-grid">
            <article
              v-for="item in items"
              :key="item.id"
              class="goods-card rise"
              @click="goDetail(item.id)"
            >
              <div class="goods-card__img" :class="phClass(item.id)">
                <img v-if="item.coverImage" :src="item.coverImage" :alt="item.title" />
                <span class="badge goods-card__type" :class="item.type === 'BUY' ? 'badge--buy' : 'badge--sell'">
                  {{ itemTypeLabel(item.type) }} <span v-if="item.deadlineLabel">{{ item.deadlineLabel }}</span>
                </span>
              </div>
              <div class="goods-card__body">
                <h2 class="goods-card__title">{{ item.title }}</h2>
                <AiTagList :tags="item.aiTags" :limit="3" @select="searchByTag" />
                <div class="goods-card__relations">
                  <span v-if="item.dormitoryRelation === 'SAME_BUILDING'" class="neighbor-badge">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"><path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2.5"/></svg>
                    本楼
                  </span>
                  <span v-else-if="item.dormitoryRelation === 'SAME_CAMPUS'" class="neighbor-badge neighbor-badge--campus">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 21V7l8-4 8 4v14"/><path d="M9 21v-5h6v5M8 9h.01M12 9h.01M16 9h.01M8 13h.01M12 13h.01M16 13h.01"/></svg>
                    本校区
                  </span>
                </div>
                <div class="goods-card__meta">
                  <strong v-if="item.type === 'SWAP'" class="price">换物</strong>
                  <PriceTag v-else :value="item.price" font-size="22px" />
                  <span class="goods-card__fav">
                    <svg class="heart-icon" viewBox="0 0 24 24" :fill="item.favoriteByCurrentUser ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2"><path d="M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2C10.5 3.5 9.3 3 7.5 3A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z"/></svg>
                    {{ item.favoriteCount || 0 }}
                  </span>
                  <button
                    class="fav-button"
                    :class="{ active: item.favoriteByCurrentUser }"
                    :disabled="favoriteBusyId === item.id"
                    @click.stop="handleFavorite(item)"
                    :title="item.favoriteByCurrentUser ? '取消收藏' : '收藏商品'"
                  >
                    <svg class="heart-icon" viewBox="0 0 24 24" :fill="item.favoriteByCurrentUser ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2"><path d="M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2C10.5 3.5 9.3 3 7.5 3A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z"/></svg>
                  </button>
                </div>
                <div class="goods-card__seller">
                  <span class="seller-name">{{ item.publisherNickname || '同学' }}</span>
                  <span v-if="item.publisherVerified" class="verified-badge" title="已填写本校邮箱">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12 4 4L19 6"/></svg>
                    已认证
                  </span>
                  <LevelBadge :level="item.publisherLevel || 1" />
                  <span class="muted">浏览 {{ item.viewCount || 0 }}</span>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="empty-panel">
            <p class="muted">未找到相关商品</p>
            <button class="btn btn--primary" @click="resetFilters">清空筛选</button>
          </div>

          <div v-if="items.length" class="load-more">
            <button class="btn btn--yellow btn--lg" :disabled="loading" @click="fetchItems">
              再看一批
              <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 11a8 8 0 1 0-2.34 5.66"/><path d="M20 4v7h-7"/></svg>
            </button>
          </div>

          <el-pagination
            v-if="total > pageSize"
            v-model:current-page="page"
            :page-size="pageSize"
            :total="total"
            layout="prev, pager, next"
            @current-change="fetchItems"
          />
        </main>

        <aside>
          <div class="hall-aside__sticky">
            <div class="card rank-card sticker-tilt-r" aria-label="近期爆款榜单">
              <div class="rank-card__head">
                <div>
                  <h3>近期爆款榜</h3>
                  <p class="muted rank-sub">按收藏数实时更新</p>
                </div>
                <router-link to="/ranking" class="rank-more" title="查看完整榜单">
                  查看完整榜单
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="m9 18 6-6-6-6"/></svg>
                </router-link>
              </div>
              <div v-if="ranking.length" class="ranking-list">
                <button
                  v-for="(item, index) in ranking"
                  :key="item.id"
                  class="rank-item"
                  @click="goDetail(item.id)"
                >
                  <span class="rank-item__no">{{ index + 1 }}</span>
                  <span class="rank-item__thumb" :class="phClass(item.id)">
                    <img v-if="item.coverImage" :src="item.coverImage" :alt="item.title" />
                  </span>
                  <span class="rank-item__info">
                    <strong class="rank-item__title">{{ item.title }}</strong>
                    <small class="rank-item__sub">
                      <span class="p">¥{{ Number(item.price || 0).toFixed(2) }}</span>
                      <span>收藏 {{ item.favoriteCount || 0 }}</span>
                    </small>
                  </span>
                </button>
              </div>
              <p v-else class="muted ranking-empty">暂无榜单数据</p>
            </div>

            <div class="publish-cta sticker-tilt">
              <h4>宿舍角落在吃灰？</h4>
              <p>发布 30 秒搞定，AI 自动打标签</p>
              <router-link to="/publish" class="btn btn--yellow">去发布闲置</router-link>
            </div>
          </div>
        </aside>
      </div>
    </div>
  </DefaultLayout>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppSelect from '@/components/common/AppSelect.vue'
import AiTagList from '@/components/common/AiTagList.vue'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import CategoryIcon from '@/components/common/CategoryIcon.vue'
import LevelBadge from '@/components/common/LevelBadge.vue'
import PriceTag from '@/components/common/PriceTag.vue'
import { getActiveTopic, getCategories, getAllTags, getItemList, getItemRanking, toggleFavorite } from '@/api/item'
import { isLoggedIn } from '@/utils/auth'

const PH = ['ph-a', 'ph-b', 'ph-c', 'ph-d', 'ph-e', 'ph-f']
const TYPE_OPTIONS = [
  { label: '全部类型', value: '' },
  { label: '出售', value: 'SELL' },
  { label: '求购', value: 'BUY' },
  { label: '以物换物', value: 'SWAP' },
  { label: '帮带跑腿', value: 'ERRAND' },
]
const FALLBACK_CATEGORIES = [
  { id: 1, name: '数码电子' },
  { id: 2, name: '教材书籍' },
  { id: 3, name: '服饰鞋包' },
  { id: 4, name: '生活日用' },
  { id: 5, name: '运动娱乐' },
  { id: 6, name: '零食饮品' },
  { id: 7, name: '学习用品' },
  { id: 8, name: '其他' },
]
const CAMPUS_TOPICS = [
  {
    id: 'new-student',
    start: 825,
    end: 915,
    stamp: 'NEW',
    dateLabel: '08.25 - 09.15',
    title: '新生入学季',
    description: '宿舍生活、学习用品和入门数码，一站配齐新学期。',
    action: '逛新生必备',
    categoryName: '生活日用',
  },
  {
    id: 'final-exam',
    start: 1220,
    end: 120,
    stamp: 'EXAM',
    dateLabel: '12.20 - 01.20',
    title: '期末备考季',
    description: '真题、笔记和教材集中上架，复习资料更快找到。',
    action: '找备考资料',
    categoryName: '教材书籍',
    keyword: '真题',
  },
  {
    id: 'graduation',
    start: 525,
    end: 630,
    stamp: 'SALE',
    dateLabel: '05.25 - 06.30',
    title: '毕业清仓季',
    description: '把带不走的好物留在校园，让下一位同学接着使用。',
    action: '查看毕业好物',
    keyword: '毕业',
  },
]

const router = useRouter()
const route = useRoute()
const loggedIn = isLoggedIn()
const categories = ref(FALLBACK_CATEGORIES)
const items = ref([])
const ranking = ref([])
const page = ref(1)
const pageSize = 12
const total = ref(0)
const loading = ref(false)
const favoriteBusyId = ref(null)
const activeTopic = ref(CAMPUS_TOPICS.find(isTopicActive) || null)
let priceFilterTimer = null
let resettingFilters = false

const allTags = ref([])
const activeTag = ref('')
const showTagCloud = ref(false)

function toggleTagCloud() {
  showTagCloud.value = !showTagCloud.value
}

const filters = reactive({
  keyword: '',
  categoryId: '',
  minPrice: undefined,
  maxPrice: undefined,
  type: '',
  tag: '',
  sort: 'random',
})

function phClass(id) {
  return PH[Number(id) % PH.length]
}

function itemTypeLabel(type) {
  return { SELL: '出售', BUY: '求购', SWAP: '换物', ERRAND: '跑腿' }[type] || type
}

function isTopicActive(topic) {
  const now = new Date()
  const monthDay = (now.getMonth() + 1) * 100 + now.getDate()
  return topic.start <= topic.end
    ? monthDay >= topic.start && monthDay <= topic.end
    : monthDay >= topic.start || monthDay <= topic.end
}

function buildParams() {
  const params = { page: page.value, size: pageSize, sort: filters.sort }
  if (filters.keyword?.trim()) params.keyword = filters.keyword.trim()
  if (filters.categoryId) params.categoryId = filters.categoryId
  if (filters.minPrice !== undefined && filters.minPrice !== null) params.minPrice = filters.minPrice
  if (filters.maxPrice !== undefined && filters.maxPrice !== null) params.maxPrice = filters.maxPrice
  if (filters.type) params.type = filters.type
  if (filters.tag) params.tag = filters.tag
  return params
}

async function fetchCategories() {
  try {
    const res = await getCategories()
    if (Array.isArray(res.data) && res.data.length) categories.value = res.data
  } catch {
    categories.value = FALLBACK_CATEGORIES
  }
}

async function fetchAllTags() {
  if (!loggedIn) return
  try {
    const res = await getAllTags()
    allTags.value = Array.isArray(res.data) ? res.data : []
  } catch {
    allTags.value = []
  }
}

function filterByTag(tag, categoryId) {
  if (activeTag.value === tag) {
    activeTag.value = ''
    filters.tag = ''
    filters.categoryId = ''
  } else {
    activeTag.value = tag
    filters.tag = tag
    filters.categoryId = categoryId
    // 清除关键词搜索，避免 LIKE 模糊匹配干扰标签的精确筛选
    filters.keyword = ''
  }
  page.value = 1
  fetchItems()
}

async function fetchRanking() {
  if (!loggedIn) return
  const res = await getItemRanking({ limit: 10 })
  ranking.value = res.data || []
}

async function fetchActiveTopic() {
  if (!loggedIn) return
  try {
    const res = await getActiveTopic()
    if (res.data) {
      const topic = res.data
      activeTopic.value = {
        ...topic,
        stamp: 'TOPIC',
        dateLabel: `${formatTopicDate(topic.startTime)} - ${formatTopicDate(topic.endTime)}`,
        description: topic.bannerText,
        action: '进入专题',
      }
    }
  } catch { /* 保留内置季节专题作为降级展示 */ }
}

function formatTopicDate(value) {
  if (!value) return ''
  const d = new Date(value)
  return `${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
}

async function fetchItems() {
  if (!loggedIn) return
  loading.value = true
  try {
    const res = await getItemList(buildParams())
    items.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

function applyPriceFilterNow() {
  if (!priceFilterTimer) return
  window.clearTimeout(priceFilterTimer)
  priceFilterTimer = null
  handleSearch()
}

function schedulePriceFilter() {
  if (!loggedIn || resettingFilters) return
  window.clearTimeout(priceFilterTimer)
  priceFilterTimer = window.setTimeout(() => {
    priceFilterTimer = null
    handleSearch()
  }, 450)
}

function handleSearch() {
  if (!loggedIn) {
    router.push({ path: '/login', query: { redirect: '/' } })
    return
  }
  page.value = 1
  fetchItems()
}

function quickSearch(keyword) {
  filters.keyword = keyword
  handleSearch()
}

function clearKeyword() {
  filters.keyword = ''
  handleSearch()
}

function searchByTag(tag) {
  filters.keyword = tag
  filters.tag = ''
  activeTag.value = ''
  router.replace({ path: '/', query: { keyword: tag } })
  handleSearch()
  nextTick(() => document.querySelector('.hall')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function applyTopic(topic) {
  filters.keyword = topic.keyword || ''
  filters.tag = topic.filterTag || ''
  activeTag.value = topic.filterTag || ''
  filters.type = topic.filterType || ''
  const category = categories.value.find((item) => item.name === topic.categoryName)
  filters.categoryId = topic.filterCategoryId || category?.id || ''
  handleSearch()
  nextTick(() => document.querySelector('.hall')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function resetFilters() {
  resettingFilters = true
  window.clearTimeout(priceFilterTimer)
  priceFilterTimer = null
  filters.keyword = ''
  filters.categoryId = ''
  filters.minPrice = undefined
  filters.maxPrice = undefined
  filters.type = ''
  filters.tag = ''
  filters.sort = 'random'
  activeTag.value = ''
  page.value = 1
  fetchItems()
  nextTick(() => { resettingFilters = false })
}

function selectCategory(id) {
  filters.categoryId = id
  handleSearch()
}

function goDetail(id) {
  router.push(`/item/${id}`)
}

async function handleFavorite(item) {
  if (!isLoggedIn()) {
    router.push({ path: '/login', query: { redirect: '/' } })
    return
  }
  favoriteBusyId.value = item.id
  try {
    const res = await toggleFavorite(item.id)
    item.favoriteByCurrentUser = res.data.favorite
    item.favoriteCount = res.data.favoriteCount
    ElMessage.success(res.data.favorite ? '已收藏' : '已取消收藏')
    fetchRanking()
  } finally {
    favoriteBusyId.value = null
  }
}

watch(() => filters.sort, () => {
  if (!resettingFilters) handleSearch()
})
watch(() => filters.type, () => {
  if (!resettingFilters) handleSearch()
})
watch(() => [filters.minPrice, filters.maxPrice], schedulePriceFilter)

onMounted(async () => {
  if (route.query.keyword) {
    filters.keyword = String(route.query.keyword)
  }
  if (!loggedIn) {
    await fetchCategories()
    return
  }
  await Promise.all([fetchCategories(), fetchItems(), fetchRanking(), fetchAllTags(), fetchActiveTopic()])
})

onBeforeUnmount(() => window.clearTimeout(priceFilterTimer))
</script>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.hero {
  position: relative;
  overflow: hidden;
  width: 100vw;
  margin-left: calc(50% - 50vw);
  margin-top: calc(var(--spacing-lg) * -1);
  border-bottom: var(--bw) solid var(--ink);
  background: var(--paper-deep);
}

.login-gate {
  margin: 30px 0;
  padding: 26px 28px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 20px;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-m);
  background: var(--white);
  box-shadow: var(--shadow-m);
}

.login-gate__icon {
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  background: var(--yellow);
  box-shadow: 3px 3px 0 var(--ink);
  transform: rotate(-4deg);
}

.login-gate__icon svg { width: 29px; height: 29px; }
.login-gate h2 { font-family: var(--font-display); font-size: 24px; }
.login-gate p { margin-top: 5px; color: var(--ink-soft); font-size: 14px; }

.topic-banner {
  margin: 24px 0 4px;
  padding: 18px 22px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-m);
  background: #DCEEFF;
  box-shadow: var(--shadow-s);
}

.topic-banner__stamp {
  min-width: 62px;
  padding: 8px 9px;
  border: var(--bw) solid var(--ink);
  border-radius: 6px;
  background: var(--primary);
  color: var(--white);
  box-shadow: 3px 3px 0 var(--ink);
  font-family: var(--font-display);
  text-align: center;
  transform: rotate(-4deg);
}

.topic-banner__copy small { color: var(--primary-deep); font-size: 11px; font-weight: 900; }
.topic-banner__copy h2 { margin-top: 1px; font-family: var(--font-display); font-size: 24px; }
.topic-banner__copy p { margin-top: 3px; color: var(--ink-soft); font-size: 13px; }

.hero__inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 52px 20px 60px;
  position: relative;
  text-align: center;
}

.hero h1 {
  font-family: var(--font-display);
  font-size: clamp(32px, 5vw, 52px);
  letter-spacing: 2px;
  line-height: 1.25;
}

.hero h1 .hl {
  display: inline-block;
  padding: 0 10px;
  background: var(--yellow);
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  box-shadow: var(--shadow-s);
  transform: rotate(-1.5deg);
}

.hero p.sub {
  margin-top: 12px;
  color: var(--ink-soft);
  font-size: 16px;
}

.searchbar {
  max-width: 640px;
  margin: 28px auto 0;
  display: flex;
  align-items: stretch;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-m);
  background: var(--white);
  box-shadow: var(--shadow-m);
  overflow: hidden;
  transition: transform .18s, box-shadow .18s;
}

.searchbar:focus-within {
  transform: translate(-2px, -2px);
  box-shadow: 7px 7px 0 var(--ink);
}

.searchbar__icon {
  width: 50px;
  flex: 0 0 50px;
  display: grid;
  place-items: center;
  color: var(--ink-soft);
  font-size: 20px;
}

.searchbar__icon svg { width: 21px; height: 21px; }

.ui-icon {
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
}

.searchbar input {
  flex: 1;
  border: none;
  padding: 16px 6px 16px 0;
  font-size: 16px;
  font-family: inherit;
  background: transparent;
  min-width: 0;
}

.searchbar input::-webkit-search-cancel-button { display: none; }

.searchbar__clear {
  width: 42px;
  flex: 0 0 42px;
  display: grid;
  place-items: center;
  border: none;
  background: var(--white);
  color: var(--ink-soft);
  cursor: pointer;
  font-size: 18px;
}

.searchbar__clear:hover { color: var(--primary); }

.searchbar input:focus {
  outline: none;
}

.searchbar button {
  border: none;
  border-left: var(--bw) solid var(--ink);
  background: var(--primary);
  color: var(--white);
  font-weight: 700;
  font-size: 16px;
  padding: 0 30px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
}

.searchbar .searchbar__clear {
  padding: 0;
  border-left: 0;
  background: var(--white);
  color: var(--ink-soft);
}

.searchbar .searchbar__clear:hover { background: var(--paper-deep); color: var(--primary); }

.searchbar button:hover {
  background: var(--primary-deep);
}

.hot-words {
  margin-top: 14px;
  display: flex;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
}

.hot-words .lab {
  font-size: 13px;
  color: var(--ink-soft);
  font-weight: 700;
}

.float-sticker {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 7px;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  background: var(--white);
  box-shadow: var(--shadow-s);
  padding: 8px 14px;
  font-weight: 700;
  font-size: 14px;
  pointer-events: none;
  will-change: translate;
}

.float-sticker svg { width: 20px; height: 20px; flex: 0 0 20px; }

.fs-1 {
  top: 44px;
  left: 4%;
  transform: rotate(-7deg);
  animation: sticker-bob 4s ease-in-out infinite;
}

.fs-2 {
  top: 130px;
  right: 5%;
  transform: rotate(5deg);
  animation: sticker-bob 4.6s .5s ease-in-out infinite;
}

.fs-3 {
  bottom: 30px;
  left: 10%;
  transform: rotate(4deg);
  background: var(--yellow);
  animation: sticker-bob 5s 1s ease-in-out infinite;
}

@keyframes sticker-bob {
  0%, 100% { translate: 0 0; }
  50% { translate: 0 -9px; }
}

.cat-row {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding: 22px 0 8px;
  scrollbar-width: none;
}

.cat-row::-webkit-scrollbar {
  display: none;
}

.cat-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding: 9px 18px;
  border: var(--bw) solid var(--ink);
  border-radius: 999px;
  background: var(--white);
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--ink);
  transition: all .18s;
}

.cat-chip svg { width: 17px; height: 17px; flex: 0 0 17px; }

.cat-chip:hover {
  transform: translate(-1px, -1px);
  box-shadow: 4px 4px 0 var(--ink);
  background: var(--paper-deep);
}

.cat-chip.active {
  background: var(--ink);
  color: var(--paper);
}

.cat-chip.active svg { stroke: var(--paper); }

.filter-panel {
  margin: 16px 0 22px;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 0;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-m);
  background: var(--paper-deep);
  box-shadow: var(--shadow-s);
}

.filter-panel__bar {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
}

.filter-panel__title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0;
  text-align: left;
  font-family: inherit;
  color: inherit;
  transition: opacity .15s;
}

.filter-panel__title:hover {
  opacity: .8;
}

.filter-panel__title:focus-visible {
  outline: 3px solid var(--blue);
  outline-offset: 2px;
  border-radius: var(--r-s);
}

.filter-panel__title strong {
  font-family: var(--font-display);
  font-size: 18px;
  line-height: 1.2;
  white-space: nowrap;
}

.filter-panel__stamp {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  background: var(--yellow);
  box-shadow: 2px 2px 0 var(--ink);
  transform: rotate(-4deg);
}

.filter-panel__stamp svg { width: 19px; height: 19px; }

.filter-panel__chevron {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  color: var(--ink-soft);
  transition: transform .25s;
}

.filter-panel__chevron svg { width: 16px; height: 16px; }

.filter-panel__chevron.open {
  transform: rotate(180deg);
}

.advanced-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  flex: 1;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.filter-field {
  min-width: 120px;
  margin: 0;
  padding: 0;
  border: 0;
}

.filter-field > span,
.filter-field legend {
  display: block;
  margin-bottom: 4px;
  padding: 0;
  color: var(--ink-soft);
  font-size: 11px;
  font-weight: 700;
}

.filter-field .select {
  height: 38px;
  padding-top: 7px;
  padding-bottom: 7px;
  font-size: 13px;
  box-shadow: none;
}

.price-field { min-width: 250px; }

.price-range {
  height: 40px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  background: var(--white);
}

.price-range:focus-within { box-shadow: var(--shadow-s); }

.price-range span {
  color: var(--primary);
  font-weight: 900;
}

.price-range i {
  color: var(--ink-soft);
  font-style: normal;
}

.price-range input {
  width: 78px;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--ink);
  font-family: inherit;
  font-size: 13px;
}

.price-range input::-webkit-inner-spin-button { display: none; }

.filter-reset {
  height: 40px;
  padding: 7px 14px;
  box-shadow: 2px 2px 0 var(--ink);
}

/* —— 标签云（按大類分组，可折叠滚动）—— */
.tag-cloud-wrap {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows .25s ease;
}

.tag-cloud-wrap.open {
  grid-template-rows: 1fr;
}

.tag-cloud-wrap__inner {
  overflow: hidden;
}

.tag-cloud {
  width: 100%;
  max-height: 280px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--ink-soft) transparent;
  margin-top: 14px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: var(--white);
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  box-shadow: inset 0 1px 4px rgba(0,0,0,.04);
}

.tag-cloud::-webkit-scrollbar { width: 6px; }
.tag-cloud::-webkit-scrollbar-thumb { background: var(--ink-soft); border-radius: 3px; }

.tag-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tag-group__label {
  font-size: 11px;
  font-weight: 700;
  color: var(--ink-soft);
  text-transform: uppercase;
  letter-spacing: .5px;
  padding-bottom: 2px;
  border-bottom: 1px solid #E5DCC9;
}

.tag-group__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag-cloud__chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 14px;
  font-size: 13px;
  font-weight: 700;
  background: var(--white);
  border: 1.5px dashed var(--ink-soft);
  border-radius: 999px;
  cursor: pointer;
  transition: all .18s;
  color: var(--ink-soft);
  font-family: var(--font-body);
}

.tag-cloud__chip:hover {
  border-style: solid;
  border-color: var(--ink);
  background: var(--yellow);
  color: var(--ink);
  transform: translate(-1px, -1px);
  box-shadow: 2px 2px 0 var(--ink);
}

.tag-cloud__chip.active {
  border-style: solid;
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper);
  box-shadow: var(--shadow-s);
}

.tag-cloud__count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--paper-deep);
  color: var(--ink-soft);
  font-size: 10.5px;
  font-weight: 700;
  line-height: 1;
}

.tag-cloud__chip.active .tag-cloud__count {
  background: var(--primary);
  color: #fff;
}

.hall {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 28px;
  margin-top: 10px;
  align-items: start;
}

.sort-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
  flex-wrap: wrap;
  gap: 12px;
}

.goods-total {
  font-size: 13px;
  white-space: nowrap;
}

.goods-total strong { color: var(--primary); }

.sort-tabs {
  display: flex;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  overflow: hidden;
  background: var(--white);
}

.sort-tabs button {
  border: none;
  background: transparent;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  color: var(--ink-soft);
  border-right: 1.5px solid #E5DCC9;
}

.sort-tabs button:last-child {
  border-right: none;
}

.sort-tabs button:hover {
  color: var(--ink);
  background: var(--paper-deep);
}

.sort-tabs button.active {
  background: var(--yellow);
  color: var(--ink);
}

.goods-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 22px;
}

.goods-card {
  background: var(--white);
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-m);
  overflow: hidden;
  cursor: pointer;
  box-shadow: var(--shadow-s);
  transition: transform .2s, box-shadow .2s;
  display: flex;
  flex-direction: column;
  position: relative;
}

.goods-card:hover {
  transform: translate(-3px, -3px);
  box-shadow: var(--shadow-l);
}

.goods-card__img {
  position: relative;
  aspect-ratio: 4 / 3;
  border-bottom: var(--bw) solid var(--ink);
  display: grid;
  place-items: center;
  overflow: hidden;
}

.goods-card__img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.goods-card__type {
  position: absolute;
  top: 10px;
  left: 10px;
}

.goods-card__body {
  padding: 13px 15px 15px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.goods-card__title {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.9em;
}

.goods-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-top: auto;
}

.goods-card__fav {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--ink-soft);
  font-size: 13px;
}

.heart-icon { width: 17px; height: 17px; flex: 0 0 17px; }

.goods-card__seller {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--ink-soft);
  border-top: 1.5px dashed #E0D6C2;
  padding-top: 9px;
}

.goods-card__relations {
  min-height: 25px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.neighbor-badge,
.verified-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 7px;
  border: 1.5px solid var(--ink);
  border-radius: 6px;
  background: var(--yellow);
  color: var(--ink);
  font-size: 10.5px;
  font-weight: 900;
  line-height: 1;
  white-space: nowrap;
}

.neighbor-badge svg { width: 12px; height: 12px; }
.neighbor-badge--campus { background: #DCEEFF; }
.verified-badge { background: #D6F2DF; }
.verified-badge svg { width: 12px; height: 12px; }

.seller-name {
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fav-button {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: var(--bw) solid var(--ink);
  border-radius: 50%;
  background: var(--white);
  cursor: pointer;
}

.fav-button.active {
  background: var(--yellow);
  color: var(--red);
}

.hall-aside__sticky {
  position: sticky;
  top: 84px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.rank-card {
  padding: 20px;
  max-height: calc(100vh - 300px);
  overflow-y: auto;
}

.rank-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.rank-more {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding-top: 4px;
  color: var(--primary);
  font-size: 11.5px;
  font-weight: 900;
  white-space: nowrap;
}

.rank-more:hover { color: var(--primary-deep); }
.rank-more svg { width: 15px; height: 15px; }

.rank-card h3 {
  font-family: var(--font-display);
  font-size: 22px;
  letter-spacing: 1px;
  margin-bottom: 4px;
}

.rank-sub {
  font-size: 12.5px;
  margin-bottom: 8px;
}

.ranking-list {
  display: flex;
  flex-direction: column;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border: none;
  border-bottom: 1.5px dashed #E0D6C2;
  background: transparent;
  cursor: pointer;
  text-align: left;
  border-radius: 8px;
  transition: background .15s;
}

.rank-item:hover {
  background: var(--paper-deep);
}

.rank-item:last-child {
  border-bottom: none;
}

.rank-item__no {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  font-family: var(--font-display);
  font-size: 16px;
  border: var(--bw) solid var(--ink);
  border-radius: 8px;
  background: var(--white);
}

.rank-item:nth-child(1) .rank-item__no {
  background: var(--yellow);
  box-shadow: 2px 2px 0 var(--ink);
}

.rank-item:nth-child(2) .rank-item__no { background: #DCD6CB; }
.rank-item:nth-child(3) .rank-item__no { background: #F0C9A8; }

.rank-item__thumb {
  width: 46px;
  height: 46px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-s);
  overflow: hidden;
  flex-shrink: 0;
  display: grid;
  place-items: center;
}

.rank-item__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.rank-item__info {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.rank-item__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13.5px;
  font-weight: 700;
}

.rank-item__sub {
  font-size: 12px;
  color: var(--ink-soft);
  display: flex;
  gap: 10px;
  margin-top: 2px;
}

.rank-item__sub .p {
  color: var(--primary);
  font-weight: 700;
}

.publish-cta {
  padding: 24px 20px;
  text-align: center;
  background: var(--ink);
  color: var(--paper);
  border-radius: var(--r-m);
  border: var(--bw) solid var(--ink);
  box-shadow: var(--shadow-m);
}

.publish-cta h4 {
  font-family: var(--font-display);
  font-size: 21px;
  letter-spacing: 1px;
}

.publish-cta p {
  font-size: 13px;
  opacity: .75;
  margin: 6px 0 16px;
}

.ranking-empty {
  color: var(--ink-soft);
}

.load-more {
  text-align: center;
  margin: 36px 0 10px;
}

.empty-panel {
  min-height: 260px;
  display: grid;
  place-items: center;
  gap: var(--spacing-md);
  justify-content: center;
  background: var(--white);
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-m);
  box-shadow: var(--shadow-m);
}

@media (max-width: 980px) {
  .hall {
    grid-template-columns: 1fr;
  }

  .hall-aside__sticky {
    position: static;
  }

  .rank-card { max-height: none; overflow: visible; }

  .filter-panel__bar {
    gap: 12px;
  }

  .advanced-row {
    justify-content: flex-start;
    flex: 1 1 100%;
  }
}

@media (max-width: 900px) {
  .float-sticker {
    display: none;
  }
}

@media (max-width: 700px) {
  .goods-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 14px;
  }
}

@media (max-width: 680px) {
  .login-gate,
  .topic-banner {
    grid-template-columns: auto minmax(0, 1fr);
    padding: 18px;
  }
  .login-gate .btn,
  .topic-banner .btn { grid-column: 1 / -1; width: 100%; }
  .hero__inner { padding: 38px 16px 42px; }
  .hero h1 { font-size: 34px; }
  .hero p.sub { font-size: 14px; }
  .searchbar { margin-top: 22px; }
  .searchbar__icon { width: 42px; flex-basis: 42px; }
  .searchbar input { padding-right: 2px; font-size: 14px; }
  .searchbar > button[type="submit"] { padding: 0 16px; font-size: 14px; }
  .searchbar > button[type="submit"] .ui-icon { display: none; }
  .filter-panel { padding: 12px 14px; }
  .filter-panel__bar { gap: 8px; }
  .advanced-row { display: grid; grid-template-columns: 1fr auto; width: 100%; }
  .filter-field { min-width: 0; }
  .price-field { min-width: 0; grid-column: 1 / -1; grid-row: 2; }
  .price-range input { width: 100%; }
  .filter-reset { grid-column: 2; grid-row: 1; }
}
</style>
