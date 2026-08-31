<template>
  <DefaultLayout>
    <div class="home-page">
      <section class="hero">
        <div class="hero__inner">
          <h1 class="rise">
            校园里的好东西
            <br />
            都在
            <span class="hl">这块布告栏</span>
            上
          </h1>
          <div class="trust-row rise rise-1" aria-label="平台保障">
            <span class="trust-chip">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />
                <path d="m9 12 2 2 4-4" />
              </svg>
              平台担保
            </span>
            <span class="trust-chip">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="8" r="4" />
                <path d="M4 21c0-4 3.6-6 8-6s8 2 8 6" />
              </svg>
              本校同学
            </span>
            <span class="trust-chip">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
                <circle cx="12" cy="10" r="2.5" />
              </svg>
              当面验货
            </span>
          </div>

          <form class="searchbar rise rise-2" role="search" @submit.prevent="handleSearch">
            <span class="searchbar__icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
                <circle cx="11" cy="11" r="7" />
                <path d="m21 21-4.3-4.3" />
              </svg>
            </span>
            <input v-model="filters.keyword" type="search" placeholder="搜一搜：iPad、高数教材、吉他、瑜伽垫…" aria-label="搜索商品" />
            <button v-if="filters.keyword" class="searchbar__clear" type="button" title="清空搜索" aria-label="清空搜索" @click="clearKeyword">
              <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round"><path d="m6 6 12 12M18 6 6 18" /></svg>
            </button>
            <button type="submit" :disabled="loading">
              <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
                <circle cx="11" cy="11" r="7" />
                <path d="m21 21-4.3-4.3" />
              </svg>
              搜索
            </button>
          </form>

          <div class="hot-words rise rise-3">
            <span class="lab" title="热门搜索" aria-label="热门搜索">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.4-.5-2-1-3-1.1-2.1-.2-4 2-5 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.2.5-2.9 1.5-4 .3 1.5 1 2.5 2 2.5Z" />
              </svg>
            </span>
            <button class="tag" type="button" @click="quickSearch('iPad')">iPad</button>
            <button class="tag" type="button" @click="quickSearch('四级真题')">四级真题</button>
            <button class="tag" type="button" @click="quickSearch('小米充电宝')">小米充电宝</button>
            <button class="tag" type="button" @click="quickSearch('Switch')">Switch</button>
            <button class="tag" type="button" @click="quickSearch('考研政治')">考研政治</button>
          </div>
        </div>
      </section>

      <section v-if="activeTopic" class="topic-banner stamp-edge">
        <span class="topic-banner__stamp">{{ activeTopic.stamp }}</span>
        <div class="topic-banner__copy">
          <small>{{ activeTopic.dateLabel }}</small>
          <h2>{{ activeTopic.title }}</h2>
          <p>{{ activeTopic.description }}</p>
        </div>
        <button class="btn btn--yellow" type="button" @click="applyTopic(activeTopic)">
          {{ activeTopic.action }}
          <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6" /></svg>
        </button>
      </section>

      <div class="cat-row" role="tablist" aria-label="商品大类筛选">
        <button class="cat-chip" :class="{ active: !filters.categoryId }" @click="selectCategory('')">
          <CategoryIcon name="全部" />
          全部
        </button>
        <button v-for="category in categories" :key="category.id" class="cat-chip" :class="{ active: filters.categoryId === category.id }" @click="selectCategory(category.id)">
          <CategoryIcon :name="category.name" />
          {{ category.name }}
        </button>
      </div>

      <section class="filter-panel">
        <div class="filter-panel__bar">
          <button class="filter-panel__title" type="button" :aria-expanded="showTagCloud" @click="toggleTagCloud">
            <span class="filter-panel__stamp">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 5h16l-6 7v5l-4 2v-7Z" /></svg>
            </span>
            <strong>精细筛选</strong>
            <span class="filter-panel__chevron" :class="{ open: showTagCloud }">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="m6 9 6 6 6-6" /></svg>
            </span>
          </button>
          <div class="advanced-row">
            <label class="filter-field">
              <span>发布类型</span>
              <AppSelect v-model="filters.type" :options="TYPE_OPTIONS" aria-label="发布类型" />
            </label>
            <fieldset class="filter-field price-field">
              <legend>价格区间</legend>
              <div class="price-range">
                <span>¥</span>
                <input v-model.number="filters.minPrice" type="number" min="0" step="1" placeholder="最低价" @blur="applyPriceFilterNow" @keyup.enter="applyPriceFilterNow" />
                <i>—</i>
                <span>¥</span>
                <input v-model.number="filters.maxPrice" type="number" min="0" step="1" placeholder="最高价" @blur="applyPriceFilterNow" @keyup.enter="applyPriceFilterNow" />
              </div>
            </fieldset>
            <button class="btn filter-reset" type="button" :disabled="loading" @click="resetFilters">
              <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 11a8 8 0 1 0-2.34 5.66" />
                <path d="M20 4v7h-7" />
              </svg>
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
                  <button v-for="tag in group.tags" :key="tag.name" class="tag-cloud__chip" :class="{ active: activeTags.includes(tag.name) }" @click="filterByTag(tag.name, group.categoryId)">
                    {{ tag.name }}
                    <span class="tag-cloud__count">{{ tag.count }}</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div class="hall">
        <!-- section+名称 形成命名 region：保留读屏"商品列表"跳转锚点，且不与布局层 main landmark 重复 -->
        <section aria-label="商品列表">
          <div class="sort-row">
            <div class="sort-tabs" role="tablist" aria-label="排序方式">
              <button :class="{ active: filters.sort === 'random' }" @click="filters.sort = 'random'">智能推荐</button>
              <button :class="{ active: filters.sort === 'latest' }" @click="filters.sort = 'latest'">最新发布</button>
              <button :class="{ active: filters.sort === 'priceAsc' }" @click="filters.sort = 'priceAsc'">价格 ↑</button>
              <button :class="{ active: filters.sort === 'priceDesc' }" @click="filters.sort = 'priceDesc'">价格 ↓</button>
            </div>
            <span class="muted goods-total">
              <strong>{{ estimatedTotal }}</strong>
              件在售
              <span title="首屏估算值，非精确计数">（约）</span>
            </span>
          </div>

          <el-skeleton v-if="loading && !items.length" :rows="8" animated />

          <div v-else-if="items.length" class="goods-grid">
            <article v-for="(item, index) in items" :key="item.id" class="goods-card rise" @click="goDetail(item.id)">
              <!-- 图钉：纸条被钉在布告栏上的视觉锚点（纯装饰） -->
              <i class="pushpin" :class="pushpinTone(item.type)" aria-hidden="true"></i>
              <div class="goods-card__img" :class="placeholderClass(item.id)">
                <img v-if="item.coverImage" :src="item.coverImage" :alt="item.title" :loading="index < EAGER_COVER_COUNT ? 'eager' : 'lazy'" decoding="async" />
                <span class="badge goods-card__type" :class="typeBadgeClass(item.type)">
                  {{ itemTypeLabel(item.type) }}
                </span>
              </div>
              <div class="goods-card__body">
                <h2 class="goods-card__title">{{ item.title }}</h2>
                <TagList :tags="item.tags" :limit="3" @select="searchByTag" />
                <div class="goods-card__relations">
                  <span v-if="item.dormitoryRelation === 'SAME_BUILDING'" class="neighbor-badge">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
                      <circle cx="12" cy="10" r="2.5" />
                    </svg>
                    本楼
                  </span>
                  <span v-else-if="item.dormitoryRelation === 'SAME_CAMPUS'" class="neighbor-badge neighbor-badge--campus">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M4 21V7l8-4 8 4v14" />
                      <path d="M9 21v-5h6v5M8 9h.01M12 9h.01M16 9h.01M8 13h.01M12 13h.01M16 13h.01" />
                    </svg>
                    本校区
                  </span>
                </div>
                <div class="goods-card__meta">
                  <ItemPrice :type="item.type" :price="item.price" font-size="22px" swap-label="换物" />
                  <span class="goods-card__fav">
                    <svg class="heart-icon" viewBox="0 0 24 24" :fill="item.favoriteByCurrentUser ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2">
                      <path d="M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2C10.5 3.5 9.3 3 7.5 3A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z" />
                    </svg>
                    {{ item.favoriteCount || 0 }}
                  </span>
                  <button
                    class="fav-button"
                    :class="{ active: item.favoriteByCurrentUser }"
                    :disabled="favoriteBusyIds.has(item.id)"
                    :title="item.favoriteByCurrentUser ? '取消收藏' : '收藏商品'"
                    @click.stop="handleFavorite(item)"
                  >
                    <svg class="heart-icon" viewBox="0 0 24 24" :fill="item.favoriteByCurrentUser ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2">
                      <path d="M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2C10.5 3.5 9.3 3 7.5 3A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z" />
                    </svg>
                  </button>
                </div>
                <div class="goods-card__seller">
                  <span class="seller-name">{{ item.publisherNickname || '同学' }}</span>
                  <span v-if="item.publisherVerified" class="verified-badge" title="已填写本校邮箱">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12 4 4L19 6" /></svg>
                    已认证
                  </span>
                  <LevelBadge :level="item.publisherLevel || 1" />
                  <span class="muted">浏览 {{ item.viewCount || 0 }}</span>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="empty-state">
            <span class="empty-state__icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <circle cx="11" cy="11" r="7" />
                <path d="m21 21-4.3-4.3M8 11h6" />
              </svg>
            </span>
            <p>未找到相关商品</p>
            <button class="btn btn--primary" @click="resetFilters">清空筛选</button>
          </div>

          <div v-if="items.length" class="load-more">
            <button v-if="hasMore" class="btn btn--yellow btn--lg" :disabled="loadingMore" @click="loadMore">
              {{ loadingMore ? '加载中…' : '加载更多' }}
              <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 11a8 8 0 1 0-2.34 5.66" />
                <path d="M20 4v7h-7" />
              </svg>
            </button>
            <button v-else class="btn btn--yellow btn--lg" :disabled="loading" @click="fetchItems">
              已经到底啦，换一批
              <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 11a8 8 0 1 0-2.34 5.66" />
                <path d="M20 4v7h-7" />
              </svg>
            </button>
          </div>
        </section>

        <aside>
          <div class="hall-aside__sticky">
            <div class="card rank-card" aria-label="近期爆款榜单">
              <div class="rank-card__head">
                <h3>近期爆款榜</h3>
                <router-link :to="ROUTE_PATH.RANKING" class="rank-more" title="查看完整榜单" aria-label="查看完整榜单">
                  完整榜单
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="m9 18 6-6-6-6" /></svg>
                </router-link>
              </div>
              <div v-if="ranking.length" class="ranking-list">
                <button v-for="(item, index) in ranking" :key="item.id" class="rank-item" @click="goDetail(item.id)">
                  <span class="rank-item__no">{{ index + 1 }}</span>
                  <span class="rank-item__thumb" :class="placeholderClass(item.id)">
                    <img v-if="item.coverImage" :src="item.coverImage" :alt="item.title" loading="lazy" decoding="async" />
                  </span>
                  <span class="rank-item__info">
                    <strong class="rank-item__title">
                      <span class="badge" :class="typeBadgeClass(item.type)">{{ itemTypeLabel(item.type) }}</span>
                      {{ item.title }}
                    </strong>
                    <small class="rank-item__sub">
                      <span class="p">
                        <ItemPrice :type="item.type" :price="item.price" font-size="13px" swap-label="换物" />
                      </span>
                      <span>收藏 {{ item.favoriteCount || 0 }}</span>
                    </small>
                  </span>
                </button>
              </div>
              <p v-else class="muted ranking-empty">暂无榜单数据</p>
            </div>

            <div class="publish-cta">
              <i class="tape tape--center tape--on-dark" aria-hidden="true"></i>
              <h4>宿舍在吃灰？</h4>
              <p>拍照发布，30 秒上架</p>
              <router-link :to="ROUTE_PATH.PUBLISH" class="btn btn--yellow">去发布闲置</router-link>
            </div>
          </div>
        </aside>
      </div>
    </div>
  </DefaultLayout>
</template>

<script setup lang="ts">
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import CategoryIcon from '@/components/common/CategoryIcon.vue'
import ItemPrice from '@/components/common/ItemPrice.vue'
import LevelBadge from '@/components/common/LevelBadge.vue'
import TagList from '@/components/common/TagList.vue'
import { typeBadgeClass } from '@/utils/trade'
import { ITEM_TYPE, type ItemType } from '@/constants/domain'
import { useMarketplaceHome } from './useMarketplaceHome'
import { ROUTE_PATH } from '@/constants/routes'

/** 首行封面不懒加载：桌面 3 列/移动 2 列网格的首行卡片在首屏内，可能成为 LCP 元素，
 *  lazy 会压低其加载优先级（Chrome 约定 LCP 图禁用 lazy）；其余卡片保持懒加载 */
const EAGER_COVER_COUNT = 3

/** 图钉配色跟随交易类型（出售柿橙 / 求购蓝 / 换物绿 / 跑腿黄），纯装饰。
 *  Record<ItemType, string>：新增交易类型时漏配在这里直接编译失败（domain.ts 状态码收口惯例） */
const PUSHPIN_TONES: Record<ItemType, string> = {
  [ITEM_TYPE.SELL]: '',
  [ITEM_TYPE.BUY]: 'pushpin--blue',
  [ITEM_TYPE.SWAP]: 'pushpin--green',
  [ITEM_TYPE.ERRAND]: 'pushpin--yellow'
}

/** 入参保持 API 契约的 string（ItemSummary.type 生成为 string），收窄后查表；
 *  契约外的意外值落到 ''（默认柿橙图钉），不抛错 */
function pushpinTone(type: string): string {
  return PUSHPIN_TONES[type as ItemType] ?? ''
}

const {
  TYPE_OPTIONS,
  activeTags,
  activeTopic,
  allTags,
  applyPriceFilterNow,
  applyTopic,
  categories,
  clearKeyword,
  favoriteBusyIds,
  fetchItems,
  filterByTag,
  filters,
  goDetail,
  handleFavorite,
  handleSearch,
  hasMore,
  itemTypeLabel,
  items,
  loadMore,
  loading,
  loadingMore,
  placeholderClass,
  quickSearch,
  ranking,
  resetFilters,
  searchByTag,
  selectCategory,
  showTagCloud,
  toggleTagCloud,
  estimatedTotal
} = useMarketplaceHome()
</script>

<style scoped src="./home-page.css"></style>
