export const KNOWN_RELATION_TAGS: readonly string[] = Object.freeze(['同学院', '同级', '同校区', '同楼'])

/**
 * 关系标签按产品约定排序、去重，同时保留后端未来新增的标签。
 * 入参为未受信的接口数据，收 unknown 并在内部过滤非字符串成员。
 */
export function normalizeRelationTags(tags: unknown): string[] {
  if (!Array.isArray(tags)) return []

  const unique = [
    ...new Set(
      tags
        .filter((tag): tag is string => typeof tag === 'string')
        .map((tag) => tag.trim())
        .filter(Boolean)
    )
  ]

  const known = KNOWN_RELATION_TAGS.filter((tag) => unique.includes(tag))
  const additional = unique.filter((tag) => !KNOWN_RELATION_TAGS.includes(tag))
  return [...known, ...additional]
}
