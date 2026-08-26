import { contracts } from '@/types/contracts'
import type { ApiResult, Schemas } from '@/types/contracts'
import { mapLoginData, mapPageData, mapRequiredData, mapVoidData, ProtocolViolationError } from '@/api/mappers'
import type {
  AdminUser,
  Category,
  ChatMessage,
  ChatThread,
  Conversation,
  DashboardStats,
  EventTopic,
  ItemLineage,
  PageQuery,
  PenaltyStats,
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

export interface AdminUserQuery extends PageQuery {
  /** 学校精确匹配；不传查全部学校 */
  schoolId?: number | null
  /** 以下字段均为模糊搜索 */
  studentId?: string
  nickname?: string
  email?: string
  phone?: string
}

export interface BanUserPayload {
  userId: number
  type: string
  reason: string
  banDays: number | null
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
  /** 商品标签筛选：零到多个，任一命中即属于专题 */
  filterTags: string[] | null
  bannerText: string
  enabled: boolean
}

/** 管理员独立登录 */
export function adminLogin(data: AdminLoginPayload) {
  return contracts.post('/api/admin/auth/login', { body: data }).then((res) => mapLoginData(res, '/api/admin/auth/login', 'username'))
}

/** 管理员修改自己的后台密码 */
export function changeAdminPassword(data: ChangePasswordPayload) {
  return contracts.put('/api/admin/auth/change-password', { body: data }).then(mapVoidData)
}

/** 数据大盘（D2：支持 schoolId 切换学校视角） */
export function getDashboard(schoolId?: number | null) {
  return contracts.get('/api/admin/dashboard', { query: schoolId != null ? { schoolId } : {} }).then((res) => mapRequiredData(res, '/api/admin/dashboard', (wire) => wire as DashboardStats))
}

/** 违规审核列表 */
export function getViolations(params: ViolationListQuery) {
  return contracts.get('/api/admin/violations', { query: params }).then((res) => mapPageData(res, '/api/admin/violations', toViolationReview))
}

/** wire（ViolationVO）→ 工作台行；关键操作字段缺失时必须暴露协议违约。 */
function toViolationReview(row: Schemas['ViolationVO']): ViolationReview {
  if (typeof row.id !== 'number' || !Number.isSafeInteger(row.id) || row.id <= 0) {
    throw new ProtocolViolationError(`/api/admin/violations 的 ViolationVO.id 非法：${String(row.id)}`)
  }
  const requiredString = (field: 'source' | 'status' | 'originalTitle' | 'createdAt'): string => {
    const value = row[field]
    if (typeof value !== 'string') {
      throw new ProtocolViolationError(`/api/admin/violations 的 ViolationVO.${field} 不是字符串`)
    }
    return value
  }
  return {
    id: row.id,
    source: requiredString('source'),
    status: requiredString('status'),
    originalTitle: requiredString('originalTitle'),
    createdAt: requiredString('createdAt'),
    ruleVersion: row.ruleVersion,
    violationType: row.violationType,
    originalDescription: row.originalDescription,
    violationReason: row.violationReason,
    handleNote: row.handleNote,
    matchedRules: row.matchedRules,
    sellerName: row.sellerName,
    userId: row.userId,
    reporterId: row.reporterId ?? null,
    reporterName: row.reporterName ?? null,
    handlerName: row.handlerName,
    itemStatus: row.itemStatus
  }
}

/** 确认内容违规并执行固定警告扣分 */
export function confirmViolation(id: number, data: ConfirmViolationPayload) {
  return contracts
    .put('/api/admin/violations/{id}/confirm', {
      path: { id },
      body: { reason: data.reason, handleNote: data.handleNote ?? undefined }
    })
    .then(mapVoidData)
}

/** 驳回违规（放行商品） */
export function dismissViolation(id: number) {
  return contracts.put('/api/admin/violations/{id}/dismiss', { path: { id } }).then(mapVoidData)
}

export function getAppeals(params: ViolationListQuery) {
  return contracts.get('/api/admin/appeals', { query: params }).then((res) => mapPageData(res, '/api/admin/appeals', (row): ViolationAppeal => row))
}

export function approveAppeal(id: number, data: { handleNote: string | null }) {
  return contracts.put('/api/admin/appeals/{id}/approve', { path: { id }, body: { handleNote: data.handleNote ?? undefined } }).then(mapVoidData)
}

export function rejectAppeal(id: number, data: { handleNote: string | null }) {
  return contracts.put('/api/admin/appeals/{id}/reject', { path: { id }, body: { handleNote: data.handleNote ?? undefined } }).then(mapVoidData)
}

/** 管理端用户列表：学校精确 + 学号/昵称/邮箱/手机号模糊搜索 */
export function searchAdminUsers(params: AdminUserQuery) {
  return contracts.get('/api/admin/users', { query: { ...params, schoolId: params.schoolId ?? undefined } }).then((res) => mapPageData(res, '/api/admin/users', (row): AdminUser => row))
}

/** 封禁用户 */
export function banUser(data: BanUserPayload) {
  return contracts.post('/api/admin/ban-user', { body: { ...data, banDays: data.banDays ?? undefined } }).then(mapVoidData)
}

/** 用户处罚评分统计（D4） */
export function getPenaltyStats(userId: number): Promise<ApiResult<PenaltyStats>> {
  return contracts.get('/api/admin/penalty-stats', { query: { userId } }).then((res) => mapRequiredData(res, '/api/admin/penalty-stats', (wire) => wire as PenaltyStats))
}

/** 解封用户 */
export function unbanUser(data: { userId: number }) {
  return contracts.post('/api/admin/unban-user', { body: data }).then(mapVoidData)
}

/** 交易热力图（D5） */
export function getTradeHeatmap(schoolId?: number | null) {
  return contracts.get('/api/admin/trade-heatmap', { query: schoolId != null ? { schoolId } : {} }).then((res) => mapRequiredData(res, '/api/admin/trade-heatmap', (wire) => wire as TradeHeatEntry[]))
}

/** 商品传承链（D3） */
export function getItemLineage(itemId: number, schoolId?: number | null) {
  return contracts
    .get('/api/admin/item/{id}/lineage', {
      path: { id: itemId },
      query: schoolId != null ? { schoolId } : {}
    })
    .then((res) => mapRequiredData(res, '/api/admin/item/{id}/lineage', (wire) => wire as ItemLineage))
}

/** 标签建议（管理端）：按专题名称生成候选，仅供选择，不落库 */
export function getAdminItemTagSuggestions(title: string, categoryId?: number | null) {
  return contracts
    .post('/api/admin/item/tag-suggestions', { body: { title, categoryId: categoryId ?? undefined } })
    .then((res) => mapRequiredData(res, '/api/admin/item/tag-suggestions', (wire) => wire as string[]))
}

/** 强制重置密码 */
export function resetUserPassword(data: { userId: number }) {
  return contracts.post('/api/admin/reset-password', { body: data }).then(mapVoidData)
}

/** 客服会话列表 */
export function getAdminSessions() {
  return contracts.get('/api/admin/chat/sessions').then((res) => mapRequiredData(res, '/api/admin/chat/sessions', (wire) => wire as Conversation[]))
}

export function getAdminChatMessages(params: { conversationId: string; peerId?: number; relatedItemId?: number; beforeId?: number }) {
  return contracts.get('/api/admin/chat/messages', { query: params }).then((res) => mapRequiredData(res, '/api/admin/chat/messages', (wire) => wire as ChatThread))
}

/** 管理端同模式显式已读确认（GET messages 只读） */
export function ackAdminChatRead(conversationId: string, lastSeenMessageId: number) {
  return contracts.post('/api/admin/chat/ack', { query: { conversationId, lastSeenMessageId } }).then(mapVoidData)
}

export function sendAdminChatMessage(data: { conversationId: string; receiverId: number; content: string }) {
  return contracts.post('/api/admin/chat/send', { body: data }).then((res) => mapRequiredData(res, '/api/admin/chat/send', (wire) => wire as ChatMessage))
}

export function getAdminUnreadMessages(params: { conversationId?: string }) {
  return contracts.get('/api/admin/chat/unread', { query: params }).then((res) => mapRequiredData(res, '/api/admin/chat/unread', (wire) => wire as ChatMessage[]))
}

/** 学校管理（D1）。传 {status:'ACTIVE'} 仅返回启用学校，不传返回全部 */
export function getSchools(params?: { status?: string }) {
  return contracts.get('/api/admin/schools', { query: params }).then((res) => mapRequiredData(res, '/api/admin/schools', (wire) => wire as School[]))
}

export function createSchool(data: SchoolPayload) {
  return contracts.post('/api/admin/schools', { body: { ...data, emailDomain: data.emailDomain ?? undefined } }).then((res) => mapRequiredData(res, '/api/admin/schools', (wire) => wire as School))
}

export function updateSchool(id: number, data: SchoolPayload) {
  return contracts
    .put('/api/admin/schools/{id}', {
      path: { id },
      body: { ...data, emailDomain: data.emailDomain ?? undefined }
    })
    .then((res) => mapRequiredData(res, '/api/admin/schools/{id}', (wire) => wire as School))
}

export function deleteSchool(id: number) {
  return contracts.delete('/api/admin/schools/{id}', { path: { id } }).then(mapVoidData)
}

/** 分类管理 */
export function getAdminCategories() {
  return contracts.get('/api/admin/categories').then((res) => mapRequiredData(res, '/api/admin/categories', (wire) => wire as Category[]))
}

export function createCategory(data: CategoryPayload) {
  return contracts.post('/api/admin/categories', { body: data }).then((res) => mapRequiredData(res, '/api/admin/categories', (wire) => wire as Category))
}

export function updateCategory(id: number, data: CategoryPayload) {
  return contracts.put('/api/admin/categories/{id}', { path: { id }, body: data }).then((res) => mapRequiredData(res, '/api/admin/categories/{id}', (wire) => wire as Category))
}

export function deleteCategory(id: number) {
  return contracts.delete('/api/admin/categories/{id}', { path: { id } }).then(mapVoidData)
}

function toTopicBody(data: EventTopicPayload) {
  return {
    ...data,
    filterType: data.filterType ?? undefined,
    filterCategoryId: data.filterCategoryId ?? undefined,
    filterTags: data.filterTags ?? undefined
  }
}

export function getEventTopics() {
  return contracts.get('/api/admin/event-topics').then((res) => mapRequiredData(res, '/api/admin/event-topics', (wire) => wire as EventTopic[]))
}
export function createEventTopic(data: EventTopicPayload) {
  return contracts.post('/api/admin/event-topics', { body: toTopicBody(data) }).then((res) => mapRequiredData(res, '/api/admin/event-topics', (wire) => wire as EventTopic))
}
export function updateEventTopic(id: number, data: EventTopicPayload) {
  return contracts.put('/api/admin/event-topics/{id}', { path: { id }, body: toTopicBody(data) }).then((res) => mapRequiredData(res, '/api/admin/event-topics/{id}', (wire) => wire as EventTopic))
}
export function deleteEventTopic(id: number) {
  return contracts.delete('/api/admin/event-topics/{id}', { path: { id } }).then(mapVoidData)
}
