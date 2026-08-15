import request from '@/utils/request'

/** 模块一：认证与用户（负责人 A） */

// —— 学校（创新功能 A9，公开）——
export function getSchools() {
  return request.get('/school/list')
}

// —— 认证 ——
export function register(data) {
  return request.post('/auth/register', data)
}

export function login(data) {
  return request.post('/auth/login', data)
}

export function getSecurityQuestion(schoolId, studentId) {
  return request.get('/auth/security-question', { params: { schoolId, studentId } })
}

export function getSecurityQuestions() {
  return request.get('/auth/security-questions')
}

export function resetPassword(data) {
  return request.post('/auth/reset-password', data)
}

/** 登出：后端清除 httpOnly 会话 Cookie */
export function logout() {
  return request.post('/auth/logout')
}

// —— 用户信息 & 成长体系 ——
export function getProfile() {
  return request.get('/user/profile')
}

export function updateProfile(data) {
  return request.put('/user/profile', data)
}

export function getExpLog(params) {
  return request.get('/user/exp-log', { params })
}

export function getUserCard(userId) {
  return request.get(`/user/${userId}/card`)
}

/** 登录后查看商品发布者的联系与校园资料 */
export function getSellerDetail(userId) {
  return request.get(`/user/${userId}/seller-detail`)
}

// 伪熟人信任标签（A5）：登录用户视角看目标用户 → ["同学院","同级","同校区","同楼"]
export function getUserRelation(userId) {
  return request.get(`/user/${userId}/relation`)
}

// 信誉雷达六维分值（A6，公开）
export function getUserReputation(userId) {
  return request.get(`/user/${userId}/reputation`)
}

// —— 账号安全 ——
export function changePassword(data) {
  return request.put('/user/change-password', data)
}

export function cancelAccount(data) {
  return request.post('/user/cancel-account', data)
}
