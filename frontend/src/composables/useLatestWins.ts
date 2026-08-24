import { onUnmounted, ref } from 'vue'
import type { Ref } from 'vue'

/**
 * 查询类请求的 latest-wins 守卫（F1/F9 根因修复）。
 *
 * generation 单调递增：每次发起新请求前推进代数，响应落地前校验代数，
 * 乱序返回的旧响应（成功/失败/finally）一律丢弃，不再覆盖新状态。
 * onUnmounted 自动失效，组件卸载后的迟到响应不写任何状态。
 *
 * 用法：
 *   const guard = useLatestWins()
 *   async function fetch() {
 *     const g = guard.begin()
 *     loading.value = true
 *     try {
 *       const res = await api()
 *       if (!guard.isCurrent(g)) return
 *       data.value = res.data
 *     } catch {
 *       if (guard.isCurrent(g)) error.value = true
 *     } finally {
 *       if (guard.isCurrent(g)) loading.value = false
 *     }
 *   }
 */
export interface UseLatestWinsReturn {
  generation: Ref<number>
  /** 推进代数并返回本次请求的代数快照 */
  begin: () => number
  /** 本次请求是否仍是最新代（组件卸载后恒为 false） */
  isCurrent: (gen: number) => boolean
}

export function useLatestWins(): UseLatestWinsReturn {
  const generation = ref(0)
  let alive = true

  function begin(): number {
    generation.value += 1
    return generation.value
  }

  function isCurrent(gen: number): boolean {
    return alive && gen === generation.value
  }

  onUnmounted(() => {
    alive = false
    generation.value += 1
  })

  return { generation, begin, isCurrent }
}
