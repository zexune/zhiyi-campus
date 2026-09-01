import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { Ref } from 'vue'

/**
 * 自绘 Modal 的焦点管理三件套（Esc 关闭 / Tab 焦点陷阱 / 关闭后焦点归还），
 * 从 SellerDetailDialog 沉淀而来，供所有 Teleport 自绘弹窗共用，
 * 与 el-dialog 的内建行为对齐（同一项目内只有一套键盘标准）。
 *
 * 用法：
 *   const { sheetRef, initialFocusRef } = useModalA11y(visibleRef, () => emit('close'))
 *   <div ref="sheetRef" role="dialog" aria-modal="true">… <button ref="initialFocusRef" />
 *
 * - visible 变 true：记录此前焦点、锁定背景滚动、焦点移入弹窗（initialFocusRef 优先）；
 * - visible 变 false：恢复背景滚动、焦点归还触发元素；
 * - Esc：触发 onClose；Tab：焦点在弹窗内可聚焦元素间循环。
 */
export function useModalA11y(visible: Ref<boolean>, onClose: () => void) {
  const sheetRef = ref<HTMLElement | null>(null)
  const initialFocusRef = ref<HTMLElement | null>(null)
  let previousBodyOverflow = ''
  let previouslyFocusedElement: HTMLElement | null = null

  const FOCUSABLE_SELECTOR = 'button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

  function handleKeydown(event: KeyboardEvent) {
    if (!visible.value) return
    if (event.key === 'Escape') {
      onClose()
      return
    }
    if (event.key !== 'Tab') return
    const focusable = [...(sheetRef.value?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR) || [])]
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

  watch(visible, async (value) => {
    if (value) {
      previouslyFocusedElement = document.activeElement as HTMLElement | null
      previousBodyOverflow = document.body.style.overflow
      document.body.style.overflow = 'hidden'
      await nextTick()
      ;(initialFocusRef.value || sheetRef.value?.querySelector<HTMLElement>(FOCUSABLE_SELECTOR))?.focus()
    } else {
      document.body.style.overflow = previousBodyOverflow
      previouslyFocusedElement?.focus?.()
      previouslyFocusedElement = null
    }
  })

  if (typeof window !== 'undefined') {
    window.addEventListener('keydown', handleKeydown)
    onBeforeUnmount(() => {
      window.removeEventListener('keydown', handleKeydown)
      document.body.style.overflow = previousBodyOverflow
    })
  }

  /** 模板绑定：`:ref="modal.bindSheet"`（函数式 ref；字符串 ref 依赖变量名匹配，重构易断） */
  function bindSheet(el: unknown): void {
    sheetRef.value = (el as HTMLElement | null) ?? null
  }
  function bindInitialFocus(el: unknown): void {
    initialFocusRef.value = (el as HTMLElement | null) ?? null
  }

  return { sheetRef, initialFocusRef, bindSheet, bindInitialFocus }
}
