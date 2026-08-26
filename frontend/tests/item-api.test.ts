import assert from 'node:assert/strict'
import { beforeEach, test, vi } from 'vitest'

/**
 * item API 模块契约测试：submitItemAppeal 的成功负载是后端回传的完整
 * AppealVO（生成契约必填 data），不再把成功数据断言为空——
 * 非空 AppealVO 必须原样保留，data:null 属协议违约立即失败。
 */

const { getMock, postMock } = vi.hoisted(() => ({ getMock: vi.fn(), postMock: vi.fn() }))

vi.mock('@/utils/request', () => ({
  default: { get: getMock, post: postMock, put: vi.fn(), delete: vi.fn() }
}))

import { getItemList, submitItemAppeal } from '@/api/item'
import { ProtocolViolationError } from '@/api/mappers'
import type { Schemas } from '@/types/contracts'

const appeal: Schemas['AppealVO'] = {
  id: 31,
  reportId: 9,
  itemId: 5,
  userId: 7,
  sellerName: '申诉卖家',
  itemTitle: '被误判的二手书',
  violationReason: '涉嫌违规商品描述',
  reason: '商品描述合规，请求复核撤销扣分',
  status: 'PENDING',
  handlerId: undefined,
  handlerName: undefined,
  handleNote: undefined,
  createdAt: '2026-08-26T10:00:00',
  handledAt: undefined
}

beforeEach(() => {
  getMock.mockReset()
  postMock.mockReset()
})

test('getItemList 保留完整的游标 Feed 负载', async () => {
  const feed = { records: [], nextCursor: null, hasMore: false, estimatedTotal: 0 }
  getMock.mockResolvedValue({ code: 200, message: 'ok', data: feed })

  const res = await getItemList({ size: 12 })

  assert.deepEqual(res.data, feed)
})

test.each([
  ['records', { nextCursor: null, hasMore: false, estimatedTotal: 0 }],
  ['nextCursor', { records: [], hasMore: false, estimatedTotal: 0 }],
  ['hasMore', { records: [], nextCursor: null, estimatedTotal: 0 }],
  ['estimatedTotal', { records: [], nextCursor: null, hasMore: false }]
])('getItemList 的 Feed 缺少 %s 时抛出协议错误', async (field, data) => {
  getMock.mockResolvedValue({ code: 200, message: 'ok', data })

  await assert.rejects(getItemList({}), (error: unknown) => error instanceof ProtocolViolationError && error.message.includes(field))
})

test('submitItemAppeal 完整保留非空 AppealVO 并透传申诉理由', async () => {
  postMock.mockResolvedValue({ code: 200, message: '申诉已提交', data: appeal })

  const res = await submitItemAppeal('5', { reason: '商品描述合规，请求复核撤销扣分' })

  assert.deepEqual(res.data, appeal, '成功信封的 AppealVO 必须逐字段保留')
  assert.equal(res.code, 200)
  assert.equal(postMock.mock.calls.length, 1)
  const [url, body] = postMock.mock.calls[0] as [string, unknown]
  assert.equal(url, '/item/5/appeals', '模板路径必须填充 path 参数后调用')
  assert.deepEqual(body, { reason: '商品描述合规，请求复核撤销扣分' })
})

test('submitItemAppeal 收到 data:null 视为协议违约并抛错', async () => {
  postMock.mockResolvedValue({ code: 200, message: '申诉已提交', data: null })

  await assert.rejects(submitItemAppeal(5, { reason: 'x'.repeat(12) }), ProtocolViolationError)
})
