<template>
  <el-dialog
    :model-value="visible"
    class="review-dialog"
    modal-class="review-modal"
    title="评价卖家"
    width="520px"
    append-to-body
    align-center
    destroy-on-close
    :show-close="!submitting"
    :close-on-click-modal="!submitting"
    :close-on-press-escape="!submitting"
    @update:model-value="handleModelValue"
  >
    <template #header>
      <div class="review-dialog__header">
        <div class="review-dialog__kicker">
          <span>ORDER REVIEW</span>
          <span aria-hidden="true">NO.{{ order?.id || '—' }}</span>
        </div>
        <h2>给卖家留个评价</h2>
        <p>你的真实反馈，会成为下一位同学的交易参考。</p>
      </div>
    </template>

    <div class="review-dialog__body">
      <section class="review-order-card" aria-label="本次评价订单">
        <div class="review-order-card__mark" aria-hidden="true">✓</div>
        <div>
          <span class="review-order-card__label">交易已完成</span>
          <strong>{{ order?.itemTitle || '校园闲置物品' }}</strong>
          <span>卖家：{{ order?.peerNickname || '校园同学' }}</span>
        </div>
      </section>

      <fieldset class="review-rating">
        <legend>这次交易体验怎么样？</legend>
        <div class="review-rating__row">
          <div class="review-stars" role="radiogroup" aria-label="卖家评分">
            <button
              v-for="n in 5"
              :key="n"
              class="star-btn"
              :class="{ active: form.rating >= n, selected: form.rating === n }"
              type="button"
              role="radio"
              :aria-label="`${n} 星`"
              :aria-checked="form.rating === n"
              :title="`${n} 星`"
              @click="form.rating = n"
            >
              <span aria-hidden="true">★</span>
            </button>
          </div>
          <div class="review-rating__value" aria-live="polite">
            <strong>{{ form.rating }}</strong>
            <span>{{ ratingLabel(form.rating) }}</span>
          </div>
        </div>
      </fieldset>

      <label class="review-accurate">
        <input v-model="form.accurate" type="checkbox" />
        <span class="review-accurate__box" aria-hidden="true">✓</span>
        <span>
          <strong>描述与实物相符</strong>
          <small>商品状态和卖家描述基本一致</small>
        </span>
      </label>

      <div class="field review-comment">
        <div class="review-comment__label">
          <label for="review-comment">评价内容 <span class="opt">选填</span></label>
          <span>{{ form.comment.length }} / 200</span>
        </div>
        <textarea
          id="review-comment"
          v-model.trim="form.comment"
          class="input textarea"
          maxlength="200"
          rows="3"
          placeholder="说说见面、沟通或商品使用体验…"
        />
      </div>
    </div>

    <template #footer>
      <div class="review-dialog__footer">
        <button class="btn" type="button" :disabled="submitting" @click="emit('close')">
          再想想
        </button>
        <button class="btn btn--primary" type="button" :disabled="submitting" @click="handleSubmit">
          {{ submitting ? '提交中…' : '提交评价 →' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, watch } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  order: { type: Object, default: null },
  submitting: { type: Boolean, default: false },
})

const emit = defineEmits(['close', 'submit'])
const RATING_LABELS = ['很不满意', '有待改进', '还算顺利', '体验不错', '非常满意']
const form = reactive(defaultForm())

function defaultForm() {
  return { rating: 5, accurate: true, comment: '' }
}

function ratingLabel(rating) {
  return RATING_LABELS[Math.min(Math.max(Number(rating) || 1, 1), 5) - 1]
}

function handleModelValue(value) {
  if (!value && !props.submitting) emit('close')
}

function handleSubmit() {
  if (!props.submitting) emit('submit', { ...form })
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) Object.assign(form, defaultForm())
  },
)
</script>

<style scoped>
.review-dialog__header {
  padding-right: 64px;
}

.review-dialog__kicker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-xs);
  color: var(--primary-deep);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 1.4px;
}

.review-dialog__kicker span:first-child {
  padding: 3px 9px;
  border: var(--bw) solid var(--ink);
  border-radius: 6px;
  background: var(--yellow);
  color: var(--ink);
  box-shadow: 2px 2px 0 var(--ink);
  transform: rotate(-1.5deg);
}

.review-dialog__header h2 {
  font-family: var(--font-display);
  font-size: 30px;
  line-height: 1.1;
  letter-spacing: 1px;
}

.review-dialog__header p {
  margin-top: var(--spacing-xs);
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.6;
}

.review-dialog__body {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.review-order-card {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  background: var(--paper-deep);
  box-shadow: var(--shadow-s);
}

.review-order-card__mark {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border: var(--bw) solid var(--ink);
  border-radius: 50%;
  background: var(--green);
  color: var(--white);
  font-size: 22px;
  font-weight: 900;
}

.review-order-card > div:last-child {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.review-order-card__label {
  color: var(--green-deep);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: .8px;
}

.review-order-card strong {
  overflow: hidden;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-order-card span:last-child {
  color: var(--ink-soft);
  font-size: 12px;
}

.review-rating {
  min-width: 0;
  padding: 0;
  border: 0;
}

.review-rating legend {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 900;
}

.review-rating__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.review-stars {
  display: flex;
  align-items: center;
  gap: 7px;
}

.star-btn {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border: var(--bw) solid var(--ink);
  border-radius: 9px;
  background: var(--white);
  color: var(--paper-deep);
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--ink);
  font-size: 25px;
  line-height: 1;
  transition: transform .15s, background-color .15s, color .15s, box-shadow .15s;
}

.star-btn:hover {
  transform: translateY(-2px) rotate(-2deg);
  background: var(--paper);
  color: var(--yellow);
}

.star-btn.active {
  background: var(--yellow);
  color: var(--ink);
}

.star-btn.selected {
  transform: translateY(-3px) rotate(3deg);
  background: var(--primary);
  color: var(--white);
  box-shadow: 4px 4px 0 var(--ink);
}

.star-btn:focus-visible {
  outline: 3px solid var(--blue);
  outline-offset: 3px;
}

.review-rating__value {
  min-width: 78px;
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: center;
  column-gap: 6px;
}

.review-rating__value strong {
  grid-row: 1 / 3;
  color: var(--primary);
  font-family: var(--font-display);
  font-size: 38px;
  line-height: 1;
}

.review-rating__value::after {
  content: 'STAR';
  color: var(--ink-soft);
  font-size: 9px;
  font-weight: 900;
  letter-spacing: 1px;
}

.review-rating__value span {
  font-size: 11px;
  font-weight: 700;
}

.review-accurate {
  position: relative;
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 11px 13px;
  border: var(--bw) dashed var(--ink);
  border-radius: var(--r-s);
  background: var(--white);
  cursor: pointer;
}

.review-accurate input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  opacity: 0;
}

.review-accurate__box {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border: var(--bw) solid var(--ink);
  border-radius: 6px;
  background: var(--white);
  color: transparent;
  font-weight: 900;
  box-shadow: 2px 2px 0 var(--ink);
}

.review-accurate input:checked + .review-accurate__box {
  background: var(--green);
  color: var(--white);
}

.review-accurate input:focus-visible + .review-accurate__box {
  outline: 3px solid var(--blue);
  outline-offset: 3px;
}

.review-accurate > span:last-child {
  display: grid;
  gap: 1px;
}

.review-accurate strong {
  font-size: 13px;
}

.review-accurate small {
  color: var(--ink-soft);
  font-size: 11px;
}

.review-comment {
  margin: 0;
}

.review-comment__label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-bottom: 7px;
}

.review-comment__label label {
  margin: 0;
}

.review-comment__label > span {
  color: var(--ink-soft);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.review-comment .textarea {
  min-height: 92px;
  resize: vertical;
}

.review-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 560px) {
  .review-dialog__header {
    padding-right: 54px;
  }

  .review-dialog__header h2 {
    font-size: 26px;
  }

  .review-rating__row {
    align-items: flex-start;
    flex-direction: column;
  }

  .review-stars {
    width: 100%;
    justify-content: space-between;
    gap: 4px;
  }

  .star-btn {
    width: 42px;
    height: 42px;
  }

  .review-rating__value {
    display: flex;
    align-items: baseline;
  }

  .review-rating__value strong {
    font-size: 30px;
  }

  .review-rating__value::after {
    order: 3;
  }

  .review-dialog__footer {
    display: grid;
    grid-template-columns: 1fr 1.35fr;
  }

  .review-dialog__footer .btn {
    width: 100%;
    padding-inline: 12px;
  }
}
</style>

<style>
/* Teleport 到 body 后需要使用专属全局类控制 Element Plus 外壳。 */
.review-modal {
  background: color-mix(in srgb, var(--ink) 62%, transparent);
  backdrop-filter: blur(3px);
}

.review-modal .el-overlay-dialog {
  padding: var(--spacing-md);
}

.review-dialog.el-dialog {
  position: relative;
  width: min(520px, calc(100vw - 32px)) !important;
  max-height: calc(100vh - 32px);
  display: flex;
  flex-direction: column;
  margin: auto;
  overflow: visible;
  border: 3px solid var(--ink);
  border-radius: var(--r-m);
  background-color: var(--paper);
  background-image: radial-gradient(color-mix(in srgb, var(--ink) 8%, transparent) 1px, transparent 1px);
  background-size: 18px 18px;
  box-shadow: 10px 10px 0 var(--ink);
}

.review-dialog.el-dialog::before {
  content: '';
  position: absolute;
  z-index: 2;
  top: -13px;
  left: 50%;
  width: 104px;
  height: 25px;
  border: var(--bw) solid var(--ink);
  background: var(--yellow);
  transform: translateX(-50%) rotate(-2deg);
  box-shadow: 2px 2px 0 var(--ink);
}

.review-dialog .el-dialog__header {
  position: relative;
  margin: 0;
  padding: 28px 28px 16px;
  border-bottom: var(--bw) solid var(--ink);
}

.review-dialog .el-dialog__headerbtn {
  top: 22px;
  right: 22px;
  width: 36px;
  height: 36px;
  border: var(--bw) solid var(--ink);
  border-radius: 50%;
  background: var(--white);
  box-shadow: 2px 2px 0 var(--ink);
  transition: transform .15s, background-color .15s, box-shadow .15s;
}

.review-dialog .el-dialog__headerbtn:hover {
  background: var(--primary);
  box-shadow: 4px 4px 0 var(--ink);
  transform: translate(-1px, -1px) rotate(5deg);
}

.review-dialog .el-dialog__headerbtn .el-dialog__close {
  color: var(--ink);
  font-size: 20px;
  font-weight: 900;
}

.review-dialog .el-dialog__headerbtn:hover .el-dialog__close {
  color: var(--white);
}

.review-dialog .el-dialog__body {
  min-height: 0;
  padding: 18px 28px;
  overflow-y: auto;
  color: var(--ink);
}

.review-dialog .el-dialog__footer {
  padding: 16px 28px 24px;
  border-top: var(--bw) solid var(--ink);
}

@media (max-width: 560px) {
  .review-modal .el-overlay-dialog {
    padding: 12px;
  }

  .review-dialog.el-dialog {
    width: calc(100vw - 24px) !important;
    max-height: calc(100vh - 24px);
    border-radius: var(--r-s);
    box-shadow: 6px 6px 0 var(--ink);
  }

  .review-dialog .el-dialog__header {
    padding: 24px 20px 14px;
  }

  .review-dialog .el-dialog__headerbtn {
    top: 17px;
    right: 17px;
    width: 34px;
    height: 34px;
  }

  .review-dialog .el-dialog__body {
    padding: 16px 20px;
  }

  .review-dialog .el-dialog__footer {
    padding: 14px 20px 20px;
  }
}
</style>
