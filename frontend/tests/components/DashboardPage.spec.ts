import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, test, vi } from 'vitest'

import DashboardPage from '@/views/admin/DashboardPage.vue'
import { getDashboard, getSchools, getTradeHeatmap } from '@/api/admin'

vi.mock('@/api/admin', () => ({
  getDashboard: vi.fn(),
  getSchools: vi.fn(),
  getTradeHeatmap: vi.fn()
}))

const global = {
  stubs: {
    AdminLayout: { template: '<main><slot /></main>' },
    RouterLink: { template: '<a><slot /></a>' }
  }
}

function dashboard(overrides = {}) {
  return {
    totalUsers: 12,
    onSaleItems: 7,
    todayTradeAmount: '39.80',
    pendingViolations: 1,
    recentViolations: [
      {
        id: 1,
        source: 'LOCAL_RULE',
        status: 'PENDING',
        reporterName: '张同学',
        originalTitle: '待复核教材',
        violationType: '虚假信息',
        violationReason: '描述需人工核验',
        createdAt: '2026-08-13T10:00:00'
      }
    ],
    trend: [{ date: '2026-08-13', count: 2, totalAmount: '39.80' }],
    ...overrides
  }
}

beforeEach(() => {
  vi.mocked(getSchools).mockResolvedValue({ code: 200, message: 'ok', data: [{ id: 1, name: '上海大学' }] })
  vi.mocked(getDashboard).mockResolvedValue({ code: 200, message: 'ok', data: dashboard() })
  vi.mocked(getTradeHeatmap).mockResolvedValue({ code: 200, message: 'ok', data: [{ location: '图书馆', count: 2 }] })
})

test('后台大盘同时呈现统计、趋势、热力和最近治理记录', async () => {
  const wrapper = mount(DashboardPage, { global })
  await flushPromises()

  assert.match(wrapper.text(), /用户总数/)
  assert.match(wrapper.text(), /12/)
  assert.match(wrapper.text(), /¥39\.80/)
  assert.match(wrapper.text(), /图书馆/)
  assert.match(wrapper.text(), /待复核教材/)
  assert.deepEqual(vi.mocked(getSchools).mock.calls[0], [{ status: 'ACTIVE' }])
  assert.deepEqual(vi.mocked(getDashboard).mock.calls[0], [null])
  assert.deepEqual(vi.mocked(getTradeHeatmap).mock.calls[0], [null])
})

test('切换学校后用同一 schoolId 重新查询大盘和热力图', async () => {
  const wrapper = mount(DashboardPage, { global })
  await flushPromises()

  const schoolButton = wrapper.findAll('.school-chip').find((button) => button.text().includes('上海大学'))
  assert.ok(schoolButton)
  await schoolButton.trigger('click')
  await flushPromises()

  assert.deepEqual(vi.mocked(getDashboard).mock.calls.at(-1), [1])
  assert.deepEqual(vi.mocked(getTradeHeatmap).mock.calls.at(-1), [1])
  assert.ok(schoolButton.classes().includes('active'))
})

test('大盘失败展示独立重试入口，不被学校和热力图失败吞掉', async () => {
  vi.mocked(getDashboard).mockRejectedValueOnce(new Error('dashboard unavailable'))
  vi.mocked(getSchools).mockRejectedValueOnce(new Error('schools unavailable'))
  vi.mocked(getTradeHeatmap).mockRejectedValueOnce(new Error('heatmap unavailable'))
  const wrapper = mount(DashboardPage, { global })
  await flushPromises()

  assert.match(wrapper.text(), /数据加载失败/)
  await wrapper.get('button').trigger('click')
  await flushPromises()

  assert.doesNotMatch(wrapper.text(), /数据加载失败/)
  assert.match(wrapper.text(), /用户总数/)
})
