import assert from 'node:assert/strict'
import { mount } from '@vue/test-utils'
import { test } from 'vitest'

import RelatedItemCard from '@/views/chat/components/RelatedItemCard.vue'
import type { ChatItemSummary } from '@/types/models'

const global = {
  stubs: {
    RouterLink: {
      props: ['to'],
      template: "<a :data-test-to=\"typeof to === 'string' ? to : ''\"><slot /></a>"
    },
    // 桩组件模拟真实 PriceTag 的两位小数格式化，保证金额断言有效；
    // ItemPrice 本体不桩：SWAP/金额分支属于被测行为
    PriceTag: { props: ['value'], template: '<span class="price-tag">{{ Number(value).toFixed(2) }}</span>' }
  }
}

const item: ChatItemSummary = {
  id: 5,
  title: '二手教材',
  type: 'SELL',
  price: 12,
  coverImage: '/img/cover.jpg',
  status: 'ON_SALE'
}

test('相关商品卡片渲染标题、价格与封面，跳转目标为商品详情', () => {
  const wrapper = mount(RelatedItemCard, { global, props: { item } })

  assert.match(wrapper.text(), /二手教材/)
  assert.match(wrapper.text(), /12\.00/)
  assert.match(wrapper.text(), /查看商品/)
  assert.equal(wrapper.get('a').attributes('data-test-to'), '/item/5')
  assert.equal(wrapper.get('img').attributes('src'), '/img/cover.jpg')
  assert.equal(wrapper.get('img').attributes('alt'), '二手教材')
})

test('无封面时不渲染图片，保留占位底色容器', () => {
  // 后端对无封面商品序列化为显式 null，而非空字符串
  const wrapper = mount(RelatedItemCard, { global, props: { item: { ...item, coverImage: null } } })
  assert.equal(wrapper.find('img').exists(), false)
  assert.ok(wrapper.find('.related-item__thumb').exists())
})

test('P0-2：SWAP 商品按 (type, price) 渲染为「以物换物」，绝不出现 ¥0.00', () => {
  const swapItem: ChatItemSummary = { ...item, type: 'SWAP', price: null }
  const wrapper = mount(RelatedItemCard, { global, props: { item: swapItem } })

  assert.match(wrapper.text(), /以物换物/)
  assert.doesNotMatch(wrapper.text(), /0\.00/)
  assert.equal(wrapper.find('.price-tag').exists(), false, 'SWAP 不应走金额渲染分支')
})

test('P0-2：SELL/BUY/ERRAND 金额仍正确显示', () => {
  const errandItem: ChatItemSummary = { ...item, type: 'ERRAND', price: 8.5 }
  const wrapper = mount(RelatedItemCard, { global, props: { item: errandItem } })

  assert.match(wrapper.text(), /8\.50/)
  assert.equal(wrapper.find('.price--swap').exists(), false)
})
