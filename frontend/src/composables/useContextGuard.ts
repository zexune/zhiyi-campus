import { onUnmounted, ref } from 'vue'
import type { Ref } from 'vue'

/**
 * 上下文类页面的会话守卫（F2/F3/M11 根因修复）。
 *
 * 三重校验：
 * - contextId：当前会话（如 conversationId / sessionId）；
 * - generation：switchContext 推进代数，切走后的旧响应整体丢弃；
 * - requestSeq：同会话内的请求序号，迟到的旧响应不覆盖新响应
 *   （加载更早历史等顺序无关的请求可只校验 gen + convId，不传 seq）。
 *
 * onUnmounted 自动失效。
 */
export interface UseContextGuardReturn<T extends string | number> {
  contextId: Ref<T | null>
  generation: Ref<number>
  requestSeq: Ref<number>
  /** 切换会话：推进代数并重置同会话序号 */
  switchContext: (id: T) => void
  /** 发起同会话请求前调用，取得 { gen, seq } 快照 */
  nextRequest: () => { gen: number; seq: number }
  /** 响应落地前校验（seq 可选：历史分页等顺序无关请求不传） */
  isCurrent: (id: T, gen: number, seq?: number) => boolean
}

export function useContextGuard<T extends string | number>(): UseContextGuardReturn<T> {
  const contextId = ref<T | null>(null) as Ref<T | null>
  const generation = ref(0)
  const requestSeq = ref(0)
  let alive = true

  function switchContext(id: T): void {
    contextId.value = id
    generation.value += 1
    requestSeq.value = 0
  }

  function nextRequest(): { gen: number; seq: number } {
    return { gen: generation.value, seq: ++requestSeq.value }
  }

  function isCurrent(id: T, gen: number, seq?: number): boolean {
    if (!alive) return false
    if (contextId.value === null || contextId.value !== id) return false
    if (generation.value !== gen) return false
    if (seq !== undefined && seq !== requestSeq.value) return false
    return true
  }

  onUnmounted(() => {
    alive = false
    generation.value += 1
  })

  return { contextId, generation, requestSeq, switchContext, nextRequest, isCurrent }
}
