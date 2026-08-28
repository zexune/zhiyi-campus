import { onUnmounted, ref, watch } from 'vue'
import type { Ref } from 'vue'
import { getAuthEpoch, isAdmin, isLoggedIn } from '@/utils/auth'

/**
 * 聊天事件流（SSE）共享订阅（替代前端定时轮询）。
 *
 * 服务端在事务提交后经 /api/chat/stream（管理员为 /api/admin/chat/stream，
 * 受 RoleInterceptor 命名空间隔离约束）推送：
 * - event:chat：MESSAGE（收到新消息）/ READ（自己发的消息被对方读）变化信号；
 * - event:ping：心跳（服务端每 zhiyi.sse.heartbeat-interval-ms 一次）。
 *
 * 事件只做信号，消息明细与未读数仍由页面收到事件后经既有 REST 端点重拉。
 * 可靠性约定：
 * - 仅登录用户建立连接：匿名访客的请求只会得到 401，且 EventSource 读不到
 *   响应状态码，必须从源头避免；登录/登出经模块级订阅即时生效；
 * - 浏览器自动重连（readyState=CONNECTING）直接放行；致命失败（CLOSED，
 *   服务端明确拒绝事件流，典型 401/非事件流响应）指数退避手动重建，
 *   连续达上限后停止打点，等待登录态变化或页面恢复可见再试；
 * - 服务端心跳是具名 ping 事件：超过 2 倍心跳间隔未收到任何服务端事件
 *   即视为静默断流（半开连接浏览器不报错），看门狗主动重建连接，
 *   不必等 30 分钟连接超时；
 * - 连接（重）建立与页面恢复可见时触发 resync，整段重拉兜底断线期间的漏推；
 * - 登录周期（epoch）变化后旧连接对当前账户无效，订阅时强制重建。
 */

/** SSE 推送的聊天事件负载（对应后端 ChatEventVO） */
export interface ChatStreamMessageEvent {
  type: 'MESSAGE'
  conversationId: string
  messageId?: number
  senderId?: number
}

export interface ChatStreamReadEvent {
  type: 'READ'
  conversationId: string
}

export interface UseChatStreamReturn {
  /** 当前是否存在活跃的事件流连接 */
  connected: Ref<boolean>
  /** 新消息到达（接收者为当前用户） */
  onMessage: (listener: (event: ChatStreamMessageEvent) => void) => () => void
  /** 已读状态变化（当前用户发出的消息被对方读） */
  onRead: (listener: (event: ChatStreamReadEvent) => void) => () => void
  /** 需要整段重拉的时机：连接（重）建立、页面恢复可见 */
  onResync: (listener: () => void) => () => void
}

const USER_STREAM_PATH = '/api/chat/stream'
const ADMIN_STREAM_PATH = '/api/admin/chat/stream'
const RECONNECT_BASE_MS = 1000
const RECONNECT_MAX_MS = 30000
/**
 * 连续致命失败上限：服务端持续拒绝事件流（登录态无效/过期）时停止自动重连，
 * 由登录态变化、页面恢复可见或重新订阅触发再试——不产生无限请求流。
 */
const RECONNECT_FATAL_LIMIT = 5
/** 静默断流判定：超过该时长未收到任何服务端事件（ping/chat）即主动重建 */
const HEARTBEAT_STALE_MS = 45000
const WATCHDOG_TICK_MS = 15000
/** 全部订阅者离场后延迟关闭：SPA 路由切换的卸载/挂载间隙不产生连接抖动 */
const IDLE_CLOSE_MS = 5000

let source: EventSource | undefined
let sourceEpoch = 0
let refCount = 0
let reconnectTimer: number | undefined
let reconnectAttempt = 0
let fatalFailures = 0
let idleCloseTimer: number | undefined
let visibilityHooked = false
let watchdogTimer: number | undefined
/** 最近一次收到服务端事件（连接建立/ping/chat）的时间戳，看门狗的判据 */
let lastSignalAt = 0

const connected = ref(false)
const messageListeners = new Set<(event: ChatStreamMessageEvent) => void>()
const readListeners = new Set<(event: ChatStreamReadEvent) => void>()
const resyncListeners = new Set<() => void>()

// 登录态变化即时联动（模块级订阅随单例存续，不绑定组件作用域；flush sync
// 保证登出当场断开）：登录且有人订阅时建立连接；登出立即断开——旧连接
// 不能以过期身份继续存活。
watch(
  isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      if (refCount > 0 && !source) connect()
    } else {
      closeSource()
    }
  },
  { flush: 'sync' }
)

function streamPath(): string {
  return isAdmin() ? ADMIN_STREAM_PATH : USER_STREAM_PATH
}

function resync(): void {
  resyncListeners.forEach((listener) => listener())
}

function dispatchChatPayload(raw: unknown): void {
  let payload: { type?: unknown; conversationId?: unknown; messageId?: unknown; senderId?: unknown }
  try {
    payload = JSON.parse(String(raw)) as typeof payload
  } catch {
    return
  }
  if (payload?.type === 'MESSAGE' && typeof payload.conversationId === 'string') {
    const event: ChatStreamMessageEvent = {
      type: 'MESSAGE',
      conversationId: payload.conversationId,
      messageId: typeof payload.messageId === 'number' ? payload.messageId : undefined,
      senderId: typeof payload.senderId === 'number' ? payload.senderId : undefined
    }
    messageListeners.forEach((listener) => listener(event))
  } else if (payload?.type === 'READ' && typeof payload.conversationId === 'string') {
    const event: ChatStreamReadEvent = { type: 'READ', conversationId: payload.conversationId }
    readListeners.forEach((listener) => listener(event))
  }
}

function closeSource(): void {
  source?.close()
  source = undefined
  if (reconnectTimer !== undefined) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = undefined
  }
  reconnectAttempt = 0
  fatalFailures = 0
  connected.value = false
}

function connect(): void {
  if (typeof EventSource === 'undefined' || source) return
  // 仅登录用户建立事件流：匿名访客请求只会得到 401（EventSource 无法读取
  // 响应状态，浏览器还会把它当可重试的失败），必须从源头避免
  if (!isLoggedIn()) return
  if (reconnectTimer !== undefined) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = undefined
  }
  const stream = new EventSource(streamPath())
  source = stream
  sourceEpoch = getAuthEpoch()
  lastSignalAt = Date.now()
  stream.onopen = () => {
    connected.value = true
    reconnectAttempt = 0
    fatalFailures = 0
    lastSignalAt = Date.now()
    resync()
  }
  stream.addEventListener('ping', () => {
    lastSignalAt = Date.now()
  })
  stream.addEventListener('chat', (raw: Event) => {
    lastSignalAt = Date.now()
    dispatchChatPayload((raw as MessageEvent).data)
  })
  stream.onerror = () => {
    connected.value = false
    if (stream.readyState !== EventSource.CLOSED) {
      // CONNECTING：浏览器按服务端下发的重连节奏自行恢复，成功后 onopen 触发 resync
      return
    }
    // CLOSED：服务端明确拒绝事件流（非 200 / 非事件流响应，典型为 401）
    stream.close()
    if (source === stream) source = undefined
    fatalFailures += 1
    scheduleReconnect()
  }
}

function scheduleReconnect(): void {
  if (!isLoggedIn() || refCount === 0 || source || reconnectTimer !== undefined) return
  if (fatalFailures >= RECONNECT_FATAL_LIMIT) {
    // 服务端持续拒绝：停止打点，等登录态变化、页面恢复可见或重新订阅再试
    return
  }
  const delay = Math.min(RECONNECT_BASE_MS * 2 ** reconnectAttempt, RECONNECT_MAX_MS)
  reconnectAttempt += 1
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = undefined
    connect()
  }, delay)
}

/**
 * 静默断流看门狗：连接看起来 open 但超过阈值未收到任何服务端事件（ping/chat）
 * 时主动重建。半开连接（服务端写入进内核缓冲一直成功、数据到不了浏览器）
 * 浏览器不会报错，注释行心跳又对 JS 不可见，具名 ping 才让"还在送达"可判定。
 */
function startWatchdog(): void {
  if (watchdogTimer !== undefined) return
  watchdogTimer = window.setInterval(() => {
    if (source && Date.now() - lastSignalAt > HEARTBEAT_STALE_MS) {
      closeSource()
      connect()
    }
  }, WATCHDOG_TICK_MS)
}

function stopWatchdog(): void {
  if (watchdogTimer === undefined) return
  window.clearInterval(watchdogTimer)
  watchdogTimer = undefined
}

function hookVisibilityOnce(): void {
  if (visibilityHooked || typeof document === 'undefined') return
  visibilityHooked = true
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState !== 'visible') return
    if (source) {
      // 后台标签页的连接可能被系统挂起：恢复可见时整段重拉兜底
      resync()
      return
    }
    if (refCount > 0) {
      // 连接已死且自动重连可能已停止（致命失败上限）：恢复可见立即重试
      fatalFailures = 0
      reconnectAttempt = 0
      connect()
    }
  })
}

function acquire(): void {
  refCount += 1
  hookVisibilityOnce()
  startWatchdog()
  if (idleCloseTimer !== undefined) {
    window.clearTimeout(idleCloseTimer)
    idleCloseTimer = undefined
  }
  // 连接缺失，或连接属于上一个登录周期（登出/换号）：立即重建（匿名由 connect 内部拦截）
  if (!source || sourceEpoch !== getAuthEpoch()) {
    closeSource()
    connect()
  }
}

function release(): void {
  refCount = Math.max(0, refCount - 1)
  if (refCount === 0) {
    stopWatchdog()
    if (source && idleCloseTimer === undefined) {
      idleCloseTimer = window.setTimeout(() => {
        idleCloseTimer = undefined
        closeSource()
      }, IDLE_CLOSE_MS)
    }
  }
}

function subscribe<T>(listeners: Set<T>, listener: T): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

const streamApi: UseChatStreamReturn = {
  connected,
  onMessage: (listener) => subscribe(messageListeners, listener),
  onRead: (listener) => subscribe(readListeners, listener),
  onResync: (listener) => subscribe(resyncListeners, listener)
}

/** 在组件 setup 中订阅聊天事件流；组件卸载自动释放（全部释放后延迟关闭连接）。 */
export function useChatStream(): UseChatStreamReturn {
  acquire()
  onUnmounted(release)
  return streamApi
}
