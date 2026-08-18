/**
 * 展示层格式化工具（无副作用纯函数）—— 时间、价格、占位图与头像配色的唯一实现，
 * 页面不得再各自复制 fmtTime/formatDate/phClass 之类的本地版本。
 *
 * 时间约定：后端 LocalDateTime 序列化为无时区的 ISO 字符串（如 2026-08-13T12:30:00），
 * 按本地时间解析并展示，不做时区换算。
 */

type DateLike = string | number | Date | null | undefined

const PLACEHOLDER_CLASSES = ['ph-a', 'ph-b', 'ph-c', 'ph-d', 'ph-e', 'ph-f'] as const
const AVATAR_CLASSES = ['avatar--orange', 'avatar--green', 'avatar--blue', 'avatar--yellow', 'avatar--ink'] as const

export type PlaceholderClass = (typeof PLACEHOLDER_CLASSES)[number]
export type AvatarClass = (typeof AVATAR_CLASSES)[number]

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** '2026-08-13 12:30'；非法/空值返回空串 */
export function formatDateTime(value: DateLike): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** '2026-08-13'；非法/空值返回空串 */
export function formatDate(value: DateLike): string {
  const dateTime = formatDateTime(value)
  return dateTime ? dateTime.slice(0, 10) : ''
}

/** '08-13 12:30'（会话列表等紧凑场景） */
export function formatTimeShort(value: DateLike): string {
  const dateTime = formatDateTime(value)
  return dateTime ? dateTime.slice(5) : ''
}

/** 聊天气泡时间：同一天只显示 HH:mm，跨天显示 M/D HH:mm */
export function formatChatTime(value: DateLike): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const pad2 = (n: number): string => String(n).padStart(2, '0')
  const time = `${pad2(date.getHours())}:${pad2(date.getMinutes())}`
  if (date.toDateString() === new Date().toDateString()) return time
  return `${date.getMonth() + 1}/${date.getDate()} ${time}`
}

/** 金额统一两位小数：'19.90'；空值按 0 处理（注意与 utils/trade.ts 的 ¥ 千分位版本语义不同） */
export function formatPrice(value: unknown): string {
  return Number(value || 0).toFixed(2)
}

/** 无图占位背景类（ph-a ~ ph-f），按 ID 稳定取色 */
export function placeholderClass(id: number | string | null | undefined): PlaceholderClass {
  return PLACEHOLDER_CLASSES[Number(id || 0) % PLACEHOLDER_CLASSES.length]
}

/** 头像配色类（avatar--orange 等），按 ID 稳定取色 */
export function avatarColorClass(id: number | string | null | undefined): AvatarClass {
  return AVATAR_CLASSES[Number(id || 0) % AVATAR_CLASSES.length]
}
