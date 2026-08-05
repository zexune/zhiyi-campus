export const KNOWN_RELATION_TAGS = Object.freeze(['同学院', '同级', '同校区', '同楼'])

/**
 * 关系标签按产品约定排序、去重，同时保留后端未来新增的标签。
 */
export function normalizeRelationTags(tags) {
  if (!Array.isArray(tags)) return []

  const unique = [...new Set(
    tags
      .filter((tag) => typeof tag === 'string')
      .map((tag) => tag.trim())
      .filter(Boolean)
  )]

  const known = KNOWN_RELATION_TAGS.filter((tag) => unique.includes(tag))
  const additional = unique.filter((tag) => !KNOWN_RELATION_TAGS.includes(tag))
  return [...known, ...additional]
}
