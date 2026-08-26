import assert from 'node:assert/strict'
import { test } from 'vitest'

import { mapLoginData, mapPageData, ProtocolViolationError } from '@/api/mappers'

test('mapLoginData 校验核心字段并统一登录用户标识', () => {
  const result = mapLoginData({ code: 200, message: 'ok', data: { token: 'jwt', user: { id: 7, username: 'admin', nickname: '管理员', role: 'ADMIN' } } }, '/api/admin/auth/login', 'username')

  assert.deepEqual(result.data, {
    token: 'jwt',
    user: { id: 7, studentId: 'admin', nickname: '管理员', role: 'ADMIN' }
  })
})

test('mapLoginData 不接受缺少 user 的成功负载', () => {
  assert.throws(
    () => mapLoginData({ code: 200, message: 'ok', data: { token: 'jwt' } }, '/api/auth/login', 'studentId'),
    (error: unknown) => error instanceof ProtocolViolationError && error.message.includes('user 缺失')
  )
})

test('mapPageData 保留合法分页行并映射领域数据', () => {
  const result = mapPageData({ code: 200, message: 'ok', data: { records: [{ id: 1 }, { id: 2 }], total: 2 } }, '/api/example', (row) => row.id)

  assert.deepEqual(result.data, { records: [1, 2], total: 2 })
})

test('mapPageData 不静默吞掉 null/undefined 分页行', () => {
  assert.throws(
    () => mapPageData({ code: 200, message: 'ok', data: { records: [null], total: 1 } }, '/api/example', (row) => row),
    (error: unknown) => error instanceof ProtocolViolationError && error.message.includes('records[0]')
  )
})
