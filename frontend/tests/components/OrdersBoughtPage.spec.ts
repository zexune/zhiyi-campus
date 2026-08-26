import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, test, vi } from 'vitest'
import { ElMessage, ElMessageBox } from 'element-plus'

import OrdersBoughtPage from '@/views/wallet/OrdersBoughtPage.vue'
import { cancelOrder, confirmReceipt, getBoughtOrders, reviewOrder } from '@/api/order'
import { ApiError } from '@/utils/request'
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
    ElPagination: {
      props: ['currentPage', 'pageSize', 'total'],
      emits: ['update:current-page', 'current-change'],
      template: '<nav data-test="pagination"><button data-test="goto-page2" @click="$emit(\'update:current-page\', 2); $emit(\'current-change\', 2)">2</button></nav>'
    },
    OrderReviewDialog: ReviewStub
  }
}

function order(overrides = {}) {
  return {
    id: 71,
    itemId: 17,
    buyerId: 1,
    sellerId: 2,
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
  vi.mocked(reviewOrder).mockResolvedValue({ code: 200, message: 'ok', data: null })
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

  assert.equal(vi.mocked(confirmReceipt).mock.calls[0]?.[0], 71)
  // 幂等键为 36 位 UUID（B6：结果不明时复用原键重试）
  assert.match(String(vi.mocked(confirmReceipt).mock.calls[0]?.[1]), /^[0-9a-f-]{36}$/i)
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

  assert.equal(vi.mocked(cancelOrder).mock.calls[0]?.[0], 71)
  assert.match(String(vi.mocked(cancelOrder).mock.calls[0]?.[1]), /^[0-9a-f-]{36}$/i)
  assert.equal(vi.mocked(getBoughtOrders).mock.calls.length, 2)
  assert.equal(success.mock.calls.length, 1)
})

test('加载失败展示错误态，重新加载按钮重试后恢复列表', async () => {
  vi.mocked(getBoughtOrders).mockRejectedValueOnce(new Error('network'))
  const wrapper = mount(OrdersBoughtPage, { global })
  await flushPromises()

  assert.match(wrapper.text(), /订单加载失败/)
  await wrapper.get('.state-card .btn').trigger('click')
  await flushPromises()

  assert.doesNotMatch(wrapper.text(), /订单加载失败/)
  assert.equal(vi.mocked(getBoughtOrders).mock.calls.length, 2)
})

test('已评价、已取消、有封面与空卖家昵称的订单都能渲染兜底形态', async () => {
  vi.mocked(getBoughtOrders).mockResolvedValue({
    code: 200,
    message: 'ok',
    data: {
      records: [
        order({ id: 1, status: 'COMPLETED', reviewed: true, itemCover: '/uploads/items/a.png' }),
        order({ id: 2, status: 'CANCELLED', reviewed: false, cancelledAt: '2026-08-13T12:00:00', peerNickname: '' })
      ],
      total: 2
    }
  })
  const wrapper = mount(OrdersBoughtPage, { global })
  await flushPromises()

  const text = wrapper.text()
  assert.match(text, /已评价/)
  assert.match(text, /取消/)
  assert.match(text, /卖家：—/)
  assert.equal(wrapper.findAll('img').length, 1)
})

test('多页订单通过分页控件跳页拉取', async () => {
  vi.mocked(getBoughtOrders).mockResolvedValue({ code: 200, message: 'ok', data: { records: [order()], total: 30 } })
  const wrapper = mount(OrdersBoughtPage, { global })
  await flushPromises()

  await wrapper.get('[data-test="goto-page2"]').trigger('click')
  await flushPromises()

  assert.deepEqual(vi.mocked(getBoughtOrders).mock.calls.at(-1), [{ page: 2, size: 10 }])
})

test('确认收货在二次确认弹窗点取消时不发起请求也不刷新', async () => {
  vi.mocked(getBoughtOrders).mockResolvedValue({ code: 200, message: 'ok', data: { records: [order({ status: 'WAITING_MEET', reviewed: null })], total: 1 } })
  vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel' as never)
  const wrapper = mount(OrdersBoughtPage, { global })
  await flushPromises()

  const confirm = wrapper.findAll('button').find((button) => button.text() === '确认收货')
  assert.ok(confirm)
  await confirm.trigger('click')
  await flushPromises()

  assert.equal(vi.mocked(confirmReceipt).mock.calls.length, 0)
  assert.equal(vi.mocked(getBoughtOrders).mock.calls.length, 1)
})

test('取消订单在二次确认弹窗点取消时不发起请求', async () => {
  vi.mocked(getBoughtOrders).mockResolvedValue({ code: 200, message: 'ok', data: { records: [order({ status: 'WAITING_MEET', reviewed: null })], total: 1 } })
  vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel' as never)
  const wrapper = mount(OrdersBoughtPage, { global })
  await flushPromises()

  const cancel = wrapper.findAll('button').find((button) => button.text() === '取消订单')
  assert.ok(cancel)
  await cancel.trigger('click')
  await flushPromises()

  assert.equal(vi.mocked(cancelOrder).mock.calls.length, 0)
  assert.equal(vi.mocked(getBoughtOrders).mock.calls.length, 1)
})

test('确认收货被明确拒绝（CLEAR）时清除未决键并刷新列表', async () => {
  vi.mocked(getBoughtOrders).mockResolvedValue({ code: 200, message: 'ok', data: { records: [order({ status: 'WAITING_MEET', reviewed: null })], total: 1 } })
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
  vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  vi.mocked(confirmReceipt).mockRejectedValueOnce(new ApiError('订单状态异常', 3002, 409, 'CLEAR'))
  const wrapper = mount(OrdersBoughtPage, { global })
  await flushPromises()

  const confirm = wrapper.findAll('button').find((button) => button.text() === '确认收货')
  assert.ok(confirm)
  await confirm.trigger('click')
  await flushPromises()

  assert.equal(vi.mocked(confirmReceipt).mock.calls.length, 1)
  assert.equal(Object.keys(localStorage).filter((key) => key.startsWith('idem:')).length, 0)
  assert.equal(vi.mocked(getBoughtOrders).mock.calls.length, 2)
})

test('确认收货结果不明（RETAIN）时保留未决键，重试复用原键', async () => {
  vi.mocked(getBoughtOrders).mockResolvedValue({ code: 200, message: 'ok', data: { records: [order({ status: 'WAITING_MEET', reviewed: null })], total: 1 } })
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
  vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  vi.mocked(confirmReceipt)
    .mockRejectedValueOnce(new ApiError('请求超时，请稍后重试', -1, 0, 'RETAIN'))
    .mockResolvedValueOnce({ code: 200, message: 'ok', data: undefined as unknown as Order })
  const wrapper = mount(OrdersBoughtPage, { global })
  await flushPromises()

  // 每次点击前重新查找按钮：刷新流程会经 loading 态销毁并重建列表 DOM
  const findConfirm = () => {
    const confirm = wrapper.findAll('button').find((button) => button.text() === '确认收货')
    assert.ok(confirm)
    return confirm
  }
  await findConfirm().trigger('click')
  await flushPromises()
  const firstKey = vi.mocked(confirmReceipt).mock.calls[0]?.[1]
  assert.equal(Object.keys(localStorage).filter((key) => key.startsWith('idem:')).length, 1)

  await findConfirm().trigger('click')
  await flushPromises()
  assert.equal(vi.mocked(confirmReceipt).mock.calls.length, 2)
  assert.equal(vi.mocked(confirmReceipt).mock.calls[1]?.[1], firstKey)
})

test('取消订单被明确拒绝（CLEAR）时清除未决键', async () => {
  vi.mocked(getBoughtOrders).mockResolvedValue({ code: 200, message: 'ok', data: { records: [order({ status: 'WAITING_MEET', reviewed: null })], total: 1 } })
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
  vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  vi.mocked(cancelOrder).mockRejectedValueOnce(new ApiError('订单状态异常', 3002, 409, 'CLEAR'))
  const wrapper = mount(OrdersBoughtPage, { global })
  await flushPromises()

  const cancel = wrapper.findAll('button').find((button) => button.text() === '取消订单')
  assert.ok(cancel)
  await cancel.trigger('click')
  await flushPromises()

  assert.equal(vi.mocked(cancelOrder).mock.calls.length, 1)
  assert.equal(Object.keys(localStorage).filter((key) => key.startsWith('idem:')).length, 0)
})

test('取消订单结果不明（RETAIN）时保留未决键', async () => {
  vi.mocked(getBoughtOrders).mockResolvedValue({ code: 200, message: 'ok', data: { records: [order({ status: 'WAITING_MEET', reviewed: null })], total: 1 } })
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
  vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  vi.mocked(cancelOrder).mockRejectedValueOnce(new Error('network'))
  const wrapper = mount(OrdersBoughtPage, { global })
  await flushPromises()

  const cancel = wrapper.findAll('button').find((button) => button.text() === '取消订单')
  assert.ok(cancel)
  await cancel.trigger('click')
  await flushPromises()

  assert.equal(vi.mocked(cancelOrder).mock.calls.length, 1)
  assert.equal(Object.keys(localStorage).filter((key) => key.startsWith('idem:')).length, 1)
})
