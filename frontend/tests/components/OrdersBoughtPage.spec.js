import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, test, vi } from 'vitest'
import { ElMessage, ElMessageBox } from 'element-plus'

import OrdersBoughtPage from '@/views/wallet/OrdersBoughtPage.vue'
import { cancelOrder, confirmReceipt, getBoughtOrders, reviewOrder } from '@/api/order'

vi.mock('@/api/order', () => ({
  cancelOrder: vi.fn(),
  confirmReceipt: vi.fn(),
  getBoughtOrders: vi.fn(),
  reviewOrder: vi.fn()
}))

const ReviewStub = {
  props: ['visible', 'order', 'submitting'],
  emits: ['close', 'submit'],
  template: `
    <aside v-if="visible" data-test="review-dialog">
      <span>{{ order.itemTitle }}</span>
      <button @click="$emit('submit', { rating: 5, accurate: true, comment: '组件测试评价' })">
        提交测试评价
      </button>
    </aside>
  `
}

const global = {
  stubs: {
    DefaultLayout: { template: '<main><slot /></main>' },
    RouterLink: { template: '<a><slot /></a>' },
    ElPagination: { template: '<nav data-test="pagination" />' },
    OrderReviewDialog: ReviewStub
  }
}

function order(overrides = {}) {
  return {
    id: 71,
    itemId: 17,
    itemTitle: '算法教材',
    itemCover: null,
    peerNickname: '卖家同学',
    price: 19.9,
    status: 'COMPLETED',
    reviewed: false,
    createdAt: '2026-08-13T10:00:00',
    completedAt: '2026-08-13T11:00:00',
    ...overrides
  }
}

beforeEach(() => {
  getBoughtOrders.mockResolvedValue({ data: { records: [order()], total: 1 } })
  reviewOrder.mockResolvedValue({ data: null })
  confirmReceipt.mockResolvedValue({ data: null })
  cancelOrder.mockResolvedValue({ data: null })
})

test('已完成未评价订单可打开评价框，提交后刷新列表', async () => {
  const success = vi.spyOn(ElMessage, 'success').mockImplementation(() => {})
  const wrapper = mount(OrdersBoughtPage, { global })
  await flushPromises()

  assert.match(wrapper.text(), /算法教材/)
  assert.match(wrapper.text(), /已完成/)
  await wrapper.get('.btn--yellow').trigger('click')
  assert.match(wrapper.get('[data-test="review-dialog"]').text(), /算法教材/)

  await wrapper.get('[data-test="review-dialog"] button').trigger('click')
  await flushPromises()

  assert.deepEqual(reviewOrder.mock.calls[0], [
    71,
    {
      rating: 5,
      accurate: true,
      comment: '组件测试评价'
    }
  ])
  assert.equal(getBoughtOrders.mock.calls.length, 2)
  assert.equal(success.mock.calls.length, 1)
})

test('订单状态筛选会回到第一页，并仅在选择时发送 status', async () => {
  const wrapper = mount(OrdersBoughtPage, { global })
  await flushPromises()

  const completed = wrapper.findAll('.filter-bar button').find((button) => button.text() === '已完成')
  assert.ok(completed)
  await completed.trigger('click')
  await flushPromises()

  assert.deepEqual(getBoughtOrders.mock.calls.at(-1), [
    {
      page: 1,
      size: 10,
      status: 'COMPLETED'
    }
  ])
})

test('待见面订单经二次确认后调用确认收货并刷新', async () => {
  getBoughtOrders.mockResolvedValue({
    data: { records: [order({ status: 'WAITING_MEET', reviewed: null })], total: 1 }
  })
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
  vi.spyOn(ElMessage, 'success').mockImplementation(() => {})
  const wrapper = mount(OrdersBoughtPage, { global })
  await flushPromises()

  const confirm = wrapper.findAll('button').find((button) => button.text() === '确认收货')
  assert.ok(confirm)
  await confirm.trigger('click')
  await flushPromises()

  assert.deepEqual(confirmReceipt.mock.calls[0], [71])
  assert.equal(getBoughtOrders.mock.calls.length, 2)
})
