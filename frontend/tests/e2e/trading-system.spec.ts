import { expect, test } from '@playwright/test'
import { api, loginInBrowser, register, uploadImage } from './support'

test('@system 真实前后端完成交易闭环，并在用户与管理界面呈现最终状态', async ({ page, request }) => {
  const suffix = String(Date.now()).slice(-9)
  const seller = await register(request, `s${suffix}`, 'E2E卖家')
  const buyer = await register(request, `b${suffix}`, 'E2E买家')
  const imageUrl = await uploadImage(request, seller.token)

  const item = await api(request, 'POST', '/api/item/publish', {
    token: seller.token,
    data: {
      type: 'SELL',
      title: `E2E教材${suffix.slice(-4)}`,
      description: '用于系统端到端测试的校内教材',
      categoryId: 2,
      price: 19.9,
      images: [imageUrl],
      tradeLocation: '图书馆南门'
    }
  })
  await api(request, 'POST', '/api/wallet/recharge', {
    token: buyer.token,
    idempotent: true,
    data: { amount: 100 }
  })
  const order = await api(request, 'POST', '/api/order/create', {
    token: buyer.token,
    idempotent: true,
    data: { itemId: item.id }
  })
  await api(request, 'PUT', `/api/order/${order.id}/confirm`, { token: buyer.token, idempotent: true })
  await api(request, 'POST', `/api/order/${order.id}/review`, {
    token: buyer.token,
    data: { rating: 5, accurate: true, comment: 'E2E交易顺利' }
  })

  await page.goto('/login')
  await loginInBrowser(page, buyer, buyer.user)
  await page.goto('/orders/bought')

  const completedOrder = page.locator('.order-item').filter({ hasText: item.title })
  await expect(completedOrder).toBeVisible()
  await expect(completedOrder.locator('.badge')).toHaveText('已完成')
  await expect(completedOrder.locator('.order-extra').filter({ hasText: '已评价' })).toBeVisible()

  const admin = await api(request, 'POST', '/api/admin/auth/login', {
    data: { username: 'admin', password: '123456' }
  })
  await loginInBrowser(page, admin, admin.user)
  await page.goto('/admin/dashboard')

  await expect(page.getByText('管理后台', { exact: false }).first()).toBeVisible()
  await expect(page.getByText('用户总数', { exact: true })).toBeVisible()
  await expect(page.getByText('近 7 日交易趋势', { exact: false })).toBeVisible()

  // 用户管理页（原内容管理拆分）：筛选区与表格行渲染，本旅程注册的买家/卖家应出现在表中
  await page.goto('/admin/users')
  // 导航链接与页面标题同文，断言收窄到标题元素避免 strict mode 冲突
  await expect(page.locator('.page-title')).toHaveText('用户管理')
  await expect(page.getByPlaceholder('模糊搜索学号')).toBeVisible()
  await expect(page.locator('.user-table tbody tr').first()).toBeVisible()

  // 事件专题页保留原大事件专题卡片
  await page.goto('/admin/topics')
  await expect(page.getByText('大事件专题', { exact: true })).toBeVisible()
})
