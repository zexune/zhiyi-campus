import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { clearAuth, getAuthEpoch, getRole } from '@/utils/auth'

/**
 * 统一的 axios 实例 —— 所有 API 请求都通过它
 * 登录凭证由后端 httpOnly Cookie 自动携带，无需手动附加 Token
 */

/** 后端统一响应包（响应拦截器已脱壳 AxiosResponse，直接 resolve 本对象） */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/**
 * 失败信封的可选 meta（P1-3）：服务端判定的幂等处置。
 * REJECTED=明确拒绝无副作用可清键；PROCESSING=服务端仍在处理；UNKNOWN=结果不明。
 * 前端以它为权威，按业务码推断（CLEAR 白名单）仅作旧服务兼容 fallback。
 */
export type RequestOutcome = 'REJECTED' | 'PROCESSING' | 'UNKNOWN'

export interface ApiFailureMeta {
  requestOutcome: RequestOutcome
}

/** 幂等键处置：CLEAR=明确拒绝无副作用可清键；RETAIN=结果不明保留原键重试 */
export type IdempotencyDisposition = 'CLEAR' | 'RETAIN'

/**
 * 统一错误对象：业务码、真实 HTTP 状态、幂等处置、服务端 outcome 四者独立携带，
 * 互不覆盖——测试覆盖三者的正交性。
 */
export class ApiError extends Error {
  /** 后端业务码（信封 code；网络错误为 -1） */
  readonly code: number
  /** 真实 HTTP 状态（业务失败为对应 4xx/5xx；网络错误为 0） */
  readonly httpStatus: number
  /** 幂等键处置 */
  readonly idempotencyDisposition: IdempotencyDisposition
  /** 信封错误时可能携带的冲突详情（如资料 409 的最新资料） */
  readonly detail?: unknown
  /** 服务端 meta.requestOutcome（旧服务未携带时为 undefined，回退业务码推断） */
  readonly outcome?: RequestOutcome
  /** 服务端建议退避秒数——只从标准 Retry-After 响应头解析（CORS 已暴露） */
  readonly retryAfterSeconds?: number

  constructor(message: string, code: number, httpStatus: number, idempotencyDisposition: IdempotencyDisposition, detail?: unknown, outcome?: RequestOutcome, retryAfterSeconds?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
    this.idempotencyDisposition = idempotencyDisposition
    this.detail = detail
    this.outcome = outcome
    this.retryAfterSeconds = retryAfterSeconds
  }
}

/**
 * CLEAR 白名单（旧服务兼容 fallback）：请求被明确拒绝且事务无副作用的业务码。
 * 新服务通过失败信封 meta.requestOutcome 权威下发处置；任何超时、网络中断、
 * 500、429、处理中、死锁/繁忙或未识别码都保持 RETAIN。
 * 401 不在白名单：裸 401（无可信信封）按结果不明保守处理，
 * 携带可信信封的 401/1401 在响应拦截器里单独判定为 CLEAR。
 */
const CLEAR_BUSINESS_CODES = new Set<number>([
  400, // BAD_REQUEST 参数非法
  403, // FORBIDDEN 权限不足
  404, // NOT_FOUND 资源不存在
  409, // CONFLICT 明确状态冲突
  1001, // 学号已注册
  1002, // 密码错误
  1003, // 账户已被封禁
  1004, // 密保答案错误
  1005, // 登录锁定
  1006, // 用户不存在
  1007, // 新旧密码相同
  1008, // 账户已注销（业务 403，不触发登出）
  1009, // 用户状态冲突（明确）
  1010, // 资料版本冲突（明确 409）
  1401, // 会话失效（认证 401，请求被明确拒绝）
  2001, // 商品已下架或已售出
  2002, // 内容转人工审核
  2003, // 已收藏
  2004, // Feed 游标过期（明确要求重启）
  3001, // 余额不足（明确拒绝）
  3002, // 订单状态异常（状态已明确迁移）
  3003, // 订单已评价
  3005, // 幂等参数冲突（明确拒绝）
  3007 // 幂等键格式非法（重新生成键）
])

function dispositionOf(code: number): IdempotencyDisposition {
  if (CLEAR_BUSINESS_CODES.has(code)) return 'CLEAR'
  // 3004 TRADE_BUSY / 3006 PROCESSING / 500 / 未知码 → 结果不确定，保留幂等键
  return 'RETAIN'
}

function outcomeFromMeta(meta: unknown): RequestOutcome | undefined {
  if (meta !== null && typeof meta === 'object' && 'requestOutcome' in meta) {
    const value = (meta as { requestOutcome?: unknown }).requestOutcome
    if (value === 'REJECTED' || value === 'PROCESSING' || value === 'UNKNOWN') return value
  }
  return undefined
}

// ---- 信封完整性校验器（P1-3 收紧）：只有完整信封里的 code/message/detail 可信 ----

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

/**
 * 完整成功信封：code 为整数 200、message 为非空字符串、data 是自有字段
 * （data 的值可为 null——void 操作的显式 null 是合法负载）。
 */
function isSuccessEnvelope(value: unknown): value is ApiResult<unknown> {
  if (!isPlainObject(value)) return false
  const { code, message } = value as { code?: unknown; message?: unknown }
  return Object.hasOwn(value, 'code') && Object.hasOwn(value, 'message') && Object.hasOwn(value, 'data') && code === 200 && typeof message === 'string' && message !== ''
}

/** 完整失败信封（新形态）：code 非 200 整数 + 非空 message + 自有 data + 合法 meta */
interface NewFailureEnvelope {
  kind: 'new'
  code: number
  message: string
  data: unknown
  outcome: RequestOutcome
}

/** 完整失败信封（旧形态）：code/message/data 齐备且 meta 自有属性完全不存在 */
interface LegacyFailureEnvelope {
  kind: 'legacy'
  code: number
  message: string
  data: unknown
}

type FailureEnvelope = NewFailureEnvelope | LegacyFailureEnvelope | null

/**
 * 失败信封分类（按可信度递减）：
 * - 新信封：meta 为对象且 requestOutcome ∈ REJECTED|PROCESSING|UNKNOWN；
 * - 旧信封：code（非 200 整数）/message（非空字符串）/data（自有字段）齐备，
 *   且 meta 自有属性完全不存在——允许业务码白名单 fallback；
 * - 其余（缺字段、meta 存在但 null/缺字段/非法枚举、非对象、非 JSON）
 *   一律返回 null：不信任 body 中的业务码、message、detail，按结果不明处理。
 */
function classifyFailureEnvelope(value: unknown): FailureEnvelope {
  if (!isPlainObject(value)) return null
  if (!Object.hasOwn(value, 'code') || !Object.hasOwn(value, 'message') || !Object.hasOwn(value, 'data')) {
    return null
  }
  const code = value.code
  const message = value.message
  if (typeof code !== 'number' || !Number.isInteger(code) || code === 200) return null
  if (typeof message !== 'string' || message === '') return null
  if (!Object.hasOwn(value, 'meta')) {
    return { kind: 'legacy', code, message, data: value.data }
  }
  const outcome = outcomeFromMeta(value.meta)
  if (value.meta === null || outcome === undefined) {
    return null
  }
  return { kind: 'new', code, message, data: value.data, outcome }
}

/**
 * 完整失败信封 → 幂等处置：
 * - 新信封按服务端 meta.requestOutcome（REJECTED→CLEAR，其余→RETAIN）；
 * - 旧信封按业务码白名单 fallback（真实 HTTP 401 的 401/1401 视为明确拒绝）；
 * - 残缺形态返回 null，由调用方按结果不明保守处理（RETAIN）。
 */
function dispositionOfFailure(failure: FailureEnvelope, httpStatus: number): IdempotencyDisposition | null {
  if (failure === null) return null
  if (failure.kind === 'new') {
    return failure.outcome === 'REJECTED' ? 'CLEAR' : 'RETAIN'
  }
  if (httpStatus === 401 && (failure.code === 401 || failure.code === 1401)) {
    return 'CLEAR'
  }
  return dispositionOf(failure.code)
}

const MAX_JSON_RESPONSE_SIZE = 5 * 1024 * 1024
const BLOCKED_JSON_KEYS = new Set(['__proto__', 'constructor', 'prototype'])

function parseApiJson(data: unknown): unknown {
  if (typeof data !== 'string' || data.length === 0) return data
  if (data.length > MAX_JSON_RESPONSE_SIZE) {
    throw new Error('响应数据过大')
  }
  try {
    return JSON.parse(data, (key, value) => (BLOCKED_JSON_KEYS.has(key) ? undefined : value))
  } catch {
    // 非 JSON 体（代理/网关的 HTML 错误页）原样透传：真实 HTTP 状态仍可见，
    // 由信封校验判定"无可信信封"并保守处理幂等键
    return data
  }
}

const instance = axios.create({
  baseURL: '/api',
  timeout: 10000,
  adapter: 'fetch',
  allowAbsoluteUrls: false,
  // 跨源部署（前后端不同域）时也携带会话 Cookie
  withCredentials: true,
  withXSRFToken: false,
  maxContentLength: MAX_JSON_RESPONSE_SIZE,
  transformResponse: [parseApiJson],
  headers: { Accept: 'application/json' }
})

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 标记该请求失败时不触发全局登录跳转（后台轮询等静默请求使用） */
    skipAuthRedirect?: boolean
    /** 请求发起时的鉴权周期（请求拦截器写入，401 处理使用） */
    authEpoch?: number
  }
}

// 发起请求时捕获鉴权周期：旧账号的迟到 401 不得清除新登录态
instance.interceptors.request.use((config) => {
  config.authEpoch = getAuthEpoch()
  return config
})

/**
 * 401 统一单飞（F11 根因修复）：按 auth epoch 绑定。
 * - 请求发起时的 epoch 与当前不一致 → 旧账号的迟到响应，静默丢弃；
 * - 同一鉴权周期只处理一次 401（成功登录产生新 epoch，不依赖路由跳转复位标记）。
 *
 * P0-1：只有真实 HTTP 401 才到达这里；响应体 code=401（若出现在 2xx 中）
 * 不是登录态失效信号，不得触发清理。
 */
let handledUnauthorizedEpoch: number | null = null

function handleUnauthorized(message: string, config?: AxiosRequestConfig, silent = false): void {
  const requestEpoch = config?.authEpoch
  const currentEpoch = getAuthEpoch()
  if (requestEpoch !== undefined && requestEpoch !== currentEpoch) {
    return
  }
  if (handledUnauthorizedEpoch === currentEpoch) return
  handledUnauthorizedEpoch = currentEpoch
  // skipAuthRedirect 只禁止提示与跳转，不能阻止登录态失效：
  // 属于当前鉴权 epoch 的真实 HTTP 401 一律清理本地登录态
  clearAuth() // epoch 递增，使所有在途旧请求立即过期
  if (silent) return
  ElMessage.error(message)
  redirectToLogin()
}

function isAuthRedirectSkipped(config: AxiosRequestConfig | undefined): boolean {
  return Boolean(config?.skipAuthRedirect)
}

let authRedirecting = false

function redirectToLogin(): void {
  if (authRedirecting) return
  authRedirecting = true
  const adminSession = getRole() === 'ADMIN' || window.location.pathname.startsWith('/admin/')
  const loginPath = adminSession ? '/admin/login' : '/login'
  if (window.location.pathname !== loginPath) {
    // 保留当前位置供重新登录后回跳；replace 避免历史记录里残留死会话页面
    const target = `${loginPath}?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`
    window.location.replace(target)
  }
}

/** 登录页挂载后解除跳转单飞，允许下一轮会话失效再次跳转 */
export function resetAuthRedirect(): void {
  authRedirecting = false
}

interface InterceptorErrorPayload {
  response?: {
    status?: number
    data?: { message?: string; code?: number; data?: unknown; meta?: unknown }
    headers?: Record<string, unknown>
  }
  config?: AxiosRequestConfig
  message?: string
  code?: string
}

// 响应拦截器：统一错误处理（ElMessage 为 unplugin-auto-import 注入的全局）
instance.interceptors.response.use(
  (response) => {
    const res = response.data
    if (isSuccessEnvelope(res)) {
      return res as unknown as never
    }
    // 2xx 携带完整失败信封：按业务失败处理。任何响应体 code 都不是
    // 登录态失效信号（P0-1）——只有真实 HTTP 401 才清理登录态。
    const failure = classifyFailureEnvelope(res)
    if (failure !== null) {
      const outcome = failure.kind === 'new' ? failure.outcome : undefined
      ElMessage.error(failure.message)
      return Promise.reject(new ApiError(failure.message, failure.code, response.status, dispositionOfFailure(failure, response.status) ?? 'RETAIN', failure.data, outcome))
    }
    // 残缺/非信封 2xx：协议违约，按传输层保守失败处理
    ElMessage.error('服务器响应格式异常')
    return Promise.reject(new ApiError('Invalid API response envelope', -1, response.status, 'RETAIN'))
  },
  (error: InterceptorErrorPayload) => {
    // 业务失败以真实 HTTP 状态码到达（如 409+3001 余额不足），统一失败信封仍在响应体中
    const failure = classifyFailureEnvelope(error.response?.data)
    const httpStatus = (error as AxiosError).response?.status ?? 0
    const disposition = dispositionOfFailure(failure, httpStatus) ?? 'RETAIN'
    const outcome = failure !== null && failure.kind === 'new' ? failure.outcome : undefined
    const isTimeout = error.code === 'ECONNABORTED' || error.code === 'ERR_NETWORK'
    // P0-1：会话失效的唯一依据是真实 HTTP 401（后端拦截器直写并已清除 Cookie）。
    // 登录态清理与幂等处置相互独立：残缺信封的 401 仍清理登录态，但幂等键 RETAIN。
    if (httpStatus === 401) {
      handleUnauthorized(failure?.message || '登录已失效，请重新登录', error.config, isAuthRedirectSkipped(error.config))
      return Promise.reject(new ApiError(failure?.message || '登录已失效', failure?.code ?? 401, 401, disposition, failure?.data, outcome))
    }
    if (failure !== null) {
      // 完整信封：code/message/detail 可信；Retry-After 只从标准响应头解析
      const retryAfterHeader = (error as AxiosError).response?.headers?.['retry-after']
      const retryAfterSeconds = typeof retryAfterHeader === 'string' && retryAfterHeader.trim() !== '' && Number.isFinite(Number(retryAfterHeader)) ? Number(retryAfterHeader) : undefined
      ElMessage.error(failure.message)
      return Promise.reject(new ApiError(failure.message, failure.code, httpStatus, disposition, failure.data, outcome, retryAfterSeconds))
    }
    // 残缺失败形态（缺 code/message/data、非 JSON、代理 HTML）：不信任 body
    // 中的业务码、message、detail，按传输层错误保守处理（结果不明，RETAIN）
    const message = isTimeout ? '请求超时，请稍后重试' : '网络错误，请稍后再试'
    ElMessage.error(message)
    return Promise.reject(new ApiError(message, -1, httpStatus, 'RETAIN'))
  }
)

/**
 * 类型化门面：响应拦截器实际 resolve 的是 ApiResult<T>（而非 AxiosResponse），
 * 这里的断言是 request 层对该事实的集中声明；业务模块应使用 types/contracts.ts
 * 的 paths 约束门面，不在此随意手写响应类型（P1-5）。
 */
const request = {
  get: <T>(url: string, config?: AxiosRequestConfig): Promise<ApiResult<T>> => instance.get(url, config) as Promise<ApiResult<T>>,
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResult<T>> => instance.post(url, data, config) as Promise<ApiResult<T>>,
  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResult<T>> => instance.put(url, data, config) as Promise<ApiResult<T>>,
  delete: <T>(url: string, config?: AxiosRequestConfig): Promise<ApiResult<T>> => instance.delete(url, config) as Promise<ApiResult<T>>
}

export default request
