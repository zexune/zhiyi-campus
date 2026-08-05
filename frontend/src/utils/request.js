import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearAuth } from '@/utils/auth'

/**
 * 统一的 axios 实例 —— 所有 API 请求都通过它
 * 自动携带 JWT Token，自动提示错误
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 请求拦截器：自动带 Token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function redirectToLogin() {
  clearAuth()
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = '/login'
  }
}

// 响应拦截器：统一错误处理
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 后端统一返回 { code, message, data }
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        redirectToLogin()
      }
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  (error) => {
    // 后端拦截器（JWT/角色校验）走 HTTP 401/403 + { code, message } 结构
    const res = error.response?.data
    if (error.response?.status === 401) {
      ElMessage.error(res?.message || '登录已失效，请重新登录')
      redirectToLogin()
    } else if (res?.message) {
      ElMessage.error(res.message)
    } else {
      ElMessage.error('网络错误，请稍后再试')
    }
    return Promise.reject(error)
  }
)

export default request
