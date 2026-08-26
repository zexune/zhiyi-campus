/**
 * OpenAPI 快照规范化（单一真相源）：
 * gen:api:dev 落盘快照与 CI 漂移比较（compare-openapi.mjs）必须应用完全相同的
 * 变换，保证"本地提交的快照"与"CI 对实时规格的比较基准"语义一致。
 *
 * 规则：
 * 1) 可空 $ref：springdoc 3.1 输出 `{"$ref": "...", "type": "null"}`，
 *    openapi-typescript 无法把该形式合并为 `| null`；统一改写为
 *    OpenAPI 3.1 标准 `{"anyOf": [{"$ref": "..."}, {"type": "null"}]}`，
 *    从而删除前端 Omit+覆写 的生成后补丁；
 * 2) 对象键深度排序：springdoc 的属性遍历顺序依赖反射，跨环境不稳定；
 * 3) 集合数组排序：required / enum / x-business-codes 是无序集合，
 *    纯顺序变化不是契约漂移；真正有序的数组（parameters、path 段、
 *    allOf/anyOf 元素等）保留原顺序。
 */

const SORTED_ARRAY_KEYS = new Set(['required', 'enum', 'x-business-codes'])

function fixNullableRefs(value) {
  if (Array.isArray(value)) return value.map(fixNullableRefs)
  if (value !== null && typeof value === 'object') {
    const fixed = {}
    for (const [key, child] of Object.entries(value)) fixed[key] = fixNullableRefs(child)
    if (typeof fixed.$ref === 'string' && fixed.type === 'null') {
      return { anyOf: [{ $ref: fixed.$ref }, { type: 'null' }] }
    }
    return fixed
  }
  return value
}

function byJson(a, b) {
  const sa = JSON.stringify(a)
  const sb = JSON.stringify(b)
  return sa < sb ? -1 : sa > sb ? 1 : 0
}

function sortDeep(value, key) {
  if (Array.isArray(value)) {
    const mapped = value.map((item) => sortDeep(item))
    return key !== undefined && SORTED_ARRAY_KEYS.has(key) ? [...mapped].sort(byJson) : mapped
  }
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map((childKey) => [childKey, sortDeep(value[childKey], childKey)])
    )
  }
  return value
}

/** 依次应用可空 $ref 改写与键序/集合序规范化；纯函数，不修改输入。 */
export function normalizeSpec(spec) {
  return sortDeep(fixNullableRefs(spec))
}

export const SORTED_ARRAY_KEYS_LIST = [...SORTED_ARRAY_KEYS]
