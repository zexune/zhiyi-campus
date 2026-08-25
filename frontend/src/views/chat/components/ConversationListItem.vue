<template>
  <button class="conv-item" :class="{ active }" @click="emit('select')">
    <UserAvatar :nickname="conversation.peer?.nickname || '同学'" :user-id="conversation.peer?.id || 0" size="m" />
    <span class="conv-item__body">
      <span class="conv-item__top">
        <span class="conv-item__name">
          {{ conversation.peer?.nickname || '同学' }}
          <LevelBadge :level="conversation.peer?.level || 1" />
        </span>
        <span class="conv-item__time">{{ formatTimeShort(conversation.lastMessageTime) }}</span>
      </span>
      <span class="conv-item__preview">{{ conversation.lastMessage || '暂无消息' }}</span>
      <span v-if="conversation.relatedItem" class="conv-item__goods">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
          <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z" />
          <path d="M3 6h18M16 10a4 4 0 0 1-8 0" />
        </svg>
        {{ conversation.relatedItem.title }}
      </span>
    </span>
    <span v-if="conversation.unreadCount > 0" class="conv-item__unread">{{ conversation.unreadCount }}</span>
  </button>
</template>

<script setup lang="ts">
import LevelBadge from '@/components/common/LevelBadge.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import type { Conversation } from '@/types/models'
import { formatTimeShort } from '@/utils/format'

/** 会话列表行：纯展示，选中事件上抛 */
defineProps<{
  conversation: Conversation
  active?: boolean
}>()

const emit = defineEmits<{
  select: []
}>()
</script>

<style scoped>
.conv-item {
  width: 100%;
  display: flex;
  gap: 12px;
  padding: 13px 20px;
  cursor: pointer;
  border: none;
  border-bottom: var(--bw) solid var(--line);
  background: transparent;
  color: var(--ink);
  text-align: left;
  position: relative;
}
.conv-item:hover {
  background: var(--paper-deep);
}
.conv-item.active {
  background: var(--yellow);
  box-shadow: inset 5px 0 0 var(--primary);
}
.conv-item__body {
  flex: 1;
  min-width: 0;
  display: block;
}
.conv-item__top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
}
.conv-item__name {
  min-width: 0;
  font-weight: 800;
  font-size: 14.5px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.conv-item__time {
  flex-shrink: 0;
  font-size: 11.5px;
  color: var(--ink-soft);
}
.conv-item__preview,
.conv-item__goods {
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conv-item__preview {
  font-size: 13px;
  color: var(--ink-soft);
  margin-top: 3px;
}
.conv-item__goods {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--green-deep);
}
.conv-item__goods svg {
  width: 12px;
  height: 12px;
  flex: 0 0 12px;
}
.conv-item__unread {
  position: absolute;
  right: 18px;
  bottom: 14px;
  min-width: 19px;
  height: 19px;
  padding: 0 5px;
  border-radius: 10px;
  background: var(--red);
  color: var(--white);
  font-size: 11px;
  font-weight: 800;
  display: grid;
  place-items: center;
  border: var(--bw) solid var(--line);
}
@media (max-width: 760px) {
  .conv-item {
    padding: 10px 8px;
    gap: 7px;
  }
  .conv-item :deep(.avatar) {
    display: none;
  }
  .conv-item__top {
    display: block;
  }
  .conv-item__time,
  .conv-item__goods,
  .conv-item__name :deep(.badge) {
    display: none;
  }
  .conv-item__unread {
    right: 6px;
    bottom: 6px;
  }
}
</style>
