import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getProfile } from '@/api/auth'
import { setToken, setRole, setUserId, setNickname, clearAuth, getToken } from '@/utils/auth'

/**
 * 用户全局状态（模块一）
 * 登录后调用 setLogin(loginVO)；任意页面用 userStore.user 取当前用户
 */
export const useUserStore = defineStore('user', () => {
  const user = ref(null)

  const isLoggedIn = computed(() => !!getToken())
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  /** 登录/注册成功后写入（后端 LoginVO：{ token, user }） */
  function setLogin({ token, user: u }) {
    setToken(token)
    setRole(u.role)
    setUserId(u.id)
    setNickname(u.nickname)
    user.value = u
  }

  /** 刷新页面后恢复用户信息 */
  async function fetchProfile() {
    if (!getToken()) return null
    const res = await getProfile()
    user.value = res.data
    setRole(res.data.role)
    setNickname(res.data.nickname)
    return res.data
  }

  function logout() {
    clearAuth()
    user.value = null
  }

  return { user, isLoggedIn, isAdmin, setLogin, fetchProfile, logout }
})
