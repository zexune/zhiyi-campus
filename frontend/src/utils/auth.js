/**
 * Token & 用户信息管理（localStorage 存取）
 */

const TOKEN_KEY = 'token'
const ROLE_KEY = 'role'
const USER_ID_KEY = 'userId'
const NICKNAME_KEY = 'nickname'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function getRole() {
  return localStorage.getItem(ROLE_KEY)
}

export function setRole(role) {
  localStorage.setItem(ROLE_KEY, role)
}

export function getUserId() {
  return localStorage.getItem(USER_ID_KEY)
}

export function setUserId(id) {
  localStorage.setItem(USER_ID_KEY, id)
}

export function getNickname() {
  return localStorage.getItem(NICKNAME_KEY)
}

export function setNickname(name) {
  localStorage.setItem(NICKNAME_KEY, name)
}

export function isLoggedIn() {
  return !!getToken()
}

export function isAdmin() {
  return getRole() === 'ADMIN'
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(ROLE_KEY)
  localStorage.removeItem(USER_ID_KEY)
  localStorage.removeItem(NICKNAME_KEY)
}
