import request from '@/utils/request'

export function startItemConversation(itemId) {
  return request.post('/chat/start', { itemId })
}

export function startCustomerService() {
  return request.post('/chat/customer-service')
}

export function getConversations() {
  return request.get('/chat/conversations')
}

export function getChatMessages(params) {
  return request.get('/chat/messages', { params })
}

export function sendChatMessage(data) {
  return request.post('/chat/send', data)
}

export function getUnreadCount() {
  return request.get('/chat/unread-count')
}

export function getUnreadMessages(params) {
  return request.get('/chat/unread', { params })
}
