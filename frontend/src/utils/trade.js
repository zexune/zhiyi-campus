/**
 * 交易模块工具函数 —— 纯函数，可独立测试，不依赖浏览器环境。
 */

import { ITEM_STATUS_BADGES, ITEM_STATUS_LABELS, ORDER_STATUS_BADGES, ORDER_STATUS_LABELS, VIOLATION_STATUS_LABELS, WALLET_LOG_TYPE_LABELS } from '../constants/domain.js'

/** 订单状态 → 中文标签 */
export function orderStatusLabel(status) {
  return ORDER_STATUS_LABELS[status] || status || '未知'
}

/** 订单状态 → CSS 类名 */
export function orderStatusBadge(status) {
  return ORDER_STATUS_BADGES[status] || 'badge--muted'
}

/** 商品状态 → 中文标签 */
export function itemStatusLabel(status) {
  return ITEM_STATUS_LABELS[status] || status || '未知'
}

/** 商品状态 → CSS 类名 */
export function itemStatusBadge(status) {
  return ITEM_STATUS_BADGES[status] || 'badge--muted'
}

/** 违规状态 → 中文标签 */
export function violationStatusLabel(status) {
  return VIOLATION_STATUS_LABELS[status] || status || '未知'
}

/** 钱包流水类型 → 中文标签 */
export function walletLogTypeLabel(type) {
  return WALLET_LOG_TYPE_LABELS[type] || type || '未知'
}

/** 金额格式化（保留两位小数，千分位） */
export function formatPrice(value) {
  if (value === null || value === undefined) return '¥0.00'
  const num = Number(value)
  if (isNaN(num)) return '¥0.00'
  return '¥' + num.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** 检查金额是否为负数（支出用） */
export function isExpense(amount) {
  if (amount === null || amount === undefined) return false
  return Number(amount) < 0
}

/** 构建订单列表查询参数 */
export function buildOrderParams(page, size, status) {
  const params = { page, size }
  if (status) params.status = status
  return params
}

/** 计算经验进度百分比（0-100） */
export function expProgress(currentExp, level) {
  if (level == null || currentExp == null) return 0
  const thresholds = { 1: 0, 2: 100, 3: 300, 4: 600, 5: 1000 }
  const currentMin = thresholds[level] || 0
  const nextMin = thresholds[level + 1]
  if (!nextMin) return 100 // 最高级
  const progress = ((currentExp - currentMin) / (nextMin - currentMin)) * 100
  return Math.max(0, Math.min(100, Math.round(progress)))
}
