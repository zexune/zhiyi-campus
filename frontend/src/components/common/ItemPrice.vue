<template>
  <strong v-if="isSwap" class="price price--swap" :style="{ fontSize }">{{ swapLabel }}</strong>
  <PriceTag v-else-if="hasValidPrice" :value="validPrice" :font-size="fontSize" />
  <strong v-else class="price price--invalid" :style="{ fontSize }">{{ invalidLabel }}</strong>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import PriceTag from '@/components/common/PriceTag.vue'
import { ITEM_TYPE } from '@/constants/domain'

/**
 * 商品价格展示（P0-2/P7）：按 (type, price) 渲染——
 * - SWAP：显示换物文案（默认「以物换物」）；
 * - 非 SWAP 且价格合法（有限数值 ≥ 0）：显示金额；
 * - 非 SWAP 且价格为空/非法：显示「价格异常」，绝不显示 ¥0.00。
 * 严禁用 price === null 反推商品类型：SWAP 无价是契约语义，
 * 非法组合（非 SWAP 且无价）保持可诊断而不是伪装成免费。
 */
const props = withDefaults(
  defineProps<{
    type: string | undefined | null
    price: number | string | null | undefined
    fontSize?: string
    swapLabel?: string
    invalidLabel?: string
  }>(),
  {
    fontSize: '20px',
    swapLabel: '以物换物',
    invalidLabel: '价格异常'
  }
)

const isSwap = computed(() => props.type === ITEM_TYPE.SWAP)

/** 合法价格 = 有限数值且非负；null/undefined/NaN/负数都视为异常 */
const hasValidPrice = computed(() => {
  if (props.price === null || props.price === undefined || props.price === '') return false
  const numeric = Number(props.price)
  return Number.isFinite(numeric) && numeric >= 0
})

/** hasValidPrice 为真时的数值形态（供 PriceTag 渲染） */
const validPrice = computed(() => Number(props.price))
</script>

<style scoped>
.price--swap {
  color: var(--primary, inherit);
  font-weight: 800;
}
.price--invalid {
  color: var(--red, #c0392b);
  font-weight: 600;
}
</style>
