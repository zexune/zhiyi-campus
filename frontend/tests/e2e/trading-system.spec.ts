import { expect, test, type APIRequestContext } from '@playwright/test'

const TEST_PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64')

interface ApiCallOptions {
  data?: Record<string, unknown>
  token?: string
}

async function api<T = Record<string, unknown>>(request: APIRequestContext, method: string, path: string, { data, token }: ApiCallOptions = {}): Promise<T> {
  const response = await request.fetch(path, {
    method,
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : undefined
  })
  expect(response.ok(), `${method} ${path} HTTP 状态`).toBeTruthy()
  const body = await response.json()
  expect(body.code, `${method} ${path}: ${body.message}`).toBe(200)
  return body.data as T
}

async function register(request: APIRequestContext, studentId: string, nickname: string): Promise<{ token: string; user: { id: number; nickname: string; role?: string } }> {
  return api(request, 'POST', '/api/auth/register', {
    data: {
      schoolId: 1,
      studentId,
      password: '123456',
      confirmPassword: '123456',
      nickname,
      securityQuestion: '端到端测试问题？',
      securityAnswer: '端到端答案'
    }
  })
}

async function uploadImage(request: APIRequestContext, token: string): Promise<string> {
  const response = await request.post('/api/item/upload-image', {
    headers: { Authorization: `Bearer ${token}` },
    multipart: {
      file: {
        name: 'e2e-system.png',
        mimeType: 'image/png',
        buffer: TEST_PNG
      }
    }
  })
  expect(response.ok(), '上传测试商品图片 HTTP 状态').toBeTruthy()
  const body = await response.json()
  expect(body.code, `上传测试商品图片: ${body.message}`).toBe(200)
  expect(body.data.url).toMatch(/^\/uploads\/items\//)
  return body.data.url
}

/**
 * 在浏览器会话中模拟登录：注入后端下发的 httpOnly 会话 Cookie（凭证本体），
 * 并写入 userId/role/nickname 展示信息（登录态派生，见 src/utils/auth.js）。
 */
async function loginInBrowser(page: import('@playwright/test').Page, { token }: { token: string }, user: { id: number; nickname: string; role?: string }): Promise<void> {
  const origin = new URL(page.url())
  await page.context().addCookies([
    {
      name: 'zhiyi_token',
      value: token,
      domain: origin.hostname,
      path: '/api',
      httpOnly: true,
      sameSite: 'Lax'
    }
  ])
  await page.evaluate(
    (u) => {
      localStorage.setItem('role', u.role)
      localStorage.setItem('userId', String(u.id))
      localStorage.setItem('nickname', u.nickname)
    },
    { id: user.id, nickname: user.nickname, role: user.role || 'USER' }
  )
}

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
    data: { amount: 100 }
  })
  const order = await api(request, 'POST', '/api/order/create', {
    token: buyer.token,
    data: { itemId: item.id }
  })
  await api(request, 'PUT', `/api/order/${order.id}/confirm`, { token: buyer.token })
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
  await expect(page.getByText('用户管理', { exact: true })).toBeVisible()
  await expect(page.getByPlaceholder('模糊搜索学号')).toBeVisible()
  await expect(page.locator('.user-table tbody tr').first()).toBeVisible()

  // 事件专题页保留原大事件专题卡片
  await page.goto('/admin/topics')
  await expect(page.getByText('大事件专题', { exact: true })).toBeVisible()
})
