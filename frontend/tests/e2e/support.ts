import { expect, type APIRequestContext, type Page } from '@playwright/test'

/**
 * 系统级 E2E 共享测试支持：注册、API 调用、图片上传与浏览器登录态注入。
 * 从 trading-system.spec.ts 提取，供 @system 旅程复用，避免两套认证逻辑。
 */

export const TEST_PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64')

export interface SessionUser {
  id: number
  nickname: string
  role?: string
}

export interface Session {
  token: string
  user: SessionUser
}

interface ApiCallOptions {
  data?: Record<string, unknown>
  token?: string
  /** 资金操作（充值/下单/确认收货/取消）必须携带 X-Idempotency-Key（UUID），见 OrderController */
  idempotent?: boolean
}

/** 统一 API 调用器：校验真实 HTTP 2xx + 业务码 200 后返回 data */
export async function api<T = Record<string, unknown>>(request: APIRequestContext, method: string, path: string, { data, token, idempotent }: ApiCallOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (token) headers.Authorization = `Bearer ${token}`
  // 每次资金意图生成新 UUID；脚本不模拟超时重试，无复用原键的场景
  if (idempotent) headers['X-Idempotency-Key'] = crypto.randomUUID()
  const response = await request.fetch(path, {
    method,
    data,
    headers: Object.keys(headers).length > 0 ? headers : undefined
  })
  const responseText = await response.text()
  let body: { code?: number; message?: string; data?: T } | undefined
  try {
    const parsed: unknown = JSON.parse(responseText)
    if (parsed !== null && typeof parsed === 'object') {
      body = parsed as NonNullable<typeof body>
    }
  } catch {
    // 非 JSON 响应也应把原始内容带入断言，避免只看到笼统的 HTTP 状态失败。
  }
  const responseDetail = body?.message || responseText || '<empty body>'
  expect(response.ok(), `${method} ${path} HTTP ${response.status()}: ${responseDetail}`).toBeTruthy()
  expect(body?.code, `${method} ${path}: ${body?.message || responseDetail}`).toBe(200)
  return body?.data as T
}

export async function register(request: APIRequestContext, studentId: string, nickname: string): Promise<Session> {
  return api<Session>(request, 'POST', '/api/auth/register', {
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

export async function adminLogin(request: APIRequestContext): Promise<Session> {
  return api<Session>(request, 'POST', '/api/admin/auth/login', {
    data: { username: 'admin', password: '123456' }
  })
}

export async function uploadImage(request: APIRequestContext, token: string): Promise<string> {
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
 * 并写入 userId/role/nickname 展示信息（登录态派生，见 src/utils/auth.ts）。
 */
export async function loginInBrowser(page: Page, { token }: Session, user: SessionUser): Promise<void> {
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

/** 唯一数据后缀（时间戳 + 随机段），隔离每次运行的注册/商品数据 */
export function uniqueSuffix(): string {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
}
