<template>
  <DefaultLayout>
    <div class="chat-list-page">
      <section class="chat-shell rise">
        <aside class="conv-list" aria-label="会话列表">
          <div class="conv-list__head">
            <h1>消息</h1>
            <button class="btn btn--primary btn--sm" :disabled="serviceLoading" @click="contactService">
              <el-icon><Service /></el-icon>联系客服
            </button>
          </div>
          <div class="conv-search">
            <el-icon><Search /></el-icon>
            <input v-model="keyword" type="search" placeholder="搜索会话…" aria-label="搜索会话">
          </div>
          <el-skeleton v-if="loading" :rows="8" animated />
          <div v-else-if="filteredConversations.length" class="conv-items">
            <button
              v-for="conversation in filteredConversations"
              :key="conversation.conversationId"
              class="conv-item"
              :class="{ active: selectedConversationId === conversation.conversationId }"
              @click="selectConversation(conversation)"
            >
              <UserAvatar :nickname="conversation.peer?.nickname || '同学'" :user-id="conversation.peer?.id || 0" size="m" />
              <span class="conv-item__body">
                <span class="conv-item__top">
                  <span class="conv-item__name">{{ conversation.peer?.nickname || '同学' }}<LevelBadge :level="conversation.peer?.level || 1" /></span>
                  <span class="conv-item__time">{{ formatTime(conversation.lastMessageTime) }}</span>
                </span>
                <span class="conv-item__preview">{{ conversation.lastMessage || '暂无消息' }}</span>
                <span v-if="conversation.relatedItem" class="conv-item__goods">关联商品：{{ conversation.relatedItem.title }}</span>
              </span>
              <span v-if="conversation.unreadCount > 0" class="conv-item__unread">{{ conversation.unreadCount }}</span>
            </button>
          </div>
          <div v-else class="conv-empty"><p class="muted">还没有聊天记录</p><router-link to="/" class="btn btn--primary btn--sm">去大厅看看</router-link></div>
        </aside>

        <section v-if="selectedConversationId" class="chat-pane" :aria-label="`与${thread?.peer?.nickname || '同学'}的对话`">
          <header class="chat-pane__head">
            <UserAvatar :nickname="thread?.peer?.nickname || selectedConversation?.peer?.nickname || '同学'" :user-id="thread?.peer?.id || selectedConversation?.peer?.id || 0" size="m" />
            <div>
              <div class="nm">{{ thread?.peer?.nickname || selectedConversation?.peer?.nickname || '会话' }}<LevelBadge :level="thread?.peer?.level || selectedConversation?.peer?.level || 1" show-title /></div>
              <div class="st"><i />消息自动同步中</div>
            </div>
          </header>

          <router-link v-if="activeRelatedItem" class="related-item" :to="`/item/${activeRelatedItem.id}`">
            <span class="related-item__thumb" :class="phClass(activeRelatedItem.id)"><img v-if="activeRelatedItem.coverImage" :src="activeRelatedItem.coverImage" :alt="activeRelatedItem.title"></span>
            <span class="related-item__info"><strong>{{ activeRelatedItem.title }}</strong><PriceTag :value="activeRelatedItem.price" font-size="18px" /></span>
            <span class="btn btn--sm btn--primary">查看商品</span>
          </router-link>

          <main ref="messagePanel" class="msg-flow">
            <el-skeleton v-if="threadLoading && !messages.length" :rows="8" animated />
            <template v-else-if="messages.length">
              <span class="msg-day">当前会话</span>
              <div v-for="message in messages" :key="message.id" class="msg" :class="message.mine ? 'msg--out' : 'msg--in'">
                <UserAvatar :nickname="message.mine ? '我' : (thread?.peer?.nickname || '同学')" :user-id="message.mine ? 0 : (thread?.peer?.id || 0)" size="s" />
                <div><div class="msg__bubble">{{ message.content }}</div><div class="msg__time">{{ formatTime(message.createdAt) }}</div></div>
              </div>
            </template>
            <div v-else class="empty-chat"><p class="muted">还没有消息，打个招呼吧。</p></div>
          </main>

          <footer class="chat-input">
            <el-input v-model="draft" type="textarea" :rows="3" maxlength="1000" show-word-limit resize="none" placeholder="输入消息，Enter 发送，Shift + Enter 换行" @keydown.enter.exact.prevent="handleSend" />
            <button class="btn btn--green" :disabled="sending || !draft.trim()" @click="handleSend">发送<svg class="send-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/></svg></button>
          </footer>
          <div class="poll-note"><i />每 2.5 秒自动刷新新消息</div>
        </section>

        <section v-else class="chat-placeholder">
          <span class="placeholder-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4Z"/><path d="M8 9h8M8 13h5"/></svg></span>
          <h2>选择一个会话</h2><p class="muted">点击左侧用户，聊天内容会直接显示在这里。</p>
          <button class="btn btn--yellow" :disabled="serviceLoading" @click="contactService">联系客服</button>
        </section>
      </section>
    </div>
  </DefaultLayout>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Service } from '@element-plus/icons-vue'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import LevelBadge from '@/components/common/LevelBadge.vue'
import PriceTag from '@/components/common/PriceTag.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { getChatMessages, getConversations, sendChatMessage, startCustomerService } from '@/api/chat'

const PH = ['ph-a', 'ph-b', 'ph-c', 'ph-d', 'ph-e', 'ph-f']
const route = useRoute()
const router = useRouter()
const conversations = ref([])
const selectedConversationId = ref('')
const thread = ref(null)
const messages = ref([])
const draft = ref('')
const keyword = ref('')
const loading = ref(false)
const threadLoading = ref(false)
const sending = ref(false)
const serviceLoading = ref(false)
const messagePanel = ref(null)
let pollTimer = null

const selectedConversation = computed(() => conversations.value.find(item => String(item.conversationId) === String(selectedConversationId.value)) || null)
const activeRelatedItem = computed(() => thread.value?.relatedItem || selectedConversation.value?.relatedItem || null)
const filteredConversations = computed(() => {
  const value = keyword.value.trim()
  if (!value) return conversations.value
  return conversations.value.filter(item => (item.peer?.nickname || '').includes(value) || (item.lastMessage || '').includes(value) || (item.relatedItem?.title || '').includes(value))
})

function phClass(id) { return PH[Number(id) % PH.length] }
function formatTime(value) { return value ? String(value).replace('T', ' ').slice(5, 16) : '' }
function threadParams(conversation = selectedConversation.value) {
  const params = { conversationId: selectedConversationId.value }
  const peerId = conversation?.peer?.id || route.query.peerId
  const relatedItemId = conversation?.relatedItem?.id || route.query.relatedItemId
  if (peerId) params.peerId = Number(peerId)
  if (relatedItemId) params.relatedItemId = Number(relatedItemId)
  return params
}
async function scrollToBottom() { await nextTick(); if (messagePanel.value) messagePanel.value.scrollTop = messagePanel.value.scrollHeight }
async function fetchConversations() {
  loading.value = true
  try { const res = await getConversations(); conversations.value = res.data || [] } finally { loading.value = false }
}
async function fetchThread({ silent = false } = {}) {
  if (!selectedConversationId.value) return
  const requestedId = selectedConversationId.value
  if (!silent) threadLoading.value = true
  try {
    const res = await getChatMessages(threadParams())
    if (requestedId !== selectedConversationId.value) return
    thread.value = res.data
    messages.value = res.data?.messages || []
    const conversation = selectedConversation.value
    if (conversation) conversation.unreadCount = 0
    await scrollToBottom()
  } finally { threadLoading.value = false }
}
async function selectConversation(conversation, updateUrl = true) {
  selectedConversationId.value = conversation.conversationId
  thread.value = null
  messages.value = []
  draft.value = ''
  if (updateUrl) await router.replace({ path: '/chat', query: { conversationId: conversation.conversationId, peerId: conversation.peer?.id, relatedItemId: conversation.relatedItem?.id } })
  await fetchThread()
}
async function handleSend() {
  const peerId = thread.value?.peer?.id || selectedConversation.value?.peer?.id
  if (!draft.value.trim() || !peerId) return
  sending.value = true
  try {
    await sendChatMessage({ conversationId: selectedConversationId.value, receiverId: peerId, relatedItemId: activeRelatedItem.value?.id, content: draft.value.trim() })
    draft.value = ''
    await fetchThread({ silent: true })
    await fetchConversations()
  } finally { sending.value = false }
}
async function contactService() {
  serviceLoading.value = true
  try {
    const res = await startCustomerService()
    await fetchConversations()
    const conversation = conversations.value.find(item => String(item.conversationId) === String(res.data.conversationId)) || res.data
    await selectConversation(conversation)
  } finally { serviceLoading.value = false }
}

onMounted(async () => {
  await fetchConversations()
  const queryId = route.query.conversationId
  if (queryId) {
    const conversation = conversations.value.find(item => String(item.conversationId) === String(queryId)) || { conversationId: queryId, peer: { id: route.query.peerId }, relatedItem: route.query.relatedItemId ? { id: route.query.relatedItemId } : null }
    await selectConversation(conversation, false)
  }
  pollTimer = window.setInterval(() => fetchThread({ silent: true }), 2500)
})
onUnmounted(() => { if (pollTimer) window.clearInterval(pollTimer) })
</script>

<style scoped>
.chat-list-page { margin: 4px 0; }
.chat-shell { display: grid; grid-template-columns: 360px minmax(0, 1fr); height: min(720px, calc(100vh - 150px)); min-height: 620px; border: var(--bw) solid var(--ink); border-radius: var(--r-l); background: var(--white); box-shadow: var(--shadow-l); overflow: hidden; }
.conv-list { min-height: 0; border-right: var(--bw) solid var(--ink); display: flex; flex-direction: column; background: var(--paper); }
.conv-list__head { padding: 18px 20px 12px; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.conv-list__head h1 { font-family: var(--font-display); font-size: 23px; }
.conv-search { margin: 8px 20px 12px; position: relative; }
.conv-search input { width: 100%; padding: 9px 14px 9px 36px; font-size: 13.5px; font-family: inherit; border: var(--bw) solid var(--ink); border-radius: 999px; background: var(--white); }
.conv-search input:focus { outline: none; box-shadow: 2px 2px 0 var(--ink); }
.conv-search .el-icon { position: absolute; left: 12px; top: 50%; translate: 0 -50%; color: var(--ink-soft); }
.conv-items { overflow-y: auto; flex: 1; min-height: 0; }
.conv-item { width: 100%; display: flex; gap: 12px; padding: 13px 20px; cursor: pointer; border: none; border-bottom: 1.5px dashed #E0D6C2; background: transparent; color: var(--ink); text-align: left; position: relative; }
.conv-item:hover { background: var(--paper-deep); }
.conv-item.active { background: var(--yellow); box-shadow: inset 5px 0 0 var(--primary); }
.conv-item__body { flex: 1; min-width: 0; display: block; }
.conv-item__top { display: flex; justify-content: space-between; align-items: baseline; gap: 8px; }
.conv-item__name { min-width: 0; font-weight: 800; font-size: 14.5px; display: flex; align-items: center; gap: 6px; }
.conv-item__time { flex-shrink: 0; font-size: 11.5px; color: var(--ink-soft); }
.conv-item__preview, .conv-item__goods { display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.conv-item__preview { font-size: 13px; color: var(--ink-soft); margin-top: 3px; }
.conv-item__goods { font-size: 12px; color: var(--green-deep); }
.conv-item__unread { position: absolute; right: 18px; bottom: 14px; min-width: 19px; height: 19px; padding: 0 5px; border-radius: 10px; background: var(--red); color: var(--white); font-size: 11px; font-weight: 800; display: grid; place-items: center; border: 1.5px solid var(--ink); }
.conv-empty, .chat-placeholder, .empty-chat { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16px; min-height: 0; padding: 24px; text-align: center; }
.chat-placeholder { background: var(--paper-deep); }
.chat-placeholder h2 { font-family: var(--font-display); font-size: 30px; }
.placeholder-icon { width: 64px; height: 64px; display: grid; place-items: center; border: var(--bw) solid var(--ink); border-radius: var(--r-m); background: var(--yellow); box-shadow: var(--shadow-s); transform: rotate(-4deg); }
.placeholder-icon svg { width: 34px; height: 34px; }
.chat-pane { min-width: 0; min-height: 0; display: flex; flex-direction: column; background: var(--paper-deep); }
.chat-pane__head { display: flex; align-items: center; gap: 12px; padding: 14px 22px; background: var(--white); border-bottom: var(--bw) solid var(--ink); }
.nm { font-weight: 900; font-size: 16px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.st { font-size: 12px; color: var(--green-deep); display: flex; align-items: center; gap: 5px; }
.st i, .poll-note i { width: 8px; height: 8px; border-radius: 50%; background: var(--green); display: inline-block; border: 1px solid var(--ink); }
.related-item { display: flex; align-items: center; gap: 12px; margin: 14px 22px 0; background: var(--white); border: var(--bw) solid var(--ink); border-radius: var(--r-m); padding: 10px 14px; box-shadow: var(--shadow-s); }
.related-item__thumb { width: 46px; height: 46px; border: 1.5px solid var(--ink); border-radius: var(--r-s); overflow: hidden; flex-shrink: 0; }
.related-item__thumb img { width: 100%; height: 100%; object-fit: cover; }
.related-item__info { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.related-item__info strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.msg-flow { flex: 1; min-height: 0; overflow-y: auto; padding: 20px 22px; display: flex; flex-direction: column; gap: 16px; }
.msg-day { align-self: center; font-size: 11.5px; font-weight: 700; color: var(--ink-soft); background: var(--white); border: 1.5px solid var(--ink); padding: 2px 14px; border-radius: 999px; }
.msg { display: flex; gap: 10px; max-width: 78%; }
.msg__bubble { padding: 10px 15px; font-size: 14.5px; line-height: 1.6; border: var(--bw) solid var(--ink); border-radius: var(--r-m); background: var(--white); box-shadow: 2px 2px 0 var(--ink); white-space: pre-wrap; word-break: break-word; }
.msg__time { font-size: 11px; color: var(--ink-soft); margin-top: 4px; }
.msg--in { align-self: flex-start; }
.msg--in .msg__bubble { border-bottom-left-radius: 4px; }
.msg--out { align-self: flex-end; flex-direction: row-reverse; }
.msg--out .msg__bubble { background: var(--green); color: var(--white); border-bottom-right-radius: 4px; }
.msg--out .msg__time { text-align: right; }
.empty-chat { height: 100%; }
.chat-input { display: flex; gap: 12px; padding: 16px 22px; background: var(--white); border-top: var(--bw) solid var(--ink); align-items: flex-end; }
.chat-input :deep(.el-textarea__inner) { resize: none; min-height: 46px !important; max-height: 120px; padding: 11px 16px; font-size: 14.5px; font-family: inherit; border: var(--bw) solid var(--ink); border-radius: var(--r-m); background: var(--paper); box-shadow: none; }
.chat-input :deep(.el-textarea__inner:focus) { box-shadow: var(--shadow-s); }
.send-icon { width: 18px; height: 18px; }
.poll-note { text-align: center; font-size: 11.5px; color: var(--ink-soft); background: var(--white); padding: 6px 0 8px; display: flex; align-items: center; justify-content: center; gap: 6px; }
@media (max-width: 760px) { .chat-shell { grid-template-columns: 130px minmax(0, 1fr); height: calc(100vh - 126px); min-height: 560px; border-radius: var(--r-m); } .conv-list__head { padding: 12px 10px 8px; } .conv-list__head .btn { display: none; } .conv-search { margin: 6px 8px 10px; } .conv-item { padding: 10px 8px; gap: 7px; } .conv-item :deep(.avatar) { display: none; } .conv-item__top { display: block; } .conv-item__time, .conv-item__goods, .conv-item__name :deep(.badge) { display: none; } .conv-item__unread { right: 6px; bottom: 6px; } .chat-pane__head, .msg-flow, .chat-input { padding-left: 12px; padding-right: 12px; } .related-item { margin-left: 12px; margin-right: 12px; } .related-item .btn { display: none; } .msg { max-width: 92%; } .chat-input { flex-direction: column; align-items: stretch; } .chat-placeholder { padding: 12px; } }
</style>
