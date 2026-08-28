import assert from 'node:assert/strict'
import { afterEach, beforeEach, test, vi } from 'vitest'
import { createApp, h } from 'vue'

import { formatDate, formatDateTime, formatPrice, formatTimeShort, placeholderClass } from '@/utils/format'
import { usePagedList } from '@/composables/usePagedList'

// ---- format ----

test('formatDateTime 输出 YYYY-MM-DD HH:mm，空值与非法值返回空串', () => {
  assert.equal(formatDateTime('2026-08-13T12:30:00'), '2026-08-13 12:30')
  assert.equal(formatDateTime('2026-01-02T03:04'), '2026-01-02 03:04')
  assert.equal(formatDateTime(''), '')
  assert.equal(formatDateTime(null), '')
  assert.equal(formatDateTime('not-a-date'), '')
})

test('formatDate / formatTimeShort 复用统一的时间解析', () => {
  assert.equal(formatDate('2026-08-13T12:30:00'), '2026-08-13')
  assert.equal(formatTimeShort('2026-08-13T12:30:00'), '08-13 12:30')
  assert.equal(formatDate(''), '')
})

test('formatPrice 统一两位小数并兜底空值', () => {
  assert.equal(formatPrice(19.9), '19.90')
  assert.equal(formatPrice('3'), '3.00')
  assert.equal(formatPrice(null), '0.00')
  assert.equal(formatPrice(undefined), '0.00')
})

test('placeholderClass 按 ID 稳定取色', () => {
  assert.equal(placeholderClass(7), 'ph-b')
  assert.equal(placeholderClass(13), 'ph-b')
  assert.equal(placeholderClass(null), 'ph-a')
  assert.equal(placeholderClass(6), 'ph-a')
  assert.equal(placeholderClass(3), 'ph-d')
})

// ---- usePagedList ----

function withSetup<T>(composable: () => T): T {
  let result!: T
  const app = createApp({
    setup() {
      result = composable()
      return () => h('div')
    }
  })
  app.mount(document.createElement('div'))
  return result
}

beforeEach(() => {
  vi.stubGlobal('localStorage', { getItem: vi.fn(), setItem: vi.fn() })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

test('usePagedList 组装分页参数并落到 records/total', async () => {
  const loader = vi.fn().mockResolvedValue({ data: { records: [{ id: 1 }], total: 41 } })
  const list = withSetup(() => usePagedList(loader, { size: 20, params: () => ({ status: 'WAITING_MEET' }) }))

  list.currentPage.value = 3
  await list.fetchList()

  assert.equal(loader.mock.calls[0][0].page, 3)
  assert.equal(loader.mock.calls[0][0].size, 20)
  assert.equal(loader.mock.calls[0][0].status, 'WAITING_MEET')
  assert.deepEqual(list.records.value, [{ id: 1 }])
  assert.equal(list.total.value, 41)
  assert.equal(list.loading.value, false)
  assert.equal(list.loadError.value, false)
})

test('usePagedList 失败置 loadError 且不抛出', async () => {
  const failing = vi.fn().mockRejectedValue(new Error('network'))
  const failed = withSetup(() => usePagedList(failing))
  await failed.fetchList()
  assert.equal(failed.loadError.value, true)
  assert.deepEqual(failed.records.value, [])
})

// ---- routes 契约 ----

test('ROUTE_PATH 静态路径与动态构造函数保持稳定', async () => {
  const { ROUTE_PATH, ROUTE_NAME } = await import('@/constants/routes')
  assert.equal(ROUTE_PATH.HOME, '/')
  assert.equal(ROUTE_PATH.LOGIN, '/login')
  assert.equal(ROUTE_PATH.ORDERS_BOUGHT, '/orders/bought')
  assert.equal(ROUTE_PATH.ADMIN_DASHBOARD, '/admin/dashboard')
  assert.equal(ROUTE_PATH.ADMIN_USERS, '/admin/users')
  assert.equal(ROUTE_PATH.ADMIN_TOPICS, '/admin/topics')
  assert.equal(ROUTE_PATH.item(42), '/item/42')
  assert.equal(ROUTE_PATH.editItem(42), '/item/42/edit')
  assert.equal(ROUTE_PATH.PUBLISH, '/publish')
  // 命名路由与路由表一致（守卫与编程式导航依赖）
  assert.equal(ROUTE_NAME.ORDERS_BOUGHT, 'OrdersBought')
  assert.equal(ROUTE_NAME.ADMIN_USERS, 'AdminUsers')
  assert.equal(ROUTE_NAME.ADMIN_TOPICS, 'AdminTopics')
})

test('usePagedList goToFirstPage 与失败后重试路径', async () => {
  const loader = vi
    .fn()
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValueOnce({ data: { records: [{ id: 1 }], total: 1 } })
  const list = withSetup(() => usePagedList(loader))
  list.currentPage.value = 4

  await list.fetchList()
  assert.equal(list.loadError.value, true)
  list.goToFirstPage()
  assert.equal(list.currentPage.value, 1)

  await list.fetchList()
  assert.equal(list.loadError.value, false)
  assert.deepEqual(list.records.value, [{ id: 1 }])
  assert.equal(loader.mock.calls[1][0].page, 1)
})

test('usePagedList 默认参数与 formValidate 空引用兜底', async () => {
  const loader = vi.fn().mockResolvedValue({ data: { records: [], total: 0 } })
  const list = withSetup(() => usePagedList(loader))
  await list.fetchList()
  assert.equal(loader.mock.calls[0][0].size, 10)
  assert.deepEqual(loader.mock.calls[0][0].page !== undefined, true)

  const { validateForm } = await import('@/utils/formValidate')
  const emptyRef = { value: null }
  assert.equal(await validateForm(emptyRef), false)
})

test('formatChatTime 同日仅时分、跨日带日期，非法输入返回空串', async () => {
  const { formatChatTime } = await import('@/utils/format')
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const today = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T09:05:00`
  assert.equal(formatChatTime(today), '09:05')
  assert.match(formatChatTime('2020-01-02T08:30:00'), /^1\/2 08:30$/)
  assert.equal(formatChatTime('not-a-date'), '')
  assert.equal(formatChatTime(null), '')
})

test('usePagedList 乱序返回的旧代成功响应被丢弃，不覆盖新结果', async () => {
  let resolveStale!: (value: { data: { records: unknown[]; total: number } }) => void
  const loader = vi
    .fn()
    .mockImplementationOnce(() => new Promise((resolve) => (resolveStale = resolve)))
    .mockResolvedValueOnce({ data: { records: [{ id: 2 }], total: 2 } })

  const list = withSetup(() => usePagedList(loader))
  const staleCall = list.fetchList()
  await list.fetchList()
  assert.deepEqual(list.records.value, [{ id: 2 }])
  assert.equal(list.loading.value, false)

  resolveStale({ data: { records: [{ id: 1 }], total: 1 } })
  await staleCall
  assert.deepEqual(list.records.value, [{ id: 2 }])
  assert.equal(list.total.value, 2)
  assert.equal(list.loading.value, false)
  assert.equal(list.loadError.value, false)
})

test('usePagedList 乱序返回的旧代失败不置 loadError', async () => {
  let rejectStale!: (reason: unknown) => void
  const loader = vi
    .fn()
    .mockImplementationOnce(() => new Promise((_, reject) => (rejectStale = reject)))
    .mockResolvedValueOnce({ data: { records: [{ id: 2 }], total: 2 } })

  const list = withSetup(() => usePagedList(loader))
  const staleCall = list.fetchList()
  await list.fetchList()
  assert.deepEqual(list.records.value, [{ id: 2 }])
  assert.equal(list.loadError.value, false)

  rejectStale(new Error('stale timeout'))
  await staleCall
  assert.equal(list.loadError.value, false)
  assert.deepEqual(list.records.value, [{ id: 2 }])
})
