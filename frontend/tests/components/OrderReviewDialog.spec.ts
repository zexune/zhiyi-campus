import assert from 'node:assert/strict'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { test } from 'vitest'

import OrderReviewDialog from '@/components/trade/OrderReviewDialog.vue'

const DialogStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: `
    <section v-if="modelValue" data-test="dialog">
      <slot name="header" />
      <slot />
      <slot name="footer" />
    </section>
  `
}

function mountDialog(props = {}) {
  return mount(OrderReviewDialog, {
    props: {
      visible: true,
      order: {
        id: 42,
        itemId: 17,
        buyerId: 1,
        sellerId: 2,
        itemTitle: '九成新教材',
        itemCover: null,
        peerNickname: '张同学',
        price: 19.9,
        status: 'COMPLETED',
        reviewed: false,
        createdAt: '2026-08-13T10:00:00'
      },
      submitting: false,
      ...props
    },
    global: {
      stubs: { ElDialog: DialogStub }
    }
  })
}

test('评价对话框展示订单，并提交用户选择的完整语义载荷', async () => {
  const wrapper = mountDialog()

  assert.match(wrapper.text(), /NO\.42/)
  assert.match(wrapper.text(), /九成新教材/)
  assert.match(wrapper.text(), /张同学/)

  await wrapper.get('[aria-label="3 星"]').trigger('click')
  await wrapper.get('.review-accurate input').setValue(false)
  await wrapper.get('#review-comment').setValue('  当面交易很顺利  ')
  await wrapper.get('.review-dialog__footer .btn--primary').trigger('click')

  assert.deepEqual(wrapper.emitted('submit'), [
    [
      {
        rating: 3,
        accurate: false,
        comment: '当面交易很顺利'
      }
    ]
  ])
})

test('每次重新打开都会清空上次草稿并恢复五星默认值', async () => {
  const wrapper = mountDialog()
  await wrapper.get('[aria-label="2 星"]').trigger('click')
  await wrapper.get('#review-comment').setValue('上一次草稿')

  await wrapper.setProps({ visible: false })
  await wrapper.setProps({ visible: true })
  await nextTick()

  assert.equal(wrapper.get('[aria-label="5 星"]').attributes('aria-checked'), 'true')
  assert.equal((wrapper.get('#review-comment').element as HTMLTextAreaElement).value, '')
})

test('提交中禁止重复提交、关闭以及遮罩回调关闭', async () => {
  const wrapper = mountDialog({ submitting: true })

  await wrapper.get('.review-dialog__footer .btn--primary').trigger('click')
  await wrapper.get('.review-dialog__footer .btn').trigger('click')
  wrapper.getComponent(DialogStub).vm.$emit('update:modelValue', false)
  await nextTick()

  assert.equal(wrapper.emitted('submit'), undefined)
  assert.equal(wrapper.emitted('close'), undefined)
  assert.equal(wrapper.get('.review-dialog__footer .btn--primary').attributes('disabled'), '')
})
