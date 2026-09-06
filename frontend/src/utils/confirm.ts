import { h } from 'vue'
import { ElMessageBox } from 'element-plus'
import ConfirmContent from '@/components/common/ConfirmContent.vue'

interface ConfirmOptions {
  title: string
  description: string
  context?: string
  subject?: string
  subjectLabel?: string
  amount?: number
  amountLabel?: string
  note?: string
  confirmText?: string
  cancelText?: string
  tone?: 'brand' | 'danger' | 'success'
}

/** 保留 Element 的焦点圈定与 Promise 语义，业务内容以 VNode 渲染，不拼接 HTML。 */
export function confirmAction({ title, confirmText = '确认', cancelText = '返回', tone = 'brand', ...content }: ConfirmOptions) {
  return ElMessageBox.confirm(h(ConfirmContent, { context: '操作确认', ...content }), title, {
    customClass: `campus-confirm campus-confirm--${tone}`,
    confirmButtonText: confirmText,
    cancelButtonText: cancelText,
    closeOnClickModal: false,
    autofocus: false
  })
}
