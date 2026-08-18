import { test } from 'vitest'
import assert from 'node:assert/strict'

import { buildMyItemsParams } from '../src/views/user/myItemsQuery'

test('includes a selected status in paginated item requests', () => {
  assert.deepEqual(buildMyItemsParams(2, 10, 'SOLD'), { page: 2, size: 10, status: 'SOLD' })
})

test('omits status for the all-items tab', () => {
  assert.deepEqual(buildMyItemsParams(1, 10, ''), { page: 1, size: 10 })
})
