import test from 'node:test'
import assert from 'node:assert/strict'

import {
  KNOWN_RELATION_TAGS,
  normalizeRelationTags,
} from '../src/utils/relation.js'

test('keeps A5 relation tags in the product order', () => {
  assert.deepEqual(
    normalizeRelationTags(['同楼', '同校区', '同学院', '同级']),
    KNOWN_RELATION_TAGS
  )
})

test('deduplicates and trims relation tags', () => {
  assert.deepEqual(
    normalizeRelationTags([' 同级 ', '同级', '', null]),
    ['同级']
  )
})

test('preserves future backend relation tags after known tags', () => {
  assert.deepEqual(
    normalizeRelationTags(['同社团', '同楼']),
    ['同楼', '同社团']
  )
  assert.deepEqual(normalizeRelationTags(null), [])
})
