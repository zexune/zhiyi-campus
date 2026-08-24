import request from '@/utils/request'
import type { ExpLog, LoginResult, School, UserProfile } from '@/types/models'
import type { PageQuery, PageResult } from '@/types/models'
import type { ReputationVo } from '@/utils/reputation'

/** 模块一：认证与用户 */

export interface LoginPayload {
  schoolId: number
  studentId: string
  password: string
}

export interface RegisterPayload {
  studentId: string
  password: string
  confirmPassword: string
  nickname: string
  schoolId: number
  schoolEmail: string | null
  securityQuestion: string
  securityAnswer: string
  phone: string
}

export interface ResetPasswordPayload {
  schoolId: number
  studentId: string
  securityAnswer: string
  newPassword: string
  confirmPassword: string
}

export interface UpdateProfilePayload {
  /** 资料乐观并发版本：读取资料时返回，提交必须携带；不匹配返回 1010 冲突与最新资料 */
  profileVersion: number
  nickname: string
  phone: string
  schoolId: number
  schoolEmail: string | null
  campus: string
  college: string
  grade: string
  dormitory: string
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

// —— 学校（创新功能 A9，公开）——
export function getSchools() {
  return request.get<School[]>('/school/list')
}

// —— 认证 ——
export function register(data: RegisterPayload) {
  return request.post<LoginResult>('/auth/register', data)
}

export function login(data: LoginPayload) {
  return request.post<LoginResult>('/auth/login', data)
}

export function getSecurityQuestion(schoolId: number, studentId: string) {
  return request.get<{ question: string }>('/auth/security-question', { params: { schoolId, studentId } })
}

export function getSecurityQuestions() {
  return request.get<string[]>('/auth/security-questions')
}

export function resetPassword(data: ResetPasswordPayload) {
  return request.post<void>('/auth/reset-password', data)
}

/** 登出：后端清除 httpOnly 会话 Cookie */
export function logout() {
  return request.post<void>('/auth/logout')
}

// —— 用户信息 & 成长体系 ——
export function getProfile() {
  return request.get<UserProfile>('/user/profile')
}

export function updateProfile(data: UpdateProfilePayload) {
  return request.put<void>('/user/profile', data)
}

export function getExpLog(params: PageQuery) {
  return request.get<PageResult<ExpLog>>('/user/exp-log', { params })
}

export function getUserCard(userId: number) {
  return request.get<Record<string, unknown>>(`/user/${userId}/card`)
}

/** 登录后查看商品发布者的联系与校园资料 */
export function getSellerDetail(userId: number) {
  return request.get<Record<string, unknown>>(`/user/${userId}/seller-detail`)
}

// 伪熟人信任标签（A5）：登录用户视角看目标用户 → ["同学院","同级","同校区","同楼"]
export function getUserRelation(userId: number) {
  return request.get<string[]>(`/user/${userId}/relation`)
}

// 信誉雷达六维分值（A6，公开）
export function getUserReputation(userId: number) {
  return request.get<ReputationVo>(`/user/${userId}/reputation`)
}

// —— 账号安全 ——
export function changePassword(data: ChangePasswordPayload) {
  return request.put<void>('/user/change-password', data)
}

export function cancelAccount(data: { password: string }) {
  return request.post<void>('/user/cancel-account', data)
}
