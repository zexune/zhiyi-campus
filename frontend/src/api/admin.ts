import request from '@/utils/request'
import type {
  AdminLoginResult,
  AdminUser,
  Category,
  ChatMessage,
  ChatThread,
  Conversation,
  DashboardStats,
  EventTopic,
  Item,
  ItemLineage,
  PageQuery,
  PageResult,
  School,
  TradeHeatEntry,
  ViolationAppeal,
  ViolationReview
} from '@/types/models'

/** 超管控制台接口（D 负责） */

export interface AdminLoginPayload {
  username: string
  password: string
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export interface ViolationListQuery extends PageQuery {
  status?: string
}

export interface ConfirmViolationPayload {
  reason: string
  handleNote: string | null
}

export interface SearchUsersQuery {
  keyword: string
  page: number
  size: number
}

export interface BanUserPayload {
  userId: number
  type: string
  reason: string
  banDays: number | null
}

export interface SearchAdminItemsQuery {
  keyword: string
  status?: string
  page: number
  size: number
}

export interface SchoolPayload {
  name: string
  code: string
  emailDomain: string | null
  status: string
}

export interface CategoryPayload {
  name: string
  icon: string
  sortOrder: number
}

export interface EventTopicPayload {
  title: string
  startTime: string
  endTime: string
  filterType: string | null
  filterCategoryId: number | null
  filterTag: string | null
  bannerText: string
  enabled: boolean
}

/** 管理员独立登录 */
export function adminLogin(data: AdminLoginPayload) {
  return request.post<AdminLoginResult>('/admin/auth/login', data)
}

/** 管理员修改自己的后台密码 */
export function changeAdminPassword(data: ChangePasswordPayload) {
  return request.put<void>('/admin/auth/change-password', data)
}

/** 数据大盘（D2：支持 schoolId 切换学校视角） */
export function getDashboard(schoolId?: number | null) {
  return request.get<DashboardStats>('/admin/dashboard', { params: schoolId ? { schoolId } : {} })
}

/** 违规审核列表 */
export function getViolations(params: ViolationListQuery) {
  return request.get<PageResult<ViolationReview>>('/admin/violations', { params })
}

/** 确认内容违规并执行固定警告扣分 */
export function confirmViolation(id: number, data: ConfirmViolationPayload) {
  return request.put<void>(`/admin/violations/${id}/confirm`, data)
}

/** 驳回违规（放行商品） */
export function dismissViolation(id: number) {
  return request.put<void>(`/admin/violations/${id}/dismiss`)
}

export function getAppeals(params: ViolationListQuery) {
  return request.get<PageResult<ViolationAppeal>>('/admin/appeals', { params })
}

export function approveAppeal(id: number, data: { handleNote: string | null }) {
  return request.put<void>(`/admin/appeals/${id}/approve`, data)
}

export function rejectAppeal(id: number, data: { handleNote: string | null }) {
  return request.put<void>(`/admin/appeals/${id}/reject`, data)
}

/** 用户搜索（封禁弹窗选人） */
export function searchUsers(params: SearchUsersQuery) {
  return request.get<PageResult<AdminUser>>('/admin/users', { params })
}

/** 封禁用户 */
export function banUser(data: BanUserPayload) {
  return request.post<void>('/admin/ban-user', data)
}

/** 用户处罚评分统计（D4） */
export function getPenaltyStats(userId: number) {
  return request.get<Record<string, unknown>>('/admin/penalty-stats', { params: { userId } })
}

/** 解封用户 */
export function unbanUser(data: { userId: number }) {
  return request.post<void>('/admin/unban-user', data)
}

/** 管理员商品检索（4.7 强制下架前选择商品用） */
export function searchAdminItems(params: SearchAdminItemsQuery) {
  return request.get<PageResult<Item>>('/admin/items', { params })
}

/** 交易热力图（D5） */
export function getTradeHeatmap(schoolId?: number | null) {
  return request.get<TradeHeatEntry[]>('/admin/trade-heatmap', { params: schoolId ? { schoolId } : {} })
}

/** 商品传承链（D3） */
export function getItemLineage(itemId: number, schoolId?: number | null) {
  const params = schoolId != null ? { schoolId } : {}
  return request.get<ItemLineage>(`/admin/item/${itemId}/lineage`, { params })
}

/** 强制下架商品 */
export function forceOffShelf(itemId: number) {
  return request.put<void>(`/admin/item/${itemId}/force-off-shelf`)
}

/** 强制重置密码 */
export function resetUserPassword(data: { userId: number }) {
  return request.post<void>('/admin/reset-password', data)
}

/** 客服会话列表 */
export function getAdminSessions() {
  return request.get<Conversation[]>('/admin/chat/sessions')
}

export function getAdminChatMessages(params: { conversationId: string; peerId?: number; relatedItemId?: number; beforeId?: number }) {
  return request.get<ChatThread>('/admin/chat/messages', { params })
}

export function sendAdminChatMessage(data: { conversationId: string; receiverId: number; content: string }) {
  return request.post<ChatMessage>('/admin/chat/send', data)
}

export function getAdminUnreadMessages(params: { conversationId?: string }) {
  return request.get<ChatMessage[]>('/admin/chat/unread', { params })
}

/** 学校管理（D1）。传 {status:'ACTIVE'} 仅返回启用学校，不传返回全部 */
export function getSchools(params?: { status?: string }) {
  return request.get<School[]>('/admin/schools', { params })
}

export function createSchool(data: SchoolPayload) {
  return request.post<School>('/admin/schools', data)
}

export function updateSchool(id: number, data: SchoolPayload) {
  return request.put<School>(`/admin/schools/${id}`, data)
}

export function deleteSchool(id: number) {
  return request.delete<void>(`/admin/schools/${id}`)
}

/** 分类管理 */
export function getAdminCategories() {
  return request.get<Category[]>('/admin/categories')
}

export function createCategory(data: CategoryPayload) {
  return request.post<Category>('/admin/categories', data)
}

export function updateCategory(id: number, data: CategoryPayload) {
  return request.put<Category>(`/admin/categories/${id}`, data)
}

export function deleteCategory(id: number) {
  return request.delete<void>(`/admin/categories/${id}`)
}

export function getEventTopics() {
  return request.get<EventTopic[]>('/admin/event-topics')
}
export function createEventTopic(data: EventTopicPayload) {
  return request.post<EventTopic>('/admin/event-topics', data)
}
export function updateEventTopic(id: number, data: EventTopicPayload) {
  return request.put<EventTopic>(`/admin/event-topics/${id}`, data)
}
export function deleteEventTopic(id: number) {
  return request.delete<void>(`/admin/event-topics/${id}`)
}
