<template>
  <DefaultLayout>
    <div class="chat-list-page">
      <!-- 移动端视图态（列表/会话二选一）：桌面端双栏不受影响，由 CSS 按 .chat-shell 的修饰类切换 -->
      <section class="chat-shell rise" :class="mobilePane === 'thread' ? 'is-on-thread' : 'is-on-list'">
        <aside class="conv-list" aria-label="会话列表">
          <div class="conv-list__head">
            <h1>消息</h1>
            <button class="btn btn--primary btn--sm" :disabled="serviceLoading" @click="contactService">
              <el-icon><Service /></el-icon>
              联系客服
            </button>
          </div>
          <div class="conv-search">
            <el-icon><Search /></el-icon>
            <input v-model="keyword" type="search" placeholder="搜索会话…" aria-label="搜索会话" />
          </div>
          <el-skeleton v-if="loading" :rows="8" animated />
          <div v-else-if="filteredConversations.length" class="conv-items">
            <ConversationListItem
              v-for="conversation in filteredConversations"
              :key="conversation.conversationId"
              :conversation="conversation"
              :active="selectedConversationId === conversation.conversationId"
              @select="selectConversation(conversation)"
            />
            <button v-if="!keyword.trim() && hasMoreConversations" class="btn btn--sm btn--ghost conv-more" :disabled="moreLoading" @click="loadMoreConversations">
              {{ moreLoading ? '加载中…' : '加载更多会话' }}
            </button>
          </div>
          <div v-else class="conv-empty">
            <p class="muted">还没有聊天记录</p>
            <router-link :to="ROUTE_PATH.HOME" class="btn btn--primary btn--sm">去大厅看看</router-link>
          </div>
        </aside>

        <section v-if="selectedConversationId" class="chat-pane" :aria-label="`与${thread?.peer?.nickname || '同学'}的对话`">
          <header class="chat-pane__head">
            <!-- 移动端返回会话列表（仅窄屏显示） -->
            <button class="btn btn--sm btn--ghost chat-back" type="button" aria-label="返回会话列表" @click="backToList">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 18-6-6 6-6" /></svg>
            </button>
            <UserAvatar
              :nickname="thread?.peer?.nickname || selectedConversation?.peer?.nickname || '同学'"
              :user-id="thread?.peer?.id || selectedConversation?.peer?.id || 0"
              size="m"
              :src="thread?.peer?.avatar || selectedConversation?.peer?.avatar || null"
              eager
            />
            <div>
              <div class="nm">
                {{ thread?.peer?.nickname || selectedConversation?.peer?.nickname || '会话' }}
                <LevelBadge :level="thread?.peer?.level || selectedConversation?.peer?.level || 1" show-title :title="thread?.peer?.levelTitle || selectedConversation?.peer?.levelTitle || ''" />
              </div>
            </div>
          </header>

          <RelatedItemCard v-if="activeRelatedItem" :item="activeRelatedItem" />

          <!-- 消息回放区不做 live region：切会话/加载历史是整表替换，进 live region 会被整段播报。
               新消息播报由下方视觉隐藏的 status 区承担（见 incomingAnnouncement）；
               不用 <main>：布局层已有 main landmark，避免页面双 main -->
          <div ref="messagePanel" class="msg-flow">
            <el-skeleton v-if="threadLoading && !messages.length" :rows="8" animated />
            <template v-else-if="messages.length">
              <button v-if="hasEarlier" class="btn btn--sm btn--ghost load-earlier" :disabled="earlierLoading" @click="loadEarlier">
                {{ earlierLoading ? '加载中…' : '加载更早的消息' }}
              </button>
              <div v-for="message in messages" :key="message.id" class="msg" :class="message.mine ? 'msg--out' : 'msg--in'">
                <UserAvatar
                  :nickname="message.mine ? '我' : thread?.peer?.nickname || '同学'"
                  :user-id="message.mine ? 0 : thread?.peer?.id || 0"
                  size="s"
                  :src="message.mine ? userStore.user?.avatar || null : thread?.peer?.avatar || null"
                  alt=""
                />
                <div>
                  <!-- 视觉隐藏的发送者标识：气泡本身无"我/对方"文本，读屏无法区分收发 -->
                  <span class="visually-hidden">{{ message.mine ? '我' : thread?.peer?.nickname || '对方' }}：</span>
                  <div class="msg__bubble">{{ message.content }}</div>
                  <div class="msg__time">{{ formatChatTime(message.createdAt) }}</div>
                </div>
              </div>
            </template>
            <div v-else class="empty-chat"><p class="muted">还没有消息，打个招呼吧。</p></div>
          </div>

          <!-- 屏幕阅读器专用的新消息播报区：仅在后台轮询发现"非自己"的新消息时写入一条 -->
          <div class="visually-hidden" role="status" aria-live="polite">{{ incomingAnnouncement }}</div>

          <footer class="chat-input">
            <el-input v-model="draft" type="textarea" :rows="3" maxlength="1000" show-word-limit resize="none" placeholder="输入消息，Enter 发送，Shift + Enter 换行" @keydown.enter.exact="onEnter" />
            <button class="btn btn--green" :disabled="sending || !draft.trim()" @click="handleSend">
              发送
              <svg class="send-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
                <path d="m22 2-7 20-4-9-9-4Z" />
                <path d="M22 2 11 13" />
              </svg>
            </button>
          </footer>
        </section>

        <section v-else class="chat-placeholder">
          <span class="placeholder-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4Z" />
              <path d="M8 9h8M8 13h5" />
            </svg>
          </span>
          <h2>选择一个会话</h2>
          <p class="muted">选择左侧会话开始聊天</p>
          <button class="btn btn--yellow" :disabled="serviceLoading" @click="contactService">联系客服</button>
        </section>
      </section>
    </div>
  </DefaultLayout>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Service } from '@element-plus/icons-vue'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import LevelBadge from '@/components/common/LevelBadge.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { ackChatRead, getChatMessages, getConversations, sendChatMessage, startCustomerService } from '@/api/chat'
import type { ChatMessagesQuery } from '@/api/chat'
import { useUserStore } from '@/stores/user'
import type { ChatMessage, ChatThread, Conversation } from '@/types/models'
import ConversationListItem from './components/ConversationListItem.vue'
import RelatedItemCard from './components/RelatedItemCard.vue'
import { ROUTE_PATH } from '@/constants/routes'
import { formatChatTime } from '@/utils/format'
import { useContextGuard } from '@/composables/useContextGuard'
import { useChatStream } from '@/composables/useChatStream'
import { useImeSafeEnter } from '@/composables/useImeSafeEnter'
import type { ChatStreamMessageEvent, ChatStreamReadEvent } from '@/composables/useChatStream'

/**
 * selectConversation 消费的最小会话形状：
 * 真实 Conversation、客服会话 ChatStartResult、URL 回填对象均结构兼容。
 */
interface ConversationLike {
  conversationId: string
  // id 兼容 URL 查询参数退化形态（多值数组）
  peer?: { id?: number | string | string[] | (string | null)[] | null | undefined }
  relatedItem?: { id?: number | string | string[] | (string | null)[] | null | undefined } | null
}

/** SSE 事件合并窗口：连续到达的多条事件收敛为一次重拉 */
const EVENT_MERGE_MS = 200
/** 距底部小于该值视为"贴近底部"，新消息到达时才自动滚底 */
const NEAR_BOTTOM_PX = 40

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const conversations = ref<Conversation[]>([])
const selectedConversationId = ref('')
const thread = ref<ChatThread | null>(null)
const messages = ref<ChatMessage[]>([])
const draft = ref('')
const keyword = ref('')
const loading = ref(false)
const threadLoading = ref(false)
const sending = ref(false)
const serviceLoading = ref(false)
const messagePanel = ref<HTMLElement | null>(null)
/** 屏幕阅读器新消息播报文本：仅后台轮询发现的非自己新消息写入，历史加载/切会话不播报 */
const incomingAnnouncement = ref('')
const hasEarlier = ref(false)
const earlierLoading = ref(false)
/** 会话列表翻页状态：满页即可能还有更多（keyset 以 lastMessageId 为游标） */
const hasMoreConversations = ref(false)
const moreLoading = ref(false)
/** 是否已向前翻页（组件内状态，随会话切换代数作废——M11 修复） */
let earlierLoaded = false
/** 事件合并定时器（线程/当前会话线程刷新） */
let threadRefreshTimer: number | undefined
let conversationsRefreshTimer: number | undefined
/** 已确认读到的最后一条接收消息 ID（避免重复 ack） */
let lastAckedMessageId: number | null = null

// F2/M11 根因修复：会话切换守卫（contextId + generation + 同会话请求序号）
const chatGuard = useContextGuard<string>()

const selectedConversation = computed(() => conversations.value.find((item) => String(item.conversationId) === String(selectedConversationId.value)) || null)
const activeRelatedItem = computed(() => thread.value?.relatedItem || selectedConversation.value?.relatedItem || null)
const filteredConversations = computed(() => {
  const value = keyword.value.trim()
  if (!value) return conversations.value
  return conversations.value.filter((item) => (item.peer?.nickname || '').includes(value) || (item.lastMessage || '').includes(value) || (item.relatedItem?.title || '').includes(value))
})

function threadParams(conversation: Conversation | null = selectedConversation.value): ChatMessagesQuery {
  const params: ChatMessagesQuery = { conversationId: selectedConversationId.value }
  const peerId = conversation?.peer?.id || route.query.peerId
  const relatedItemId = conversation?.relatedItem?.id || route.query.relatedItemId
  if (peerId) params.peerId = Number(peerId)
  if (relatedItemId) params.relatedItemId = Number(relatedItemId)
  return params
}

function isNearBottom(): boolean {
  const panel = messagePanel.value
  if (!panel) return true
  return panel.scrollHeight - panel.scrollTop - panel.clientHeight < NEAR_BOTTOM_PX
}

async function scrollToBottom() {
  await nextTick()
  if (messagePanel.value) messagePanel.value.scrollTop = messagePanel.value.scrollHeight
}

/** 会话列表 keyset 翻页页大小（与后端 CONVERSATION_PAGE_SIZE 对齐）；返回满页视为可能还有下一页 */
const CONVERSATION_PAGE_SIZE = 50

/** 移动端视图态：窄屏下列表与会话二选一展示（桌面端双栏，此状态被 CSS 忽略） */
const mobilePane = ref<'list' | 'thread'>('list')

function backToList(): void {
  mobilePane.value = 'list'
}

async function fetchConversations() {
  loading.value = true
  try {
    const res = await getConversations()
    conversations.value = res.data || []
    hasMoreConversations.value = conversations.value.length >= CONVERSATION_PAGE_SIZE
  } finally {
    loading.value = false
  }
}

async function loadMoreConversations() {
  const last = conversations.value[conversations.value.length - 1]
  if (!last || moreLoading.value) return
  moreLoading.value = true
  try {
    const res = await getConversations(last.lastMessageId)
    const fresh = res.data || []
    const existing = new Set(conversations.value.map((item) => item.conversationId))
    for (const item of fresh) {
      if (!existing.has(item.conversationId)) conversations.value.push(item)
    }
    hasMoreConversations.value = fresh.length >= CONVERSATION_PAGE_SIZE
  } finally {
    moreLoading.value = false
  }
}

/** 消息落地后按需显式 ACK：只取最后一条"接收"消息作为边界（M1/B10） */
function ackVisibleMessages(conversationId: string) {
  const lastReceived = [...messages.value].filter((item) => !item.mine).pop()
  if (!lastReceived || lastReceived.id === lastAckedMessageId) return
  lastAckedMessageId = lastReceived.id
  ackChatRead(conversationId, lastReceived.id).catch(() => {
    // ACK 失败不影响会话使用；下次消息变化会以新的边界重试
    lastAckedMessageId = null
  })
}

async function fetchThread({ silent = false }: { silent?: boolean } = {}) {
  const conversationId = selectedConversationId.value
  if (!conversationId) return
  // 轮询请求携带同会话序号：旧轮询响应不覆盖新轮询结果（F7）
  const { gen, seq } = chatGuard.nextRequest()
  if (!silent) threadLoading.value = true
  try {
    const res = await getChatMessages(threadParams(), silent ? { skipAuthRedirect: true } : undefined)
    if (!chatGuard.isCurrent(conversationId, gen, seq)) return
    const previousLastMessage = messages.value[messages.value.length - 1]
    const wasNearBottom = isNearBottom()
    thread.value = res.data
    const fresh = res.data?.messages || []
    if (!earlierLoaded) {
      // 未向前翻页：最新一页就是完整视图
      messages.value = fresh
    } else if (fresh.length) {
      // 已加载更早消息：只追加新消息，避免轮询把翻页结果重置回最新一页
      const existingIds = new Set(messages.value.map((item) => item.id))
      for (const item of fresh) {
        if (!existingIds.has(item.id)) messages.value.push(item)
      }
    }
    hasEarlier.value = !!res.data?.hasMore
    // GET 已只读化：消息渲染后显式确认已读
    ackVisibleMessages(conversationId)
    const conversation = selectedConversation.value
    if (conversation) conversation.unreadCount = 0
    // 仅在贴近底部或刚发出自己的消息时自动滚底（F7：不打断向上翻阅）
    const lastMessage = messages.value[messages.value.length - 1]
    const ownMessageArrived = !!lastMessage?.mine && lastMessage?.id !== previousLastMessage?.id
    if (ownMessageArrived || wasNearBottom || !silent) {
      await scrollToBottom()
    }
    // 新消息播报：仅后台静默轮询发现的、非自己发送的最后一条（历史加载/切会话/向上翻页不播报）
    const incomingArrived = silent && !!lastMessage && lastMessage.id !== previousLastMessage?.id
    if (incomingArrived && !lastMessage.mine) {
      const peerName = thread.value?.peer?.nickname || conversation?.peer?.nickname || '对方'
      // 先清空再于下一轮写入：内容相同时也能重新触发 live region 播报
      incomingAnnouncement.value = ''
      void nextTick(() => {
        incomingAnnouncement.value = `${peerName}：${lastMessage.content}`
      })
    }
  } finally {
    if (chatGuard.isCurrent(conversationId, gen)) {
      threadLoading.value = false
    }
  }
}

async function loadEarlier() {
  const conversationId = selectedConversationId.value
  if (!conversationId || !messages.value.length || earlierLoading.value) return
  // 历史分页顺序无关：只校验 gen + conversationId，不参与同会话序号（M11）
  const { gen } = chatGuard.nextRequest()
  earlierLoading.value = true
  try {
    const res = await getChatMessages({ ...threadParams(), beforeId: messages.value[0].id })
    if (!chatGuard.isCurrent(conversationId, gen)) return
    const older = res.data?.messages || []
    earlierLoaded = true
    hasEarlier.value = !!res.data?.hasMore
    if (older.length) {
      const panel = messagePanel.value
      const previousHeight = panel?.scrollHeight || 0
      messages.value = [...older, ...messages.value]
      await nextTick()
      if (panel) panel.scrollTop = panel.scrollHeight - previousHeight
    }
  } finally {
    earlierLoading.value = false
  }
}

async function selectConversation(conversation: ConversationLike, updateUrl = true) {
  // 切换会话：推进守卫代数，作废在途请求与 earlierLoaded（F2/M11）
  chatGuard.switchContext(conversation.conversationId)
  selectedConversationId.value = conversation.conversationId
  thread.value = null
  messages.value = []
  draft.value = ''
  earlierLoaded = false
  hasEarlier.value = false
  lastAckedMessageId = null
  mobilePane.value = 'thread'
  if (updateUrl) await router.replace({ path: ROUTE_PATH.CHAT, query: { conversationId: conversation.conversationId, peerId: conversation.peer?.id, relatedItemId: conversation.relatedItem?.id } })
  await fetchThread()
}

async function handleSend() {
  // F4 根因修复：入口同步互斥——Enter 连击在 sending 置位前无法穿透第二次
  if (sending.value) return
  const peerId = thread.value?.peer?.id || selectedConversation.value?.peer?.id
  if (!draft.value.trim() || !peerId) return
  const conversationId = selectedConversationId.value
  sending.value = true
  try {
    await sendChatMessage({ conversationId, receiverId: peerId, relatedItemId: activeRelatedItem.value?.id, content: draft.value.trim() })
    draft.value = ''
    if (chatGuard.isCurrent(conversationId, chatGuard.generation.value)) {
      await fetchThread({ silent: true })
    }
    await fetchConversations()
  } finally {
    sending.value = false
  }
}

// Enter 发送走 IME 安全通道：拼音组词选字的 Enter（isComposing/229）放行为上屏，不误发半截消息
const { onEnter } = useImeSafeEnter(handleSend)

async function contactService() {
  if (serviceLoading.value) return
  serviceLoading.value = true
  try {
    const res = await startCustomerService()
    await fetchConversations()
    const conversation = conversations.value.find((item) => String(item.conversationId) === String(res.data.conversationId)) || res.data
    await selectConversation(conversation)
  } finally {
    serviceLoading.value = false
  }
}

/** SSE 推送驱动：新消息到达当前会话时静默刷新线程（合并事件风暴） */
function scheduleThreadRefresh() {
  if (threadRefreshTimer !== undefined) return
  threadRefreshTimer = window.setTimeout(() => {
    threadRefreshTimer = undefined
    void fetchThread({ silent: true })
  }, EVENT_MERGE_MS)
}

/** 其他会话有新消息：刷新会话列表（最近消息与未读角标） */
function scheduleConversationsRefresh() {
  if (conversationsRefreshTimer !== undefined) return
  conversationsRefreshTimer = window.setTimeout(() => {
    conversationsRefreshTimer = undefined
    void fetchConversations()
  }, EVENT_MERGE_MS)
}

const chatStream = useChatStream()
const offStreamMessage = chatStream.onMessage((event: ChatStreamMessageEvent) => {
  if (event.conversationId === selectedConversationId.value) scheduleThreadRefresh()
  else scheduleConversationsRefresh()
})
const offStreamRead = chatStream.onRead((event: ChatStreamReadEvent) => {
  // 自己发出的消息被对方读：当前会话刷新已读状态（无重复 ACK 时不会回弹）
  if (event.conversationId === selectedConversationId.value) scheduleThreadRefresh()
})
const offStreamResync = chatStream.onResync(() => {
  // 连接（重）建立/页面恢复可见：整段重拉兜底断线期间的漏推
  void fetchConversations()
  if (selectedConversationId.value) scheduleThreadRefresh()
})

onMounted(async () => {
  await fetchConversations()
  const queryId = route.query.conversationId
  if (queryId) {
    // URL 直达且会话列表尚未包含该会话时的兜底对象：仅承载 id 供线程拉取，字段不全属预期
    const fallback: ConversationLike = {
      conversationId: String(queryId),
      peer: { id: route.query.peerId ?? null },
      relatedItem: route.query.relatedItemId ? { id: route.query.relatedItemId } : null
    }
    const conversation = conversations.value.find((item) => String(item.conversationId) === String(queryId)) || fallback
    await selectConversation(conversation, false)
  }
})
onUnmounted(() => {
  offStreamMessage()
  offStreamRead()
  offStreamResync()
  if (threadRefreshTimer !== undefined) window.clearTimeout(threadRefreshTimer)
  if (conversationsRefreshTimer !== undefined) window.clearTimeout(conversationsRefreshTimer)
})
</script>

<style scoped>
.chat-list-page {
  margin: 4px 0;
}
.load-earlier {
  display: block;
  margin: 0 auto 10px;
}
.chat-shell {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  height: min(720px, calc(100vh - 150px));
  height: min(720px, calc(100dvh - 150px));
  min-height: 620px;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-l);
  background: var(--white);
  box-shadow: var(--shadow-l);
  overflow: hidden;
}
.conv-list {
  min-height: 0;
  border-right: var(--bw) solid var(--line);
  display: flex;
  flex-direction: column;
  background: var(--paper);
}
.conv-list__head {
  padding: 18px 20px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.conv-list__head h1 {
  font-family: var(--font-display);
  font-size: 23px;
}
.conv-search {
  margin: 8px 20px 12px;
  position: relative;
}
.conv-search input {
  width: 100%;
  padding: 9px 14px 9px 36px;
  font-size: 13.5px;
  font-family: inherit;
  border: var(--bw) solid var(--line);
  border-radius: 999px;
  background: var(--white);
}
.conv-search input:focus {
  outline: none;
  box-shadow: var(--shadow-s);
}
.conv-search .el-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  translate: 0 -50%;
  color: var(--ink-soft);
}
.conv-items {
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}
.conv-more {
  display: block;
  width: calc(100% - 24px);
  margin: 8px 12px 12px;
}
.conv-empty,
.chat-placeholder,
.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  min-height: 0;
  padding: 24px;
  text-align: center;
}
.chat-placeholder {
  background: var(--paper-deep);
}
.chat-placeholder h2 {
  font-family: var(--font-display);
  font-size: 30px;
}
.placeholder-icon {
  width: 60px;
  height: 60px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--paper-deep);
  color: var(--ink-soft);
}
.placeholder-icon svg {
  width: 34px;
  height: 34px;
}
.chat-pane {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--paper-deep);
}
.chat-pane__head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 22px;
  background: var(--white);
  border-bottom: var(--bw) solid var(--line);
}
.nm {
  font-weight: 900;
  font-size: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.msg-flow {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 22px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.msg {
  display: flex;
  gap: 10px;
  max-width: 78%;
}
.msg__bubble {
  padding: 10px 15px;
  font-size: 14.5px;
  line-height: 1.6;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-m);
  background: var(--white);
  box-shadow: var(--shadow-s);
  white-space: pre-wrap;
  word-break: break-word;
}
.msg__time {
  font-size: 11px;
  color: var(--ink-soft);
  margin-top: 4px;
}
.msg--in {
  align-self: flex-start;
}
.msg--in .msg__bubble {
  border-bottom-left-radius: 4px;
}
.msg--out {
  align-self: flex-end;
  flex-direction: row-reverse;
}
.msg--out .msg__bubble {
  background: var(--green);
  color: var(--white);
  border-bottom-right-radius: 4px;
}
.msg--out .msg__time {
  text-align: right;
}
.empty-chat {
  height: 100%;
}
.chat-input {
  display: flex;
  gap: 12px;
  padding: 16px 22px;
  background: var(--white);
  border-top: var(--bw) solid var(--line);
  align-items: flex-end;
}
.chat-input :deep(.el-textarea__inner) {
  resize: none;
  min-height: 46px !important;
  max-height: 120px;
  padding: 11px 16px;
  font-size: 14.5px;
  font-family: inherit;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-m);
  background: var(--paper);
  box-shadow: none;
}
.chat-input :deep(.el-textarea__inner:focus) {
  box-shadow: var(--shadow-s);
}
.send-icon {
  width: 18px;
  height: 18px;
}
/* 返回按钮默认隐藏，仅移动端显示（写在媒体查询之前，避免被同特异性的后来者覆盖） */
.chat-back {
  display: none;
  flex: 0 0 auto;
}
/* 移动端单列 + 视图切换：此前 130px 会话列塞不下头像+昵称+摘要，
   气泡区只剩 ~200px。改为列表态/会话态二选一（.is-on-list / .is-on-thread），
   桌面端（>760px）双栏不受影响 */
@media (max-width: 760px) {
  .chat-shell {
    grid-template-columns: minmax(0, 1fr);
    height: calc(100vh - 126px);
    height: calc(100dvh - 126px);
    /* iPhone SE 一类矮视口：不再用固定 560px 撑出外层滚动 */
    min-height: min(560px, calc(100dvh - 126px));
    border-radius: var(--r-m);
  }
  .chat-shell.is-on-list .chat-pane,
  .chat-shell.is-on-list .chat-placeholder {
    display: none;
  }
  .chat-shell.is-on-thread .conv-list {
    display: none;
  }
  /* 单列下不再画右侧分隔线（那是双栏布局的产物） */
  .conv-list {
    border-right: none;
  }
  .chat-back {
    display: inline-flex;
  }
  .conv-list__head {
    padding: 12px 14px 8px;
  }
  .conv-search {
    margin: 6px 12px 10px;
  }
  .chat-pane__head,
  .msg-flow,
  .chat-input {
    padding-left: 12px;
    padding-right: 12px;
  }
  .msg {
    max-width: 92%;
  }
  .chat-input {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
