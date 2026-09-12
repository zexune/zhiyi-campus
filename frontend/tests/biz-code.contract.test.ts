import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test } from 'vitest'
import { BIZ_CODE } from '@/constants/domain'

/**
 * domain.ts BIZ_CODE ↔ OpenAPI 快照的双向漂移守护。
 *
 * BIZ_CODE 手工镜像后端 ResultCode 的业务码段（键名与后端枚举一一对应）。
 * 快照中每个 operation 的错误响应都携带 x-business-codes（由后端
 * @BusinessErrors 注解生成），其业务码段（≥1000）的集合必须与 BIZ_CODE
 * 完全一致：后端增删改业务码后，先跑 npm run gen:api:dev 更新快照，
 * 本测试随即指出 domain.ts 需要同步的条目。
 */
const SNAPSHOT_PATH = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
  '..',
  'openapi.json'
)

interface Operation {
  responses?: Record<string, { 'x-business-codes'?: unknown[] }>
}

function businessCodesInSpec(): Set<number> {
  const spec = JSON.parse(readFileSync(SNAPSHOT_PATH, 'utf8')) as {
    paths: Record<string, Record<string, Operation | unknown>>
  }
  const codes = new Set<number>()
  for (const operations of Object.values(spec.paths)) {
    if (operations === null || typeof operations !== 'object') continue
    for (const operation of Object.values(operations)) {
      if (operation === null || typeof operation !== 'object') continue
      const responses = (operation as Operation).responses
      if (responses === null || typeof responses !== 'object') continue
      for (const response of Object.values(responses)) {
        for (const entry of response?.['x-business-codes'] ?? []) {
          // 快照按字符串数组序列化（"1001"），统一转数值再过滤业务码段
          const code = typeof entry === 'number' ? entry : Number(entry)
          if (Number.isInteger(code) && code >= 1000) {
            codes.add(code)
          }
        }
      }
    }
  }
  return codes
}

test('BIZ_CODE 数值唯一（镜像后端“业务码唯一登记”契约）', () => {
  const values = Object.values(BIZ_CODE)
  expect(new Set(values).size).toBe(values.length)
})

test('BIZ_CODE 与快照 x-business-codes 的业务码段双向一致', () => {
  const specCodes = businessCodesInSpec()
  const localCodes: Set<number> = new Set(Object.values(BIZ_CODE))

  const missingLocally = [...specCodes].filter((code) => !localCodes.has(code))
  const staleLocally = [...localCodes].filter((code) => !specCodes.has(code))

  expect(missingLocally, '快照中存在但 domain.ts 未登记的业务码').toEqual([])
  expect(staleLocally, 'domain.ts 中已不在快照业务码段的条目').toEqual([])
})
