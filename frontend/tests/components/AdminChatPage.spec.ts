import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, test, vi } from 'vitest'

import ChatAdminPage from '@/views/admin/AdminChatInboxPage.vue'
import { ackAdminChatRead, getAdminChatMessages, getAdminSessions } from '@/api/admin'
import type { ApiResult } from '@/utils/request'
import type { ChatThread, Conversation } from '@/types/models'

vi.mock('@/api/admin', () => ({
  getAdminSessions: vi.fn(),
  getAdminChatMessages: vi.fn(),
  sendAdminChatMessage: vi.fn(),
  ackAdminChatRead: vi.fn()
}))

// ---- SSE 事件流替身：捕获页面注册的监听器，供用例手动派发事件 ----
type AdminStreamMessageEvent = { type: 'MESSAGE'; conversationId: string; messageId?: number }
const streamMessageHandlers = new Set<(event: AdminStreamMessageEvent) => void>()
const streamResyncHandlers = new Set<() => void>()

vi.mock('@/composables/useChatStream', () => ({
  useChatStream: () => ({
    onMessage: (handler: (event: AdminStreamMessageEvent) => void) => {
      streamMessageHandlers.add(handler)
      return () => streamMessageHandlers.delete(handler)
    },
    onRead: () => () => {},
    onResync: (handler: () => void) => {
      streamResyncHandlers.add(handler)
      return () => streamResyncHandlers.delete(handler)
    }
  })
}))

const global = {
  stubs: {
    AdminLayout: { template: '<main><slot /></main>' },
    UserAvatar: { template: '<span />' },
    LevelBadge: { template: '<span />' }
  }
}

function session(overrides: Record<string, unknown> = {}) {
  return {
    conversationId: 'c1',
    peer: { id: 2, nickname: '咨询同学', level: 1 },
    lastMessage: '在吗？',
    lastMessageTime: '2026-08-24T10:00:00',
    unreadCount: 2,
    ...overrides
  } as unknown as Conversation
}

function thread(overrides: Record<string, unknown> = {}) {
  return {
    conversationId: 'c1',
    peer: { id: 2, nickname: '咨询同学', level: 1 },
    messages: [{ id: 10, conversationId: 'c1', senderId: 2, receiverId: 9, content: '你好，订单有问题', mine: false, createdAt: '2026-08-24T09:59:00' }],
    hasMore: false,
    ...overrides
  } as unknown as ChatThread
}

beforeEach(() => {
  streamMessageHandlers.clear()
  streamResyncHandlers.clear()
  vi.mocked(getAdminSessions).mockResolvedValue({ code: 200, message: 'ok', data: [session()] } as unknown as ApiResult<Conversation[]>)
  vi.mocked(getAdminChatMessages).mockResolvedValue({ code: 200, message: 'ok', data: thread() } as unknown as ApiResult<ChatThread>)
  vi.mocked(ackAdminChatRead).mockResolvedValue({ code: 200, message: 'ok', data: null })
})

test('会话列表渲染未读角标，点选会话加载消息', async () => {
  const wrapper = mount(ChatAdminPage, { global })
  await flushPromises()

  assert.equal(wrapper.findAll('.session-item').length, 1)
  assert.equal(wrapper.get('.unread-dot').text(), '2')

  await wrapper.find('.session-item').trigger('click')
  await flushPromises()

  assert.equal(wrapper.findAll('.msg-bubble').length, 1)
  assert.match(wrapper.text(), /你好，订单有问题/)
})

async function mountWithOpenSession() {
  vi.useFakeTimers()
  try {
    const wrapper = mount(ChatAdminPage, { global })
    await flushPromises()
    await wrapper.find('.session-item').trigger('click')
    await flushPromises()
    vi.mocked(getAdminChatMessages).mockClear()
    vi.mocked(getAdminSessions).mockClear()
    return wrapper
  } catch (error) {
    vi.useRealTimers()
    throw error
  }
}

test('SSE MESSAGE 事件（当前会话）刷新消息线程与会话列表', async () => {
  const wrapper = await mountWithOpenSession()
  try {
    const withNewMessage = thread({
      messages: [
        { id: 10, conversationId: 'c1', senderId: 2, receiverId: 9, content: '你好，订单有问题', mine: false, createdAt: '2026-08-24T09:59:00' },
        { id: 11, conversationId: 'c1', senderId: 2, receiverId: 9, content: '麻烦看一下', mine: false, createdAt: '2026-08-24T10:01:00' }
      ]
    })
    vi.mocked(getAdminChatMessages).mockResolvedValue({ code: 200, message: 'ok', data: withNewMessage } as unknown as ApiResult<ChatThread>)

    streamMessageHandlers.forEach((handler) => handler({ type: 'MESSAGE', conversationId: 'c1', messageId: 11 }))
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()

    assert.equal(vi.mocked(getAdminChatMessages).mock.calls.length, 1)
    assert.equal(vi.mocked(getAdminSessions).mock.calls.length, 1)
    assert.equal(wrapper.findAll('.msg-bubble').length, 2)
    assert.match(wrapper.text(), /麻烦看一下/)
  } finally {
    vi.useRealTimers()
  }
})

test('SSE MESSAGE 事件（其他会话）只刷新会话列表', async () => {
  await mountWithOpenSession()
  try {
    streamMessageHandlers.forEach((handler) => handler({ type: 'MESSAGE', conversationId: 'c9' }))
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()

    assert.equal(vi.mocked(getAdminSessions).mock.calls.length, 1)
    assert.equal(vi.mocked(getAdminChatMessages).mock.calls.length, 0)
  } finally {
    vi.useRealTimers()
  }
})

test('SSE resync 整段重拉会话与当前线程', async () => {
  await mountWithOpenSession()
  try {
    streamResyncHandlers.forEach((handler) => handler())
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()

    assert.equal(vi.mocked(getAdminSessions).mock.calls.length, 1)
    assert.equal(vi.mocked(getAdminChatMessages).mock.calls.length, 1)
  } finally {
    vi.useRealTimers()
  }
})
