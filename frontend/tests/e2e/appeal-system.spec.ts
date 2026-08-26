import { expect, test } from '@playwright/test'
import { adminLogin, api, loginInBrowser, register, uniqueSuffix, uploadImage } from './support'

/**
 * 申诉全链路（@system：真实 Vue + Spring + MySQL）：
 * 1. 注册卖家与举报人；
 * 2. 上传图片并发布唯一商品；
 * 3. 举报人提交举报；
 * 4. 管理员登录，查询举报并确认违规；
 * 5. 卖家在真实页面（我的发布 → 申诉弹窗）提交申诉；
 * 6. 管理员重新调用申诉列表 API，确认申诉记录已提交事务且可查询；
 * 7. 打开 /admin/violations 申诉复核页签，确认 pending 申诉可见。
 */

test('@system 违规确认后卖家经真实页面提交申诉，管理端 API 与申诉复核页均可查询', async ({ page, request }) => {
  const suffix = uniqueSuffix()
  const seller = await register(request, `as${suffix}`, '申诉卖家')
  const reporter = await register(request, `ar${suffix}`, '举报人')
  const imageUrl = await uploadImage(request, seller.token)

  const itemTitle = `申诉链路商品${suffix}`
  const item = await api<{ id: number }>(request, 'POST', '/api/item/publish', {
    token: seller.token,
    data: {
      type: 'SELL',
      title: itemTitle,
      description: '用于申诉端到端测试的校内商品',
      categoryId: 2,
      price: 29.9,
      images: [imageUrl],
      tradeLocation: '图书馆北门'
    }
  })
  expect(item.id).toBeTruthy()

  // 举报人提交举报
  await api(request, 'POST', `/api/item/${item.id}/reports`, {
    token: reporter.token,
    data: { type: 'PROHIBITED_ITEM', details: '端到端测试：疑似违禁物品' }
  })

  // 管理员查询违规列表并确认违规
  const admin = await adminLogin(request)
  const violations = await api<{ records: Array<{ id: number; originalTitle?: string; status?: string }> }>(request, 'GET', '/api/admin/violations?status=PENDING&size=50', { token: admin.token })
  const violation = violations.records.find((row) => row.originalTitle === itemTitle)
  expect(violation, '举报记录应出现在管理端待审列表').toBeTruthy()
  await api(request, 'PUT', `/api/admin/violations/${violation!.id}/confirm`, {
    token: admin.token,
    data: { reason: '端到端测试：确认违规并下架' }
  })

  // 卖家在真实页面提交申诉
  const appealReason = `端到端申诉理由（${suffix}）：商品内容合规，请求复核撤销扣分。`
  await page.goto('/login')
  await loginInBrowser(page, seller, seller.user)
  await page.goto('/user/my-items')

  const row = page.locator('.item-row').filter({ hasText: itemTitle })
  await expect(row).toBeVisible()
  await expect(row.getByText('已确认内容违规，可整改或申诉')).toBeVisible()
  await row.getByRole('button', { name: '申诉', exact: true }).click()

  const dialog = page.getByRole('dialog').filter({ hasText: '提交内容违规申诉' })
  await expect(dialog).toBeVisible()
  await dialog.getByPlaceholder('请填写 10-500 字的申诉理由').fill(appealReason)
  await dialog.getByRole('button', { name: '确认提交' }).click()

  await expect(page.locator('.el-message--success')).toHaveText(/申诉已提交/)
  await expect(dialog).toBeHidden()
  await expect(row.getByText('申诉审核中')).toBeVisible()

  // 管理员重新调用申诉列表 API：记录已提交事务且可查询
  const appeals = await api<{ records: Array<{ itemTitle?: string; reason?: string; status?: string; itemId?: number }> }>(request, 'GET', '/api/admin/appeals?status=PENDING&size=50', {
    token: admin.token
  })
  const appeal = appeals.records.find((record) => record.itemId === item.id)
  expect(appeal, '申诉记录应已提交事务并出现在管理端列表').toBeTruthy()
  expect(appeal!.status).toBe('PENDING')
  expect(appeal!.reason).toBe(appealReason)
  expect(appeal!.itemTitle).toBe(itemTitle)

  // 管理端申诉复核页面可见 pending 申诉
  await loginInBrowser(page, admin, admin.user)
  await page.goto('/admin/violations')
  await page.getByRole('button', { name: '申诉复核' }).click()
  const appealCard = page.locator('.appeal-card').filter({ hasText: itemTitle })
  await expect(appealCard).toBeVisible()
  await expect(appealCard.getByText('待复核', { exact: true })).toBeVisible()
  await expect(appealCard.getByText(appealReason)).toBeVisible()
})
