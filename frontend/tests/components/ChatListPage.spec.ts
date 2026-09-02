import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, test, vi } from 'vitest'
import { createPinia } from 'pinia'

import ChatListPage from '@/views/chat/ChatListPage.vue'
import { ackChatRead, getChatMessages, getConversations } from '@/api/chat'
import type { ApiResult } from '@/utils/request'
import type { ChatThread, Conversation } from '@/types/models'

vi.mock('@/api/chat', () => ({
  getConversations: vi.fn(),
  getChatMessages: vi.fn(),
  sendChatMessage: vi.fn(),
  ackChatRead: vi.fn(),
  startCustomerService: vi.fn(),
  getUnreadCount: vi.fn()
}))

// ---- SSE 事件流替身：捕获页面注册的监听器，供用例手动派发事件 ----
const streamMessageHandlers = new Set<(event: { type: 'MESSAGE'; conversationId: string; messageId?: number; senderId?: number }) => void>()
const streamReadHandlers = new Set<(event: { type: 'READ'; conversationId: string }) => void>()
const streamResyncHandlers = new Set<() => void>()

vi.mock('@/composables/useChatStream', () => ({
  useChatStream: () => ({
    onMessage: (handler: (event: { type: 'MESSAGE'; conversationId: string }) => void) => {
      streamMessageHandlers.add(handler)
      return () => streamMessageHandlers.delete(handler)
    },
    onRead: (handler: (event: { type: 'READ'; conversationId: string }) => void) => {
      streamReadHandlers.add(handler)
      return () => streamReadHandlers.delete(handler)
    },
    onResync: (handler: () => void) => {
      streamResyncHandlers.add(handler)
      return () => streamResyncHandlers.delete(handler)
    }
  })
}))

function emitStreamMessage(conversationId: string, messageId?: number) {
  streamMessageHandlers.forEach((handler) => handler({ type: 'MESSAGE', conversationId, messageId }))
}

function emitStreamRead(conversationId: string) {
  streamReadHandlers.forEach((handler) => handler({ type: 'READ', conversationId }))
}

const route = { query: {} as Record<string, string> }
const router = { push: vi.fn(), replace: vi.fn() }

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router
}))

const global = {
  plugins: [createPinia()],
  stubs: {
    DefaultLayout: { template: '<main><slot /></main>' },
    RouterLink: {
      props: ['to'],
      template: "<a :data-test-to=\"typeof to === 'string' ? to : ''\"><slot /></a>"
    },
    ElSkeleton: { template: '<div data-test="skeleton" />' },
    ElInput: { props: ['modelValue'], emits: ['update:modelValue'], template: '<textarea data-test="draft" />' },
    ElIcon: { template: '<i><slot /></i>' },
    LevelBadge: { template: '<span />' },
    PriceTag: { props: ['value'], template: '<span class="price-tag">{{ value }}</span>' },
    UserAvatar: { template: '<span />' }
  }
}

function conversation(overrides: Record<string, unknown> = {}) {
  return {
    conversationId: 'c1',
    peer: { id: 2, nickname: '卖家小明', level: 2 },
    relatedItem: { id: 5, title: '二手教材', price: '12.00', coverImage: '/img/cover.jpg', status: 'ON_SALE' },
    lastMessage: '在吗？',
    lastMessageTime: '2026-08-24T10:00:00',
    unreadCount: 3,
    ...overrides
  } as unknown as Conversation
}

function thread(overrides: Record<string, unknown> = {}) {
  return {
    conversationId: 'c1',
    peer: { id: 2, nickname: '卖家小明', level: 2 },
    relatedItem: { id: 5, title: '二手教材', price: '12.00', coverImage: '/img/cover.jpg', status: 'ON_SALE' },
    messages: [
      { id: 10, conversationId: 'c1', senderId: 2, receiverId: 999, content: '你好', mine: false, createdAt: '2026-08-24T09:59:00' },
      { id: 11, conversationId: 'c1', senderId: 999, receiverId: 2, content: '想买这本', mine: true, createdAt: '2026-08-24T10:00:00' },
      { id: 12, conversationId: 'c1', senderId: 2, receiverId: 999, content: '可以约图书馆', mine: false, createdAt: '2026-08-24T10:01:00' }
    ],
    hasMore: false,
    ...overrides
  } as unknown as ChatThread
}

beforeEach(() => {
  route.query = {}
  router.push.mockClear()
  router.replace.mockClear()
  streamMessageHandlers.clear()
  streamReadHandlers.clear()
  streamResyncHandlers.clear()
  vi.mocked(getConversations).mockResolvedValue({ code: 200, message: 'ok', data: [conversation()] } as unknown as ApiResult<Conversation[]>)
  vi.mocked(getChatMessages).mockResolvedValue({ code: 200, message: 'ok', data: thread() } as unknown as ApiResult<ChatThread>)
  vi.mocked(ackChatRead).mockResolvedValue({ code: 200, message: 'ok', data: null })
})

test('会话列表渲染昵称、最近消息与未读角标', async () => {
  const wrapper = mount(ChatListPage, { global })
  await flushPromises()

  const items = wrapper.findAll('.conv-item')
  assert.equal(items.length, 1)
  assert.match(items[0]!.text(), /卖家小明/)
  assert.match(items[0]!.text(), /在吗？/)
  assert.match(items[0]!.text(), /二手教材/)
  assert.equal(wrapper.get('.conv-item__unread').text(), '3')
})

test('URL 直达会话：拉取线程渲染消息、对最后一条接收消息 ACK 并清零未读', async () => {
  route.query = { conversationId: 'c1', peerId: '2', relatedItemId: '5' }
  const wrapper = mount(ChatListPage, { global })
  await flushPromises()

  assert.deepEqual(vi.mocked(getChatMessages).mock.calls[0]?.[0], { conversationId: 'c1', peerId: 2, relatedItemId: 5 })
  assert.equal(wrapper.findAll('.msg').length, 3)
  assert.match(wrapper.text(), /可以约图书馆/)
  // 只对最后一条接收消息（id=12）显式 ACK，且不重复
  assert.deepEqual(vi.mocked(ackChatRead).mock.calls, [['c1', 12]])
  assert.equal(wrapper.find('.conv-item__unread').exists(), false)
})

test('相关商品卡片渲染并可跳转到商品详情', async () => {
  route.query = { conversationId: 'c1' }
  const wrapper = mount(ChatListPage, { global })
  await flushPromises()

  const card = wrapper.get('.related-item')
  assert.match(card.text(), /二手教材/)
  assert.match(card.text(), /查看商品/)
  assert.equal(card.attributes('data-test-to'), '/item/5')
})

test('点击会话切换选中态并更新 URL 参数', async () => {
  vi.mocked(getConversations).mockResolvedValue({
    code: 200,
    message: 'ok',
    data: [conversation(), conversation({ conversationId: 'c2', peer: { id: 3, nickname: '客服小智', level: 1 }, relatedItem: null, lastMessage: '您好', unreadCount: 0 })] as never
  })
  const wrapper = mount(ChatListPage, { global })
  await flushPromises()

  const second = wrapper.findAll('.conv-item')[1]!
  await second.trigger('click')
  await flushPromises()

  assert.ok(second.classes().includes('active'))
  assert.deepEqual(router.replace.mock.calls[0], [{ path: '/chat', query: { conversationId: 'c2', peerId: 3, relatedItemId: undefined } }])
})

/** SSE 推送用例公共骨架：URL 直达打开会话 → 派发事件 → 推进合并窗口定时器 */
async function mountWithOpenConversation() {
  vi.useFakeTimers()
  try {
    route.query = { conversationId: 'c1', peerId: '2', relatedItemId: '5' }
    const wrapper = mount(ChatListPage, { global })
    await flushPromises()
    vi.mocked(getChatMessages).mockClear()
    vi.mocked(getConversations).mockClear()
    return { wrapper }
  } catch (error) {
    vi.useRealTimers()
    throw error
  }
}

test('SSE MESSAGE 事件（当前会话）静默刷新线程并渲染新消息', async () => {
  const { wrapper } = await mountWithOpenConversation()
  try {
    const withNewMessage = thread({
      messages: [
        { id: 10, conversationId: 'c1', senderId: 2, receiverId: 999, content: '你好', mine: false, createdAt: '2026-08-24T09:59:00' },
        { id: 12, conversationId: 'c1', senderId: 2, receiverId: 999, content: '可以约图书馆', mine: false, createdAt: '2026-08-24T10:01:00' },
        { id: 13, conversationId: 'c1', senderId: 2, receiverId: 999, content: '到了说一声', mine: false, createdAt: '2026-08-24T10:02:00' }
      ]
    })
    vi.mocked(getChatMessages).mockResolvedValue({ code: 200, message: 'ok', data: withNewMessage } as unknown as ApiResult<ChatThread>)

    emitStreamMessage('c1', 13)
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()

    // 只重拉线程（事件合并后一次），不打扰会话列表
    assert.equal(vi.mocked(getChatMessages).mock.calls.length, 1)
    assert.equal(vi.mocked(getConversations).mock.calls.length, 0)
    assert.equal(wrapper.findAll('.msg').length, 3)
    assert.match(wrapper.text(), /到了说一声/)
  } finally {
    vi.useRealTimers()
  }
})

test('SSE MESSAGE 事件（其他会话）只刷新会话列表', async () => {
  await mountWithOpenConversation()
  try {
    emitStreamMessage('c9', 99)
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()

    assert.equal(vi.mocked(getConversations).mock.calls.length, 1)
    assert.equal(vi.mocked(getChatMessages).mock.calls.length, 0)
  } finally {
    vi.useRealTimers()
  }
})

test('SSE READ 事件（当前会话）静默刷新线程，其他会话忽略', async () => {
  await mountWithOpenConversation()
  try {
    streamReadHandlers.forEach((handler) => handler({ type: 'READ', conversationId: 'c8' }))
    await vi.advanceTimersByTimeAsync(250)
    assert.equal(vi.mocked(getChatMessages).mock.calls.length, 0)

    emitStreamRead('c1')
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    assert.equal(vi.mocked(getChatMessages).mock.calls.length, 1)
  } finally {
    vi.useRealTimers()
  }
})

test('SSE resync（重连/恢复可见）整段重拉会话与当前线程', async () => {
  await mountWithOpenConversation()
  try {
    streamResyncHandlers.forEach((handler) => handler())
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()

    assert.equal(vi.mocked(getConversations).mock.calls.length, 1)
    assert.equal(vi.mocked(getChatMessages).mock.calls.length, 1)
  } finally {
    vi.useRealTimers()
  }
})

/** 在基准线程（最后一条 id=12）后追加一条新消息，构造静默刷新的返回 */
function threadWithAppended(message: { id: number; content: string; mine: boolean }) {
  const base = thread().messages as unknown as Array<Record<string, unknown>>
  return thread({
    messages: [
      ...base,
      {
        id: message.id,
        conversationId: 'c1',
        senderId: message.mine ? 999 : 2,
        receiverId: message.mine ? 2 : 999,
        content: message.content,
        mine: message.mine,
        createdAt: '2026-08-24T10:02:00'
      }
    ]
  })
}

function mockThreadResponse(data: ChatThread) {
  vi.mocked(getChatMessages).mockResolvedValue({ code: 200, message: 'ok', data } as unknown as ApiResult<ChatThread>)
}

test('静默刷新收到对方新消息：live region 播报“昵称：内容”，后续新消息更新播报', async () => {
  const { wrapper } = await mountWithOpenConversation()
  try {
    // 初始加载（非静默）不写播报区
    assert.equal(wrapper.get('[role="status"]').text(), '')

    mockThreadResponse(threadWithAppended({ id: 13, content: '到了说一声', mine: false }))
    emitStreamMessage('c1', 13)
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    assert.equal(wrapper.get('[role="status"]').text(), '卖家小明：到了说一声')

    // 之后的另一条新消息覆盖播报文本（清空重写路径保证内容相同也能重触发）
    mockThreadResponse(threadWithAppended({ id: 14, content: '稍等五分钟', mine: false }))
    emitStreamMessage('c1', 14)
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    assert.equal(wrapper.get('[role="status"]').text(), '卖家小明：稍等五分钟')
  } finally {
    vi.useRealTimers()
  }
})

test('静默刷新最后一条是自己发的消息（多端回显）：不写播报区', async () => {
  const { wrapper } = await mountWithOpenConversation()
  try {
    mockThreadResponse(threadWithAppended({ id: 13, content: '我在路上', mine: true }))
    emitStreamMessage('c1', 13)
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    assert.equal(wrapper.get('[role="status"]').text(), '')
  } finally {
    vi.useRealTimers()
  }
})
