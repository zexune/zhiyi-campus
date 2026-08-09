<template>
  <DefaultLayout>
    <div class="ranking-page">
      <header class="ranking-header">
        <div>
          <h1 class="page-title">
            <span class="ranking-title__icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.4-.5-2-1-3-1.1-2.1-.2-4 2-5 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.2.5-2.9 1.5-4 .3 1.5 1 2.5 2 2.5Z"/></svg>
            </span>
            近期爆款榜
            <span class="stamp">收藏热度</span>
          </h1>
          <p class="ranking-subtitle">收藏热度实时排名，发现校园里正在被关注的好物</p>
        </div>
        <router-link to="/" class="btn btn--yellow">
          <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
          返回交易大厅
        </router-link>
      </header>

      <hr class="doodle-hr">

      <el-skeleton v-if="loading" :rows="10" animated />

      <section v-else class="trending-panel" aria-labelledby="trending-title">
        <div class="trending-panel__head">
          <span class="trending-panel__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/><path d="M8 11h6M11 8v6"/></svg>
          </span>
          <div>
            <span class="trending-panel__eyebrow">TAG TOP 10</span>
            <h2 id="trending-title">热门搜索</h2>
            <p>统计本校在售商品的高频商品标签</p>
          </div>
        </div>
        <div v-if="trendingTags.length" class="trending-tags">
          <button
            v-for="(item, index) in trendingTags"
            :key="item.tag"
            class="trending-tag"
            :class="{ 'trending-tag--top': index < 3 }"
            type="button"
            :title="`搜索 ${item.tag}`"
            @click="goTag(item.tag)"
          >
            <span class="trending-tag__rank">{{ String(index + 1).padStart(2, '0') }}</span>
            <strong>#{{ item.tag }}</strong>
            <small>{{ item.count }} 件</small>
          </button>
        </div>
        <p v-else class="trending-panel__empty">暂无可统计的商品标签</p>
      </section>

      <template v-if="!loading && ranking.length">
        <div class="section-heading">
          <div>
            <span>TOP 3</span>
            <h2>本周人气好物</h2>
          </div>
          <small>按收藏数实时排序</small>
        </div>
        <section class="podium" aria-label="榜单前三名">
          <article
            v-for="entry in podiumEntries"
            :key="entry.item.id"
            class="podium-card rise"
            :class="[`podium-card--${entry.rank}`, `rise-${entry.rank}`]"
            @click="goDetail(entry.item.id)"
          >
            <span class="podium-card__rank">TOP {{ entry.rank }}</span>
            <div class="podium-card__image" :class="phClass(entry.item.id)">
              <img v-if="entry.item.coverImage" :src="entry.item.coverImage" :alt="entry.item.title">
              <span class="podium-card__medal" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="6"/><path d="M8.5 13 7 22l5-3 5 3-1.5-9"/></svg>
                {{ entry.rank }}
              </span>
            </div>
            <div class="podium-card__body">
              <span class="badge" :class="entry.item.type === 'BUY' ? 'badge--buy' : 'badge--sell'">{{ entry.item.type === 'BUY' ? '求购' : '出售' }}</span>
              <h2>{{ entry.item.title }}</h2>
              <TagList :tags="entry.item.tags" :limit="3" @select="goTag" />
              <div class="podium-card__meta">
                <PriceTag :value="entry.item.price" font-size="25px" />
                <span class="favorite-count">
                  <svg viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="1.8"><path d="M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2C10.5 3.5 9.3 3 7.5 3A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z"/></svg>
                  {{ entry.item.favoriteCount || 0 }}
                </span>
              </div>
              <div class="seller-row">
                <span>{{ entry.item.publisherNickname || '同学' }}</span>
                <span v-if="entry.item.publisherVerified" class="seller-chip seller-chip--verified">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12 4 4L19 6"/></svg>
                  已认证
                </span>
                <span v-if="entry.item.dormitoryRelation === 'SAME_BUILDING'" class="seller-chip">本楼</span>
                <span v-else-if="entry.item.dormitoryRelation === 'SAME_CAMPUS'" class="seller-chip seller-chip--campus">本校区</span>
                <LevelBadge :level="entry.item.publisherLevel || 1" />
              </div>
            </div>
          </article>
        </section>

        <section v-if="remainingItems.length" class="ranking-board">
          <div class="ranking-board__head">
            <h2>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0Z"/><path d="M7 6H4v2a4 4 0 0 0 4 4M17 6h3v2a4 4 0 0 1-4 4"/></svg>
              完整榜单
            </h2>
            <span>TOP 20</span>
          </div>
          <ol class="ranking-rows" start="4">
            <li v-for="(item, index) in remainingItems" :key="item.id" class="ranking-row" @click="goDetail(item.id)">
              <span class="ranking-row__number">{{ index + 4 }}</span>
              <span class="ranking-row__image" :class="phClass(item.id)">
                <img v-if="item.coverImage" :src="item.coverImage" :alt="item.title">
              </span>
              <span class="ranking-row__main">
                <strong>{{ item.title }}</strong>
                <small>
                  {{ item.publisherNickname || '同学' }}
                  <span v-if="item.publisherVerified" class="inline-verified">已认证</span>
                  · 浏览 {{ item.viewCount || 0 }}
                </small>
                <TagList :tags="item.tags" :limit="2" compact @select="goTag" />
              </span>
              <PriceTag :value="item.price" font-size="21px" />
              <span class="ranking-row__favorites">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2C10.5 3.5 9.3 3 7.5 3A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z"/></svg>
                {{ item.favoriteCount || 0 }} 收藏
              </span>
              <button class="favorite-button" :class="{ active: item.favoriteByCurrentUser }" :disabled="favoriteBusyId === item.id" :title="item.favoriteByCurrentUser ? '取消收藏' : '收藏商品'" @click.stop="handleFavorite(item)">
                <svg viewBox="0 0 24 24" :fill="item.favoriteByCurrentUser ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2"><path d="M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2C10.5 3.5 9.3 3 7.5 3A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z"/></svg>
              </button>
              <svg class="ranking-row__arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round"><path d="m9 18 6-6-6-6"/></svg>
            </li>
          </ol>
        </section>
      </template>

      <section v-else-if="!loading" class="empty-ranking">
        <div class="empty-ranking__icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0Z"/><path d="M7 6H4v2a4 4 0 0 0 4 4M17 6h3v2a4 4 0 0 1-4 4"/></svg>
        </div>
        <h2>榜单正在等待第一件好物</h2>
        <router-link to="/" class="btn btn--primary">逛逛交易大厅</router-link>
      </section>
    </div>
  </DefaultLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import TagList from '@/components/common/TagList.vue'
import LevelBadge from '@/components/common/LevelBadge.vue'
import PriceTag from '@/components/common/PriceTag.vue'
import { getItemRanking, getTrendingTags, toggleFavorite } from '@/api/item'
import { isLoggedIn } from '@/utils/auth'

const PH = ['ph-a', 'ph-b', 'ph-c', 'ph-d', 'ph-e', 'ph-f']
const router = useRouter()
const ranking = ref([])
const trendingTags = ref([])
const loading = ref(true)
const favoriteBusyId = ref(null)

const podiumEntries = computed(() => {
  const entries = ranking.value.slice(0, 3).map((item, index) => ({ item, rank: index + 1 }))
  if (entries.length === 3) return [entries[1], entries[0], entries[2]]
  return entries
})
const remainingItems = computed(() => ranking.value.slice(3))

function phClass(id) { return PH[Number(id) % PH.length] }
function goDetail(id) { router.push(`/item/${id}`) }
function goTag(tag) { router.push({ path: '/', query: { keyword: tag } }) }

async function fetchRanking() {
  loading.value = true
  try {
    const [rankingRes, tagsRes] = await Promise.all([
      getItemRanking({ limit: 20 }),
      getTrendingTags({ limit: 10 }),
    ])
    ranking.value = rankingRes.data || []
    trendingTags.value = tagsRes.data || []
  } finally {
    loading.value = false
  }
}

async function handleFavorite(item) {
  if (!isLoggedIn()) {
    router.push({ path: '/login', query: { redirect: '/ranking' } })
    return
  }
  favoriteBusyId.value = item.id
  try {
    const res = await toggleFavorite(item.id)
    ElMessage.success(res.data.favorite ? '已收藏' : '已取消收藏')
    await fetchRanking()
  } finally {
    favoriteBusyId.value = null
  }
}

onMounted(fetchRanking)
</script>

<style scoped>
.ranking-page { display: flex; flex-direction: column; gap: 26px; }

.ranking-header {
  padding: 10px 0 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.ranking-title__icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  background: var(--primary);
  color: var(--white);
  box-shadow: var(--shadow-s);
  transform: rotate(-5deg);
}
.ranking-title__icon svg { width: 25px; height: 25px; }
.ranking-subtitle { margin: 8px 0 0 58px; color: var(--ink-soft); font-size: 14px; }
.ranking-page > .doodle-hr { margin: -8px 0 0; }

.trending-panel { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: 24px; align-items: center; padding: 18px 20px; border: var(--bw) solid var(--ink); border-radius: var(--r-m); background: var(--white); box-shadow: var(--shadow-m); }
.trending-panel__head { display: flex; align-items: center; gap: 13px; }
.trending-panel__icon { width: 46px; height: 46px; flex: 0 0 46px; display: grid; place-items: center; border: var(--bw) solid var(--ink); border-radius: var(--r-s); background: var(--yellow); box-shadow: 2px 2px 0 var(--ink); transform: rotate(-4deg); }
.trending-panel__icon svg { width: 25px; height: 25px; }
.trending-panel__eyebrow { color: var(--primary); font-size: 11px; font-weight: 900; }
.trending-panel__head h2 { font-family: var(--font-display); font-size: 23px; line-height: 1.1; }
.trending-panel__head p { margin-top: 4px; color: var(--ink-soft); font-size: 12px; }
.trending-tags { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
.trending-tag { min-height: 38px; display: inline-flex; align-items: center; gap: 7px; padding: 6px 10px 6px 7px; border: 1.5px solid var(--ink); border-radius: 7px; background: var(--paper); color: var(--ink); box-shadow: 2px 2px 0 var(--ink); cursor: pointer; transition: transform .15s, background .15s; }
.trending-tag:hover { background: var(--paper-deep); transform: translate(-1px, -2px); }
.trending-tag--top { background: var(--yellow); }
.trending-tag__rank { min-width: 23px; height: 23px; display: grid; place-items: center; border: 1.5px solid var(--ink); border-radius: 5px; background: var(--white); font-family: var(--font-display); font-size: 11px; }
.trending-tag strong { font-size: 13px; }
.trending-tag small { color: var(--ink-soft); font-size: 11px; white-space: nowrap; }
.trending-panel__empty { color: var(--ink-soft); font-size: 13px; }

.section-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; }
.section-heading > div { display: flex; align-items: center; gap: 10px; }
.section-heading span { padding: 3px 9px; border: var(--bw) solid var(--ink); border-radius: 6px; background: var(--primary); color: var(--white); box-shadow: 2px 2px 0 var(--ink); font-family: var(--font-display); font-size: 14px; transform: rotate(-2deg); }
.section-heading h2 { font-family: var(--font-display); font-size: 25px; }
.section-heading small { color: var(--ink-soft); font-size: 12px; }

.podium { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 24px; align-items: stretch; }
.podium-card { position: relative; overflow: hidden; border: var(--bw) solid var(--ink); border-radius: var(--r-m); background: var(--white); box-shadow: var(--shadow-m); cursor: pointer; transition: transform .2s, box-shadow .2s; }
.podium-card:hover { transform: translate(-3px, -3px); box-shadow: var(--shadow-l); }
.podium-card__rank { position: absolute; z-index: 2; top: 12px; left: 12px; padding: 4px 11px; border: var(--bw) solid var(--ink); border-radius: 7px; background: var(--white); box-shadow: 2px 2px 0 var(--ink); font-family: var(--font-display); font-size: 16px; }
.podium-card--1 .podium-card__rank { background: var(--yellow); }
.podium-card--2 .podium-card__rank { background: #DCD6CB; }
.podium-card--3 .podium-card__rank { background: #F0C9A8; }
.podium-card__image { position: relative; aspect-ratio: 16 / 10; overflow: hidden; border-bottom: var(--bw) solid var(--ink); }
.podium-card__image img { width: 100%; height: 100%; object-fit: cover; }
.podium-card__medal { position: absolute; right: 12px; bottom: 9px; width: 42px; height: 42px; display: grid; place-items: center; color: var(--ink); font-family: var(--font-display); font-size: 14px; font-weight: 900; filter: drop-shadow(2px 2px 0 rgba(38, 34, 28, .2)); }
.podium-card__medal svg { position: absolute; inset: 0; width: 42px; height: 42px; color: var(--primary); }
.podium-card--1 .podium-card__medal svg { color: #D99B00; }
.podium-card--2 .podium-card__medal svg { color: #777067; }
.podium-card--3 .podium-card__medal svg { color: #B76B32; }
.podium-card__body { padding: 16px 18px 18px; }
.podium-card__body h2 { min-height: 2.9em; margin: 9px 0 12px; font-size: 17px; line-height: 1.45; display: -webkit-box; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.podium-card__meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.favorite-count { display: inline-flex; align-items: center; gap: 5px; color: var(--red); font-size: 13px; font-weight: 700; }
.favorite-count svg { width: 17px; height: 17px; }
.seller-row { display: flex; align-items: center; gap: 8px; margin-top: 12px; padding-top: 10px; border-top: 1.5px dashed #E0D6C2; color: var(--ink-soft); font-size: 13px; }
.seller-chip { display: inline-flex; align-items: center; gap: 3px; padding: 2px 6px; border: 1px solid var(--ink); border-radius: 5px; background: var(--yellow); color: var(--ink); font-size: 10px; font-weight: 900; white-space: nowrap; }
.seller-chip svg { width: 11px; height: 11px; }
.seller-chip--verified { background: #D6F2DF; }
.seller-chip--campus { background: #DCEEFF; }
.inline-verified { margin-left: 4px; padding: 1px 4px; border: 1px solid var(--ink); border-radius: 4px; background: #D6F2DF; color: var(--ink); font-size: 9.5px; font-weight: 900; }

.ranking-board { border: var(--bw) solid var(--ink); border-radius: var(--r-m); background: var(--white); box-shadow: var(--shadow-m); overflow: hidden; }
.ranking-board__head { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; border-bottom: var(--bw) solid var(--ink); background: var(--yellow); }
.ranking-board__head h2 { display: flex; align-items: center; gap: 8px; font-family: var(--font-display); font-size: 23px; }
.ranking-board__head h2 svg { width: 23px; height: 23px; }
.ranking-board__head span { padding: 2px 9px; border: var(--bw) solid var(--ink); border-radius: 6px; background: var(--white); font-size: 12px; font-weight: 900; transform: rotate(2deg); }
.ranking-rows { list-style: none; }
.ranking-row { min-height: 96px; display: grid; grid-template-columns: 44px 62px minmax(0, 1fr) 110px 105px 38px 20px; align-items: center; gap: 14px; padding: 11px 18px; border-bottom: 1.5px dashed #D8CEBB; cursor: pointer; transition: background .15s; }
.ranking-row:last-child { border-bottom: 0; }
.ranking-row:hover { background: var(--paper-deep); }
.ranking-row__number { width: 36px; height: 36px; display: grid; place-items: center; border: var(--bw) solid var(--ink); border-radius: 8px; background: var(--paper); font-family: var(--font-display); font-size: 17px; }
.ranking-row__image { width: 62px; height: 62px; overflow: hidden; border: 1.5px solid var(--ink); border-radius: var(--r-s); }
.ranking-row__image img { width: 100%; height: 100%; object-fit: cover; }
.ranking-row__main { min-width: 0; display: flex; flex-direction: column; }
.ranking-row__main strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 15px; }
.ranking-row__main small { color: var(--ink-soft); font-size: 12px; }
.ranking-row__favorites { display: flex; align-items: center; gap: 5px; color: var(--ink-soft); font-size: 12px; white-space: nowrap; }
.ranking-row__favorites svg { width: 16px; height: 16px; color: var(--red); }
.favorite-button { width: 36px; height: 36px; display: grid; place-items: center; border: var(--bw) solid var(--ink); border-radius: 50%; background: var(--white); cursor: pointer; }
.favorite-button:hover, .favorite-button.active { background: var(--yellow); color: var(--red); }
.favorite-button:disabled { opacity: .55; cursor: wait; }
.favorite-button svg { width: 17px; height: 17px; }
.ranking-row__arrow { width: 18px; height: 18px; color: var(--ink-soft); }

.empty-ranking { min-height: 420px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 18px; text-align: center; }
.empty-ranking__icon { width: 82px; height: 82px; display: grid; place-items: center; border: var(--bw) solid var(--ink); border-radius: var(--r-m); background: var(--yellow); box-shadow: var(--shadow-m); transform: rotate(-4deg); }
.empty-ranking__icon svg { width: 42px; height: 42px; }
.empty-ranking h2 { font-family: var(--font-display); font-size: 27px; }

@media (max-width: 900px) {
  .ranking-header { align-items: flex-start; flex-direction: column; }
  .trending-panel { grid-template-columns: 1fr; gap: 14px; }
  .podium { grid-template-columns: 1fr; padding-top: 0; }
  .podium-card--1 { order: -1; }
  .ranking-row { grid-template-columns: 40px 56px minmax(0, 1fr) 92px 38px 18px; gap: 10px; padding-inline: 12px; }
  .ranking-row__image { width: 56px; height: 56px; }
  .ranking-row__favorites { display: none; }
}

@media (max-width: 620px) {
  .ranking-header { padding-top: 2px; }
  .ranking-header .page-title { align-items: flex-start; flex-wrap: wrap; }
  .ranking-header .page-title .stamp { align-self: center; }
  .ranking-subtitle { margin-left: 0; }
  .section-heading { align-items: flex-start; flex-direction: column; gap: 4px; }
  .trending-panel { padding: 16px; }
  .trending-tag { flex: 1 1 calc(50% - 9px); justify-content: flex-start; }
  .ranking-row { grid-template-columns: 34px 50px minmax(0, 1fr) 36px; }
  .ranking-row__number { width: 30px; height: 30px; font-size: 15px; }
  .ranking-row__image { width: 50px; height: 50px; }
  .ranking-row :deep(.price), .ranking-row__arrow { display: none; }
}
</style>
