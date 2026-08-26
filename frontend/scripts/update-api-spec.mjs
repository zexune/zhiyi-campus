/**
 * gen:api:dev 第一步：从本地运行中的后端拉取实时 OpenAPI 规格，
 * 规范化后覆盖仓库根目录的契约快照。类型生成仍由后续的 openapi-typescript
 * 完成，两条命令共享同一个输入源（快照文件），行为完全对称：
 *   gen:api     = 快照 → 类型
 *   gen:api:dev = 后端 → 快照 → 类型
 *
 * 为什么必须规范化：springdoc 对部分类型的属性枚举顺序在每次重启间不稳定，
 * 直接落盘会让快照与生成类型产生大量纯顺序噪音 diff。规范化规则与 CI 的
 * compare-openapi.mjs 共享 openapi-normalize.mjs 单一实现——包括可空 $ref
 * 改写为 anyOf（OpenAPI 3.1 标准形式）与 required/enum/x-business-codes
 * 集合排序，落盘快照与漂移比较基准永远一致。
 */
import { writeFileSync } from 'node:fs'
import { normalizeSpec } from './openapi-normalize.mjs'

const SPEC_URL = 'http://127.0.0.1:8080/v3/api-docs'
// 本文件位于 frontend/scripts/，仓库根目录是上两级
const SNAPSHOT_PATH = new URL('../../openapi.json', import.meta.url)

const response = await fetch(SPEC_URL)
if (!response.ok) {
  console.error(`✗ ${SPEC_URL} 返回 HTTP ${response.status}——请确认后端已在 8080 端口启动`)
  process.exit(1)
}

const specText = await response.text()
let spec
try {
  spec = JSON.parse(specText)
} catch (error) {
  console.error(`✗ 响应不是合法 JSON：${error.message}`)
  process.exit(1)
}

writeFileSync(SNAPSHOT_PATH, JSON.stringify(normalizeSpec(spec), null, 2) + '\n')
console.log('✓ openapi.json 快照已从运行中的后端更新（规范化：键序 + 可空 $ref + 集合序）')
