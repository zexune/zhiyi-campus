import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getProfile, logout as logoutApi } from '@/api/auth'
import { isLoggedIn as isAuthed, setLoginUser, getStoredUser, clearAuth } from '@/utils/auth'

/**
 * 用户全局状态（模块一）
 * 登录后调用 setLogin(loginVO)；任意页面用 userStore.user 取当前用户。
 *
 * 凭证由 httpOnly Cookie 承载（见 utils/auth.js），本 store 不再持久化任何字段，
 * 刷新页面后从 auth.js 的展示信息恢复用户摘要，fetchProfile 再升级为完整资料。
 */
export const useUserStore = defineStore('user', () => {
  const user = ref(getStoredUser())

  // isAuthed 读取 auth.js 的模块级 ref，登录/登出后所有依赖处自动更新
  const isLoggedIn = computed(() => isAuthed())
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  /** 登录/注册成功后写入（后端 LoginVO：{ token, user }；token 走 Cookie，忽略） */
  function setLogin({ user: u }) {
    setLoginUser(u)
    user.value = u
  }

  /** 拉取并刷新当前用户资料（封禁/角色变化在下次调用后生效） */
  async function fetchProfile() {
    if (!isAuthed()) return null
    const res = await getProfile()
    user.value = res.data
    setLoginUser(res.data)
    return res.data
  }

  /** 登出：通知后端清除会话 Cookie，再清理本地展示信息 */
  async function logout() {
    try {
      await logoutApi()
    } catch {
      // 后端不可达时仍继续本地清理，保证用户不会被卡在登录态
    }
    clearAuth()
    user.value = null
  }

  return { user, isLoggedIn, isAdmin, setLogin, fetchProfile, logout }
})
