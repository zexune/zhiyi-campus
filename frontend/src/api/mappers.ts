/**
 * wire → domain 显式转换（P1-5 / P6 分层）。
 *
 * 协议违约立即失败（快速暴露后端契约漂移），禁止任何静默伪造：
 * - mapRequiredData：data 缺失或 null 立即抛协议错误；
 * - mapNullableData：保留真实 null（"没有活动专题"是正常结果）；
 * - mapVoidData：只接受 null（成功 void 信封的 data 是显式 null）；
 * - mapPageData：records 必须是数组、total 必须是非负数，
 *   错误形状不能被归一化成空列表掩盖。
 */
import type { ApiResult } from '@/utils/request'
import type { LoginResult, PageResult } from '@/types/models'

/** 协议违约：服务端响应与生成契约不一致（缺 data、错误分页形状等） */
export class ProtocolViolationError extends Error {
  constructor(detail: string) {
    super(`API 协议违约：${detail}`)
    this.name = 'ProtocolViolationError'
  }
}

function requireData<T>(res: ApiResult<T | null | undefined>, operation: string): T {
  if (res.data === null || res.data === undefined) {
    throw new ProtocolViolationError(`${operation} 的成功信封缺少 data（预期为必填负载）`)
  }
  return res.data
}

/** 必填负载：data 缺失/null 即协议违约 */
export function mapRequiredData<Wire, Domain>(res: ApiResult<Wire | null | undefined>, operation: string, mapData: (data: Wire) => Domain): ApiResult<Domain> {
  return { code: res.code, message: res.message, data: mapData(requireData(res, operation)) }
}

/** 可空负载：真实 null 原样保留，非 null 走 mapper */
export function mapNullableData<Wire, Domain>(res: ApiResult<Wire | null | undefined>, mapData: (data: Wire) => Domain): ApiResult<Domain | null> {
  return { code: res.code, message: res.message, data: res.data === null || res.data === undefined ? null : mapData(res.data) }
}

interface LoginUserWire {
  id?: unknown
  nickname?: unknown
  role?: unknown
  studentId?: unknown
  username?: unknown
  avatar?: unknown
}

interface LoginPayloadWire {
  token?: unknown
  user?: LoginUserWire | null
}

/** 登录成功负载：认证态依赖的核心字段必须在运行时完整存在。 */
export function mapLoginData<Wire extends LoginPayloadWire>(res: ApiResult<Wire | null | undefined>, operation: string, identityField: 'studentId' | 'username'): ApiResult<LoginResult> {
  return mapRequiredData(res, operation, (wire) => {
    if (typeof wire.token !== 'string' || wire.token.length === 0) {
      throw new ProtocolViolationError(`${operation} 的登录负载 token 缺失或为空`)
    }
    const user = wire.user
    if (user === null || user === undefined || typeof user !== 'object') {
      throw new ProtocolViolationError(`${operation} 的登录负载 user 缺失`)
    }
    if (typeof user.id !== 'number' || !Number.isSafeInteger(user.id) || user.id <= 0) {
      throw new ProtocolViolationError(`${operation} 的登录用户 id 非法：${String(user.id)}`)
    }
    if (typeof user.nickname !== 'string' || user.nickname.length === 0) {
      throw new ProtocolViolationError(`${operation} 的登录用户 nickname 缺失或为空`)
    }
    if (typeof user.role !== 'string' || user.role.length === 0) {
      throw new ProtocolViolationError(`${operation} 的登录用户 role 缺失或为空`)
    }
    const identity = user[identityField]
    if (typeof identity !== 'string' || identity.length === 0) {
      throw new ProtocolViolationError(`${operation} 的登录用户 ${identityField} 缺失或为空`)
    }
    // avatar 是相对路径字符串或显式 null（UserVO 恒有该字段）；登录后导航栏可见
    const avatar = user.avatar === undefined ? null : user.avatar
    return {
      token: wire.token,
      user: {
        id: user.id,
        studentId: identity,
        nickname: user.nickname,
        role: user.role,
        avatar: avatar === null || typeof avatar === 'string' ? avatar : null
      }
    }
  })
}

/** void 负载：只接受显式 null */
export function mapVoidData(res: ApiResult<unknown>): ApiResult<null> {
  if (res.data !== null) {
    throw new ProtocolViolationError(`void 信封的 data 必须为 null，实际收到 ${typeof res.data}`)
  }
  return { code: res.code, message: res.message, data: null }
}

/** 分页 wire（生成 PageResponseXxx）的最小结构形状 */
interface PageWire<Row> {
  records?: Array<Row | undefined> | null
  total?: number | null
}

/** 生成 PageResponseXxx 的 records 元素类型（wire 行） */
type RowOf<T> = T extends { records?: Array<infer R> | null } ? R : never

/**
 * 分页 wire（生成的 PageResponseXxx，经 ApiResult 携带）→ 前端 PageResult。
 * Row 由 wire 的 records[number] 推导，不再接受任意 unknown 信封。
 * records 不是数组或 total 为负数/非有限数时抛协议错误，绝不伪装成空列表。
 */
export function mapPageData<WirePage extends PageWire<unknown>, Domain>(
  res: ApiResult<WirePage | null | undefined>,
  operation: string,
  mapRow: (row: RowOf<WirePage>) => Domain
): ApiResult<PageResult<Domain>> {
  const page = res.data
  if (page === null || page === undefined || typeof page !== 'object') {
    throw new ProtocolViolationError(`${operation} 的成功信封缺少分页 data`)
  }
  const wire = page as PageWire<RowOf<WirePage>>
  if (!Array.isArray(wire.records)) {
    throw new ProtocolViolationError(`${operation} 的分页 records 不是数组`)
  }
  if (typeof wire.total !== 'number' || !Number.isFinite(wire.total) || wire.total < 0) {
    throw new ProtocolViolationError(`${operation} 的分页 total 非法：${String(wire.total)}`)
  }
  const records = wire.records.map((row, index) => {
    if (row === null || row === undefined) {
      throw new ProtocolViolationError(`${operation} 的分页 records[${index}] 不能为空`)
    }
    return mapRow(row as RowOf<WirePage>)
  })
  return {
    code: res.code,
    message: res.message,
    data: {
      records,
      total: wire.total
    }
  }
}
