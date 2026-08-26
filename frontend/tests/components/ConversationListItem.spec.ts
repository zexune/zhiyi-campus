import assert from 'node:assert/strict'
import { mount } from '@vue/test-utils'
import { test } from 'vitest'

import ConversationListItem from '@/views/chat/components/ConversationListItem.vue'
import type { Conversation } from '@/types/models'

const global = {
  stubs: {
    UserAvatar: { template: '<span class="avatar" />' },
    LevelBadge: { props: ['level'], template: '<span class="badge" />' }
  }
}

const conversation = {
  conversationId: 'c1',
  peer: { id: 2, nickname: '卖家小明', level: 3 },
  relatedItem: { id: 5, title: '二手教材', price: 12, status: 'ON_SALE' },
  lastMessage: '可以约图书馆',
  lastMessageTime: '2026-08-24T10:00:00',
  unreadCount: 3
} as Conversation

test('会话行渲染昵称、最近消息、相关商品与未读角标，缺省字段回退', () => {
  const wrapper = mount(ConversationListItem, { global, props: { conversation } })
  const text = wrapper.text()
  assert.match(text, /卖家小明/)
  assert.match(text, /可以约图书馆/)
  assert.match(text, /二手教材/)
  assert.equal(wrapper.get('.conv-item__unread').text(), '3')

  const bare = mount(ConversationListItem, {
    global,
    props: { conversation: { ...conversation, peer: undefined, relatedItem: null, lastMessage: '', unreadCount: 0 } as unknown as Conversation }
  })
  assert.match(bare.text(), /同学/)
  assert.match(bare.text(), /暂无消息/)
  assert.equal(bare.find('.conv-item__goods').exists(), false)
  assert.equal(bare.find('.conv-item__unread').exists(), false)
})

test('选中态类名与 select 事件上抛', async () => {
  const wrapper = mount(ConversationListItem, { global, props: { conversation, active: true } })
  assert.ok(wrapper.get('.conv-item').classes().includes('active'))

  await wrapper.get('.conv-item').trigger('click')
  assert.equal(wrapper.emitted('select')?.length, 1)
})
