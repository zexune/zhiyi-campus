import assert from 'node:assert/strict'
import { mount } from '@vue/test-utils'
import { test } from 'vitest'

import GalleryBlock from '@/views/item/components/GalleryBlock.vue'

const images = ['/img/a.jpg', '/img/b.jpg', '/img/c.jpg']

function mountGallery(props: Record<string, unknown> = {}) {
  return mount(GalleryBlock, {
    props: { images, coverImage: '/img/a.jpg', alt: '九成新教材', placeholder: 'ph-1', ...props }
  })
}

test('首图封面优先渲染，多图时提供计数与切换', async () => {
  const wrapper = mountGallery()
  assert.equal(wrapper.get('.gallery__main img').attributes('src'), '/img/a.jpg')
  assert.equal(wrapper.get('.gallery__main img').attributes('alt'), '九成新教材')
  assert.ok(wrapper.get('.gallery__main').classes().includes('ph-1'))
  assert.equal(wrapper.get('.gallery__count').text(), '1 / 3')
  assert.equal(wrapper.findAll('.th').length, 3)

  await wrapper.get('.gallery__nav--next').trigger('click')
  assert.equal(wrapper.get('.gallery__main img').attributes('src'), '/img/b.jpg')
  assert.equal(wrapper.get('.gallery__count').text(), '2 / 3')
})

test('上一张在首图处回绕到末图，缩略图点选直达', async () => {
  const wrapper = mountGallery()

  await wrapper.get('.gallery__nav--prev').trigger('click')
  assert.equal(wrapper.get('.gallery__main img').attributes('src'), '/img/c.jpg')
  assert.equal(wrapper.get('.gallery__count').text(), '3 / 3')

  await wrapper.findAll('.th')[1]!.trigger('click')
  assert.equal(wrapper.get('.gallery__main img').attributes('src'), '/img/b.jpg')
  assert.ok(wrapper.findAll('.th')[1]!.classes().includes('active'))
})

test('单图与空图集不渲染导航和缩略图，计数仅在有图时出现', () => {
  const single = mountGallery({ images: ['/img/only.jpg'], coverImage: '/img/only.jpg' })
  assert.equal(single.find('.gallery__nav--prev').exists(), false)
  assert.equal(single.find('.gallery__thumbs').exists(), false)
  assert.equal(single.get('.gallery__count').text(), '1 / 1')

  const empty = mountGallery({ images: [], coverImage: '' })
  assert.equal(empty.find('.gallery__main img').exists(), false)
  assert.equal(empty.find('.gallery__count').exists(), false)
})

test('图片集或封面变化时按封面优先重置当前图（承接路由切换商品）', async () => {
  const wrapper = mountGallery()
  await wrapper.get('.gallery__nav--next').trigger('click')
  assert.equal(wrapper.get('.gallery__main img').attributes('src'), '/img/b.jpg')

  // 空集兜底：路由切换瞬间 item 置空的等价形态
  await wrapper.setProps({ images: [], coverImage: '' })
  assert.equal(wrapper.find('.gallery__main img').exists(), false)

  // 新商品图集到来：封面优先初始化
  await wrapper.setProps({ images: ['/img/x.jpg', '/img/y.jpg'], coverImage: '/img/x.jpg' })
  assert.equal(wrapper.get('.gallery__main img').attributes('src'), '/img/x.jpg')

  // 无封面时退回首图
  await wrapper.setProps({ images: ['/img/m.jpg', '/img/n.jpg'], coverImage: '' })
  assert.equal(wrapper.get('.gallery__main img').attributes('src'), '/img/m.jpg')
})
