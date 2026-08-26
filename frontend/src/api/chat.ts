import { contracts } from '@/types/contracts'
import type { TransportOptions } from '@/types/contracts'
import { mapRequiredData, mapVoidData } from '@/api/mappers'
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
  return contracts.post('/api/chat/start', { body: { itemId } }).then((res) => mapRequiredData(res, '/api/chat/start', (wire) => wire as ChatStartResult))
}

export function startCustomerService() {
  return contracts.post('/api/chat/customer-service').then((res) => mapRequiredData(res, '/api/chat/customer-service', (wire) => wire as ChatStartResult))
}

export function getConversations() {
  return contracts.get('/api/chat/conversations').then((res) => mapRequiredData(res, '/api/chat/conversations', (wire) => wire as Conversation[]))
}

/** transport 承载超时/静默等传输层选项，不能绕过 query/header 契约 */
export function getChatMessages(params: ChatMessagesQuery, transport?: TransportOptions) {
  return contracts.get('/api/chat/messages', { query: params, transport }).then((res) => mapRequiredData(res, '/api/chat/messages', (wire) => wire as ChatThread))
}

export function sendChatMessage(data: SendChatPayload) {
  return contracts.post('/api/chat/send', { body: { ...data, relatedItemId: data.relatedItemId ?? undefined } }).then((res) => mapRequiredData(res, '/api/chat/send', (wire) => wire as ChatMessage))
}

/** 显式已读确认：lastSeenMessageId 为当前视口最后一条可见的"接收"消息 ID */
export function ackChatRead(conversationId: string, lastSeenMessageId: number) {
  return contracts.post('/api/chat/ack', { query: { conversationId, lastSeenMessageId } }).then(mapVoidData)
}

export function getUnreadCount(transport?: TransportOptions) {
  return contracts.get('/api/chat/unread-count', { transport }).then((res) => mapRequiredData(res, '/api/chat/unread-count', (wire) => wire as number))
}

export function getUnreadMessages(params: { conversationId?: string }) {
  return contracts.get('/api/chat/unread', { query: params }).then((res) => mapRequiredData(res, '/api/chat/unread', (wire) => wire as ChatMessage[]))
}
