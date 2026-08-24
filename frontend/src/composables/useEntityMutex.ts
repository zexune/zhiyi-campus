import { ref } from 'vue'
import type { Ref } from 'vue'

/**
 * 写操作的 per-entity 互斥（F4/F5/F10 根因修复）。
 *
 * 以实体 ID（订单 ID / 商品 ID / 会话 ID）为粒度互斥：
 * 同一实体的写入入口同步早退（先置位再 await，杜绝 await 窗口内的重复提交），
 * 不同实体互不阻塞（A 进行中不影响 B）。
 *
 * 用法：
 *   const mutex = useEntityMutex<number>()
 *   async function toggle(id: number) {
 *     if (!mutex.tryLock(id)) return
 *     try { await api(id) } finally { mutex.unlock(id) }
 *   }
 */
export interface UseEntityMutexReturn<K extends string | number> {
  lockedIds: Ref<Set<K>>
  /** 同步尝试加锁：已锁定返回 false（调用方直接早退） */
  tryLock: (id: K) => boolean
  unlock: (id: K) => void
  isLocked: (id: K) => boolean
}

export function useEntityMutex<K extends string | number>(): UseEntityMutexReturn<K> {
  const lockedIds = ref(new Set<K>()) as Ref<Set<K>>

  function tryLock(id: K): boolean {
    if (lockedIds.value.has(id)) return false
    lockedIds.value.add(id)
    lockedIds.value = new Set(lockedIds.value)
    return true
  }

  function unlock(id: K): void {
    if (!lockedIds.value.has(id)) return
    lockedIds.value.delete(id)
    lockedIds.value = new Set(lockedIds.value)
  }

  function isLocked(id: K): boolean {
    return lockedIds.value.has(id)
  }

  return { lockedIds, tryLock, unlock, isLocked }
}
