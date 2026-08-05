/**
 * 交易模块工具函数 —— 纯函数，可独立测试，不依赖浏览器环境。
 */

/** 订单状态 → 中文标签 */
export function orderStatusLabel(status) {
  const map = {
    WAITING_MEET: '待见面',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
  }
  return map[status] || status || '未知'
}

/** 订单状态 → CSS 类名 */
export function orderStatusBadge(status) {
  const map = {
    WAITING_MEET: 'badge--warn',
    COMPLETED: 'badge--ok',
    CANCELLED: 'badge--muted',
  }
  return map[status] || 'badge--muted'
}

/** 商品状态 → 中文标签 */
export function itemStatusLabel(status) {
  const map = {
    ON_SALE: '在售',
    PENDING: '交易中',
    SOLD: '已售出',
    OFF_SHELF: '已下架',
  }
  return map[status] || status || '未知'
}

/** 商品状态 → CSS 类名 */
export function itemStatusBadge(status) {
  const map = {
    ON_SALE: 'badge--ok',
    PENDING: 'badge--warn',
    SOLD: 'badge--muted',
    OFF_SHELF: 'badge--muted',
  }
  return map[status] || 'badge--muted'
}

/** 违规状态 → 中文标签 */
export function violationStatusLabel(status) {
  const map = {
    PENDING: '待处理',
    CONFIRMED: '已确认',
    DISMISSED: '已驳回',
  }
  return map[status] || status || '未知'
}

/** 钱包流水类型 → 中文标签 */
export function walletLogTypeLabel(type) {
  const map = {
    RECHARGE: '充值',
    PAYMENT: '支付',
    REFUND: '退款',
    INCOME: '收入',
  }
  return map[type] || type || '未知'
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
