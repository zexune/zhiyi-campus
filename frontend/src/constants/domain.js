/**
 * 前后端共享语义的领域代码。
 *
 * 这些值是 API/数据库契约，不允许在页面中重复手写，避免拼写错误只能在
 * 运行时暴露。Object.freeze 也防止组件意外改写全局契约。
 */
export const ITEM_TYPE = Object.freeze({
  SELL: 'SELL',
  BUY: 'BUY',
  SWAP: 'SWAP',
  ERRAND: 'ERRAND',
})

export const ITEM_STATUS = Object.freeze({
  ON_SALE: 'ON_SALE',
  REVIEWING: 'REVIEWING',
  SOLD: 'SOLD',
  OFF_SHELF: 'OFF_SHELF',
})

export const MODERATION_STATUS = Object.freeze({
  PASSED: 'PASSED',
  PENDING: 'PENDING',
  REJECTED: 'REJECTED',
})

export const ORDER_STATUS = Object.freeze({
  WAITING_MEET: 'WAITING_MEET',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
})

export const VIOLATION_STATUS = Object.freeze({
  PENDING: 'PENDING',
  CONFIRMED: 'CONFIRMED',
  DISMISSED: 'DISMISSED',
  OVERTURNED: 'OVERTURNED',
})

export const APPEAL_STATUS = Object.freeze({
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
})

export const USER_STATUS = Object.freeze({
  ACTIVE: 'ACTIVE',
  BANNED_TEMP: 'BANNED_TEMP',
  BANNED_PERM: 'BANNED_PERM',
  CANCELLED: 'CANCELLED',
})

export const USER_ROLE = Object.freeze({
  USER: 'USER',
  ADMIN: 'ADMIN',
})

export const BAN_ACTION = Object.freeze({
  TEMPORARY: 'BAN_TEMP',
  PERMANENT: 'BAN_PERM',
})

export const WALLET_LOG_TYPE = Object.freeze({
  RECHARGE: 'RECHARGE',
  PAYMENT: 'PAYMENT',
  REFUND: 'REFUND',
  INCOME: 'INCOME',
})

export const ITEM_TYPE_LABELS = Object.freeze({
  [ITEM_TYPE.SELL]: '出售',
  [ITEM_TYPE.BUY]: '求购',
  [ITEM_TYPE.SWAP]: '换物',
  [ITEM_TYPE.ERRAND]: '跑腿',
})

export const ITEM_STATUS_LABELS = Object.freeze({
  [ITEM_STATUS.ON_SALE]: '在售中',
  [ITEM_STATUS.REVIEWING]: '审核中',
  [ITEM_STATUS.SOLD]: '已售出',
  [ITEM_STATUS.OFF_SHELF]: '已下架',
})

export const ITEM_STATUS_BADGES = Object.freeze({
  [ITEM_STATUS.ON_SALE]: 'badge--ok',
  [ITEM_STATUS.REVIEWING]: 'badge--warn',
  [ITEM_STATUS.SOLD]: 'badge--muted',
  [ITEM_STATUS.OFF_SHELF]: 'badge--muted',
})

export const ORDER_STATUS_LABELS = Object.freeze({
  [ORDER_STATUS.WAITING_MEET]: '待见面',
  [ORDER_STATUS.COMPLETED]: '已完成',
  [ORDER_STATUS.CANCELLED]: '已取消',
})

export const ORDER_STATUS_BADGES = Object.freeze({
  [ORDER_STATUS.WAITING_MEET]: 'badge--warn',
  [ORDER_STATUS.COMPLETED]: 'badge--ok',
  [ORDER_STATUS.CANCELLED]: 'badge--muted',
})

export const VIOLATION_STATUS_LABELS = Object.freeze({
  [VIOLATION_STATUS.PENDING]: '待处理',
  [VIOLATION_STATUS.CONFIRMED]: '已确认',
  [VIOLATION_STATUS.DISMISSED]: '已放行',
  [VIOLATION_STATUS.OVERTURNED]: '申诉撤销',
})

export const WALLET_LOG_TYPE_LABELS = Object.freeze({
  [WALLET_LOG_TYPE.RECHARGE]: '充值',
  [WALLET_LOG_TYPE.PAYMENT]: '支付',
  [WALLET_LOG_TYPE.REFUND]: '退款',
  [WALLET_LOG_TYPE.INCOME]: '收入',
})

export const APPEAL_STATUS_LABELS = Object.freeze({
  [APPEAL_STATUS.PENDING]: '申诉审核中',
  [APPEAL_STATUS.APPROVED]: '申诉已通过',
  [APPEAL_STATUS.REJECTED]: '申诉未通过',
})

export const USER_STATUS_LABELS = Object.freeze({
  [USER_STATUS.ACTIVE]: '正常',
  [USER_STATUS.BANNED_TEMP]: '限时封禁',
  [USER_STATUS.BANNED_PERM]: '永久封禁',
  [USER_STATUS.CANCELLED]: '已注销',
})

export const ITEM_TYPE_OPTIONS = Object.freeze([
  { label: '全部类型', value: '' },
  { label: '出售', value: ITEM_TYPE.SELL },
  { label: '求购', value: ITEM_TYPE.BUY },
  { label: '以物换物', value: ITEM_TYPE.SWAP },
  { label: '帮带跑腿', value: ITEM_TYPE.ERRAND },
])

export const ITEM_STATUS_OPTIONS = Object.freeze([
  { label: '在售中', value: ITEM_STATUS.ON_SALE },
  { label: '审核中', value: ITEM_STATUS.REVIEWING },
  { label: '已售出', value: ITEM_STATUS.SOLD },
  { label: '已下架', value: ITEM_STATUS.OFF_SHELF },
])

export const ORDER_STATUS_OPTIONS = Object.freeze([
  { label: '待见面', value: ORDER_STATUS.WAITING_MEET },
  { label: '已完成', value: ORDER_STATUS.COMPLETED },
  { label: '已取消', value: ORDER_STATUS.CANCELLED },
])
