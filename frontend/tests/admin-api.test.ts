import assert from 'node:assert/strict'
import { beforeEach, test, vi } from 'vitest'

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }))

vi.mock('@/utils/request', () => ({
  default: { get: getMock, post: vi.fn(), put: vi.fn(), delete: vi.fn() }
}))

import { getViolations } from '@/api/admin'
import { ProtocolViolationError } from '@/api/mappers'

const validRow = {
  id: 17,
  source: 'USER_REPORT',
  status: 'PENDING',
  originalTitle: '二手教材',
  createdAt: '2026-08-26T10:00:00'
}

beforeEach(() => {
  getMock.mockReset()
})

test('getViolations 将完整关键字段映射为可操作审核行', async () => {
  getMock.mockResolvedValue({ code: 200, message: 'ok', data: { records: [validRow], total: 1 } })

  const res = await getViolations({ page: 1, size: 10 })

  const review = res.data.records[0]
  assert.equal(review.id, validRow.id)
  assert.equal(review.source, validRow.source)
  assert.equal(review.status, validRow.status)
  assert.equal(review.originalTitle, validRow.originalTitle)
  assert.equal(review.createdAt, validRow.createdAt)
  assert.equal(review.reporterId, null)
  assert.equal(review.reporterName, null)
})

test.each(['id', 'source', 'status', 'originalTitle', 'createdAt'] as const)('getViolations 的审核行缺少 %s 时抛出协议错误', async (field) => {
  const row: Record<string, unknown> = { ...validRow }
  delete row[field]
  getMock.mockResolvedValue({ code: 200, message: 'ok', data: { records: [row], total: 1 } })

  await assert.rejects(getViolations({ page: 1, size: 10 }), (error: unknown) => error instanceof ProtocolViolationError && error.message.includes(field))
})
