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
    PriceTag: { props: ['value'], template: '<span class="price-tag">{{ value }}</span>' }
  }
}

const item: ChatItemSummary = { id: 5, title: '二手教材', price: '12.00', coverImage: '/img/cover.jpg', status: 'ON_SALE' }

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
  const wrapper = mount(RelatedItemCard, { global, props: { item: { ...item, coverImage: undefined } } })
  assert.equal(wrapper.find('img').exists(), false)
  assert.ok(wrapper.find('.related-item__thumb').exists())
})
