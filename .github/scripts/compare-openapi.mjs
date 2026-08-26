/**
 * OpenAPI 契约漂移审计：语义级比较两份规格文档。
 *
 * 比较前对两侧应用与本地快照落盘完全相同的规范化（frontend/scripts/
 * openapi-normalize.mjs 单一实现）：可空 $ref 改写为 anyOf、对象键深度排序、
 * required/enum/x-business-codes 集合排序。对纯顺序变化不敏感，
 * 对任何语义差异以非零码退出并打印首个分歧点。
 *
 * 用法：node compare-openapi.mjs <live.json> <committed.json>
 */
import { readFileSync } from 'node:fs'
import { normalizeSpec } from '../../frontend/scripts/openapi-normalize.mjs'

function load(path) {
  try {
    return JSON.parse(readFileSync(path, 'utf8'))
  } catch (error) {
    console.error(`::error::无法解析 ${path}：${error.message}`)
    process.exit(2)
  }
}

const [, , livePath, committedPath] = process.argv
const live = normalizeSpec(load(livePath))
const committed = normalizeSpec(load(committedPath))

if (JSON.stringify(live) === JSON.stringify(committed)) {
  console.log(`✅ 实时规格与 ${committedPath} 快照一致（规范化：键序 + 可空 $ref + 集合序）`)
  process.exit(0)
}

console.error(`::error::实时 OpenAPI 规格与仓库快照不一致（契约已漂移）`)
// 打印第一个分歧字段，帮助定位是哪个 VO/路径变了
for (const section of ['paths', 'components']) {
  const a = JSON.stringify(live[section] ?? null)
  const b = JSON.stringify(committed[section] ?? null)
  if (a !== b) {
    const ai = a ? findFirstDiff(a, b) : -1
    console.error(`  分歧区段：${section}（约在序列化位置 ${ai} 处开始不同）`)
  }
}
console.error(`  修复方式：本地启动后端后，在 frontend 目录运行 npm run gen:api:dev，`)
console.error(`  将更新后的 openapi.json 与 frontend/src/types/api.gen.d.ts 一并提交。`)
process.exit(1)

function findFirstDiff(a, b) {
  const len = Math.min(a.length, b.length)
  let i = 0
  while (i < len && a[i] === b[i]) i += 1
  return i
}
