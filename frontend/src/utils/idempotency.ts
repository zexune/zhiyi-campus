import { getAuthContext } from '@/utils/auth'

/**
 * 资金操作幂等键管理（localStorage 持久化，按用户 ID 隔离）。
 *
 * 生命周期契约（B6 前端侧）：
 * - 每次资金意图生成唯一 UUID 并持久化（跨会话存活：关闭标签页重开后仍复用原键）；
 * - 网络超时/500/429/处理中/任何结果不确定场景保留原键，重试复用，
 *   服务端按幂等记录复返同一结果，不会重复扣款/退款；
 * - 只有明确的业务拒绝（余额不足、商品已售等 CLEAR 白名单）才清除键；
 * - 充值等可能并存的操作使用独立客户端操作 ID（UUID）作为槽位，
 *   不能以用户或金额作为唯一槽位。
 */

export type IdempotentOperation = 'ORDER_CREATE' | 'ORDER_CONFIRM' | 'ORDER_CANCEL' | 'RECHARGE'

/** 未决资金操作记录 */
export interface PendingOperation {
  operation: IdempotentOperation
  /** 业务实体 ID（订单类）或独立客户端操作 ID（充值类，UUID） */
  entityId: string
  idempotencyKey: string
  /** 创建时间（ms），用于排查与过期提示 */
  createdAt: number
  /** 规范化请求参数摘要（调试用，不参与协议） */
  params?: Record<string, unknown>
}

const PREFIX = 'idem:'

function storageKey(operation: string, entityId: string | number): string {
  const { userId } = getAuthContext()
  return `${PREFIX}${userId}:${operation}:${entityId}`
}

/** 读取未决操作；无记录或解析失败返回 null */
export function getPending(operation: IdempotentOperation, entityId: string | number): PendingOperation | null {
  try {
    const raw = localStorage.getItem(storageKey(operation, entityId))
    if (!raw) return null
    const parsed = JSON.parse(raw) as PendingOperation
    if (!parsed || typeof parsed.idempotencyKey !== 'string') return null
    return parsed
  } catch {
    return null
  }
}

/** 写入未决操作（生成新幂等键；旧操作已明确结束后才允许调用） */
export function setPending(operation: IdempotentOperation, entityId: string | number, params?: Record<string, unknown>): PendingOperation {
  const pending: PendingOperation = {
    operation,
    entityId: String(entityId),
    idempotencyKey: crypto.randomUUID(),
    createdAt: Date.now(),
    params
  }
  localStorage.setItem(storageKey(operation, entityId), JSON.stringify(pending))
  return pending
}

/** 取得或创建未决操作（重试复用原键） */
export function getOrCreatePending(operation: IdempotentOperation, entityId: string | number, params?: Record<string, unknown>): PendingOperation {
  return getPending(operation, entityId) ?? setPending(operation, entityId, params)
}

/** 明确结束后清除未决操作 */
export function clearPending(operation: IdempotentOperation, entityId: string | number): void {
  localStorage.removeItem(storageKey(operation, entityId))
}

/** 清理当前用户全部未决记录（登出时调用；不清理其他用户的数据） */
export function clearAllPendingForCurrentUser(): void {
  const { userId } = getAuthContext()
  const prefix = `${PREFIX}${userId}:`
  const keys: string[] = []
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (key && key.startsWith(prefix)) keys.push(key)
  }
  keys.forEach((key) => localStorage.removeItem(key))
}

/** 当前用户的未决操作数量（用于页面提示"有未完成的充值"） */
export function countPendingForCurrentUser(): number {
  const { userId } = getAuthContext()
  const prefix = `${PREFIX}${userId}:`
  let count = 0
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (key && key.startsWith(prefix)) count++
  }
  return count
}
