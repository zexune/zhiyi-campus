import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, test, vi } from 'vitest'

import ChatListPage from '@/views/chat/ChatListPage.vue'
import { ackChatRead, getChatMessages, getConversations } from '@/api/chat'
import type { ApiResult } from '@/utils/request'
import type { ChatThread, Conversation } from '@/types/models'

vi.mock('@/api/chat', () => ({
  getConversations: vi.fn(),
  getChatMessages: vi.fn(),
  sendChatMessage: vi.fn(),
  ackChatRead: vi.fn(),
  startCustomerService: vi.fn()
}))

const route = { query: {} as Record<string, string> }
const router = { push: vi.fn(), replace: vi.fn() }

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router
}))

const global = {
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
