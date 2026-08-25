/**
 * 前后端共享语义的领域代码。
 *
 * 这些值是 API/数据库契约，不允许在页面中重复手写。TS 迁移后：
 * - 枚举对象 `as const` + 派生联合类型（如 ItemType），值域错误在编译期暴露；
 * - LABELS/BADGES 以 Record<枚举, string> 约束，漏配某个状态的标签会直接编译失败；
 * - Object.freeze 继续防止运行时改写全局契约。
 */

export const ITEM_TYPE = Object.freeze({
  SELL: 'SELL',
  BUY: 'BUY',
  SWAP: 'SWAP',
  ERRAND: 'ERRAND'
} as const)

export const ITEM_STATUS = Object.freeze({
  ON_SALE: 'ON_SALE',
  REVIEWING: 'REVIEWING',
  /** 交易中：存在进行中的订单（item.status 是可交易性唯一权威来源） */
  RESERVED: 'RESERVED',
  SOLD: 'SOLD',
  OFF_SHELF: 'OFF_SHELF'
} as const)

export const MODERATION_STATUS = Object.freeze({
  PASSED: 'PASSED',
  PENDING: 'PENDING',
  REJECTED: 'REJECTED'
} as const)

export const ORDER_STATUS = Object.freeze({
  WAITING_MEET: 'WAITING_MEET',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED'
} as const)

export const VIOLATION_STATUS = Object.freeze({
  PENDING: 'PENDING',
  CONFIRMED: 'CONFIRMED',
  DISMISSED: 'DISMISSED',
  OVERTURNED: 'OVERTURNED'
} as const)

export const APPEAL_STATUS = Object.freeze({
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED'
} as const)

export const USER_STATUS = Object.freeze({
  ACTIVE: 'ACTIVE',
  BANNED_TEMP: 'BANNED_TEMP',
  BANNED_PERM: 'BANNED_PERM',
  CANCELLED: 'CANCELLED'
} as const)

export const USER_ROLE = Object.freeze({
  USER: 'USER',
  ADMIN: 'ADMIN'
} as const)

export const BAN_ACTION = Object.freeze({
  TEMPORARY: 'BAN_TEMP',
  PERMANENT: 'BAN_PERM'
} as const)

export const WALLET_LOG_TYPE = Object.freeze({
  RECHARGE: 'RECHARGE',
  PAYMENT: 'PAYMENT',
  REFUND: 'REFUND',
  INCOME: 'INCOME'
} as const)

// ---- 派生联合类型：API 返回的状态字符串按这些类型收窄 ----

export type ItemType = (typeof ITEM_TYPE)[keyof typeof ITEM_TYPE]
export type ItemStatus = (typeof ITEM_STATUS)[keyof typeof ITEM_STATUS]
export type ModerationStatus = (typeof MODERATION_STATUS)[keyof typeof MODERATION_STATUS]
export type OrderStatus = (typeof ORDER_STATUS)[keyof typeof ORDER_STATUS]
export type ViolationStatus = (typeof VIOLATION_STATUS)[keyof typeof VIOLATION_STATUS]
export type AppealStatus = (typeof APPEAL_STATUS)[keyof typeof APPEAL_STATUS]
export type UserStatus = (typeof USER_STATUS)[keyof typeof USER_STATUS]
export type UserRole = (typeof USER_ROLE)[keyof typeof USER_ROLE]
export type BanAction = (typeof BAN_ACTION)[keyof typeof BAN_ACTION]
export type WalletLogType = (typeof WALLET_LOG_TYPE)[keyof typeof WALLET_LOG_TYPE]

/** 下拉选项的通用形状（value 用 string：部分选项的「全部」为空串） */
export interface SelectOption {
  readonly label: string
  readonly value: string
}

export const ITEM_TYPE_LABELS: Record<ItemType, string> = Object.freeze({
  [ITEM_TYPE.SELL]: '出售',
  [ITEM_TYPE.BUY]: '求购',
  [ITEM_TYPE.SWAP]: '换物',
  [ITEM_TYPE.ERRAND]: '跑腿'
})

export const ITEM_STATUS_LABELS: Record<ItemStatus, string> = Object.freeze({
  [ITEM_STATUS.ON_SALE]: '在售中',
  [ITEM_STATUS.REVIEWING]: '审核中',
  [ITEM_STATUS.RESERVED]: '交易中',
  [ITEM_STATUS.SOLD]: '已售出',
  [ITEM_STATUS.OFF_SHELF]: '已下架'
})

export const ITEM_STATUS_BADGES: Record<ItemStatus, string> = Object.freeze({
  [ITEM_STATUS.ON_SALE]: 'badge--ok',
  [ITEM_STATUS.REVIEWING]: 'badge--warn',
  [ITEM_STATUS.RESERVED]: 'badge--warn',
  [ITEM_STATUS.SOLD]: 'badge--muted',
  [ITEM_STATUS.OFF_SHELF]: 'badge--muted'
})

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = Object.freeze({
  [ORDER_STATUS.WAITING_MEET]: '待见面',
  [ORDER_STATUS.COMPLETED]: '已完成',
  [ORDER_STATUS.CANCELLED]: '已取消'
})

export const ORDER_STATUS_BADGES: Record<OrderStatus, string> = Object.freeze({
  [ORDER_STATUS.WAITING_MEET]: 'badge--warn',
  [ORDER_STATUS.COMPLETED]: 'badge--ok',
  [ORDER_STATUS.CANCELLED]: 'badge--muted'
})

export const VIOLATION_STATUS_LABELS: Record<ViolationStatus, string> = Object.freeze({
  [VIOLATION_STATUS.PENDING]: '待处理',
  [VIOLATION_STATUS.CONFIRMED]: '已确认',
  [VIOLATION_STATUS.DISMISSED]: '已放行',
  [VIOLATION_STATUS.OVERTURNED]: '申诉撤销'
})

export const WALLET_LOG_TYPE_LABELS: Record<WalletLogType, string> = Object.freeze({
  [WALLET_LOG_TYPE.RECHARGE]: '充值',
  [WALLET_LOG_TYPE.PAYMENT]: '支出', // 钱包页展示措辞（买家视角），与 WalletPage 收敛时确定
  [WALLET_LOG_TYPE.REFUND]: '退款',
  [WALLET_LOG_TYPE.INCOME]: '收入'
})

export const APPEAL_STATUS_LABELS: Record<AppealStatus, string> = Object.freeze({
  [APPEAL_STATUS.PENDING]: '申诉审核中',
  [APPEAL_STATUS.APPROVED]: '申诉已通过',
  [APPEAL_STATUS.REJECTED]: '申诉未通过'
})

export const USER_STATUS_LABELS: Record<UserStatus, string> = Object.freeze({
  [USER_STATUS.ACTIVE]: '正常',
  [USER_STATUS.BANNED_TEMP]: '限时封禁',
  [USER_STATUS.BANNED_PERM]: '永久封禁',
  [USER_STATUS.CANCELLED]: '已注销'
})

export const ITEM_TYPE_OPTIONS: readonly SelectOption[] = Object.freeze([
  { label: '全部类型', value: '' },
  { label: '出售', value: ITEM_TYPE.SELL },
  { label: '求购', value: ITEM_TYPE.BUY },
  { label: '以物换物', value: ITEM_TYPE.SWAP },
  { label: '帮带跑腿', value: ITEM_TYPE.ERRAND }
])

export const ITEM_STATUS_OPTIONS: readonly SelectOption[] = Object.freeze([
  { label: '在售中', value: ITEM_STATUS.ON_SALE },
  { label: '审核中', value: ITEM_STATUS.REVIEWING },
  { label: '交易中', value: ITEM_STATUS.RESERVED },
  { label: '已售出', value: ITEM_STATUS.SOLD },
  { label: '已下架', value: ITEM_STATUS.OFF_SHELF }
])

export const ORDER_STATUS_OPTIONS: readonly SelectOption[] = Object.freeze([
  { label: '待见面', value: ORDER_STATUS.WAITING_MEET },
  { label: '已完成', value: ORDER_STATUS.COMPLETED },
  { label: '已取消', value: ORDER_STATUS.CANCELLED }
])
