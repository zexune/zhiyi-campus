import { ElMessage } from 'element-plus'
import { toggleFavorite } from '@/api/item'
import { useEntityMutex } from '@/composables/useEntityMutex'

/**
 * 收藏切换的共用实现 —— 此前首页与榜单是两份手写副本（互斥集合、
 * 成功提示各一份，行为细节还会漂移）。这里收口 per-entity 互斥 +
 * 接口调用 + 结果提示；调用方只负责拿到结果后更新自己的视图
 * （首页就地改字段，榜单整表刷新），刷新策略的差异保留在各自页面。
 */
export function useFavorite() {
  const mutex = useEntityMutex<number>()
  /** 模板按 busy 集合禁用按钮 */
  const busyIds = mutex.lockedIds

  async function toggle(itemId: number): Promise<{ favorite: boolean; favoriteCount: number } | null> {
    if (!mutex.tryLock(itemId)) return null
    try {
      const res = await toggleFavorite(itemId)
      ElMessage.success(res.data.favorite ? '已收藏' : '已取消收藏')
      return { favorite: res.data.favorite, favoriteCount: res.data.favoriteCount }
    } finally {
      mutex.unlock(itemId)
    }
  }

  return { busyIds, toggle }
}
