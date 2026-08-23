import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, test, vi } from 'vitest'
import { ElMessage, ElMessageBox } from 'element-plus'

import UsersPage from '@/views/admin/UsersPage.vue'
import { banUser, resetUserPassword, searchAdminUsers, unbanUser } from '@/api/admin'
import { getSchools } from '@/api/auth'
import type { AdminUser } from '@/types/models'

vi.mock('@/api/admin', () => ({
  searchAdminUsers: vi.fn(),
  resetUserPassword: vi.fn(),
  banUser: vi.fn(),
  unbanUser: vi.fn()
}))

// 用户管理页经 useSchoolOptions 共享学校下拉数据
vi.mock('@/api/auth', () => ({
  getSchools: vi.fn()
}))

const global = {
  stubs: {
    AdminLayout: { template: '<main><slot /></main>' },
    RouterLink: { template: '<a><slot /></a>' },
    ElPagination: { template: '<nav data-test="pagination" />' }
  }
}

function user(overrides = {}) {
  return {
    id: 31,
    studentId: '2224101',
    nickname: '筛选探针甲',
    role: 'USER',
    status: 'ACTIVE',
    banUntilTime: null,
    schoolId: 1,
    schoolName: '上海大学',
    schoolEmail: 'probe@shu.edu.cn',
    phone: '13800001111',
    levelTitle: '校园新人',
    createdAt: '2026-08-13T10:00:00',
    ...overrides
  } as AdminUser
}

beforeEach(() => {
  vi.mocked(getSchools).mockResolvedValue({
    code: 200,
    message: 'ok',
    data: [
      { id: 1, name: '上海大学' },
      { id: 2, name: '东华大学' }
    ]
  })
  vi.mocked(searchAdminUsers).mockResolvedValue({ code: 200, message: 'ok', data: { records: [user()], total: 1 } })
})

afterEach(() => {
  // 封禁弹窗 Teleport 到 body，逐例清理避免串扰
  document.body.innerHTML = ''
})

test('用户管理页渲染列表行并展示学校、联系方式与账号状态', async () => {
  vi.mocked(searchAdminUsers).mockResolvedValue({
    code: 200,
    message: 'ok',
    data: {
      records: [user(), user({ id: 32, studentId: '2224102', nickname: '探针乙', status: 'BANNED_TEMP', banUntilTime: '2026-09-01T10:00:00' })],
      total: 2
    }
  })
  const wrapper = mount(UsersPage, { global })
  await flushPromises()

  assert.match(wrapper.text(), /筛选探针甲/)
  assert.match(wrapper.text(), /2224101/)
  assert.match(wrapper.text(), /上海大学/)
  assert.match(wrapper.text(), /probe@shu\.edu\.cn/)
  assert.match(wrapper.text(), /13800001111/)
  assert.match(wrapper.text(), /校园新人/)
  // 已封禁用户展示状态徽章、解禁时间与解除入口
  assert.match(wrapper.text(), /限时封禁/)
  assert.match(wrapper.text(), /2026-09-01 10:00 解除/)
  assert.ok(wrapper.findAll('button').some((button) => button.text() === '解除封禁'))
  // 首次进入不带任何筛选条件
  assert.deepEqual(vi.mocked(searchAdminUsers).mock.calls[0], [{ page: 1, size: 10 }])
  assert.equal(vi.mocked(getSchools).mock.calls.length, 1)
})

test('点击搜索固化模糊筛选并回到第一页，重置后清空条件', async () => {
  const wrapper = mount(UsersPage, { global })
  await flushPromises()

  // el-select 内部也渲染 input，按 placeholder 精确取筛选输入框
  await wrapper.get('input[placeholder="模糊搜索学号"]').setValue('2024')
  await wrapper.get('input[placeholder="模糊搜索昵称"]').setValue('探针')
  const search = wrapper.findAll('.filter-actions button').find((button) => button.text() === '搜索')
  assert.ok(search)
  await search.trigger('click')
  await flushPromises()

  assert.deepEqual(vi.mocked(searchAdminUsers).mock.calls.at(-1), [
    {
      page: 1,
      size: 10,
      studentId: '2024',
      nickname: '探针'
    }
  ])

  const reset = wrapper.findAll('.filter-actions button').find((button) => button.text() === '重置')
  assert.ok(reset)
  await reset.trigger('click')
  await flushPromises()

  assert.deepEqual(vi.mocked(searchAdminUsers).mock.calls.at(-1), [{ page: 1, size: 10 }])
})

test('强制重置密码经二次确认后调用接口', async () => {
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
  const success = vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  vi.mocked(resetUserPassword).mockResolvedValue({ code: 200, message: 'ok', data: undefined })
  const wrapper = mount(UsersPage, { global })
  await flushPromises()

  const button = wrapper.findAll('button').find((button) => button.text() === '重置密码')
  assert.ok(button)
  await button.trigger('click')
  await flushPromises()

  assert.deepEqual(vi.mocked(resetUserPassword).mock.calls[0], [{ userId: 31 }])
  assert.equal(success.mock.calls.length, 1)
})

test('封禁弹窗提交后调用接口并就地更新行状态', async () => {
  const wrapper = mount(UsersPage, { global })
  await flushPromises()

  const banButton = wrapper.findAll('button').find((button) => button.text() === '封禁')
  assert.ok(banButton)
  await banButton.trigger('click')
  await flushPromises()

  const modal = document.body.querySelector('[aria-label="账号封禁"]')
  assert.ok(modal, '封禁弹窗应传送到 body')
  const reason = modal.querySelector('textarea')
  assert.ok(reason)
  reason.value = '发布违禁内容'
  reason.dispatchEvent(new Event('input'))

  const submit = Array.from(modal.querySelectorAll('button')).find((button) => button.textContent === '确认封禁')!
  submit.click()
  await flushPromises()

  assert.deepEqual(vi.mocked(banUser).mock.calls[0], [
    {
      userId: 31,
      type: 'BAN_TEMP',
      reason: '发布违禁内容',
      banDays: 7
    }
  ])
  // 行状态就地更新为限时封禁，无需重新拉取列表
  assert.match(wrapper.text(), /限时封禁/)
})

test('已封禁用户经二次确认后解除封禁并恢复状态', async () => {
  vi.mocked(searchAdminUsers).mockResolvedValue({ code: 200, message: 'ok', data: { records: [user({ status: 'BANNED_PERM' })], total: 1 } })
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
  const success = vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  vi.mocked(unbanUser).mockResolvedValue({ code: 200, message: 'ok', data: undefined })
  const wrapper = mount(UsersPage, { global })
  await flushPromises()

  const button = wrapper.findAll('button').find((button) => button.text() === '解除封禁')
  assert.ok(button)
  await button.trigger('click')
  await flushPromises()

  assert.deepEqual(vi.mocked(unbanUser).mock.calls[0], [{ userId: 31 }])
  // 行状态就地恢复为正常
  assert.match(wrapper.text(), /正常/)
  assert.equal(success.mock.calls.length, 1)
})
