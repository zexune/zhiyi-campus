import { expect, test } from '@playwright/test'

test('未登录访问首页交易大厅会被路由守卫送回登录页', async ({ page }) => {
  const runtimeErrors = []
  page.on('pageerror', (error) => runtimeErrors.push(error.message))
  await page.route('https://fonts.googleapis.com/**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'text/css',
      body: ''
    })
  )
  await page.route('**/api/school/list', (route) =>
    route.fulfill({
      status: 200,
      json: { code: 200, message: '操作成功', data: [{ id: 1, name: '上海大学', code: 'SHU' }] }
    })
  )
  await page.route('**/api/auth/security-questions', (route) =>
    route.fulfill({
      status: 200,
      json: { code: 200, message: '操作成功', data: ['测试问题？'] }
    })
  )
  await page.goto('/')

  await expect(page).toHaveURL(/\/login\?redirect=\/$/)
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  expect(runtimeErrors).toEqual([])
})

test('已登录用户访问登录页会被路由守卫送回交易大厅', async ({ page }) => {
  await page.route('https://fonts.googleapis.com/**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'text/css',
      body: ''
    })
  )
  // 只拦截真实 API 路径：不能用 **/api/** 通配，dev server 下会误伤 /src/api/* 源码模块请求
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: '操作成功', data: null })
      })
  )
  await page.addInitScript(() => {
    localStorage.setItem('role', 'USER')
    localStorage.setItem('userId', '7')
    localStorage.setItem('nickname', '端到端同学')
  })

  await page.goto('/login')

  await expect(page).toHaveURL(/\/$/)
})
