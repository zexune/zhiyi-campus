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

/** 幂等键处置：CLEAR=明确拒绝无副作用可清键；RETAIN=结果不明保留原键重试 */
export type IdempotencyDisposition = 'CLEAR' | 'RETAIN'

/**
 * 统一错误对象（F5/B6 前端侧）：除业务码与 HTTP 状态外，
 * 携带服务端语义等价的幂等处置判定。
 * 前端不能按 HTTP 状态或"是否业务错误"决定清键——只有明确白名单可 CLEAR，
 * 未识别业务码默认 RETAIN。
 */
export class ApiError extends Error {
  /** 后端业务码（信封 code；网络错误为 -1） */
  readonly code: number
  /** 真实 HTTP 状态（信封错误为 200；网络错误为 0） */
  readonly httpStatus: number
  /** 幂等键处置 */
  readonly idempotencyDisposition: IdempotencyDisposition
  /** 信封错误时可能携带的冲突详情（如资料 409 的最新资料） */
  readonly detail?: unknown

  constructor(message: string, code: number, httpStatus: number, idempotencyDisposition: IdempotencyDisposition, detail?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
    this.idempotencyDisposition = idempotencyDisposition
    this.detail = detail
  }
}

/**
 * CLEAR 白名单：请求被明确拒绝且事务无副作用的业务码。
 * 任何超时、网络中断、500、429、处理中、死锁/繁忙或未识别码都保持 RETAIN。
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
  1008, // 账户已注销
  1009, // 用户状态冲突（明确）
  1010, // 资料版本冲突（明确 409）
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

const MAX_JSON_RESPONSE_SIZE = 5 * 1024 * 1024
const BLOCKED_JSON_KEYS = new Set(['__proto__', 'constructor', 'prototype'])

function parseApiJson(data: unknown): unknown {
  if (typeof data !== 'string' || data.length === 0) return data
  if (data.length > MAX_JSON_RESPONSE_SIZE) {
    throw new Error('响应数据过大')
  }
  return JSON.parse(data, (key, value) => (BLOCKED_JSON_KEYS.has(key) ? undefined : value))
}

function isApiEnvelope(value: unknown): value is ApiResult<unknown> {
  return (
    value !== null &&
    typeof value === 'object' &&
    !Array.isArray(value) &&
    Object.hasOwn(value, 'code') &&
    Object.hasOwn(value, 'message') &&
    Object.hasOwn(value, 'data') &&
    Number.isInteger((value as ApiResult<unknown>).code)
  )
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
 */
let handledUnauthorizedEpoch: number | null = null

function handleUnauthorized(message: string, config?: AxiosRequestConfig): void {
  const requestEpoch = config?.authEpoch
  const currentEpoch = getAuthEpoch()
  if (requestEpoch !== undefined && requestEpoch !== currentEpoch) {
    return
  }
  if (handledUnauthorizedEpoch === currentEpoch) return
  handledUnauthorizedEpoch = currentEpoch
  ElMessage.error(message)
  clearAuth() // epoch 递增，使所有在途旧请求立即过期
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
  response?: { status?: number; data?: { message?: string; code?: number } }
  config?: AxiosRequestConfig
  message?: string
  code?: string
}

// 响应拦截器：统一错误处理（ElMessage 为 unplugin-auto-import 注入的全局）
instance.interceptors.response.use(
  (response) => {
    const res = response.data
    // 后端统一返回 { code, message, data }
    if (!isApiEnvelope(res)) {
      ElMessage.error('服务器响应格式异常')
      return Promise.reject(new ApiError('Invalid API response envelope', -1, response.status, 'RETAIN'))
    }
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401 && !isAuthRedirectSkipped(response.config)) {
        handleUnauthorized(res.message || '登录已失效，请重新登录', response.config)
      }
      return Promise.reject(new ApiError(res.message || '请求失败', res.code, response.status, dispositionOf(res.code), (res as { data?: unknown }).data))
    }
    return res as unknown as never
  },
  (error: InterceptorErrorPayload) => {
    // 网络层错误（超时/断网/5xx）：结果不确定，一律 RETAIN
    const httpStatus = (error as AxiosError).response?.status ?? 0
    const isTimeout = error.code === 'ECONNABORTED' || error.code === 'ERR_NETWORK'
    if (httpStatus === 401 || (error.response as { status?: number } | undefined)?.status === 401) {
      const res = error.response?.data as { message?: string } | undefined
      if (!isAuthRedirectSkipped(error.config)) {
        handleUnauthorized(res?.message || '登录已失效，请重新登录', error.config)
      } else {
        ElMessage.error(res?.message || '登录已失效，请重新登录')
      }
      return Promise.reject(new ApiError(res?.message || '登录已失效', 401, 401, 'RETAIN'))
    }
    const message = error.response?.data?.message || (isTimeout ? '请求超时，请稍后重试' : '网络错误，请稍后再试')
    ElMessage.error(message)
    return Promise.reject(new ApiError(message, error.response?.data?.code ?? -1, httpStatus, 'RETAIN'))
  }
)

/**
 * 类型化门面：响应拦截器实际 resolve 的是 ApiResult<T>（而非 AxiosResponse），
 * 这里的断言是全项目唯一一次对该事实的集中声明。
 */
const request = {
  get: <T>(url: string, config?: AxiosRequestConfig): Promise<ApiResult<T>> => instance.get(url, config) as Promise<ApiResult<T>>,
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResult<T>> => instance.post(url, data, config) as Promise<ApiResult<T>>,
  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResult<T>> => instance.put(url, data, config) as Promise<ApiResult<T>>,
  delete: <T>(url: string, config?: AxiosRequestConfig): Promise<ApiResult<T>> => instance.delete(url, config) as Promise<ApiResult<T>>
}

export default request
