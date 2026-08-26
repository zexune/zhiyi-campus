import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, test, vi } from 'vitest'
import { ElMessage } from 'element-plus'

import WalletPage from '@/views/wallet/WalletPage.vue'
import { getWalletBalance, getWalletLogs, rechargeWallet } from '@/api/wallet'
import { ApiError } from '@/utils/request'

vi.mock('@/api/wallet', () => ({
  getWalletBalance: vi.fn(),
  getWalletLogs: vi.fn(),
  rechargeWallet: vi.fn()
}))

const DialogStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<section v-if="modelValue" data-test="recharge-dialog"><slot/><slot name="footer"/><button data-test="dialog-close" @click="$emit(\'update:modelValue\', false)">x</button></section>'
}

const global = {
  stubs: {
    DefaultLayout: { template: '<main><slot /></main>' },
    RouterLink: { template: '<a><slot /></a>' },
    ElDialog: DialogStub,
    ElPagination: {
      props: ['currentPage', 'pageSize', 'total'],
      emits: ['update:current-page', 'current-change'],
      template: '<nav data-test="pagination"><button data-test="goto-page2" @click="$emit(\'update:current-page\', 2); $emit(\'current-change\', 2)">2</button></nav>'
    }
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

  assert.equal(vi.mocked(rechargeWallet).mock.calls[0]?.[0], 10.5)
  // 幂等键为 36 位 UUID（B6：未决充值复用原键恢复结果）
  assert.match(String(vi.mocked(rechargeWallet).mock.calls[0]?.[1]), /^[0-9a-f-]{36}$/i)
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

test('非法金额字符被按键拦截，校验失败不发起充值', async () => {
  const wrapper = mount(WalletPage, { global })
  await flushPromises()

  await wrapper.get('.balance-card__actions .btn--primary').trigger('click')
  const input = wrapper.get('[data-test="recharge-dialog"] input')
  const keydownEvents: KeyboardEvent[] = []
  input.element.addEventListener('keydown', (e) => keydownEvents.push(e as KeyboardEvent))
  await input.trigger('keydown', { key: 'e' })
  await input.trigger('keydown', { key: '5' })
  assert.equal(keydownEvents[0]?.defaultPrevented, true)
  assert.equal(keydownEvents[1]?.defaultPrevented, false)

  await input.setValue('0.001')
  await wrapper.get('[data-test="recharge-dialog"] .btn--primary').trigger('click')
  await flushPromises()
  assert.equal(vi.mocked(rechargeWallet).mock.calls.length, 0)
})

test('结果不明的充值保留幂等键，重开弹窗提示恢复原单据并复用原键', async () => {
  const info = vi.spyOn(ElMessage, 'info').mockImplementation(() => ({}) as never)
  vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  vi.mocked(rechargeWallet).mockRejectedValueOnce(new ApiError('请求超时，请稍后重试', -1, 0, 'RETAIN'))
  const wrapper = mount(WalletPage, { global })
  await flushPromises()

  await wrapper.get('.balance-card__actions .btn--primary').trigger('click')
  await wrapper.get('[data-test="recharge-dialog"] input').setValue('8')
  await wrapper.get('[data-test="recharge-dialog"] .btn--primary').trigger('click')
  await flushPromises()
  assert.equal(vi.mocked(rechargeWallet).mock.calls.length, 1)
  const firstKey = vi.mocked(rechargeWallet).mock.calls[0]?.[1]
  // 结果不明（RETAIN）：未决键保留，供重试复用
  assert.equal(Object.keys(localStorage).filter((key) => key.startsWith('idem:')).length, 1)

  // 经弹窗关闭事件收起再打开 → 提示存在未完成充值
  await wrapper.get('[data-test="dialog-close"]').trigger('click')
  assert.equal(wrapper.find('[data-test="recharge-dialog"]').exists(), false)
  await wrapper.get('.balance-card__actions .btn--primary').trigger('click')
  assert.equal(info.mock.calls.length, 1)

  // 重试复用原幂等键，服务端按幂等记录复返同一结果
  await wrapper.get('[data-test="recharge-dialog"] input').setValue('8')
  await wrapper.get('[data-test="recharge-dialog"] .btn--primary').trigger('click')
  await flushPromises()
  assert.equal(vi.mocked(rechargeWallet).mock.calls.length, 2)
  assert.equal(vi.mocked(rechargeWallet).mock.calls[1]?.[1], firstKey)
})

test('明确业务拒绝（CLEAR）清空未决键，下一次充值使用新键', async () => {
  vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  const wrapper = mount(WalletPage, { global })
  await flushPromises()

  await wrapper.get('.balance-card__actions .btn--primary').trigger('click')
  await wrapper.get('[data-test="recharge-dialog"] input').setValue('8')
  vi.mocked(rechargeWallet).mockRejectedValueOnce(new ApiError('参数非法', 400, 400, 'CLEAR'))
  await wrapper.get('[data-test="recharge-dialog"] .btn--primary').trigger('click')
  await flushPromises()
  const rejectedKey = vi.mocked(rechargeWallet).mock.calls[0]?.[1]
  // 明确拒绝无副作用：未决键被清除
  assert.equal(Object.keys(localStorage).filter((key) => key.startsWith('idem:')).length, 0)

  await wrapper.get('[data-test="recharge-dialog"] input').setValue('8')
  await wrapper.get('[data-test="recharge-dialog"] .btn--primary').trigger('click')
  await flushPromises()
  assert.equal(vi.mocked(rechargeWallet).mock.calls[1]?.[1] !== rejectedKey, true)
})

test('充值请求在途时重复提交被同步互斥拦截', async () => {
  vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  let resolveRecharge!: (value: { code: number; message: string; data: { balance: number } }) => void
  vi.mocked(rechargeWallet).mockImplementationOnce(() => new Promise((resolve) => (resolveRecharge = resolve)) as never)
  const wrapper = mount(WalletPage, { global })
  await flushPromises()

  await wrapper.get('.balance-card__actions .btn--primary').trigger('click')
  await wrapper.get('[data-test="recharge-dialog"] input').setValue('6')
  await wrapper.get('[data-test="recharge-dialog"] .btn--primary').trigger('click')
  // 推进到 rechargeWallet 挂起（recharging 保持 true），此时按钮已被禁用
  await flushPromises()
  assert.equal(vi.mocked(rechargeWallet).mock.calls.length, 1)
  // VTU 不会向禁用元素派发事件，直接再次调用提交函数验证同步互斥
  await (wrapper.vm as unknown as { handleRecharge: () => Promise<void> }).handleRecharge()
  resolveRecharge({ code: 200, message: 'ok', data: { balance: 31 } })
  await flushPromises()

  assert.equal(vi.mocked(rechargeWallet).mock.calls.length, 1)
})

test('流水类型徽标与收支方向：支出不加号、退款加号、未知类型回退原值', async () => {
  vi.mocked(getWalletLogs).mockResolvedValue({
    code: 200,
    message: 'ok',
    data: {
      records: [
        { id: 1, type: 'PAYMENT', amount: 12, balanceAfter: 13, remark: '', createdAt: '2026-08-13T12:30:00' },
        { id: 2, type: 'REFUND', amount: 12, balanceAfter: 25, remark: '交易取消退款', createdAt: '2026-08-13T12:40:00' },
        { id: 3, type: 'MYSTERY', amount: 1, balanceAfter: 26, remark: '未知类型', createdAt: '2026-08-13T12:50:00' }
      ],
      total: 3
    }
  })
  const wrapper = mount(WalletPage, { global })
  await flushPromises()

  const text = wrapper.text()
  assert.match(text, /支出/)
  assert.match(text, /退款/)
  assert.match(text, /MYSTERY/)
  assert.match(text, /—/)
  const amounts = wrapper.findAll('.log-amount')
  assert.equal(amounts[0]?.classes().includes('is-income'), false)
  assert.equal(amounts[1]?.classes().includes('is-income'), true)
  assert.match(amounts[0]?.text() ?? '', /^¥/)
  const badges = wrapper.findAll('.log-type-badge')
  assert.equal(badges[2]?.classes().includes('badge--muted'), true)
})

test('刷新流水按钮与分页跳页都会按当前页码重新拉取', async () => {
  vi.mocked(getWalletLogs).mockResolvedValue({
    code: 200,
    message: 'ok',
    data: { records: [{ id: 1, type: 'RECHARGE', amount: 25, balanceAfter: 25, remark: '', createdAt: '2026-08-13T12:30:00' }], total: 30 }
  })
  const wrapper = mount(WalletPage, { global })
  await flushPromises()
  assert.equal(vi.mocked(getWalletLogs).mock.calls.length, 1)

  await wrapper.get('[aria-label="刷新流水"]').trigger('click')
  await flushPromises()
  assert.equal(vi.mocked(getWalletLogs).mock.calls.length, 2)

  await wrapper.get('[data-test="goto-page2"]').trigger('click')
  await flushPromises()
  assert.deepEqual(vi.mocked(getWalletLogs).mock.calls.at(-1), [{ page: 2, size: 10 }])
})
