import assert from 'node:assert/strict'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, test, vi } from 'vitest'

import { useChatStream } from '@/composables/useChatStream'
import type { UseChatStreamReturn } from '@/composables/useChatStream'
import { clearAuth, setLoginUser } from '@/utils/auth'

/** EventSource 测试替身：只实现 composable 依赖的最小面 */
class FakeEventSource {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSED = 2
  static instances: FakeEventSource[] = []

  readyState = FakeEventSource.OPEN
  url: string
  closed = false
  onopen: (() => void) | null = null
  onerror: (() => void) | null = null
  private listeners = new Map<string, Set<(payload: unknown) => void>>()

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  addEventListener(type: string, listener: (payload: unknown) => void) {
    if (!this.listeners.has(type)) this.listeners.set(type, new Set())
    this.listeners.get(type)!.add(listener)
  }

  close() {
    this.closed = true
    this.readyState = FakeEventSource.CLOSED
  }

  // ---- 测试驱动 ----
  open() {
    this.readyState = FakeEventSource.OPEN
    this.onopen?.()
  }

  emitChat(data: unknown) {
    this.listeners.get('chat')?.forEach((listener) => listener({ data }))
  }

  emitPing() {
    this.listeners.get('ping')?.forEach((listener) => listener({}))
  }

  failAsConnecting() {
    this.readyState = FakeEventSource.CONNECTING
    this.onerror?.()
  }

  failFatal() {
    this.readyState = FakeEventSource.CLOSED
    this.onerror?.()
  }
}

vi.stubGlobal('EventSource', FakeEventSource)

/** 以真实组件生命周期挂载订阅者（onUnmounted 需要组件实例） */
function mountSubscriber() {
  let handle!: UseChatStreamReturn
  const wrapper = mount(
    defineComponent({
      setup() {
        handle = useChatStream()
        return () => h('div')
      }
    })
  )
  return { wrapper, handle }
}

beforeEach(() => {
  vi.useFakeTimers()
  FakeEventSource.instances.length = 0
})

afterEach(async () => {
  // 清空待定的空闲关闭/重连/看门狗定时器，避免单例状态跨用例泄漏
  await vi.runAllTimersAsync()
  vi.useRealTimers()
})

test('普通用户订阅建立 /api/chat/stream 连接，open 触发 resync，chat 事件按类型派发', async () => {
  setLoginUser({ id: 2, nickname: '买家', role: 'USER' })
  const received: string[] = []
  const { wrapper, handle } = mountSubscriber()
  const offMessage = handle.onMessage((event) => received.push(`M:${event.conversationId}:${event.messageId}`))
  const offRead = handle.onRead((event) => received.push(`R:${event.conversationId}`))
  const resync = vi.fn()
  const offResync = handle.onResync(resync)

  const stream = FakeEventSource.instances.at(-1)!
  assert.equal(stream.url, '/api/chat/stream')
  assert.equal(stream.closed, false)

  stream.open()
  assert.equal(resync.mock.calls.length, 1)

  stream.emitChat(JSON.stringify({ type: 'MESSAGE', conversationId: '1_2', messageId: 10, senderId: 1 }))
  stream.emitChat(JSON.stringify({ type: 'READ', conversationId: '1_2' }))
  // 缺省字段与畸形负载：不派发也不抛错
  stream.emitChat(JSON.stringify({ type: 'MESSAGE', conversationId: '1_2' }))
  stream.emitChat('not-json')
  stream.emitChat(JSON.stringify({ type: 'UNKNOWN', conversationId: '1_2' }))
  assert.deepEqual(received, ['M:1_2:10', 'R:1_2', 'M:1_2:undefined'])

  offMessage()
  offRead()
  offResync()
  wrapper.unmount()
})

test('管理员订阅走 /api/admin/chat/stream（管理命名空间隔离）', async () => {
  setLoginUser({ id: 9, nickname: '管理员', role: 'ADMIN' })
  const { wrapper } = mountSubscriber()
  assert.equal(FakeEventSource.instances.at(-1)!.url, '/api/admin/chat/stream')
  wrapper.unmount()
})

test('匿名访客不建立连接；登录后自动建立，登出立即断开', async () => {
  clearAuth()
  const { wrapper } = mountSubscriber()
  // 未登录：零连接零请求（公开页 DefaultLayout 同样挂载本订阅）
  assert.equal(FakeEventSource.instances.length, 0)

  setLoginUser({ id: 2, nickname: '用户', role: 'USER' })
  assert.equal(FakeEventSource.instances.length, 1)
  assert.equal(FakeEventSource.instances.at(-1)!.url, '/api/chat/stream')

  // 登出：连接立即关闭，不以过期身份存活
  clearAuth()
  assert.equal(FakeEventSource.instances.at(-1)!.closed, true)
  wrapper.unmount()
})

test('登录周期变化（换号/切换角色）后旧连接关闭并按新身份重建', async () => {
  setLoginUser({ id: 2, nickname: '用户', role: 'USER' })
  const first = mountSubscriber()
  const firstStream = FakeEventSource.instances.at(-1)!

  setLoginUser({ id: 9, nickname: '管理员', role: 'ADMIN' })
  const second = mountSubscriber()

  const secondStream = FakeEventSource.instances.at(-1)!
  assert.notEqual(secondStream, firstStream)
  assert.equal(firstStream.closed, true)
  assert.equal(secondStream.url, '/api/admin/chat/stream')

  first.wrapper.unmount()
  second.wrapper.unmount()
})

test('致命错误（CLOSED）按指数退避手动重连，CONNECTING 交给浏览器自愈', async () => {
  setLoginUser({ id: 2, nickname: '用户', role: 'USER' })
  const { wrapper } = mountSubscriber()

  // CONNECTING：浏览器自行重连，不安排手动重建
  FakeEventSource.instances.at(-1)!.failAsConnecting()
  await vi.advanceTimersByTimeAsync(10_000)
  assert.equal(FakeEventSource.instances.length, 1)

  // CLOSED：1s 后重建
  FakeEventSource.instances.at(-1)!.failFatal()
  await vi.advanceTimersByTimeAsync(999)
  assert.equal(FakeEventSource.instances.length, 1)
  await vi.advanceTimersByTimeAsync(1)
  assert.equal(FakeEventSource.instances.length, 2)

  // 连续失败退避翻倍：第二次 2s 后重建
  FakeEventSource.instances.at(-1)!.failFatal()
  await vi.advanceTimersByTimeAsync(1_999)
  assert.equal(FakeEventSource.instances.length, 2)
  await vi.advanceTimersByTimeAsync(1)
  assert.equal(FakeEventSource.instances.length, 3)

  // 重连成功后退避与致命计数重置
  FakeEventSource.instances.at(-1)!.open()
  FakeEventSource.instances.at(-1)!.failFatal()
  await vi.advanceTimersByTimeAsync(1_000)
  assert.equal(FakeEventSource.instances.length, 4)

  wrapper.unmount()
})

test('服务端持续拒绝（致命失败）达上限后停止重连，恢复可见立即重试', async () => {
  setLoginUser({ id: 2, nickname: '用户', role: 'USER' })
  const { wrapper } = mountSubscriber()

  // 初始连接 + 4 次退避重试 = 5 个实例；第 5 次致命失败触发上限停止
  for (let fatal = 0; fatal < 5; fatal++) {
    FakeEventSource.instances.at(-1)!.failFatal()
    await vi.advanceTimersByTimeAsync(30_000)
  }
  assert.equal(FakeEventSource.instances.length, 5)

  // 停止后不再产生请求（匿名/过期会话不形成无限 401 循环）
  await vi.advanceTimersByTimeAsync(120_000)
  assert.equal(FakeEventSource.instances.length, 5)

  // 恢复可见：立即重试
  const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
  document.dispatchEvent(new Event('visibilitychange'))
  visibility.mockRestore()
  assert.equal(FakeEventSource.instances.length, 6)

  wrapper.unmount()
})

test('全部订阅者离场后延迟关闭连接，间隙内再订阅复用连接', async () => {
  setLoginUser({ id: 2, nickname: '用户', role: 'USER' })
  const first = mountSubscriber()
  const stream = FakeEventSource.instances.at(-1)!
  first.wrapper.unmount()

  await vi.advanceTimersByTimeAsync(4_999)
  assert.equal(stream.closed, false)

  // 间隙内（SPA 路由切换）新订阅者到来：复用连接
  const second = mountSubscriber()
  assert.equal(FakeEventSource.instances.length, 1)
  second.wrapper.unmount()

  await vi.advanceTimersByTimeAsync(5_000)
  assert.equal(stream.closed, true)
})

test('页面恢复可见触发 resync，兜底后台期间可能漏掉的变化', async () => {
  setLoginUser({ id: 2, nickname: '用户', role: 'USER' })
  const { wrapper, handle } = mountSubscriber()
  const resync = vi.fn()
  const offResync = handle.onResync(resync)

  const stream = FakeEventSource.instances.at(-1)!
  stream.open()
  assert.equal(resync.mock.calls.length, 1)

  const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
  document.dispatchEvent(new Event('visibilitychange'))
  visibility.mockRestore()
  assert.equal(resync.mock.calls.length, 2)

  offResync()
  wrapper.unmount()
})

test('退避重连的新连接 open 后同样触发 resync 并复位退避', async () => {
  setLoginUser({ id: 2, nickname: '用户', role: 'USER' })
  const { wrapper, handle } = mountSubscriber()
  const resync = vi.fn()
  const offResync = handle.onResync(resync)

  FakeEventSource.instances.at(-1)!.failFatal()
  await vi.advanceTimersByTimeAsync(1_000)
  assert.equal(FakeEventSource.instances.length, 2)
  FakeEventSource.instances.at(-1)!.open()
  assert.equal(resync.mock.calls.length, 1)

  offResync()
  wrapper.unmount()
})

test('看门狗：超时未收到任何服务端事件即视为静默断流并主动重建', async () => {
  setLoginUser({ id: 2, nickname: '用户', role: 'USER' })
  const { wrapper } = mountSubscriber()
  const stream = FakeEventSource.instances.at(-1)!
  stream.open()

  // 45s 阈值 + 15s 检查周期：60s 内必触发一次重建（半开连接浏览器不报错）
  await vi.advanceTimersByTimeAsync(60_000)
  assert.equal(FakeEventSource.instances.length, 2)
  assert.equal(stream.closed, true)

  wrapper.unmount()
})

test('持续收到 ping 心跳则不触发看门狗重建', async () => {
  setLoginUser({ id: 2, nickname: '用户', role: 'USER' })
  const { wrapper } = mountSubscriber()
  const stream = FakeEventSource.instances.at(-1)!
  stream.open()

  // 服务端 20s 一次 ping，模拟 2 分钟：连接保持
  for (let beat = 0; beat < 6; beat++) {
    await vi.advanceTimersByTimeAsync(20_000)
    stream.emitPing()
  }
  assert.equal(FakeEventSource.instances.length, 1)
  assert.equal(stream.closed, false)

  wrapper.unmount()
})
