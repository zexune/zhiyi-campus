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

/**
 * 统一信封的业务码（镜像 backend ResultCode 的业务码段，键名与后端枚举一一对应）。
 *
 * 页面/组合式函数按码分支（如 ApiError.code 比较）禁止再手写数字；
 * 与 OpenAPI 快照的一致性由 tests/biz-code.contract.test.ts 双向守护——
 * 后端增删业务码后需同步更新这里（CI 漂移比较会先失败提醒）。
 * 只收录 1xxx/2xxx/3xxx 业务码：400/401/409 等传输对齐码的语义由真实
 * HTTP 状态承载（ApiError.httpStatus），不在本枚举重复登记。
 */
export const BIZ_CODE = Object.freeze({
  // 用户模块 1xxx
  /** 学号已在该学校注册（409） */
  STUDENT_ID_EXISTS: 1001,
  /** 学号或密码错误（400；文案刻意不区分两种失败，防学号枚举） */
  PASSWORD_ERROR: 1002,
  /** 账户被封禁（403，不触发前端登出） */
  USER_BANNED: 1003,
  /** 密保答案错误（400） */
  SECURITY_ANSWER_ERROR: 1004,
  /** 登录/密保失败锁定（429 + Retry-After，秒数由数据库计算） */
  LOGIN_LOCKED: 1005,
  USER_NOT_FOUND: 1006,
  /** 新密码与原密码相同（400） */
  SAME_AS_OLD_PASSWORD: 1007,
  /** 账户已注销（403，业务拒绝而非认证失效，不触发前端登出） */
  USER_CANCELLED: 1008,
  /** 对方账户状态异常（409；结果明确，幂等键可清除） */
  USER_STATUS_ERROR: 1009,
  /** 资料版本乐观并发冲突（409，信封附带服务端最新资料） */
  PROFILE_CONFLICT: 1010,
  /** 认证端点准入背压（429 + Retry-After；请求确定未执行） */
  AUTH_BUSY: 1011,
  /** 会话失效（401：改密/改角色/封禁后的旧 Token，清 Cookie 重新登录） */
  SESSION_INVALIDATED: 1401,
  // 商品模块 2xxx
  /** 商品已下架/已售出/不可交易（409） */
  ITEM_NOT_ON_SALE: 2001,
  /** Feed 游标过期/签名不匹配/筛选或资料版本变化（400）：从首屏重启 */
  FEED_CURSOR_INVALID: 2004,
  // 交易模块 3xxx
  BALANCE_NOT_ENOUGH: 3001,
  ORDER_STATUS_ERROR: 3002,
  ORDER_ALREADY_REVIEWED: 3003,
  /** 交易准入/锁繁忙背压（429；默认结果不明，幂等键保留） */
  TRADE_BUSY: 3004,
  /** 幂等键参数冲突：同键不同参数（409，明确拒绝） */
  IDEMPOTENCY_CONFLICT: 3005,
  /** 相同幂等请求处理中（409；保留原键稍后查询） */
  IDEMPOTENCY_PROCESSING: 3006,
  /** 幂等键缺失或格式非法（400） */
  IDEMPOTENCY_KEY_INVALID: 3007
} as const)

// ---- 派生联合类型：API 返回的状态字符串按这些类型收窄 ----

export type ItemType = (typeof ITEM_TYPE)[keyof typeof ITEM_TYPE]
export type ItemStatus = (typeof ITEM_STATUS)[keyof typeof ITEM_STATUS]
export type ModerationStatus = (typeof MODERATION_STATUS)[keyof typeof MODERATION_STATUS]
export type OrderStatus = (typeof ORDER_STATUS)[keyof typeof ORDER_STATUS]
export type ViolationStatus = (typeof VIOLATION_STATUS)[keyof typeof VIOLATION_STATUS]
export type AppealStatus = (typeof APPEAL_STATUS)[keyof typeof APPEAL_STATUS]
export type UserStatus = (typeof USER_STATUS)[keyof typeof USER_STATUS]
export type WalletLogType = (typeof WALLET_LOG_TYPE)[keyof typeof WALLET_LOG_TYPE]
/** 信封业务码联合类型（仅业务码段；成功码 200 与传输对齐码见后端 ResultCode） */
export type BizCode = (typeof BIZ_CODE)[keyof typeof BIZ_CODE]

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
