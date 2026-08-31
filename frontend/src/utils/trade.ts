/**
 * 交易模块工具函数 —— 纯函数，可独立测试，不依赖浏览器环境。
 * 状态入参是后端返回的原始字符串（契约上应属于对应枚举），
 * 越界值经查表兜底原样回显，因此签名收 string 而非枚举联合。
 */

import {
  ITEM_STATUS_BADGES,
  ITEM_STATUS_LABELS,
  ITEM_TYPE_LABELS,
  ITEM_TYPE,
  ORDER_STATUS_BADGES,
  ORDER_STATUS_LABELS,
  WALLET_LOG_TYPE_LABELS,
  type ItemStatus,
  type ItemType,
  type OrderStatus,
  type WalletLogType
} from '../constants/domain'

/** 订单状态 → 中文标签 */
export function orderStatusLabel(status: string | null | undefined): string {
  return ORDER_STATUS_LABELS[status as OrderStatus] || status || '未知'
}

/** 订单状态 → CSS 类名 */
export function orderStatusBadge(status: string): string {
  return ORDER_STATUS_BADGES[status as OrderStatus] || 'badge--muted'
}

/** 商品状态 → 中文标签 */
export function itemStatusLabel(status: string): string {
  return ITEM_STATUS_LABELS[status as ItemStatus] || status || '未知'
}

/** 商品状态 → CSS 类名 */
export function itemStatusBadge(status: string): string {
  return ITEM_STATUS_BADGES[status as ItemStatus] || 'badge--muted'
}

/** 商品类型 → 中文标签 */
export function itemTypeLabel(type: string): string {
  return ITEM_TYPE_LABELS[type as ItemType] || type
}

const ITEM_TYPE_BADGES: Record<ItemType, string> = Object.freeze({
  [ITEM_TYPE.SELL]: 'badge--sell',
  [ITEM_TYPE.BUY]: 'badge--buy',
  [ITEM_TYPE.SWAP]: 'badge--swap',
  [ITEM_TYPE.ERRAND]: 'badge--errand'
})

/** 商品类型 → 徽标样式类（与 itemTypeLabel 成对使用，四种类型全覆盖） */
export function typeBadgeClass(type: string): string {
  return ITEM_TYPE_BADGES[type as ItemType] || 'badge--muted'
}

/** 钱包流水类型 → 中文标签 */
export function walletLogTypeLabel(type: string): string {
  return WALLET_LOG_TYPE_LABELS[type as WalletLogType] || type || '未知'
}

/** 金额格式化（¥ + 两位小数 + 千分位）；注意与 utils/format.ts 的 formatPrice（纯小数版）语义不同 */
export function formatPriceYuan(value: unknown): string {
  if (value === null || value === undefined) return '¥0.00'
  const num = Number(value)
  if (Number.isNaN(num)) return '¥0.00'
  return '¥' + num.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** 检查金额是否为负数（支出用） */
export function isExpense(amount: unknown): boolean {
  if (amount === null || amount === undefined) return false
  return Number(amount) < 0
}

/** 构建订单列表查询参数（空状态不带 status，避免后端把空串当筛选值） */
export function buildOrderParams(page: number, size: number, status: string | null | undefined): { page: number; size: number; status?: string } {
  const params: { page: number; size: number; status?: string } = { page, size }
  if (status) params.status = status
  return params
}
