import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, test, vi } from 'vitest'
import { createPinia } from 'pinia'

import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import { getUnreadCount } from '@/api/chat'
import type { ApiResult } from '@/utils/request'

vi.mock('@/api/chat', () => ({
  getUnreadCount: vi.fn()
}))

// ---- SSE 事件流替身：捕获布局注册的监听器，供用例手动派发事件 ----
const streamMessageHandlers = new Set<() => void>()
const streamResyncHandlers = new Set<() => void>()

vi.mock('@/composables/useChatStream', () => ({
  useChatStream: () => ({
    onMessage: (handler: () => void) => {
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

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/', fullPath: '/' }),
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('@/utils/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/auth')>()),
  isLoggedIn: () => true
}))

const global = {
  plugins: [createPinia()],
  stubs: {
    UserAvatar: { template: '<span />' },
    RouterLink: { template: '<a><slot /></a>' },
    'router-link': { template: '<a><slot /></a>' },
    ElDropdown: { template: '<div><slot /><slot name="dropdown" /></div>' },
    ElDropdownMenu: { template: '<div><slot /></div>' },
    ElDropdownItem: { template: '<div><slot /></div>' }
  }
}

beforeEach(() => {
  streamMessageHandlers.clear()
  streamResyncHandlers.clear()
  localStorage.setItem('userId', '7')
  localStorage.setItem('nickname', '测试用户')
  vi.mocked(getUnreadCount).mockResolvedValue({ code: 200, message: 'ok', data: 5 } as unknown as ApiResult<number>)
})

test('挂载即拉取未读数并渲染角标', async () => {
  const wrapper = mount(DefaultLayout, { global })
  await flushPromises()

  assert.equal(vi.mocked(getUnreadCount).mock.calls.length, 1)
  assert.equal(wrapper.get('.dot').text(), '5')
})

test('SSE MESSAGE 事件驱动未读数刷新（合并事件风暴），不再定时轮询', async () => {
  vi.useFakeTimers()
  try {
    const wrapper = mount(DefaultLayout, { global })
    await flushPromises()
    assert.equal(vi.mocked(getUnreadCount).mock.calls.length, 1)

    // 连续多条消息合并为一次重拉
    vi.mocked(getUnreadCount).mockResolvedValue({ code: 200, message: 'ok', data: 7 } as unknown as ApiResult<number>)
    streamMessageHandlers.forEach((handler) => handler())
    streamMessageHandlers.forEach((handler) => handler())
    streamMessageHandlers.forEach((handler) => handler())
    await vi.advanceTimersByTimeAsync(310)
    await flushPromises()

    assert.equal(vi.mocked(getUnreadCount).mock.calls.length, 2)
    assert.equal(wrapper.get('.dot').text(), '7')

    // 没有事件就没有请求：空闲时段不再产生轮询流量
    await vi.advanceTimersByTimeAsync(60_000)
    assert.equal(vi.mocked(getUnreadCount).mock.calls.length, 2)
  } finally {
    vi.useRealTimers()
  }
})

test('SSE resync（重连/恢复可见）触发未读数兜底重拉', async () => {
  vi.useFakeTimers()
  try {
    mount(DefaultLayout, { global })
    await flushPromises()
    assert.equal(vi.mocked(getUnreadCount).mock.calls.length, 1)

    streamResyncHandlers.forEach((handler) => handler())
    await vi.advanceTimersByTimeAsync(310)
    await flushPromises()

    assert.equal(vi.mocked(getUnreadCount).mock.calls.length, 2)
  } finally {
    vi.useRealTimers()
  }
})
