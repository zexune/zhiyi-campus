<template>
  <router-link class="related-item" :to="ROUTE_PATH.item(item.id)">
    <span class="related-item__thumb" :class="placeholderClass(item.id)">
      <img v-if="item.coverImage" :src="item.coverImage" :alt="item.title" loading="lazy" decoding="async" />
    </span>
    <span class="related-item__info">
      <strong>{{ item.title }}</strong>
      <ItemPrice :type="item.type" :price="item.price" font-size="18px" />
    </span>
    <span class="btn btn--sm btn--primary">查看商品</span>
  </router-link>
</template>

<script setup lang="ts">
import ItemPrice from '@/components/common/ItemPrice.vue'
import type { ChatItemSummary } from '@/types/models'
import { ROUTE_PATH } from '@/constants/routes'
import { placeholderClass } from '@/utils/format'

/** 会话内相关商品卡片：纯展示，点击跳商品详情（SWAP 显示以物换物，不出现 ¥0.00） */
defineProps<{
  item: ChatItemSummary
}>()
</script>

<style scoped>
.related-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 14px 22px 0;
  background: var(--white);
  border: var(--bw) solid var(--line);
  border-radius: var(--r-m);
  padding: 10px 14px;
  box-shadow: var(--shadow-s);
}
.related-item__thumb {
  width: 46px;
  height: 46px;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-s);
  overflow: hidden;
  flex-shrink: 0;
}
.related-item__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.related-item__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.related-item__info strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 760px) {
  .related-item {
    margin-left: 12px;
    margin-right: 12px;
  }
  .related-item .btn {
    display: none;
  }
}
</style>
