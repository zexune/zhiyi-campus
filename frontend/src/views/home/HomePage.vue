<template>
  <DefaultLayout>
    <div class="home-page">
      <section class="hero">
        <div class="hero__inner">
          <div class="float-sticker fs-1" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="#F5562E" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 18V5l12-2v13" />
              <circle cx="6" cy="18" r="3" />
              <circle cx="18" cy="16" r="3" />
            </svg>
            吉他 ¥260
          </div>
          <div class="float-sticker fs-2" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="#3B7BD8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="4" y="3" width="16" height="18" rx="2" />
              <path d="M8 7h8M8 11h8M8 15h5" />
            </svg>
            高数教材 ¥15
          </div>
          <div class="float-sticker fs-3" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="#26221C" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="5" y="2" width="14" height="20" rx="2" />
              <path d="M12 18h.01" />
            </svg>
            iPad Air5 ¥2000
          </div>

          <h1 class="rise">
            校园里的好东西
            <br />
            都在
            <span class="hl">这块布告栏</span>
            上
          </h1>
          <p class="sub rise rise-1">本地合规检测 · 平台担保交易 · 当面验货，放心买卖</p>

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
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 11 12 4l9 7" />
            <path d="M5 10v10h14V10" />
            <path d="M9 20v-6h6v6" />
          </svg>
        </span>
        <div>
          <h2>先登录，再逛本校交易大厅</h2>
          <p>登录后只会展示你所在学校的商品、榜单和聊天内容。</p>
        </div>
        <router-link :to="{ path: ROUTE_PATH.LOGIN, query: { redirect: ROUTE_PATH.HOME } }" class="btn btn--primary">登录并进入</router-link>
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
          <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6" /></svg>
        </button>
      </section>

      <div v-if="loggedIn" class="cat-row" role="tablist" aria-label="商品大类筛选">
        <button class="cat-chip" :class="{ active: !filters.categoryId }" @click="selectCategory('')">
          <CategoryIcon name="全部" />
          全部
        </button>
        <button v-for="category in categories" :key="category.id" class="cat-chip" :class="{ active: filters.categoryId === category.id }" @click="selectCategory(category.id)">
          <CategoryIcon :name="category.name" />
          {{ category.name }}
        </button>
      </div>

      <section v-if="loggedIn" class="filter-panel">
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
                  <button v-for="tag in group.tags" :key="tag.name" class="tag-cloud__chip" :class="{ active: activeTag === tag.name }" @click="filterByTag(tag.name, group.categoryId)">
                    {{ tag.name }}
                    <span class="tag-cloud__count">{{ tag.count }}</span>
                  </button>
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
            <span class="muted goods-total">
              共
              <strong>{{ total }}</strong>
              件在售好物
            </span>
          </div>

          <el-skeleton v-if="loading && !items.length" :rows="8" animated />

          <div v-else-if="items.length" class="goods-grid">
            <article v-for="item in items" :key="item.id" class="goods-card rise" @click="goDetail(item.id)">
              <div class="goods-card__img" :class="placeholderClass(item.id)">
                <img v-if="item.coverImage" :src="item.coverImage" :alt="item.title" />
                <span class="badge goods-card__type" :class="item.type === ITEM_TYPE.BUY ? 'badge--buy' : 'badge--sell'">
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
                  <strong v-if="item.type === ITEM_TYPE.SWAP" class="price">换物</strong>
                  <PriceTag v-else :value="item.price" font-size="22px" />
                  <span class="goods-card__fav">
                    <svg class="heart-icon" viewBox="0 0 24 24" :fill="item.favoriteByCurrentUser ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2">
                      <path d="M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2C10.5 3.5 9.3 3 7.5 3A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z" />
                    </svg>
                    {{ item.favoriteCount || 0 }}
                  </span>
                  <button
                    class="fav-button"
                    :class="{ active: item.favoriteByCurrentUser }"
                    :disabled="favoriteBusyId === item.id"
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

          <div v-else class="empty-panel">
            <p class="muted">未找到相关商品</p>
            <button class="btn btn--primary" @click="resetFilters">清空筛选</button>
          </div>

          <div v-if="items.length" class="load-more">
            <button class="btn btn--yellow btn--lg" :disabled="loading" @click="fetchItems">
              再看一批
              <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 11a8 8 0 1 0-2.34 5.66" />
                <path d="M20 4v7h-7" />
              </svg>
            </button>
          </div>

          <el-pagination v-if="total > pageSize" v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="fetchItems" />
        </main>

        <aside>
          <div class="hall-aside__sticky">
            <div class="card rank-card sticker-tilt-r" aria-label="近期爆款榜单">
              <div class="rank-card__head">
                <div>
                  <h3>近期爆款榜</h3>
                  <p class="muted rank-sub">按收藏数实时更新</p>
                </div>
                <router-link :to="ROUTE_PATH.RANKING" class="rank-more" title="查看完整榜单">
                  查看完整榜单
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="m9 18 6-6-6-6" /></svg>
                </router-link>
              </div>
              <div v-if="ranking.length" class="ranking-list">
                <button v-for="(item, index) in ranking" :key="item.id" class="rank-item" @click="goDetail(item.id)">
                  <span class="rank-item__no">{{ index + 1 }}</span>
                  <span class="rank-item__thumb" :class="placeholderClass(item.id)">
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
              <p>发布 30 秒搞定，本地自动生成标签</p>
              <router-link :to="ROUTE_PATH.PUBLISH" class="btn btn--yellow">去发布闲置</router-link>
            </div>
          </div>
        </aside>
      </div>
    </div>
  </DefaultLayout>
</template>

<script setup>
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import CategoryIcon from '@/components/common/CategoryIcon.vue'
import LevelBadge from '@/components/common/LevelBadge.vue'
import PriceTag from '@/components/common/PriceTag.vue'
import TagList from '@/components/common/TagList.vue'
import { ITEM_TYPE } from '@/constants/domain'
import { useMarketplaceHome } from './useMarketplaceHome'
import { ROUTE_PATH } from '@/constants/routes'

const {
  TYPE_OPTIONS,
  activeTag,
  activeTopic,
  allTags,
  applyPriceFilterNow,
  applyTopic,
  categories,
  clearKeyword,
  favoriteBusyId,
  fetchItems,
  filterByTag,
  filters,
  goDetail,
  handleFavorite,
  handleSearch,
  itemTypeLabel,
  items,
  loading,
  loggedIn,
  page,
  pageSize,
  placeholderClass,
  quickSearch,
  ranking,
  resetFilters,
  searchByTag,
  selectCategory,
  showTagCloud,
  toggleTagCloud,
  total
} = useMarketplaceHome()
</script>

<style scoped src="./home-page.css"></style>
