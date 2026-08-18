import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, test, vi } from 'vitest'
import { ElMessage, ElMessageBox } from 'element-plus'

import OrdersBoughtPage from '@/views/wallet/OrdersBoughtPage.vue'
import { cancelOrder, confirmReceipt, getBoughtOrders, reviewOrder } from '@/api/order'
import type { Order } from '@/types/models'

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
  vi.mocked(getBoughtOrders).mockResolvedValue({ code: 200, message: 'ok', data: { records: [order()], total: 1 } })
  vi.mocked(reviewOrder).mockResolvedValue({ code: 200, message: 'ok', data: undefined })
  // 组件不消费这两个响应的 data，载荷无关紧要
  vi.mocked(confirmReceipt).mockResolvedValue({ code: 200, message: 'ok', data: undefined as unknown as Order })
  vi.mocked(cancelOrder).mockResolvedValue({ code: 200, message: 'ok', data: undefined as unknown as Order })
})

test('已完成未评价订单可打开评价框，提交后刷新列表', async () => {
  const success = vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  const wrapper = mount(OrdersBoughtPage, { global })
  await flushPromises()

  assert.match(wrapper.text(), /算法教材/)
  assert.match(wrapper.text(), /已完成/)
  await wrapper.get('.btn--yellow').trigger('click')
  assert.match(wrapper.get('[data-test="review-dialog"]').text(), /算法教材/)

  await wrapper.get('[data-test="review-dialog"] button').trigger('click')
  await flushPromises()

  assert.deepEqual(vi.mocked(reviewOrder).mock.calls[0], [
    71,
    {
      rating: 5,
      accurate: true,
      comment: '组件测试评价'
    }
  ])
  assert.equal(vi.mocked(getBoughtOrders).mock.calls.length, 2)
  assert.equal(success.mock.calls.length, 1)
})

test('订单状态筛选会回到第一页，并仅在选择时发送 status', async () => {
  const wrapper = mount(OrdersBoughtPage, { global })
  await flushPromises()

  const completed = wrapper.findAll('.filter-bar button').find((button) => button.text() === '已完成')
  assert.ok(completed)
  await completed.trigger('click')
  await flushPromises()

  assert.deepEqual(vi.mocked(getBoughtOrders).mock.calls.at(-1), [
    {
      page: 1,
      size: 10,
      status: 'COMPLETED'
    }
  ])
})

test('待见面订单经二次确认后调用确认收货并刷新', async () => {
  vi.mocked(getBoughtOrders).mockResolvedValue({ code: 200, message: 'ok', data: { records: [order({ status: 'WAITING_MEET', reviewed: null })], total: 1 } })
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
  vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  const wrapper = mount(OrdersBoughtPage, { global })
  await flushPromises()

  const confirm = wrapper.findAll('button').find((button) => button.text() === '确认收货')
  assert.ok(confirm)
  await confirm.trigger('click')
  await flushPromises()

  assert.deepEqual(vi.mocked(confirmReceipt).mock.calls[0], [71])
  assert.equal(vi.mocked(getBoughtOrders).mock.calls.length, 2)
})

test('待见面订单取消需二次确认，确认后取消并刷新列表', async () => {
  vi.mocked(getBoughtOrders).mockResolvedValue({ code: 200, message: 'ok', data: { records: [order({ status: 'WAITING_MEET', reviewed: null })], total: 1 } })
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
  const success = vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  const wrapper = mount(OrdersBoughtPage, { global })
  await flushPromises()

  const cancel = wrapper.findAll('button').find((button) => button.text() === '取消订单')
  assert.ok(cancel)
  await cancel.trigger('click')
  await flushPromises()

  assert.deepEqual(vi.mocked(cancelOrder).mock.calls[0], [71])
  assert.equal(vi.mocked(getBoughtOrders).mock.calls.length, 2)
  assert.equal(success.mock.calls.length, 1)
})
