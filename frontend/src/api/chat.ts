import request from '@/utils/request'
import type { AxiosRequestConfig } from 'axios'
import type { ChatMessage, ChatStartResult, ChatThread, Conversation } from '@/types/models'

export interface ChatMessagesQuery {
  conversationId: string
  peerId?: number
  relatedItemId?: number
  beforeId?: number
}

export interface SendChatPayload {
  conversationId: string
  receiverId: number
  relatedItemId?: number | null
  content: string
}

export function startItemConversation(itemId: number) {
  return request.post<ChatStartResult>('/chat/start', { itemId })
}

export function startCustomerService() {
  return request.post<ChatStartResult>('/chat/customer-service')
}

export function getConversations() {
  return request.get<Conversation[]>('/chat/conversations')
}

export function getChatMessages(params: ChatMessagesQuery, config?: AxiosRequestConfig) {
  return request.get<ChatThread>('/chat/messages', { params, ...config })
}

export function sendChatMessage(data: SendChatPayload) {
  return request.post<ChatMessage>('/chat/send', data)
}

/** 显式已读确认：lastSeenMessageId 为当前视口最后一条可见的"接收"消息 ID */
export function ackChatRead(conversationId: string, lastSeenMessageId: number) {
  return request.post<void>('/chat/ack', null, { params: { conversationId, lastSeenMessageId } })
}

export function getUnreadCount(config?: AxiosRequestConfig) {
  return request.get<number>('/chat/unread-count', config)
}

export function getUnreadMessages(params: { conversationId?: string }) {
  return request.get<ChatMessage[]>('/chat/unread', { params })
}
