import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, test, vi } from 'vitest'
import { ElMessage } from 'element-plus'

import MyItemsPage from '@/views/user/MyItemsPage.vue'
import { getMyItems, relistItem, submitItemAppeal } from '@/api/item'

vi.mock('@/api/item', () => ({
  getMyItems: vi.fn(),
  deleteItem: vi.fn(),
  offShelfItem: vi.fn(),
  relistItem: vi.fn(),
  submitItemAppeal: vi.fn()
}))

const DialogStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<section v-if="modelValue" data-test="appeal-dialog"><slot/><slot name="footer"/></section>'
}

const global = {
  stubs: {
    DefaultLayout: { template: '<main><slot /></main>' },
    RouterLink: { template: '<a><slot /></a>' },
    ElDialog: DialogStub,
    ElPagination: { props: ['currentPage', 'pageSize', 'total'], template: '<nav data-test="pagination" />' }
  }
}

const APPEAL_REASON = '申诉理由：商品描述并无违规内容，请求管理员复核并撤销扣分。'

function pageOf(records: object[]) {
  return { code: 200, message: 'ok', data: { records, total: records.length } }
}

/** 可申诉行：违规确认后（REJECTED）appealable=true，模板渲染"申诉"按钮 */
function appealableRow() {
  return {
    id: 5,
    title: '被误判的二手教材',
    type: 'SELL',
    status: 'OFF_SHELF',
    moderationStatus: 'REJECTED',
    reserved: false,
    appealable: true,
    price: 12.5,
    coverImage: null,
    images: [],
    viewCount: 3,
    createdAt: '2026-08-20T09:00:00'
  }
}

/** 申诉提交后刷新得到的行：appealStatus=PENDING，appealable=false */
function pendingAppealRow() {
  return { ...appealableRow(), appealable: false, appealStatus: 'PENDING' }
}

beforeEach(() => {
  vi.mocked(relistItem).mockReset()
  vi.mocked(submitItemAppeal).mockReset()
})

test('申诉弹窗提交非空 AppealVO 后关闭弹窗、提示成功并刷新列表为"申诉审核中"', async () => {
  const success = vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  vi.mocked(getMyItems)
    .mockResolvedValueOnce(pageOf([appealableRow()]) as never)
    .mockResolvedValue(pageOf([pendingAppealRow()]) as never)
  // 申诉接口回传完整 AppealVO（生成契约必填 data），页面不得再把它当 void 丢弃
  vi.mocked(submitItemAppeal).mockResolvedValue({
    code: 200,
    message: '申诉已提交',
    data: {
      id: 31,
      reportId: 9,
      itemId: 5,
      userId: 7,
      itemTitle: '被误判的二手教材',
      reason: APPEAL_REASON,
      status: 'PENDING',
      createdAt: '2026-08-26T10:00:00'
    }
  })

  const wrapper = mount(MyItemsPage, { global })
  await flushPromises()

  assert.equal(vi.mocked(getMyItems).mock.calls.length, 1)
  assert.ok(wrapper.text().includes('已确认内容违规'), '违规行应展示可申诉提示')
  const appealButton = wrapper.findAll('button').find((button) => button.text() === '申诉')
  assert.ok(appealButton, 'appealable 行必须渲染"申诉"按钮')

  await appealButton!.trigger('click')
  assert.ok(wrapper.find('[data-test="appeal-dialog"]').exists(), '点击申诉应打开弹窗')

  await wrapper.get('[data-test="appeal-dialog"] textarea').setValue(APPEAL_REASON)
  await wrapper.get('[data-test="appeal-dialog"] .btn--primary').trigger('click')
  await flushPromises()

  assert.deepEqual(vi.mocked(submitItemAppeal).mock.calls[0], [5, { reason: APPEAL_REASON }])
  assert.equal(success.mock.calls.length, 1)
  assert.equal(success.mock.calls[0]?.[0], '申诉已提交，请等待管理员复核')
  assert.equal(wrapper.find('[data-test="appeal-dialog"]').exists(), false, '提交成功后弹窗关闭')
  assert.equal(vi.mocked(getMyItems).mock.calls.length, 2, '提交成功后必须重新拉取列表')
  assert.ok(wrapper.text().includes('申诉审核中'), '刷新后的 PENDING 行展示现有文案"申诉审核中"')
})

test('申诉接口返回 data:null 时按协议违约失败，弹窗保持打开且列表不刷新', async () => {
  vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  vi.mocked(getMyItems).mockResolvedValue(pageOf([appealableRow()]) as never)
  vi.mocked(submitItemAppeal).mockImplementation(() => Promise.reject(new Error('API 协议违约：成功信封缺少 data')))

  const wrapper = mount(MyItemsPage, { global })
  await flushPromises()

  await wrapper
    .findAll('button')
    .find((button) => button.text() === '申诉')!
    .trigger('click')
  await wrapper.get('[data-test="appeal-dialog"] textarea').setValue(APPEAL_REASON)
  await wrapper.get('[data-test="appeal-dialog"] .btn--primary').trigger('click')
  await flushPromises()

  assert.equal(wrapper.find('[data-test="appeal-dialog"]').exists(), true, '失败时弹窗保持打开供重试')
  assert.equal(vi.mocked(getMyItems).mock.calls.length, 1, '失败不触发列表刷新')
})
