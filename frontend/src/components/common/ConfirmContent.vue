<template>
  <div class="confirm-content">
    <div class="confirm-content__identity">
      <img src="/logo.png" width="24" height="24" alt="" />
      <span>
        智易校园
        <i aria-hidden="true">/</i>
        {{ context }}
      </span>
    </div>
    <p class="confirm-content__description">{{ description }}</p>
    <div v-if="subject" class="confirm-content__subject">
      <span>{{ subjectLabel }}</span>
      <strong>{{ subject }}</strong>
    </div>
    <div v-if="amount !== undefined" class="confirm-content__amount">
      <span>{{ amountLabel }}</span>
      <strong>
        <small>¥</small>
        {{ formatPrice(amount) }}
      </strong>
    </div>
    <div v-if="note" class="confirm-content__note">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6z" />
        <path d="m8 12 3 3 5-6" />
      </svg>
      <span>{{ note }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { formatPrice } from '@/utils/format'

withDefaults(
  defineProps<{
    context: string
    description: string
    subject?: string
    subjectLabel?: string
    amount?: number
    amountLabel?: string
    note?: string
  }>(),
  { subjectLabel: '操作对象', amountLabel: '本次金额', subject: undefined, amount: undefined, note: undefined }
)
</script>
