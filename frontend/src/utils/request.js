import axios from 'axios'
import { clearAuth, getRole } from '@/utils/auth'

const MAX_JSON_RESPONSE_SIZE = 5 * 1024 * 1024
const BLOCKED_JSON_KEYS = new Set(['__proto__', 'constructor', 'prototype'])

function parseApiJson(data) {
  if (typeof data !== 'string' || data.length === 0) return data
  if (data.length > MAX_JSON_RESPONSE_SIZE) {
    throw new Error('响应数据过大')
  }
  return JSON.parse(data, (key, value) => (BLOCKED_JSON_KEYS.has(key) ? undefined : value))
}

function isApiEnvelope(value) {
  return (
    value !== null &&
    typeof value === 'object' &&
    !Array.isArray(value) &&
    Object.hasOwn(value, 'code') &&
    Object.hasOwn(value, 'message') &&
    Object.hasOwn(value, 'data') &&
    Number.isInteger(value.code)
  )
}

/**
 * 统一的 axios 实例 —— 所有 API 请求都通过它
 * 登录凭证由后端 httpOnly Cookie 自动携带，无需手动附加 Token
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  adapter: 'fetch',
  allowAbsoluteUrls: false,
  // 跨源部署（前后端不同域）时也携带会话 Cookie
  withCredentials: true,
  withXSRFToken: false,
  maxContentLength: MAX_JSON_RESPONSE_SIZE,
  transformResponse: [parseApiJson],
  headers: { Accept: 'application/json' }
})

function redirectToLogin() {
  const adminSession = getRole() === 'ADMIN' || window.location.pathname.startsWith('/admin/')
  const loginPath = adminSession ? '/admin/login' : '/login'
  clearAuth()
  if (window.location.pathname !== loginPath) {
    window.location.href = loginPath
  }
}

// 响应拦截器：统一错误处理
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 后端统一返回 { code, message, data }
    if (!isApiEnvelope(res)) {
      ElMessage.error('服务器响应格式异常')
      return Promise.reject(new Error('Invalid API response envelope'))
    }
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        redirectToLogin()
      }
      return Promise.reject(new Error(res.message || '请求失败'))
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
