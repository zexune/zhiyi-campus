import { expect, test, type Route } from '@playwright/test'

/**
 * 申诉旅程（纯前端 mock 型，不带 @system）：通过真实页面打开"我的发布"
 * → 申诉弹窗 → 填写理由提交，验证：
 * - 提交请求体只携带 { reason }；
 * - 申诉接口返回非空 AppealVO 时弹窗关闭、成功提示、列表重新拉取；
 * - 刷新后的列表以 appealStatus=PENDING 呈现"申诉审核中"；
 * - 全程无浏览器运行时错误。
 */

const APPEAL_REASON = '冒烟申诉理由：商品内容并未违反平台规则，请求复核并撤销处罚。'

function envelope(data: unknown): Record<string, unknown> {
  return { code: 200, message: '操作成功', data }
}

function appealableRow() {
  return {
    id: 501,
    title: '冒烟测试-被误判的商品',
    type: 'SELL',
    status: 'OFF_SHELF',
    moderationStatus: 'REJECTED',
    reserved: false,
    appealable: true,
    latestViolationId: 88,
    price: 9.9,
    coverImage: null,
    images: [],
    viewCount: 2,
    createdAt: '2026-08-20T09:00:00'
  }
}

const APPEAL_VO = {
  id: 31,
  reportId: 88,
  itemId: 501,
  sellerName: '冒烟卖家',
  itemTitle: '冒烟测试-被误判的商品',
  violationReason: '涉嫌违规',
  reason: APPEAL_REASON,
  status: 'PENDING',
  createdAt: '2026-08-26T10:00:00'
}

test('申诉弹窗提交非空 AppealVO 后关闭弹窗、提示成功并刷新为"申诉审核中"', async ({ page }) => {
  const pageErrors: Error[] = []
  page.on('pageerror', (error) => pageErrors.push(error))

  let myItemsCalls = 0
  let appealRequestBody: Record<string, unknown> | undefined

  await page.route('**/api/item/my-items*', async (route: Route) => {
    myItemsCalls += 1
    const row = myItemsCalls === 1 ? appealableRow() : { ...appealableRow(), appealable: false, appealStatus: 'PENDING' }
    await route.fulfill({ json: envelope({ records: [row], total: 1, current: 1, size: 10, pages: 1 }) })
  })
  await page.route('**/api/item/*/appeals', async (route: Route) => {
    appealRequestBody = route.request().postDataJSON() as Record<string, unknown>
    await route.fulfill({ json: envelope(APPEAL_VO) })
  })
  await page.route('**/api/chat/unread-count', async (route) => route.fulfill({ json: envelope(0) }))
  await page.route('https://fonts.googleapis.com/**', (route) => route.abort())

  // 登录态（凭证全 mock，localStorage 摘要即可通过路由守卫）
  await page.addInitScript(() => {
    localStorage.setItem('role', 'USER')
    localStorage.setItem('userId', '7')
    localStorage.setItem('nickname', '冒烟卖家')
  })

  await page.goto('/user/my-items')

  const row = page.locator('.item-row').filter({ hasText: '冒烟测试-被误判的商品' })
  await expect(row).toBeVisible()
  await expect(row.getByRole('button', { name: '申诉', exact: true })).toBeVisible()

  await row.getByRole('button', { name: '申诉', exact: true }).click()
  const dialog = page.getByRole('dialog').filter({ hasText: '提交内容违规申诉' })
  await expect(dialog).toBeVisible()
  await dialog.getByPlaceholder('请填写 10-500 字的申诉理由').fill(APPEAL_REASON)
  await dialog.getByRole('button', { name: '确认提交' }).click()

  await expect(page.locator('.el-message--success')).toHaveText(/申诉已提交/)
  await expect(dialog).toBeHidden()
  expect(appealRequestBody).toEqual({ reason: APPEAL_REASON })
  expect(myItemsCalls, '提交成功后必须重新拉取列表').toBe(2)

  await expect(row.getByText('申诉审核中')).toBeVisible()

  expect(pageErrors, '不允许浏览器运行时错误').toEqual([])
})
