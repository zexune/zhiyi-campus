import { ref } from 'vue'
import type { Ref } from 'vue'
import type { ApiResult } from '@/utils/request'
import type { PageQuery, PageResult } from '@/types/models'

/**
 * 服务端分页列表的统一状态机：records / total / currentPage / loading / loadError + 拉取动作。
 *
 * 用法：
 *   const { records, currentPage, pageSize, total, loading, loadError, fetchList, goToFirstPage }
 *     = usePagedList(getBoughtOrders, { params: () => ({ status: currentFilter.value }) })
 *
 * - loader 收到 `{ page, size, ...params() }`，返回统一响应（res.data.records / res.data.total）；
 *   运行时兼容裸数组响应（历史接口未分页时直接返回列表）；
 * - params 必须是函数：切换筛选时先 `goToFirstPage()` 再 `fetchList()`，params() 取到的始终是最新筛选；
 * - 失败只置 loadError，不抛出（错误提示由调用方或 request.ts 决定）。
 */
export interface UsePagedListReturn<T> {
  records: Ref<T[]>
  currentPage: Ref<number>
  pageSize: Ref<number>
  total: Ref<number>
  loading: Ref<boolean>
  loadError: Ref<boolean>
  fetchList: () => Promise<void>
  goToFirstPage: () => void
}

export function usePagedList<T, P extends object = Record<string, never>>(
  loader: (query: PageQuery & P) => Promise<ApiResult<PageResult<T> | T[]>>,
  { size = 10, params = () => ({}) as P }: { size?: number; params?: () => P | null } = {}
): UsePagedListReturn<T> {
  const records: Ref<T[]> = ref([])
  const currentPage: Ref<number> = ref(1)
  const pageSize: Ref<number> = ref(size)
  const total: Ref<number> = ref(0)
  const loading: Ref<boolean> = ref(false)
  const loadError: Ref<boolean> = ref(false)

  async function fetchList(): Promise<void> {
    loading.value = true
    loadError.value = false
    try {
      const extra = params() ?? ({} as P)
      const res = await loader({ page: currentPage.value, size: pageSize.value, ...extra })
      // 兼容裸数组响应（部分接口未分页时直接返回列表）
      const payload: PageResult<T> | T[] | undefined = res?.data
      records.value = Array.isArray(payload) ? payload : payload?.records || []
      total.value = Array.isArray(payload) ? payload.length : payload?.total || 0
    } catch {
      loadError.value = true
    } finally {
      loading.value = false
    }
  }

  /** 切换筛选/刷新场景：回到第一页（不会自动拉取，配合 fetchList 使用） */
  function goToFirstPage(): void {
    currentPage.value = 1
  }

  return { records, currentPage, pageSize, total, loading, loadError, fetchList, goToFirstPage }
}
