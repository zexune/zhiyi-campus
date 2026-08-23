<template>
  <AdminLayout>
    <div class="chat-admin-page rise">
      <!-- 页面标题 -->
      <div class="page-title">
        客服收件箱
      </div>

      <!-- 主体：左右分栏 -->
      <div class="chat-layout card">
        <!-- ===== 左侧：会话列表 ===== -->
        <div class="chat-sidebar">
          <div class="sidebar-header">
            <span class="sidebar-title">会话列表</span>
            <button class="btn btn--sm btn--ghost" :disabled="sessionsLoading" aria-label="刷新会话列表" @click="fetchSessions">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 11a8 8 0 1 0-2.34 5.66" />
                <path d="M20 4v7h-7" />
              </svg>
            </button>
          </div>

          <div v-if="sessionsLoading" class="sidebar-state muted">加载中...</div>
          <div v-else-if="sessions.length === 0" class="sidebar-state muted">暂无客服会话</div>
          <div v-else class="session-list">
            <div v-for="s in sessions" :key="s.conversationId" class="session-item" :class="{ active: activeConv === s.conversationId }" @click="openSession(s)">
              <UserAvatar :nickname="s.peer?.nickname || '?'" :user-id="s.peer?.id || 0" size="m" />
              <div class="session-item__info">
                <div class="session-item__top">
                  <span class="session-item__name">{{ s.peer?.nickname || '未知用户' }}</span>
                  <LevelBadge v-if="s.peer?.level" :level="s.peer.level" />
                  <span class="session-item__time muted">{{ formatChatTime(s.lastMessageTime) }}</span>
                </div>
                <div class="session-item__preview muted">
                  {{ truncate(s.lastMessage, 40) }}
                </div>
              </div>
              <span v-if="s.unreadCount > 0" class="unread-dot">{{ s.unreadCount > 99 ? '99+' : s.unreadCount }}</span>
            </div>
          </div>
        </div>

        <!-- ===== 右侧：聊天详情 ===== -->
        <div class="chat-main">
          <!-- 未选择会话 -->
          <div v-if="!activeConv" class="chat-placeholder">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4Z" />
              <path d="M8 9h8M8 13h5" />
            </svg>
            <span>在左侧选择一个会话开始回复</span>
          </div>

          <!-- 聊天中 -->
          <template v-else>
            <!-- 顶部：对方信息 -->
            <div class="chat-header">
              <UserAvatar :nickname="activePeer?.nickname || '?'" :user-id="activePeer?.id || 0" size="m" />
              <div class="chat-header__info">
                <span class="chat-header__name">{{ activePeer?.nickname || '未知用户' }}</span>
                <LevelBadge v-if="activePeer?.level" :level="activePeer.level" />
              </div>
            </div>

            <!-- 消息列表 -->
            <div ref="msgContainer" class="chat-messages">
              <div v-if="messagesLoading" class="chat-placeholder muted">加载中...</div>
              <div v-else-if="messages.length === 0" class="chat-placeholder muted">暂无消息</div>
              <template v-else>
                <button v-if="hasEarlier" class="btn btn--sm btn--ghost load-earlier" :disabled="earlierLoading" @click="loadEarlier">
                  {{ earlierLoading ? '加载中...' : '加载更早的消息' }}
                </button>
                <div v-for="m in messages" :key="m.id" class="msg-bubble" :class="{ 'msg-bubble--mine': m.mine }">
                  <div class="msg-bubble__text">{{ m.content }}</div>
                  <div class="msg-bubble__time muted">{{ formatChatTime(m.createdAt) }}</div>
                </div>
              </template>
            </div>

            <!-- 输入区 -->
            <div class="chat-input-bar">
              <textarea v-model="inputText" class="chat-input" rows="2" maxlength="500" placeholder="输入回复内容..." @keydown.enter.exact.prevent="handleSend"></textarea>
              <button class="btn btn--primary btn--sm" :disabled="!inputText.trim() || sending" @click="handleSend">
                {{ sending ? '发送中' : '发送' }}
              </button>
            </div>
          </template>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import LevelBadge from '@/components/common/LevelBadge.vue'
import { getAdminSessions, getAdminChatMessages, getAdminUnreadMessages, sendAdminChatMessage } from '@/api/admin'
import type { ChatMessage, ChatUser, Conversation } from '@/types/models'
import { formatChatTime } from '@/utils/format'

// ---- 会话列表 ----
const sessions = ref<Conversation[]>([])
const sessionsLoading = ref(false)

async function fetchSessions() {
  sessionsLoading.value = true
  try {
    const res = await getAdminSessions()
    sessions.value = res.data || []
  } catch {
    ElMessage.error('加载会话列表失败')
  } finally {
    sessionsLoading.value = false
  }
}

// ---- 当前会话 ----
const activeConv = ref<string | null>(null)
const activePeer = ref<ChatUser | null>(null)
const messages = ref<ChatMessage[]>([])
const messagesLoading = ref(false)
const hasEarlier = ref(false)
const earlierLoading = ref(false)
const inputText = ref('')
const sending = ref(false)
const msgContainer = ref<HTMLElement | null>(null)

async function openSession(session: Conversation) {
  activeConv.value = session.conversationId
  activePeer.value = session.peer
  inputText.value = ''
  hasEarlier.value = false
  await loadMessages()
  scrollToBottom()
}

async function loadMessages() {
  if (!activeConv.value) return
  messagesLoading.value = true
  try {
    const res = await getAdminChatMessages({
      conversationId: activeConv.value,
      peerId: activePeer.value?.id
    })
    messages.value = res.data?.messages || []
    hasEarlier.value = !!res.data?.hasMore
  } catch {
    ElMessage.error('加载消息失败')
  } finally {
    messagesLoading.value = false
  }
}

// 消息历史按 id 倒序 keyset 分页，向前翻页时保持视口停留在原位置
async function loadEarlier() {
  if (!messages.value.length || !activeConv.value || earlierLoading.value) return
  earlierLoading.value = true
  try {
    const res = await getAdminChatMessages({
      conversationId: activeConv.value,
      peerId: activePeer.value?.id,
      beforeId: messages.value[0].id
    })
    const older = res.data?.messages || []
    hasEarlier.value = !!res.data?.hasMore
    if (older.length) {
      const container = msgContainer.value
      const previousHeight = container?.scrollHeight || 0
      messages.value = [...older, ...messages.value]
      await nextTick()
      if (container) container.scrollTop = container.scrollHeight - previousHeight
    }
  } catch {
    ElMessage.error('加载历史消息失败')
  } finally {
    earlierLoading.value = false
  }
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || !activeConv.value || !activePeer.value) return
  sending.value = true
  try {
    await sendAdminChatMessage({
      conversationId: activeConv.value,
      receiverId: activePeer.value.id,
      content: text
    })
    inputText.value = ''
    await loadMessages()
    scrollToBottom()
    // 刷新会话列表（更新 lastMessage）
    fetchSessions()
  } catch {
    ElMessage.error('发送失败')
  } finally {
    sending.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = msgContainer.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// ---- 轮询 ----
let pollTimer: number | undefined

async function poll() {
  if (!activeConv.value) return
  try {
    const res = await getAdminUnreadMessages({ conversationId: activeConv.value })
    const unread = res.data || []
    if (unread.length > 0) {
      await loadMessages()
      scrollToBottom()
      fetchSessions()
    }
  } catch {
    /* ignore poll errors */
  }
}

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(poll, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = undefined
  }
}

function truncate(text: string, max: number) {
  if (!text) return ''
  return text.length > max ? text.slice(0, max) + '...' : text
}

onMounted(() => {
  fetchSessions()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.chat-admin-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

/* 导航 */

/* 左右分栏 */
.chat-layout {
  display: flex;
  height: 580px;
  overflow: hidden;
}
@media (max-width: 700px) {
  .chat-layout {
    flex-direction: column;
    height: auto;
  }
  .chat-sidebar {
    max-width: 100% !important;
    border-right: none !important;
    border-bottom: var(--bw) solid var(--line);
  }
}

/* 左侧栏 */
.chat-sidebar {
  width: 100%;
  max-width: 320px;
  min-width: 240px;
  border-right: var(--bw) solid var(--line);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: var(--bw) solid var(--line);
}
.sidebar-title {
  font-family: var(--font-display);
  font-size: 17px;
  letter-spacing: 0.5px;
}
.sidebar-state {
  padding: 40px 16px;
  text-align: center;
  font-size: 14px;
}
.session-list {
  flex: 1;
  overflow-y: auto;
}
.session-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  cursor: pointer;
  border-bottom: 1px solid rgba(38, 34, 28, 0.08);
  transition: background 0.12s;
}
.session-item:hover {
  background: var(--paper-deep);
}
.session-item.active {
  background: var(--paper-deep);
}
.session-item__info {
  flex: 1;
  min-width: 0;
}
.session-item__top {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 3px;
}
.session-item__name {
  font-weight: 700;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-item__time {
  font-size: 11px;
  margin-left: auto;
  flex-shrink: 0;
}
.session-item__preview {
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.unread-dot {
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 11px;
  background: var(--red);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

/* 右侧聊天区 */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.chat-placeholder {
  flex: 1;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  color: var(--ink-soft);
  font-size: 14px;
}
.chat-placeholder svg {
  width: 44px;
  height: 44px;
  opacity: 0.55;
}
.chat-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  border-bottom: var(--bw) solid var(--line);
  flex-shrink: 0;
}
.chat-header__info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chat-header__name {
  font-weight: 700;
  font-size: 16px;
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.load-earlier {
  align-self: center;
}
.msg-bubble {
  max-width: 70%;
  align-self: flex-start;
}
.msg-bubble--mine {
  align-self: flex-end;
}
.msg-bubble__text {
  padding: 10px 16px;
  font-size: 14px;
  line-height: 1.55;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-s);
  background: var(--white);
  box-shadow: var(--shadow-s);
}
.msg-bubble--mine .msg-bubble__text {
  background: var(--blue);
  color: #fff;
  border-color: var(--ink);
}
.msg-bubble__time {
  font-size: 11px;
  margin-top: 4px;
  margin-left: 4px;
}
.msg-bubble--mine .msg-bubble__time {
  text-align: right;
  margin-right: 4px;
  margin-left: 0;
}

/* 输入区 */
.chat-input-bar {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: var(--bw) solid var(--line);
  flex-shrink: 0;
}
.chat-input {
  flex: 1;
  padding: 10px 14px;
  font-size: 14px;
  font-family: inherit;
  background: var(--white);
  color: var(--ink);
  border: var(--bw) solid var(--line);
  border-radius: var(--r-s);
  resize: none;
  outline: none;
}
.chat-input:focus {
  box-shadow: var(--shadow-s);
}
.chat-input::placeholder {
  color: #b3a893;
}
</style>
