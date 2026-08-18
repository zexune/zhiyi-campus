import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, test, vi } from 'vitest'
import { ElMessage } from 'element-plus'

import WalletPage from '@/views/wallet/WalletPage.vue'
import { getWalletBalance, getWalletLogs, rechargeWallet } from '@/api/wallet'

vi.mock('@/api/wallet', () => ({
  getWalletBalance: vi.fn(),
  getWalletLogs: vi.fn(),
  rechargeWallet: vi.fn()
}))

const DialogStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<section v-if="modelValue" data-test="recharge-dialog"><slot/><slot name="footer"/></section>'
}

const global = {
  stubs: {
    DefaultLayout: { template: '<main><slot /></main>' },
    RouterLink: { template: '<a><slot /></a>' },
    ElDialog: DialogStub,
    ElPagination: { template: '<nav data-test="pagination" />' }
  }
}

beforeEach(() => {
  vi.mocked(getWalletBalance).mockResolvedValue({ code: 200, message: 'ok', data: { balance: 25 } })
  vi.mocked(getWalletLogs).mockResolvedValue({
    code: 200,
    message: 'ok',
    data: {
      records: [
        {
          id: 1,
          type: 'RECHARGE',
          amount: 25,
          balanceAfter: 25,
          remark: '首次充值',
          createdAt: '2026-08-13T12:30:00'
        }
      ],
      total: 1
    }
  })
  vi.mocked(rechargeWallet).mockResolvedValue({ code: 200, message: 'ok', data: { balance: 35.5 } })
})

test('钱包页并行加载余额和流水，并在充值后刷新余额及第一页流水', async () => {
  const success = vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  const wrapper = mount(WalletPage, { global })
  await flushPromises()

  assert.match(wrapper.text(), /25\.00/)
  assert.match(wrapper.text(), /首次充值/)
  assert.equal(vi.mocked(getWalletBalance).mock.calls.length, 1)
  assert.deepEqual(vi.mocked(getWalletLogs).mock.calls[0], [{ page: 1, size: 10 }])

  await wrapper.get('.balance-card__actions .btn--primary').trigger('click')
  await wrapper.get('[data-test="recharge-dialog"] input').setValue('10.50')
  await wrapper.get('[data-test="recharge-dialog"] .btn--primary').trigger('click')
  await flushPromises()

  assert.deepEqual(vi.mocked(rechargeWallet).mock.calls[0], [10.5])
  assert.match(wrapper.text(), /35\.50/)
  assert.equal(vi.mocked(getWalletLogs).mock.calls.length, 2)
  assert.equal(success.mock.calls.length, 1)
})

test('余额和流水失败分别展示可重试状态，重试后恢复内容', async () => {
  vi.mocked(getWalletBalance).mockRejectedValueOnce(new Error('balance unavailable'))
  vi.mocked(getWalletLogs).mockRejectedValueOnce(new Error('logs unavailable'))
  const wrapper = mount(WalletPage, { global })
  await flushPromises()

  assert.match(wrapper.text(), /余额加载失败/)
  assert.match(wrapper.text(), /流水加载失败/)

  const retryButtons = wrapper.findAll('button').filter((button) => button.text() === '重新加载')
  assert.equal(retryButtons.length, 2)
  await Promise.all(retryButtons.map((button) => button.trigger('click')))
  await flushPromises()

  assert.doesNotMatch(wrapper.text(), /余额加载失败/)
  assert.doesNotMatch(wrapper.text(), /流水加载失败/)
  assert.match(wrapper.text(), /25\.00/)
})
