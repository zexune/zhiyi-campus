import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, test, vi } from 'vitest'

import ViolationsPage from '@/views/admin/ViolationsPage.vue'
import { getAppeals, getViolations } from '@/api/admin'
import type { ApiResult } from '@/utils/request'
import type { PageResult, ViolationAppeal, ViolationReview } from '@/types/models'

vi.mock('@/api/admin', () => ({
  getViolations: vi.fn(),
  getAppeals: vi.fn(),
  confirmViolation: vi.fn(),
  dismissViolation: vi.fn(),
  approveAppeal: vi.fn(),
  rejectAppeal: vi.fn()
}))

const global = {
  stubs: {
    AdminLayout: { template: '<main><slot /></main>' },
    ElSkeleton: { template: '<div data-test="skeleton" />' },
    ElDialog: {
      props: ['modelValue'],
      template: '<div v-if="modelValue" data-test="dialog"><slot /><slot name="footer" /></div>'
    },
    ElPagination: {
      props: ['currentPage', 'pageSize', 'total'],
      emits: ['update:current-page', 'current-change'],
      template: '<nav v-if="total > pageSize" data-test="pagination"><button data-test="goto-page2" @click="$emit(\'update:current-page\', 2); $emit(\'current-change\', 2)">2</button></nav>'
    }
  }
}

type ReviewResult = ApiResult<PageResult<ViolationReview>>

function review(overrides = {}) {
  return {
    id: 1,
    source: 'LOCAL_RULE',
    status: 'PENDING',
    originalTitle: '待审核教材',
    originalDescription: '页面展示用描述',
    violationReason: '命中违禁词',
    matchedRules: ['违禁品'],
    sellerName: '卖家甲',
    userId: 11,
    createdAt: '2026-08-13T10:00:00',
    ...overrides
  } as ViolationReview
}

function appeal(overrides = {}) {
  return {
    id: 5,
    status: 'PENDING',
    itemTitle: '申诉中的自行车',
    itemId: 9,
    sellerName: '卖家乙',
    userId: 12,
    createdAt: '2026-08-13T09:00:00',
    violationReason: '举报说明',
    reason: '我是冤枉的',
    ...overrides
  } as ViolationAppeal
}

/** 手动放行的 Promise：模拟可乱序返回的在途请求 */
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => (resolve = res))
  return { promise, resolve }
}

beforeEach(() => {
  vi.mocked(getViolations).mockResolvedValue({ code: 200, message: 'ok', data: { records: [review()], total: 1 } })
  vi.mocked(getAppeals).mockResolvedValue({ code: 200, message: 'ok', data: { records: [appeal()], total: 1 } })
})

test('挂载只拉取审核列表与角标计数，申诉工作区首次打开才加载', async () => {
  const wrapper = mount(ViolationsPage, { global })
  await flushPromises()

  assert.match(wrapper.text(), /待审核教材/)
  // 首屏请求 = 审核列表（page/size=10）+ 两路角标计数（page:1 size:1 只取 total）
  assert.deepEqual(vi.mocked(getViolations).mock.calls[0], [{ page: 1, size: 10, status: 'PENDING' }])
  assert.deepEqual(vi.mocked(getAppeals).mock.calls, [[{ page: 1, size: 1, status: 'PENDING' }]])

  const appealsTab = wrapper.findAll('.workspace-tab').find((button) => button.text().includes('申诉复核'))
  assert.ok(appealsTab)
  await appealsTab.trigger('click')
  await flushPromises()

  assert.match(wrapper.text(), /申诉中的自行车/)
  assert.deepEqual(vi.mocked(getAppeals).mock.calls.at(-1), [{ page: 1, size: 10, status: 'PENDING' }])
})

test('筛选 A 未返回时切到筛选 B，A 后到被丢弃：列表内容与 loading 都归属 B', async () => {
  const slowPending = deferred<ReviewResult>()
  const slowConfirmed = deferred<ReviewResult>()
  vi.mocked(getViolations)
    // #1 初始待审核列表（挂起）→ #2 角标计数 → #3 切换后的已确认列表（挂起）
    .mockReturnValueOnce(slowPending.promise)
    .mockResolvedValueOnce({ code: 200, message: 'ok', data: { records: [], total: 0 } })
    .mockReturnValueOnce(slowConfirmed.promise)

  const wrapper = mount(ViolationsPage, { global })
  await flushPromises()
  // A（待审核）在途：骨架屏属于 A
  assert.ok(wrapper.find('[data-test="skeleton"]').exists())

  const confirmedTab = wrapper.findAll('.filter-tabs button').find((button) => button.text() === '已确认违规')
  assert.ok(confirmedTab)
  await confirmedTab.trigger('click')
  await flushPromises()
  assert.deepEqual(vi.mocked(getViolations).mock.calls.at(-1), [{ page: 1, size: 10, status: 'CONFIRMED' }])
  // B 在途：loading 仍归属 B，未被 A 相关流程提前复位
  assert.ok(wrapper.find('[data-test="skeleton"]').exists())

  // B 先返回：展示 B 的内容并结束加载
  slowConfirmed.resolve({ code: 200, message: 'ok', data: { records: [review({ id: 2, status: 'CONFIRMED', originalTitle: '已确认的违规商品' })], total: 1 } })
  await flushPromises()
  assert.match(wrapper.text(), /已确认的违规商品/)
  assert.ok(!wrapper.find('[data-test="skeleton"]').exists())

  // A 后到：乱序旧响应必须被守卫丢弃——不覆盖 B 的结果，也不重启 loading
  slowPending.resolve({ code: 200, message: 'ok', data: { records: [review({ id: 3, originalTitle: '迟到的旧筛选结果' })], total: 1 } })
  await flushPromises()
  assert.doesNotMatch(wrapper.text(), /迟到的旧筛选结果/)
  assert.match(wrapper.text(), /已确认的违规商品/)
  assert.ok(!wrapper.find('[data-test="skeleton"]').exists())
})

test('审核列表在途时切到申诉工作区，两路 loading 隔离互不复位', async () => {
  const slowReviews = deferred<ReviewResult>()
  vi.mocked(getViolations)
    // #1 初始审核列表（挂起）→ #2 角标计数
    .mockReturnValueOnce(slowReviews.promise)
    .mockResolvedValueOnce({ code: 200, message: 'ok', data: { records: [], total: 0 } })

  const wrapper = mount(ViolationsPage, { global })
  await flushPromises()
  assert.ok(wrapper.find('[data-test="skeleton"]').exists())

  const appealsTab = wrapper.findAll('.workspace-tab').find((button) => button.text().includes('申诉复核'))
  assert.ok(appealsTab)
  await appealsTab.trigger('click')
  await flushPromises()

  // 申诉列表已返回：当前工作区加载完成，内容正常展示
  assert.match(wrapper.text(), /申诉中的自行车/)
  assert.ok(!wrapper.find('[data-test="skeleton"]').exists())

  // 审核路的迟到响应落地：只更新它自己的 records，不影响申诉工作区的展示与加载态
  slowReviews.resolve({ code: 200, message: 'ok', data: { records: [review({ id: 8, originalTitle: '迟到的审核结果' })], total: 1 } })
  await flushPromises()
  assert.ok(!wrapper.find('[data-test="skeleton"]').exists())
  assert.match(wrapper.text(), /申诉中的自行车/)
  assert.doesNotMatch(wrapper.text(), /迟到的审核结果/)
})

test('多页审核记录通过分页控件跳页拉取', async () => {
  vi.mocked(getViolations).mockResolvedValue({ code: 200, message: 'ok', data: { records: [review()], total: 30 } })
  const wrapper = mount(ViolationsPage, { global })
  await flushPromises()

  await wrapper.get('[data-test="goto-page2"]').trigger('click')
  await flushPromises()

  assert.deepEqual(vi.mocked(getViolations).mock.calls.at(-1), [{ page: 2, size: 10, status: 'PENDING' }])
})
