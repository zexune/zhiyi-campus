import assert from 'node:assert/strict'
import { mount } from '@vue/test-utils'
import { test } from 'vitest'

import ProvenanceTimeline from '@/views/item/components/ProvenanceTimeline.vue'
import type { LineageNode } from '@/types/models'

const global = {
  stubs: {
    ElSkeleton: { template: '<div data-test="skeleton" />' }
  }
}

const chain: LineageNode[] = [
  { userId: 1, nickname: '首届主人', role: 'PUBLISHER', time: '2025-09-01T10:00:00', price: 30 },
  { userId: 2, nickname: null, role: 'BUYER', time: '2026-01-01T10:00:00' }
]

test('传承链按节点顺序渲染昵称、角色与成交价，缺省昵称回退通用文案', () => {
  const wrapper = mount(ProvenanceTimeline, { global, props: { chain } })

  const nodes = wrapper.findAll('.lineage-timeline li')
  assert.equal(nodes.length, 2)
  assert.match(nodes[0]!.text(), /首届主人/)
  assert.match(nodes[0]!.text(), /最初发布/)
  assert.match(nodes[0]!.text(), /¥30\.00/)
  assert.match(nodes[1]!.text(), /校园同学/)
  assert.match(nodes[1]!.text(), /完成接力/)
  // 无成交价的接力节点不渲染价格段
  assert.doesNotMatch(nodes[1]!.text(), /成交/)
})

test('加载中显示骨架屏，空链显示起步文案', async () => {
  const loading = mount(ProvenanceTimeline, { global, props: { chain: [], loading: true } })
  assert.ok(loading.find('[data-test="skeleton"]').exists())
  assert.equal(loading.find('.lineage-timeline').exists(), false)

  const empty = mount(ProvenanceTimeline, { global, props: { chain: null, loading: false } })
  assert.match(empty.text(), /这本教材刚刚开始它的校园旅程/)
})
