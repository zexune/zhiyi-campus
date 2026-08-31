<template>
  <Teleport to="body">
    <Transition name="seller-dialog">
      <div v-if="visible" class="seller-dialog__overlay" role="presentation" @click.self="emit('close')">
        <section ref="dialogSheet" class="seller-dialog__sheet" role="dialog" aria-modal="true" aria-labelledby="seller-dialog-title" :aria-busy="loading">
          <span class="seller-dialog__tape" aria-hidden="true"></span>
          <span class="seller-dialog__file-tag" aria-hidden="true">SELLER FILE</span>

          <button ref="closeButton" class="seller-dialog__close" type="button" aria-label="关闭卖家详情" @click="emit('close')">×</button>

          <header class="seller-dialog__header">
            <UserAvatar :nickname="seller?.nickname || '同学'" :user-id="seller?.id || 0" size="l" :src="seller?.avatar || null" />
            <div class="seller-dialog__identity">
              <span class="seller-dialog__eyebrow">校园卖家档案</span>
              <div class="seller-dialog__name-row">
                <h2 id="seller-dialog-title">{{ seller?.nickname || '' }}</h2>
                <LevelBadge v-if="seller?.level" :level="seller.level" show-title :title="seller.levelTitle || ''" />
              </div>
              <span class="seller-dialog__school">{{ seller?.schoolName || '' }}</span>
            </div>
          </header>

          <div v-if="loading" class="seller-dialog__loading" aria-label="卖家信息加载中">
            <div class="seller-dialog__loading-fields">
              <i v-for="n in 6" :key="n"></i>
            </div>
            <div class="seller-dialog__loading-radar"></div>
          </div>

          <div v-else-if="error" class="seller-dialog__error">
            <span class="seller-dialog__error-mark" aria-hidden="true">!</span>
            <strong>卖家信息加载失败</strong>
            <button class="btn btn--sm" type="button" @click="emit('retry')">重新加载</button>
          </div>

          <div v-else class="seller-dialog__content">
            <section class="seller-dialog__details" aria-labelledby="seller-info-title">
              <div class="seller-dialog__section-head">
                <span aria-hidden="true">01</span>
                <h3 id="seller-info-title">联系与校园信息</h3>
              </div>
              <dl class="seller-dialog__grid">
                <div v-for="field in detailFields" :key="field.label" class="seller-dialog__field">
                  <dt>{{ field.label }}</dt>
                  <dd :class="{ 'is-empty': !field.value }">{{ field.value || '\u00A0' }}</dd>
                </div>
              </dl>
            </section>

            <section class="seller-dialog__reputation" aria-labelledby="seller-reputation-title">
              <div class="seller-dialog__section-head">
                <span aria-hidden="true">02</span>
                <h3 id="seller-reputation-title">信誉雷达</h3>
              </div>
              <ReputationRadar v-if="reputation" :reputation="reputation" :size="250" />
              <div v-else class="seller-dialog__radar-empty" aria-label="暂无信誉数据"></div>
            </section>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { PropType } from 'vue'
import LevelBadge from '@/components/common/LevelBadge.vue'
import ReputationRadar from '@/components/common/ReputationRadar.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import type { ReputationVo } from '@/utils/reputation'

/** getSellerDetail 响应中本对话框实际渲染的字段（宽松契约：全部可缺省） */
interface SellerDetail {
  id?: number
  nickname?: string
  level?: number
  schoolName?: string
  campus?: string
  college?: string
  grade?: string
  dormitory?: string
  phone?: string
  schoolEmail?: string
  /** 卖家头像相对路径（getSellerDetail 的 SellerDetailVO 新增字段） */
  avatar?: string | null
  /** 等级称号（服务端 LevelRule 下发，徽章不做本地映射） */
  levelTitle?: string
}

const props = defineProps({
  visible: { type: Boolean, default: false },
  seller: { type: Object as PropType<SellerDetail | null>, default: null },
  reputation: { type: Object as PropType<ReputationVo | null>, default: null },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false }
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'retry'): void
}>()
const closeButton = ref<HTMLElement | null>(null)
const dialogSheet = ref<HTMLElement | null>(null)
let previousBodyOverflow = ''
// 打开弹窗前记录焦点元素，关闭时归还焦点（activeElement 契约为 Element，此处按 HTMLElement 使用）
let previouslyFocusedElement: HTMLElement | null = null

const detailFields = computed(() => [
  { label: '昵称', value: props.seller?.nickname },
  { label: '手机号', value: props.seller?.phone },
  { label: '学校邮箱', value: props.seller?.schoolEmail },
  { label: '校区', value: props.seller?.campus },
  { label: '学院', value: props.seller?.college },
  { label: '年级', value: props.seller?.grade },
  { label: '宿舍楼', value: props.seller?.dormitory }
])

function handleKeydown(event: KeyboardEvent) {
  if (props.visible && event.key === 'Escape') {
    emit('close')
    return
  }
  if (props.visible && event.key === 'Tab') {
    const focusable = [...(dialogSheet.value?.querySelectorAll<HTMLElement>('button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled])') || [])]
    if (!focusable.length) return
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }
}

watch(
  () => props.visible,
  async (visible) => {
    if (visible) {
      previouslyFocusedElement = document.activeElement as HTMLElement | null
      previousBodyOverflow = document.body.style.overflow
      document.body.style.overflow = 'hidden'
      await nextTick()
      closeButton.value?.focus()
    } else {
      document.body.style.overflow = previousBodyOverflow
      previouslyFocusedElement?.focus?.()
      previouslyFocusedElement = null
    }
  }
)

onMounted(() => window.addEventListener('keydown', handleKeydown))

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  document.body.style.overflow = previousBodyOverflow
})
</script>

<style scoped>
.seller-dialog__overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: grid;
  place-items: center;
  padding: 28px;
  overflow-y: auto;
  background: radial-gradient(circle at 15% 20%, rgba(255, 201, 77, 0.22), transparent 28%), rgba(38, 34, 28, 0.66);
  backdrop-filter: blur(4px);
}

.seller-dialog__sheet {
  position: relative;
  width: min(900px, 100%);
  max-height: calc(100vh - 56px);
  overflow-y: auto;
  padding: 34px;
  color: var(--ink);
  background-color: var(--paper);
  background-image: radial-gradient(rgba(38, 34, 28, 0.07) 1px, transparent 1px);
  background-size: 18px 18px;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-l);
  box-shadow: var(--shadow-l);
}

.seller-dialog__tape {
  position: absolute;
  top: -10px;
  left: 50%;
  width: 118px;
  height: 30px;
  translate: -50% 0;
  rotate: -2deg;
  background: rgba(255, 201, 77, 0.82);
  border: 1px solid rgba(38, 34, 28, 0.22);
}

.seller-dialog__file-tag {
  position: absolute;
  top: 22px;
  right: 76px;
  padding: 3px 10px;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 1.5px;
  color: var(--paper);
  background: var(--ink);
  border-radius: 5px;
  rotate: 2deg;
}

.seller-dialog__close {
  position: absolute;
  top: 18px;
  right: 20px;
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  padding: 0 0 3px;
  color: var(--ink);
  background: var(--white);
  border: var(--bw) solid var(--line);
  border-radius: 50%;
  box-shadow: var(--shadow-s);
  font-size: 28px;
  line-height: 1;
  cursor: pointer;
  transition:
    transform 0.15s,
    box-shadow 0.15s,
    background 0.15s;
}

.seller-dialog__close:hover {
  background: var(--paper-deep);
  color: var(--ink);
}

.seller-dialog__close:focus-visible {
  outline: 3px solid var(--blue);
  outline-offset: 3px;
}

.seller-dialog__header {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 12px 150px 24px 4px;
  border-bottom: var(--bw) solid var(--line);
}

.seller-dialog__identity {
  min-width: 0;
}

.seller-dialog__eyebrow {
  display: block;
  margin-bottom: 2px;
  color: var(--primary);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 2px;
}

.seller-dialog__name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.seller-dialog__name-row h2 {
  max-width: 420px;
  overflow: hidden;
  font-family: var(--font-display);
  font-size: clamp(28px, 4vw, 40px);
  font-weight: 400;
  line-height: 1.1;
  letter-spacing: 1px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.seller-dialog__school {
  display: block;
  min-height: 24px;
  margin-top: 4px;
  color: var(--ink-soft);
  font-size: 14px;
  font-weight: 700;
}

.seller-dialog__content,
.seller-dialog__loading {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(300px, 0.95fr);
  gap: 28px;
  padding-top: 28px;
}

.seller-dialog__details,
.seller-dialog__reputation {
  min-width: 0;
}

.seller-dialog__section-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.seller-dialog__section-head span {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  font-family: var(--font-display);
  font-size: 16px;
  color: var(--white);
  background: var(--primary);
  border: var(--bw) solid var(--line);
  border-radius: 50%;
  box-shadow: var(--shadow-s);
}

.seller-dialog__section-head h3 {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 400;
  letter-spacing: 0.5px;
}

.seller-dialog__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  overflow: hidden;
  background: var(--white);
  border: var(--bw) solid var(--line);
  border-radius: var(--r-m);
  box-shadow: var(--shadow-s);
}

.seller-dialog__field {
  min-width: 0;
  min-height: 82px;
  padding: 13px 15px;
  border-bottom: var(--bw) solid var(--line);
}

.seller-dialog__field:nth-child(odd) {
  border-right: var(--bw) solid var(--line);
}

.seller-dialog__field:nth-last-child(-n + 2) {
  border-bottom: none;
}

.seller-dialog__field dt {
  margin-bottom: 5px;
  color: var(--ink-soft);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.8px;
}

.seller-dialog__field dd {
  min-height: 24px;
  overflow: hidden;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.seller-dialog__field dd.is-empty {
  background-image: linear-gradient(to bottom, transparent calc(100% - 1px), rgba(38, 34, 28, 0.2) 1px);
}

.seller-dialog__reputation {
  position: relative;
  padding: 0 16px 16px;
  background: var(--white);
  border: var(--bw) solid var(--line);
  border-radius: var(--r-m);
  box-shadow: var(--shadow-m);
}

.seller-dialog__reputation .seller-dialog__section-head {
  margin: -13px 0 4px;
}

.seller-dialog__radar-empty {
  min-height: 310px;
}

.seller-dialog__loading-fields {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  overflow: hidden;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-m);
}

.seller-dialog__loading-fields i {
  min-height: 82px;
  background: linear-gradient(100deg, var(--paper-deep) 25%, var(--white) 40%, var(--paper-deep) 55%);
  background-size: 220% 100%;
  border: var(--bw) dashed var(--line-strong);
  animation: seller-shimmer 1.2s linear infinite;
}

.seller-dialog__loading-radar {
  min-height: 330px;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-m);
  background: radial-gradient(circle, transparent 28%, rgba(245, 86, 46, 0.11) 29% 30%, transparent 31%), var(--white);
  animation: seller-pulse 1.2s ease-in-out infinite alternate;
}

.seller-dialog__error {
  min-height: 300px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 14px;
  text-align: center;
}

.seller-dialog__error-mark {
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  font-family: var(--font-display);
  font-size: 34px;
  color: var(--white);
  background: var(--red);
  border: var(--bw) solid var(--line);
  border-radius: 50%;
  box-shadow: var(--shadow-s);
  rotate: -5deg;
}

.seller-dialog-enter-active,
.seller-dialog-leave-active {
  transition: opacity 0.2s ease;
}

.seller-dialog-enter-active .seller-dialog__sheet,
.seller-dialog-leave-active .seller-dialog__sheet {
  transition:
    opacity 0.2s ease,
    transform 0.28s cubic-bezier(0.2, 0.8, 0.2, 1);
}

.seller-dialog-enter-from,
.seller-dialog-leave-to {
  opacity: 0;
}

.seller-dialog-enter-from .seller-dialog__sheet,
.seller-dialog-leave-to .seller-dialog__sheet {
  opacity: 0;
  transform: translateY(22px) scale(0.98);
}

@keyframes seller-shimmer {
  to {
    background-position: -120% 0;
  }
}

@keyframes seller-pulse {
  to {
    opacity: 0.58;
  }
}

@media (max-width: 760px) {
  .seller-dialog__overlay {
    place-items: end center;
    padding: 12px;
  }

  .seller-dialog__sheet {
    max-height: calc(100vh - 24px);
    padding: 26px 18px 22px;
    border-radius: var(--r-l) var(--r-l) var(--r-s) var(--r-s);
    box-shadow: var(--shadow-m);
  }

  .seller-dialog__file-tag {
    display: none;
  }

  .seller-dialog__header {
    padding: 18px 48px 20px 2px;
  }

  .seller-dialog__content,
  .seller-dialog__loading {
    grid-template-columns: 1fr;
    gap: 26px;
  }
}

@media (max-width: 440px) {
  .seller-dialog__overlay {
    padding: 0;
  }

  .seller-dialog__sheet {
    max-height: 100vh;
    min-height: 100vh;
    border-right: none;
    border-bottom: none;
    border-left: none;
    border-radius: var(--r-l) var(--r-l) 0 0;
    box-shadow: none;
  }

  .seller-dialog__grid {
    grid-template-columns: 1fr;
  }

  .seller-dialog__field,
  .seller-dialog__field:nth-child(odd),
  .seller-dialog__field:nth-last-child(-n + 2) {
    min-height: 72px;
    border-right: none;
    border-bottom: var(--bw) solid var(--line);
  }

  .seller-dialog__field:last-child {
    border-bottom: none;
  }
}
</style>
