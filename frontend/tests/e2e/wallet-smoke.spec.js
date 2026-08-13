import { expect, test } from '@playwright/test'

test('未登录用户访问钱包会被路由守卫送回登录页', async ({ page }) => {
  const runtimeErrors = []
  page.on('pageerror', (error) => runtimeErrors.push(error.message))
  await page.route('https://fonts.googleapis.com/**', (route) => route.fulfill({
    status: 200,
    contentType: 'text/css',
    body: '',
  }))
  await page.route('**/api/school/list', (route) => route.fulfill({
    status: 200,
    json: { code: 200, message: '操作成功', data: [{ id: 1, name: '上海大学', code: 'SHU' }] },
  }))
  await page.route('**/api/auth/security-questions', (route) => route.fulfill({
    status: 200,
    json: { code: 200, message: '操作成功', data: ['测试问题？'] },
  }))
  await page.goto('/wallet')

  await expect(page).toHaveURL(/\/login\?redirect=\/wallet$/)
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  expect(runtimeErrors).toEqual([])
})

test('钱包关键交互可在稳定 API 契约替身下跑通', async ({ page }) => {
  let balance = 25
  let recharged = false
  const runtimeErrors = []
  page.on('pageerror', (error) => runtimeErrors.push(error.message))
  await page.route('https://fonts.googleapis.com/**', (route) => route.fulfill({
    status: 200,
    contentType: 'text/css',
    body: '',
  }))

  await page.addInitScript(() => {
    localStorage.setItem('token', 'e2e-contract-token')
    localStorage.setItem('role', 'USER')
    localStorage.setItem('userId', '7')
    localStorage.setItem('nickname', '端到端同学')
  })

  await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    let data

    if (url.pathname === '/api/wallet/balance') {
      data = { balance }
    } else if (url.pathname === '/api/wallet/recharge' && request.method() === 'POST') {
      const body = request.postDataJSON()
      balance += Number(body.amount)
      recharged = true
      data = { balance }
    } else if (url.pathname === '/api/wallet/logs') {
      const records = recharged
        ? [{ id: 2, type: 'RECHARGE', amount: 12.5, balanceAfter: balance, remark: '模拟充值', createdAt: '2026-08-13T13:00:00' }]
        : [{ id: 1, type: 'RECHARGE', amount: 25, balanceAfter: 25, remark: '首次充值', createdAt: '2026-08-13T12:00:00' }]
      data = { records, total: records.length }
    } else if (url.pathname === '/api/chat/unread-count') {
      data = 0
    } else {
      return route.fulfill({ status: 404, json: { code: 404, message: '未配置的测试接口', data: null } })
    }

    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: '操作成功', data }),
    })
  })

  await page.goto('/wallet')
  await expect(page.getByText('当前余额')).toBeVisible()
  await expect(page.locator('.balance-card .price')).toContainText('25.00')
  await expect(page.getByText('首次充值')).toBeVisible()

  await page.getByRole('button', { name: '充值' }).click()
  await page.getByPlaceholder(/请输入充值金额/).fill('12.50')
  await page.getByRole('button', { name: '确认充值' }).click()

  await expect(page.locator('.balance-card .price')).toContainText('37.50')
  await expect(page.locator('.log-remark')).toHaveText('模拟充值')
  await expect(page.getByText(/充值成功/)).toBeVisible()
  expect(runtimeErrors).toEqual([])
})
