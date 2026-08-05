import request from '@/utils/request'

/** 超管控制台接口（D 负责） */

/** 数据大盘（D2：支持 schoolId 切换学校视角） */
export function getDashboard(schoolId) {
  return request.get('/admin/dashboard', { params: schoolId ? { schoolId } : {} })
}

/** 违规审核列表 */
export function getViolations(params) {
  return request.get('/admin/violations', { params })
}

/** 确认违规并封禁 */
export function confirmViolation(id, data) {
  return request.put(`/admin/violations/${id}/confirm`, data)
}

/** 驳回违规（放行商品） */
export function dismissViolation(id) {
  return request.put(`/admin/violations/${id}/dismiss`)
}

/** 用户搜索（封禁弹窗选人） */
export function searchUsers(params) {
  return request.get('/admin/users', { params })
}

/** 封禁用户 */
export function banUser(data) {
  return request.post('/admin/ban-user', data)
}

/** 用户处罚评分统计（D4） */
export function getPenaltyStats(userId) {
  return request.get('/admin/penalty-stats', { params: { userId } })
}

/** 解封用户 */
export function unbanUser(data) {
  return request.post('/admin/unban-user', data)
}

/** 管理员商品检索（4.7 强制下架前选择商品用） */
export function searchAdminItems(params) {
  return request.get('/admin/items', { params })
}

/** 交易热力图（D5） */
export function getTradeHeatmap(schoolId) {
  return request.get('/admin/trade-heatmap', { params: schoolId ? { schoolId } : {} })
}

/** 商品传承链（D3） */
export function getItemLineage(itemId, schoolId) {
  const params = schoolId != null ? { schoolId } : {}
  return request.get(`/admin/item/${itemId}/lineage`, { params })
}

/** 强制下架商品 */
export function forceOffShelf(itemId) {
  return request.put(`/admin/item/${itemId}/force-off-shelf`)
}

/** 强制重置密码 */
export function resetUserPassword(data) {
  return request.post('/admin/reset-password', data)
}

/** 客服会话列表 */
export function getAdminSessions() {
  return request.get('/admin/chat/sessions')
}

export function getAdminChatMessages(params) {
  return request.get('/admin/chat/messages', { params })
}

export function sendAdminChatMessage(data) {
  return request.post('/admin/chat/send', data)
}

export function getAdminUnreadMessages(params) {
  return request.get('/admin/chat/unread', { params })
}

/** 学校管理（D1）。传 {status:'ACTIVE'} 仅返回启用学校，不传返回全部 */
export function getSchools(params) {
  return request.get('/admin/schools', { params })
}

export function createSchool(data) {
  return request.post('/admin/schools', data)
}

export function updateSchool(id, data) {
  return request.put(`/admin/schools/${id}`, data)
}

export function deleteSchool(id) {
  return request.delete(`/admin/schools/${id}`)
}

/** 分类管理 */
export function getAdminCategories() {
  return request.get('/admin/categories')
}

export function createCategory(data) {
  return request.post('/admin/categories', data)
}

export function updateCategory(id, data) {
  return request.put(`/admin/categories/${id}`, data)
}

export function deleteCategory(id) {
  return request.delete(`/admin/categories/${id}`)
}

export function getEventTopics() { return request.get('/admin/event-topics') }
export function createEventTopic(data) { return request.post('/admin/event-topics', data) }
export function updateEventTopic(id, data) { return request.put(`/admin/event-topics/${id}`, data) }
export function deleteEventTopic(id) { return request.delete(`/admin/event-topics/${id}`) }
