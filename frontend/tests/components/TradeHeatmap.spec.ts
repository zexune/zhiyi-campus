import assert from 'node:assert/strict'
import { mount } from '@vue/test-utils'
import { test } from 'vitest'

import TradeHeatmap from '@/views/admin/components/TradeHeatmap.vue'
import type { TradeHeatEntry } from '@/types/models'

const entries: TradeHeatEntry[] = [
  { location: '图书馆', count: 10 },
  { location: '一食堂', count: 5 },
  { location: '体育馆', count: 1 },
  { location: '南门', count: 3 },
  { location: '宿舍楼', count: 2 },
  { location: '教学楼', count: 7 }
]

test('热力条按最大值等比计算宽度，颜色按行循环配色', () => {
  const wrapper = mount(TradeHeatmap, { props: { entries } })

  const rows = wrapper.findAll('.heatmap-bar-row')
  assert.equal(rows.length, 6)
  assert.match(rows[0]!.text(), /图书馆/)
  assert.match(rows[0]!.text(), /10 笔/)

  const bars = wrapper.findAll('.heatmap-bar')
  assert.equal(bars[0]!.attributes('style'), 'width: 100%;')
  assert.equal(bars[1]!.attributes('style'), 'width: 50%;')
  assert.equal(bars[2]!.attributes('style'), 'width: 10%;')

  assert.ok(bars[0]!.classes().includes('heat--1'))
  assert.ok(bars[4]!.classes().includes('heat--5'))
  assert.ok(bars[5]!.classes().includes('heat--1'))
})

test('空数据展示占位提示', () => {
  const wrapper = mount(TradeHeatmap, { props: { entries: [] } })
  assert.match(wrapper.text(), /暂无交易地点数据/)
  assert.equal(wrapper.find('.heatmap-grid').exists(), false)
})
