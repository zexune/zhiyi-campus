import { ref } from 'vue'

/**
 * 登录态管理（唯一真相源）
 *
 * 凭证（JWT）由后端写入 httpOnly Cookie，前端 JavaScript 不持有、不存储 token，
 * 从根上消除 XSS 窃取凭证的攻击面；请求自动携带 Cookie（同源或 withCredentials）。
 *
 * localStorage 只保存非敏感的展示信息（userId / 昵称 / 角色），并由此派生响应式登录态；
 * 登录态真实有效性仍由服务端校验，会话过期时接口返回 401 并清理本地展示信息。
 *
 * authEpoch（F6/F11 根因修复）：模块级单调递增的鉴权周期号。
 * - setLoginUser / clearAuth 推进 epoch，使所有在途旧请求立即"过期"；
 * - fetchProfile 等跨登录周期的异步落地前校验四元组（isAuthed + epoch + userId + 响应归属），
 *   登出/换号后旧响应不得复活登录态；
 * - 401 单飞按 epoch 绑定：旧账号请求返回的 401 不清除新登录态。
 */

const ROLE_KEY = 'role'
const USER_ID_KEY = 'userId'
const NICKNAME_KEY = 'nickname'
// 旧版本遗留的 token 键，清理以免残留过期凭证
const LEGACY_TOKEN_KEY = 'token'
const LEGACY_PERSISTED_USER_KEY = 'zhiyi-user'

/** localStorage 中的用户摘要形状 */
export interface StoredUser {
  id: number
  nickname: string
  role: string
}

/** 鉴权上下文快照：跨周期校验的最小四元组 */
export interface AuthContext {
  epoch: number
  userId: number | null
}

const authEpoch = ref(0)

function hasStoredUser(): boolean {
  return !!localStorage.getItem(USER_ID_KEY)
}

// 模块级 ref：所有调用 isLoggedIn() 的 computed / 模板都会自动响应登录/登出
const loggedIn = ref(hasStoredUser())

export function isLoggedIn(): boolean {
  return loggedIn.value
}

export function isAdmin(): boolean {
  return getRole() === 'ADMIN'
}

export function getRole(): string | null {
  return localStorage.getItem(ROLE_KEY)
}

export function getUserId(): string | null {
  return localStorage.getItem(USER_ID_KEY)
}

export function getNickname(): string | null {
  return localStorage.getItem(NICKNAME_KEY)
}

/** 当前鉴权上下文（epoch + userId）：异步操作发起时捕获、落地前比对 */
export function getAuthContext(): AuthContext {
  const raw = localStorage.getItem(USER_ID_KEY)
  const id = raw === null ? null : Number(raw)
  return { epoch: authEpoch.value, userId: id !== null && Number.isFinite(id) ? id : null }
}

/** 当前鉴权周期号 */
export function getAuthEpoch(): number {
  return authEpoch.value
}

/** 上下文是否仍有效（epoch 与 userId 均与当前一致） */
export function isAuthContextCurrent(context: AuthContext): boolean {
  const current = getAuthContext()
  return current.epoch === context.epoch && current.userId === context.userId
}

/** localStorage 中的用户摘要（刷新页面后恢复 Pinia user 状态用） */
export function getStoredUser(): StoredUser | null {
  if (!hasStoredUser()) return null
  const id = Number(getUserId())
  if (!Number.isFinite(id)) return null
  return {
    id,
    nickname: getNickname() || '',
    role: getRole() || 'USER'
  }
}

/** 登录/注册成功后写入用户摘要并置为已登录；token 由 httpOnly Cookie 承载，此处忽略。 */
export function setLoginUser(user: { id: number | string; nickname?: string | null; role?: string | null }): void {
  if (!user) return
  localStorage.setItem(USER_ID_KEY, String(user.id))
  localStorage.setItem(NICKNAME_KEY, user.nickname || '')
  localStorage.setItem(ROLE_KEY, user.role || 'USER')
  localStorage.removeItem(LEGACY_TOKEN_KEY)
  localStorage.removeItem(LEGACY_PERSISTED_USER_KEY)
  authEpoch.value += 1
  loggedIn.value = true
}

export function clearAuth(): void {
  localStorage.removeItem(ROLE_KEY)
  localStorage.removeItem(USER_ID_KEY)
  localStorage.removeItem(NICKNAME_KEY)
  localStorage.removeItem(LEGACY_TOKEN_KEY)
  localStorage.removeItem(LEGACY_PERSISTED_USER_KEY)
  authEpoch.value += 1
  loggedIn.value = false
}
