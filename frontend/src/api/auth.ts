import { contracts } from '@/types/contracts'
import type { Schemas } from '@/types/contracts'
import { mapLoginData, mapPageData, mapRequiredData, mapVoidData } from '@/api/mappers'
import type { ExpLog, PageQuery, School, UserProfile } from '@/types/models'
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

/** 卖家档案（生成的命名类型） */
export type SellerDetail = Schemas['SellerDetailVO']

// —— 学校（创新功能 A9，公开）——
export function getSchools() {
  return contracts.get('/api/school/list').then((res) => mapRequiredData(res, '/api/school/list', (wire) => wire as School[]))
}

// —— 认证 ——
export function register(data: RegisterPayload) {
  return contracts.post('/api/auth/register', { body: { ...data, schoolEmail: data.schoolEmail ?? undefined } }).then((res) => mapLoginData(res, '/api/auth/register', 'studentId'))
}

export function login(data: LoginPayload) {
  return contracts.post('/api/auth/login', { body: data }).then((res) => mapLoginData(res, '/api/auth/login', 'studentId'))
}

export function getSecurityQuestion(schoolId: number, studentId: string) {
  return contracts
    .get('/api/auth/security-question', { query: { schoolId, studentId } })
    .then((res) => mapRequiredData(res, '/api/auth/security-question', (wire) => wire as Schemas['SecurityQuestionVO']))
}

export function getSecurityQuestions() {
  return contracts.get('/api/auth/security-questions').then((res) => mapRequiredData(res, '/api/auth/security-questions', (wire) => wire as string[]))
}

export function resetPassword(data: ResetPasswordPayload) {
  return contracts.post('/api/auth/reset-password', { body: data }).then(mapVoidData)
}

/** 登出：后端清除 httpOnly 会话 Cookie */
export function logout() {
  return contracts.post('/api/auth/logout').then(mapVoidData)
}

// —— 用户信息 & 成长体系 ——
export function getProfile() {
  return contracts.get('/api/user/profile').then((res) => mapRequiredData(res, '/api/user/profile', (wire) => wire as UserProfile))
}

export function updateProfile(data: UpdateProfilePayload) {
  return contracts.put('/api/user/profile', { body: { ...data, schoolEmail: data.schoolEmail ?? undefined } }).then((res) => mapRequiredData(res, '/api/user/profile', (wire) => wire as UserProfile))
}

/**
 * 上传用户头像（单文件替换语义，非多图列表）。
 * 走受控 postFile：multipart 契约（必填 file 字段）由生成类型推导；
 * 服务端校验类型（jpg/jpeg/png/webp）与 ≤2MB，客户端预校验在页面侧完成。
 * 返回的最新资料会推进 profileVersion，调用方须用它同步表单版本，否则后续 PUT /profile 会 409。
 */
export function uploadUserAvatar(file: File) {
  return contracts.postFile('/api/user/avatar', file).then((res) => mapRequiredData(res, '/api/user/avatar', (wire) => wire as UserProfile))
}

export function getExpLog(params: PageQuery) {
  return contracts.get('/api/user/exp-log', { query: params }).then((res) => mapPageData(res, '/api/user/exp-log', (row): ExpLog => row))
}

/** 登录后查看商品发布者的联系与校园资料 */
export function getSellerDetail(userId: number) {
  return contracts.get('/api/user/{id}/seller-detail', { path: { id: userId } }).then((res) => mapRequiredData(res, '/api/user/{id}/seller-detail', (wire) => wire as SellerDetail))
}

// 伪熟人信任标签（A5）：登录用户视角看目标用户 → ["同学院","同级","同校区","同楼"]
export function getUserRelation(userId: number) {
  return contracts.get('/api/user/{id}/relation', { path: { id: userId } }).then((res) => mapRequiredData(res, '/api/user/{id}/relation', (wire) => wire as string[]))
}

// 信誉雷达六维分值（A6，公开）
export function getUserReputation(userId: number) {
  return contracts.get('/api/user/{id}/reputation', { path: { id: userId } }).then((res) => mapRequiredData(res, '/api/user/{id}/reputation', (wire) => wire as ReputationVo))
}

// —— 账号安全 ——
export function changePassword(data: ChangePasswordPayload) {
  return contracts.put('/api/user/change-password', { body: data }).then(mapVoidData)
}

export function cancelAccount(data: { password: string }) {
  return contracts.post('/api/user/cancel-account', { body: data }).then(mapVoidData)
}
