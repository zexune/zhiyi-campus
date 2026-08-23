import { mount } from '@vue/test-utils'
import { test } from 'vitest'
import assert from 'node:assert/strict'

import TagInput from '@/components/common/TagInput.vue'

function mountInput(value: string[] = [], suggestions: string[] = []) {
  return mount(TagInput, { props: { modelValue: value, suggestions } })
}

/** 模拟 v-model：把组件最近一次 emit 回写进 props */
async function applyLast(wrapper: ReturnType<typeof mountInput>) {
  const emitted = wrapper.emitted('update:modelValue')
  if (emitted?.length) await wrapper.setProps({ modelValue: emitted.at(-1) as string[] })
}

function lastEmitted(wrapper: ReturnType<typeof mountInput>): string[][] {
  const events = wrapper.emitted('update:modelValue')
  return (events?.at(-1) ?? [[]]) as string[][]
}

test('回车提交自定义标签并去重（忽略大小写）', async () => {
  const wrapper = mountInput(['95新'])
  const input = wrapper.find('input.tag-input__field')
  await input.setValue('95新')
  await input.trigger('keydown.enter')
  // 重复（忽略大小写）不再添加
  assert.equal(wrapper.emitted('update:modelValue'), undefined)

  await input.setValue('可小刀')
  await input.trigger('keydown.enter')
  assert.deepEqual(lastEmitted(wrapper), [['95新', '可小刀']])
})

test('点击建议一键选用，已选建议不再展示', async () => {
  const wrapper = mountInput(['教材'], ['教材', '考研'])
  // "教材" 已选：候选只剩 "考研"
  const buttons = wrapper.findAll('.tag-input__suggestion')
  assert.equal(buttons.length, 1)
  assert.equal(buttons[0].text(), '考研')

  await buttons[0].trigger('click')
  assert.deepEqual(lastEmitted(wrapper), [['教材', '考研']])
})

test('达到上限后禁用输入且不再展示建议', async () => {
  const wrapper = mountInput(['标签一', '标签二'], ['标签三'])
  await wrapper.setProps({ modelValue: ['标签一', '标签二'], max: 2 })

  assert.equal(wrapper.find('input.tag-input__field').attributes('disabled'), '')
  assert.equal(wrapper.findAll('.tag-input__suggestion').length, 0)
  assert.ok(wrapper.text().includes('标签已达数量上限'))
})

test('删除按钮移除指定标签；空输入退格删除末位', async () => {
  const wrapper = mountInput(['甲', '乙'])
  await wrapper.find('.tag-input__remove').trigger('click')
  assert.deepEqual(lastEmitted(wrapper), [['乙']])
  await applyLast(wrapper)
  // 移除后只剩一个 chip（乙）
  assert.equal(wrapper.findAll('.tag-input__remove').length, 1)

  const input = wrapper.find('input.tag-input__field')
  await input.trigger('keydown.backspace')
  assert.deepEqual(lastEmitted(wrapper), [[]])
})

test('过短输入被忽略，超长由 maxlength 截断', async () => {
  const wrapper = mountInput([], [])
  const input = wrapper.find('input.tag-input__field')
  await input.setValue('a')
  await input.trigger('keydown.enter')
  assert.equal(wrapper.emitted('update:modelValue'), undefined)
  assert.equal(input.attributes('maxlength'), '12')
})
