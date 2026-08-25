import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, test, vi } from 'vitest'
import { ElMessage } from 'element-plus'

import ReportDialog from '@/views/item/components/ReportDialog.vue'
import { reportItem } from '@/api/item'

vi.mock('@/api/item', () => ({
  reportItem: vi.fn()
}))

const DialogStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<section v-if="modelValue" data-test="report-dialog"><slot /><slot name="footer" /><button data-test="dialog-close" @click="$emit(\'update:modelValue\', false)">x</button></section>'
}

const InputStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<textarea data-test="report-details" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
}

const SelectStub = {
  props: ['modelValue', 'options'],
  emits: ['update:modelValue'],
  template:
    '<select data-test="report-type" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><option v-for="o in options" :key="o.value" :value="o.value">{{ o.label }}</option></select>'
}

const global = {
  stubs: {
    ElDialog: DialogStub,
    ElInput: InputStub,
    AppSelect: SelectStub
  }
}

function mountDialog(visible = true) {
  return mount(ReportDialog, {
    global,
    props: { itemId: 7, visible }
  })
}

beforeEach(() => {
  vi.mocked(reportItem).mockReset()
  vi.mocked(reportItem).mockResolvedValue({ code: 200, message: 'ok', data: undefined })
  vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({}) as never)
})

test('默认类型可直接提交：参数正确、成功提示并自行关闭', async () => {
  const wrapper = mountDialog()
  await wrapper.get('[data-test="report-dialog"] .btn--danger').trigger('click')
  await flushPromises()

  assert.deepEqual(vi.mocked(reportItem).mock.calls, [[7, { type: 'PRICE_FRAUD', details: null }]])
  assert.equal(vi.mocked(ElMessage.success).mock.calls.length, 1)
  assert.equal(wrapper.emitted('update:visible')?.at(-1)?.[0], false)
})

test('其他类型缺补充说明被拦截，填写后按文本提交', async () => {
  const wrapper = mountDialog()
  await wrapper.get('[data-test="report-type"]').setValue('OTHER')
  await wrapper.get('[data-test="report-dialog"] .btn--danger').trigger('click')
  await flushPromises()
  assert.equal(vi.mocked(reportItem).mock.calls.length, 0)
  assert.equal(vi.mocked(ElMessage.warning).mock.calls.length, 1)

  await wrapper.get('[data-test="report-details"]').setValue(' 展示与实物不符 ')
  await wrapper.get('[data-test="report-dialog"] .btn--danger').trigger('click')
  await flushPromises()
  assert.deepEqual(vi.mocked(reportItem).mock.calls, [[7, { type: 'OTHER', details: '展示与实物不符' }]])
})

test('每次打开重置表单，取消与弹窗关闭事件不发起举报', async () => {
  const wrapper = mountDialog()
  await wrapper.get('[data-test="report-type"]').setValue('ADVERTISING')
  await wrapper.get('[data-test="dialog-close"]').trigger('click')
  assert.equal(wrapper.emitted('update:visible')?.at(-1)?.[0], false)
  assert.equal(vi.mocked(reportItem).mock.calls.length, 0)

  // 关闭后重开：类型回到默认值
  await wrapper.setProps({ visible: false })
  await wrapper.setProps({ visible: true })
  await wrapper.get('[data-test="report-dialog"] .btn--danger').trigger('click')
  await flushPromises()
  assert.deepEqual(vi.mocked(reportItem).mock.calls.at(-1), [7, { type: 'PRICE_FRAUD', details: null }])

  // 取消按钮：只关闭，不提交
  await wrapper.setProps({ visible: false })
  await wrapper.setProps({ visible: true })
  await wrapper.get('[data-test="report-dialog"] .btn:not(.btn--danger)').trigger('click')
  assert.equal(wrapper.emitted('update:visible')?.at(-1)?.[0], false)
  assert.equal(vi.mocked(reportItem).mock.calls.length, 1)
})
