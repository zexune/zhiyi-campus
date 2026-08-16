import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, test, vi } from 'vitest'

import AdminLoginPage from '@/views/admin/AdminLoginPage.vue'
import { adminLogin } from '@/api/admin'

vi.mock('@/api/admin', () => ({ adminLogin: vi.fn() }))

const global = {
  plugins: [createPinia()],
  stubs: {
    RouterLink: { template: '<a><slot /></a>' }
  }
}

const adminUser = { id: 1, username: 'admin', nickname: '管理员', role: 'ADMIN' }

beforeEach(() => {
  adminLogin.mockResolvedValue({ data: { token: 'jwt', user: adminUser } })
})

test('空表单提交被声明式校验拦截，展示行内错误且不请求接口', async () => {
  const wrapper = mount(AdminLoginPage, { global, mocks: { $route: { query: {} } } })
  await wrapper.get('form button').trigger('submit')
  // el-form 的 validate Promise 与错误渲染跨多个微任务批次，固定等待渲染完成
  await new Promise((resolve) => setTimeout(resolve, 300))

  const errors = wrapper.findAll('.el-form-item__error').map((node) => node.text())
  assert.equal(errors.length, 2)
  assert.match(errors[0], /请输入管理员账号/)
  assert.match(errors[1], /请输入密码/)
  assert.equal(adminLogin.mock.calls.length, 0)
})

test('填写后提交通过校验并以表单值调用 adminLogin', async () => {
  const wrapper = mount(AdminLoginPage, { global, mocks: { $route: { query: {} } } })
  await wrapper.get('#admin-username').setValue('admin')
  await wrapper.get('#admin-password').setValue('123456')
  await wrapper.get('form button').trigger('submit')
  await flushPromises()

  assert.deepEqual(adminLogin.mock.calls[0], [{ username: 'admin', password: '123456' }])
})
