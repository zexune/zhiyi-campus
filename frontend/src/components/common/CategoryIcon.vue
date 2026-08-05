<template>
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <template v-if="name === '全部'">
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </template>
    <template v-else-if="name === '数码电子'">
      <rect x="5" y="2" width="14" height="20" rx="2" />
      <path d="M12 18h.01" />
    </template>
    <path v-else-if="name === '教材书籍'" d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20V2H6.5A2.5 2.5 0 0 0 4 4.5v15A2.5 2.5 0 0 0 6.5 22H20" />
    <template v-else-if="name === '服饰鞋包'">
      <path d="M20.4 14.5 16 10 4 20M6 4l2.5 2.5M6 4 3.5 6.5M6 4v0l2.4 7.2a2 2 0 0 0 1.3 1.3L17 15" />
      <path d="m14 6 7 7-2 2" />
    </template>
    <template v-else-if="name === '生活日用'">
      <path d="M3 21h18M5 21V7l7-4 7 4v14M9 21v-6h6v6" />
    </template>
    <template v-else-if="name === '运动娱乐'">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 3a15 15 0 0 1 0 18M12 3a15 15 0 0 0 0 18M3.5 9h17M3.5 15h17" />
    </template>
    <template v-else-if="name === '零食饮品'">
      <path d="M17 8h1a4 4 0 1 1 0 8h-1M3 8h14v9a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4Z" />
      <path d="M6 2v2M10 2v2M14 2v2" />
    </template>
    <template v-else-if="name === '学习用品'">
      <path d="M12 19l7-7 3 3-7 7-3-3z" />
      <path d="M18 13l-1.5-7.5L2 2l3.5 14.5L13 18l5-5z" />
      <circle cx="11" cy="11" r="2" />
    </template>
    <template v-else>
      <path d="M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z" />
      <path d="m3.3 7 8.7 5 8.7-5M12 22V12" />
    </template>
  </svg>
</template>

<script setup>
defineProps({
  name: { type: String, required: true },
})
</script>
