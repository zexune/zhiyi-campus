import assert from 'node:assert/strict'
import { mount } from '@vue/test-utils'
import { test } from 'vitest'

import UserAvatar from '@/components/common/UserAvatar.vue'

test('无 src：渲染昵称首字文字头像', () => {
  const wrapper = mount(UserAvatar, { props: { nickname: '小明', userId: 7, size: 'm' } })
  assert.equal(wrapper.find('img').exists(), false)
  assert.equal(wrapper.text(), '小')
  // 尺寸类与标题保留，回退逻辑不变
  assert.ok(wrapper.get('.avatar').classes().includes('avatar--m'))
  assert.equal(wrapper.get('.avatar').attributes('title'), '小明')
})

test('src 非空：渲染圆形图片并隐藏文字', () => {
  const wrapper = mount(UserAvatar, { props: { nickname: '小明', userId: 7, size: 'm', src: '/uploads/avatars/abc.png' } })
  const img = wrapper.get('img')
  assert.equal(img.attributes('src'), '/uploads/avatars/abc.png')
  assert.equal(img.attributes('alt'), '小明')
  assert.equal(wrapper.text(), '')
})

test('图片加载失败：回退文字头像，且换 src 后重置失败标记重试', async () => {
  const wrapper = mount(UserAvatar, { props: { nickname: '小红', userId: 3, size: 's', src: '/uploads/avatars/broken.png' } })
  await wrapper.get('img').trigger('error')
  // 失败后隐藏图片，回退到首字
  assert.equal(wrapper.find('img').exists(), false)
  assert.equal(wrapper.text(), '小')

  // 更换 src：失败标记应被重置，允许重新加载新 URL
  await wrapper.setProps({ src: '/uploads/avatars/new.png' })
  assert.equal(wrapper.find('img').exists(), true)
  assert.equal(wrapper.find('img').attributes('src'), '/uploads/avatars/new.png')
})

test('src 为空字符串：等同未设置，走文字头像', () => {
  const wrapper = mount(UserAvatar, { props: { nickname: '无名', userId: 1, size: 'l', src: '' } })
  assert.equal(wrapper.find('img').exists(), false)
  assert.equal(wrapper.text(), '无')
})
