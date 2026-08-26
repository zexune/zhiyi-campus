import assert from 'node:assert/strict'
import { afterEach, beforeEach, test, vi } from 'vitest'

/**
 * request.ts 拦截器直测（P0-5 / 信封可信度收紧）：用真实 axios（fetch adapter）
 * + 桩 fetch 覆盖 401/403/409/422/429/网络失败分支，验证：
 * - 只有真实 HTTP 401 触发登录态清理（且同一鉴权周期只清理一次）；
 *   登录态清理与幂等键处置相互独立（残缺信封的 401 清登录但 RETAIN）；
 * - 403/409/422 等业务失败绝不登出；
 * - ApiError.httpStatus、业务码、幂等处置、服务端 outcome 四者正交携带互不覆盖；
 * - 幂等处置矩阵：完整新信封按 meta.requestOutcome；完整旧信封（meta 自有
 *   属性完全不存在）按业务码白名单 fallback；残缺形态（缺 code/message/data、
 *   meta null/缺字段/非法枚举、非 JSON、代理 HTML）不信任 body 的业务码与
 *   message，按传输层错误保守处理（RETAIN）。
 */

type FreshModules = {
  request: (typeof import('@/utils/request'))['default']
  utils: typeof import('@/utils/request')
  auth: typeof import('@/utils/auth')
}

async function importFresh(): Promise<FreshModules> {
  vi.resetModules()
  const utils = await import('@/utils/request')
  const auth = await import('@/utils/auth')
  return { request: utils.default, utils, auth }
}

function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json', ...headers }
  })
}

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  ;(globalThis as Record<string, unknown>).ElMessage = { error: vi.fn(), success: vi.fn() }
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  localStorage.clear()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  localStorage.clear()
})

test('200 成功信封 resolve 为 ApiResult<data>', { timeout: 30000 }, async () => {
  fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(200, { code: 200, message: '操作成功', data: { amount: '9.9' } })))
  const { request } = await importFresh()

  const res = await request.get<{ amount: string }>('/wallet/balance')

  assert.equal(res.code, 200)
  assert.equal(res.data.amount, '9.9')
})

// ---- 完整新信封：meta.requestOutcome 为权威处置 ----

test('409 + meta REJECTED：业务码/HTTP 状态/处置/outcome 正交携带且不登出', async () => {
  fetchMock.mockResolvedValue(
    jsonResponse(409, {
      code: 3001,
      message: '余额不足',
      data: null,
      meta: { requestOutcome: 'REJECTED' }
    })
  )
  const { request, auth } = await importFresh()
  auth.setLoginUser({ id: 7, nickname: 'buyer', role: 'USER' })

  const error = await request.post('/order/create', { itemId: 1 }).catch((e: unknown) => e)

  assert.ok(error instanceof Error)
  const apiError = error as import('@/utils/request').ApiError
  assert.equal(apiError.httpStatus, 409)
  assert.equal(apiError.code, 3001)
  assert.equal(apiError.idempotencyDisposition, 'CLEAR')
  assert.equal(apiError.outcome, 'REJECTED')
  // 409 不触发登出
  assert.equal(auth.isLoggedIn(), true)
})

test('429 + Retry-After + meta UNKNOWN：保留幂等键并携带退避建议', async () => {
  fetchMock.mockResolvedValue(jsonResponse(429, { code: 3004, message: '当前交易繁忙，请稍后重试', data: null, meta: { requestOutcome: 'UNKNOWN' } }, { 'retry-after': '2' }))
  const { request } = await importFresh()

  const error = (await request.post('/order/create', { itemId: 1 }).catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(error.httpStatus, 429)
  assert.equal(error.code, 3004)
  assert.equal(error.idempotencyDisposition, 'RETAIN')
  assert.equal(error.outcome, 'UNKNOWN')
  assert.equal(error.retryAfterSeconds, 2)
})

test('完整新信封 meta UNKNOWN：结果不明保留幂等键', async () => {
  fetchMock.mockResolvedValue(jsonResponse(500, { code: 500, message: '服务器内部错误', data: null, meta: { requestOutcome: 'UNKNOWN' } }))
  const { request } = await importFresh()

  const error = (await request.get('/wallet/balance').catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(error.httpStatus, 500)
  assert.equal(error.outcome, 'UNKNOWN')
  assert.equal(error.idempotencyDisposition, 'RETAIN')
})

// ---- 完整旧信封（meta 自有属性完全不存在）：业务码白名单 fallback ----

test('完整旧信封（完全没有 meta）：按业务码白名单推断处置，outcome 为 undefined', async () => {
  fetchMock.mockResolvedValue(jsonResponse(409, { code: 3001, message: '余额不足', data: null }))
  const { request } = await importFresh()

  const known = (await request.post('/order/create', {}).catch((e: unknown) => e)) as import('@/utils/request').ApiError
  assert.equal(known.idempotencyDisposition, 'CLEAR')
  assert.equal(known.outcome, undefined)
  assert.equal(known.code, 3001)
  assert.equal(known.message, '余额不足', '完整旧信封的 message 可信')
})

test('完整旧信封 + 未知业务码：结果不明保留幂等键', async () => {
  fetchMock.mockResolvedValue(jsonResponse(400, { code: 3999, message: '未来新增的码', data: null }))
  const { request } = await importFresh()

  const unknown = (await request.post('/order/create', {}).catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(unknown.idempotencyDisposition, 'RETAIN')
  assert.equal(unknown.code, 3999)
})

// ---- 残缺失败形态：不信任 body 业务码/message/detail，传输层保守处理 ----

test('meta 存在但为 null：不可信，RETAIN 且不携带业务码', async () => {
  fetchMock.mockResolvedValue(jsonResponse(409, { code: 3001, message: '余额不足', data: null, meta: null }))
  const { request } = await importFresh()

  const error = (await request.post('/order/create', {}).catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(error.idempotencyDisposition, 'RETAIN')
  assert.equal(error.code, -1, '残缺信封的业务码不可信')
  assert.equal(error.message, '网络错误，请稍后再试', '残缺信封的 message 不可信')
})

test('白名单业务码 + 非法 meta（缺 requestOutcome）：仍为 RETAIN', async () => {
  fetchMock.mockResolvedValue(jsonResponse(409, { code: 3001, message: '余额不足', data: null, meta: {} }))
  const { request } = await importFresh()

  const error = (await request.post('/order/create', {}).catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(error.idempotencyDisposition, 'RETAIN', '白名单 code 只有在完整（新或旧）信封下才允许 fallback')
  assert.equal(error.code, -1)
})

test('白名单业务码 + 非法 meta 枚举：仍为 RETAIN', async () => {
  fetchMock.mockResolvedValue(jsonResponse(409, { code: 3001, message: '余额不足', data: null, meta: { requestOutcome: 'MAYBE' } }))
  const { request } = await importFresh()

  const error = (await request.post('/order/create', {}).catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(error.idempotencyDisposition, 'RETAIN')
  assert.equal(error.code, -1)
})

test('缺少 data 字段：不是完整失败信封，按传输层保守处理', async () => {
  fetchMock.mockResolvedValue(jsonResponse(409, { code: 3001, message: '余额不足' }))
  const { request } = await importFresh()

  const error = (await request.post('/order/create', {}).catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(error.idempotencyDisposition, 'RETAIN')
  assert.equal(error.code, -1)
  assert.equal(error.message, '网络错误，请稍后再试')
})

test('缺少 code 字段或 message 为空字符串：不可信，RETAIN', async () => {
  fetchMock.mockResolvedValueOnce(jsonResponse(409, { message: '余额不足', data: null }))
  fetchMock.mockResolvedValueOnce(jsonResponse(409, { code: 3001, message: '', data: null }))
  const { request } = await importFresh()

  const noCode = (await request.post('/order/create', {}).catch((e: unknown) => e)) as import('@/utils/request').ApiError
  const emptyMessage = (await request.post('/order/create', {}).catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(noCode.idempotencyDisposition, 'RETAIN')
  assert.equal(noCode.code, -1)
  assert.equal(emptyMessage.idempotencyDisposition, 'RETAIN')
  assert.equal(emptyMessage.code, -1)
})

test('非 JSON / 代理 HTML 错误页：按传输层错误处理（RETAIN）', async () => {
  fetchMock.mockImplementation(() =>
    Promise.resolve(
      new Response('<html><body>502 Bad Gateway</body></html>', {
        status: 502,
        headers: { 'Content-Type': 'text/html' }
      })
    )
  )
  const { request } = await importFresh()

  const error = (await request.get('/wallet/balance').catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(error.httpStatus, 502)
  assert.equal(error.code, -1)
  assert.equal(error.idempotencyDisposition, 'RETAIN')
})

test('网络层失败（fetch 拒绝）：code=-1、HTTP 0、结果不明', async () => {
  fetchMock.mockRejectedValue(new TypeError('network down'))
  const { request } = await importFresh()

  const error = (await request.get('/wallet/balance').catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(error.code, -1)
  assert.equal(error.httpStatus, 0)
  assert.equal(error.idempotencyDisposition, 'RETAIN')
})

test('非信封 2xx 响应：按协议违约失败（RETAIN）', async () => {
  fetchMock.mockResolvedValue(jsonResponse(200, { unexpected: 'shape' }))
  const { request } = await importFresh()

  const error = (await request.get('/wallet/balance').catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(error.code, -1)
  assert.equal(error.httpStatus, 200)
  assert.equal(error.idempotencyDisposition, 'RETAIN')
})

test('成功信封缺 data 或 message 为空：按协议违约失败（RETAIN）', async () => {
  fetchMock.mockResolvedValueOnce(jsonResponse(200, { code: 200, message: '操作成功' }))
  fetchMock.mockResolvedValueOnce(jsonResponse(200, { code: 200, message: '', data: null }))
  const { request } = await importFresh()

  const noData = (await request.get('/wallet/balance').catch((e: unknown) => e)) as import('@/utils/request').ApiError
  const emptyMessage = (await request.get('/wallet/balance').catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(noData.code, -1)
  assert.equal(noData.idempotencyDisposition, 'RETAIN')
  assert.equal(emptyMessage.code, -1)
  assert.equal(emptyMessage.idempotencyDisposition, 'RETAIN')
})

// ---- 业务失败绝不登出（真实 401 之外） ----

test('422 内容转审：明确拒绝可清键', async () => {
  fetchMock.mockResolvedValue(jsonResponse(422, { code: 2002, message: '内容涉嫌违规，已转入人工审核', data: null, meta: { requestOutcome: 'REJECTED' } }))
  const { request } = await importFresh()

  const error = (await request.post('/item/publish', {}).catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(error.httpStatus, 422)
  assert.equal(error.code, 2002)
  assert.equal(error.idempotencyDisposition, 'CLEAR')
})

test('403 注销账户（1008）：业务拒绝不清理登录态', async () => {
  fetchMock.mockResolvedValue(jsonResponse(403, { code: 1008, message: '该账户已注销', data: null, meta: { requestOutcome: 'REJECTED' } }))
  const { request, auth } = await importFresh()
  auth.setLoginUser({ id: 7, nickname: 'user', role: 'USER' })

  const error = (await request.post('/user/cancel-account', {}).catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(error.httpStatus, 403)
  assert.equal(error.code, 1008)
  assert.equal(error.idempotencyDisposition, 'CLEAR')
  assert.equal(auth.isLoggedIn(), true, '403 绝不触发前端登出')
})

test('2xx 响应体携带 code=401：不是登录态失效信号，绝不登出（P0-1）', async () => {
  fetchMock.mockResolvedValue(jsonResponse(200, { code: 401, message: '协议违约', data: null }))
  const { request, auth } = await importFresh()
  auth.setLoginUser({ id: 7, nickname: 'user', role: 'USER' })

  const error = (await request.get('/wallet/balance').catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(error.code, 401)
  assert.equal(error.httpStatus, 200)
  assert.equal(auth.isLoggedIn(), true, '响应体 code=401 不触发清理')
})

// ---- 真实 HTTP 401：清登录态；幂等处置独立按信封可信度计算 ----

test('真实 HTTP 401（完整新信封 REJECTED）：清理登录态且幂等键 CLEAR', { timeout: 30000 }, async () => {
  fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(401, { code: 1401, message: '登录状态已失效', data: null, meta: { requestOutcome: 'REJECTED' } })))
  const { request, auth } = await importFresh()
  auth.setLoginUser({ id: 7, nickname: 'user', role: 'USER' })
  const replaceSpy = vi.fn()
  window.location.replace = replaceSpy

  const error = (await request.get('/wallet/balance').catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(error.httpStatus, 401)
  assert.equal(error.idempotencyDisposition, 'CLEAR', '完整信封的 401/1401 是明确拒绝，可清幂等键')
  assert.equal(error.code, 1401)
  assert.equal(auth.isLoggedIn(), false)
  assert.equal(replaceSpy.mock.calls.length, 1)
})

test('真实 HTTP 401 + 完整旧信封（业务码 401/1401）：清登录态，幂等键 CLEAR', { timeout: 30000 }, async () => {
  fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(401, { code: 401, message: '未登录或 Token 过期', data: null })))
  const { request, auth } = await importFresh()
  auth.setLoginUser({ id: 7, nickname: 'user', role: 'USER' })

  const error = (await request.get('/wallet/balance').catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(error.code, 401)
  assert.equal(error.idempotencyDisposition, 'CLEAR', '完整旧信封的业务码 401 是明确拒绝')
  assert.equal(auth.isLoggedIn(), false)
})

test('真实 HTTP 401 + 残缺信封（meta null）：清登录态但幂等键 RETAIN（处置独立计算）', { timeout: 30000 }, async () => {
  fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(401, { code: 1401, message: '登录状态已失效', data: null, meta: null })))
  const { request, auth } = await importFresh()
  auth.setLoginUser({ id: 7, nickname: 'user', role: 'USER' })

  const error = (await request.get('/wallet/balance').catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(auth.isLoggedIn(), false, '真实 HTTP 401 一律清理登录态')
  assert.equal(error.idempotencyDisposition, 'RETAIN', '残缺信封不能证明明确拒绝，保留幂等键')
  assert.equal(error.code, 401, '业务码取自传输层 401，不透传 body 里的 1401')
})

test('裸 401（代理 HTML，无可信信封）：清理登录态但幂等键按结果不明 RETAIN', { timeout: 30000 }, async () => {
  fetchMock.mockImplementation(() =>
    Promise.resolve(
      new Response('<html><body>401 Unauthorized</body></html>', {
        status: 401,
        headers: { 'Content-Type': 'text/html' }
      })
    )
  )
  const { request, auth } = await importFresh()
  auth.setLoginUser({ id: 7, nickname: 'user', role: 'USER' })
  const replaceSpy = vi.fn()
  window.location.replace = replaceSpy

  const error = (await request.get('/wallet/balance').catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(error.httpStatus, 401)
  assert.equal(error.idempotencyDisposition, 'RETAIN', '无可信信封的 401 结果不明，保留幂等键')
  assert.equal(error.code, 401)
  assert.equal(auth.isLoggedIn(), false, '真实 HTTP 401 仍清理登录态')
})

test('同一鉴权周期的多次真实 401 只清理/跳转一次', { timeout: 30000 }, async () => {
  fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(401, { code: 401, message: '未登录或 Token 过期', data: null, meta: { requestOutcome: 'REJECTED' } })))
  const { request, auth } = await importFresh()
  auth.setLoginUser({ id: 7, nickname: 'user', role: 'USER' })
  const replaceSpy = vi.fn()
  window.location.replace = replaceSpy

  const first = (await request.get('/user/profile').catch((e: unknown) => e)) as import('@/utils/request').ApiError
  const second = (await request.get('/wallet/balance').catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(first.httpStatus, 401)
  assert.equal(second.httpStatus, 401)
  assert.equal(auth.isLoggedIn(), false, 'HTTP 401 清理登录态')
  assert.equal(localStorage.getItem('userId'), null)
  assert.equal(replaceSpy.mock.calls.length, 1, '同一鉴权周期只处理一次 401 跳转')
})

test('skipAuthRedirect 的 401：不提示不跳转，但登录态仍被清理（最终裁决：不能阻止失效）', { timeout: 30000 }, async () => {
  fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(401, { code: 401, message: '未登录', data: null })))
  const { request, auth } = await importFresh()
  auth.setLoginUser({ id: 7, nickname: 'user', role: 'USER' })
  const replaceSpy = vi.fn()
  window.location.replace = replaceSpy

  const error = (await request.get('/chat/unread-count', { skipAuthRedirect: true }).catch((e: unknown) => e)) as import('@/utils/request').ApiError

  assert.equal(auth.isLoggedIn(), false, '属于当前鉴权 epoch 的真实 401 必须清理登录态（skipAuthRedirect 只静默提示与跳转）')
  assert.equal(replaceSpy.mock.calls.length, 0)
  // 完整旧信封（code/message/data 齐备）的业务码 401 是明确拒绝，可清幂等键
  assert.equal(error.idempotencyDisposition, 'CLEAR')
})
