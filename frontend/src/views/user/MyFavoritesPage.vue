<template>
  <DefaultLayout>
    <div class="fav-page">
      <h1 class="page-title">
        我的收藏
        <span class="stamp">FAVS</span>
      </h1>

      <template v-if="items.length">
        <div class="fav-grid">
          <article v-for="item in items" :key="item.id" class="card card--hover fav-card" @click="goDetail(item)">
            <div class="fav-card__img" :class="placeholderClass(item.id)">
              <img v-if="mainImage(item)" :src="mainImage(item)" :alt="item.title" />
              <span v-if="displayStatus(item) !== ITEM_STATUS.ON_SALE" class="badge badge--muted fav-card__state">
                {{ statusText(displayStatus(item)) }}
              </span>
            </div>
            <div class="fav-card__body">
              <div class="fav-card__title">{{ item.title }}</div>
              <TagList :tags="item.tags" :limit="3" @select="goTag" />
              <div class="fav-card__foot">
                <PriceTag :value="item.price" />
                <button class="btn btn--sm" :disabled="acting" @click.stop="handleUnfavorite(item)">取消收藏</button>
              </div>
            </div>
          </article>
        </div>
        <el-pagination v-if="total > pageSize" v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="fetchFavorites" />
      </template>

      <div v-else class="card empty-card">
        <p v-if="loadError" class="muted">{{ loadError }}</p>
        <template v-else>
          <p class="muted">还没有收藏的宝贝</p>
          <router-link :to="ROUTE_PATH.HOME" class="btn btn--primary">去大厅逛逛 →</router-link>
        </template>
      </div>
    </div>
  </DefaultLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import TagList from '@/components/common/TagList.vue'
import PriceTag from '@/components/common/PriceTag.vue'
import { getMyFavorites, toggleFavorite } from '@/api/item'
import { ITEM_STATUS, MODERATION_STATUS } from '@/constants/domain'
import { itemStatusLabel } from '@/utils/trade'
import { usePagedList } from '@/composables/usePagedList'
import { placeholderClass } from '@/utils/format'
import { ROUTE_NAME, ROUTE_PATH } from '@/constants/routes'

/**
 * 我的收藏（模块一页面归属 A；收藏接口由 C 提供，按附录 B 契约调用）
 */
const router = useRouter()
const acting = ref(false)
const { records: items, currentPage: page, pageSize, total, loadError, fetchList: fetchFavorites } = usePagedList(getMyFavorites, { size: 12 })

function statusText(status) {
  return itemStatusLabel(status)
}
function displayStatus(item) {
  return item.moderationStatus === MODERATION_STATUS.PENDING ? ITEM_STATUS.REVIEWING : item.status
}

function mainImage(item) {
  return Array.isArray(item.images) ? item.images[0] || '' : ''
}

function goDetail(item) {
  router.push(ROUTE_PATH.item(item.id))
}

function goTag(tag) {
  router.push({ name: ROUTE_NAME.HOME, query: { keyword: tag } })
}

async function handleUnfavorite(item) {
  acting.value = true
  try {
    await toggleFavorite(item.id)
    ElMessage.success('已取消收藏')
    fetchFavorites()
  } catch {
    /* 提示由 request.js 处理 */
  } finally {
    acting.value = false
  }
}

onMounted(fetchFavorites)
</script>

<style scoped>
.fav-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.fav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--spacing-md);
}

.fav-card {
  overflow: hidden;
}

.fav-card__img {
  position: relative;
  aspect-ratio: 1 / 1;
  border-bottom: var(--bw) solid var(--ink);
}
.fav-card__img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.fav-card__state {
  position: absolute;
  top: 10px;
  left: 10px;
}

.fav-card__body {
  padding: 12px 14px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.fav-card__title {
  font-weight: 700;
  font-size: 14.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  min-height: 2.9em;
}

.fav-card__foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
}

.empty-card {
  padding: 48px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-items: center;
}
</style>
