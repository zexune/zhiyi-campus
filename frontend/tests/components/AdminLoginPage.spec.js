import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, expect, test, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import AdminLoginPage from '@/views/admin/AdminLoginPage.vue'
import { adminLogin } from '@/api/admin'

vi.mock('@/api/admin', () => ({ adminLogin: vi.fn() }))

const adminUser = { id: 1, username: 'admin', nickname: '管理员', role: 'ADMIN' }

/**
 * 组件内 useRoute()/useRouter() 需要真实 router 上下文，
 * 只 mock $route 会让 useRoute() 返回 undefined 并在登录成功后抛错（Unhandled Rejection）。
 */
async function mountPage(initialPath = '/admin/login') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/login', component: AdminLoginPage },
      { path: '/admin/dashboard', component: { template: '<div>dashboard</div>' } }
    ]
  })
  await router.push(initialPath)
  const wrapper = mount(AdminLoginPage, {
    global: {
      plugins: [createPinia(), router],
      stubs: { RouterLink: { template: '<a><slot /></a>' } }
    }
  })
  return { wrapper, router }
}

beforeEach(() => {
  adminLogin.mockResolvedValue({ data: { token: 'jwt', user: adminUser } })
})

test('空表单提交被声明式校验拦截，展示行内错误且不请求接口', async () => {
  const { wrapper } = await mountPage()
  await wrapper.get('form button').trigger('submit')

  // el-form validate 的 Promise 与错误渲染跨多个微任务批次，轮询至错误出现
  await vi.waitFor(() => {
    const errors = wrapper.findAll('.el-form-item__error')
    expect(errors.length).toBe(2)
  })
  const errors = wrapper.findAll('.el-form-item__error').map((node) => node.text())
  assert.match(errors[0], /请输入管理员账号/)
  assert.match(errors[1], /请输入密码/)
  assert.equal(adminLogin.mock.calls.length, 0)
})

test('填写后提交通过校验，以表单值调用 adminLogin 并跳转管理后台', async () => {
  const { wrapper, router } = await mountPage()
  await wrapper.get('#admin-username').setValue('admin')
  await wrapper.get('#admin-password').setValue('123456')
  await wrapper.get('form button').trigger('submit')
  await flushPromises()
  await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/admin/dashboard'))

  assert.deepEqual(adminLogin.mock.calls[0], [{ username: 'admin', password: '123456' }])
})

test('携带合法 redirect 查询参数时登录后跳回该地址', async () => {
  const { wrapper, router } = await mountPage('/admin/login?redirect=/admin/violations')
  router.addRoute({ path: '/admin/violations', component: { template: '<div>violations</div>' } })
  await wrapper.get('#admin-username').setValue('admin')
  await wrapper.get('#admin-password').setValue('123456')
  await wrapper.get('form button').trigger('submit')
  await flushPromises()
  await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/admin/violations'))
})
