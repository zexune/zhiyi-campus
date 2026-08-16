import { ref } from 'vue'

/**
 * 服务端分页列表的统一状态机：records / total / currentPage / loading / loadError + 拉取动作。
 *
 * 用法：
 *   const { records, currentPage, pageSize, total, loading, loadError, fetchList, goToFirstPage }
 *     = usePagedList(getBoughtOrders, { params: () => ({ status: currentFilter.value }) })
 *
 * - loader 收到 `{ page, size, ...params() }`，返回统一响应（res.data.records / res.data.total）；
 * - params 必须是函数：切换筛选时先 `goToFirstPage()` 再 `fetchList()`，params() 取到的始终是最新筛选；
 * - 失败只置 loadError，不抛出（错误提示由调用方或 request.js 决定）。
 */
export function usePagedList(loader, { size = 10, params = () => ({}) } = {}) {
  const records = ref([])
  const currentPage = ref(1)
  const pageSize = ref(size)
  const total = ref(0)
  const loading = ref(false)
  const loadError = ref(false)

  async function fetchList() {
    loading.value = true
    loadError.value = false
    try {
      const extra = params() || {}
      const res = await loader({ page: currentPage.value, size: pageSize.value, ...extra })
      // 兼容裸数组响应（部分接口未分页时直接返回列表）
      const payload = res?.data
      records.value = Array.isArray(payload) ? payload : payload?.records || []
      total.value = Array.isArray(payload) ? payload.length : payload?.total || 0
    } catch {
      loadError.value = true
    } finally {
      loading.value = false
    }
  }

  /** 切换筛选/刷新场景：回到第一页（不会自动拉取，配合 fetchList 使用） */
  function goToFirstPage() {
    currentPage.value = 1
  }

  return { records, currentPage, pageSize, total, loading, loadError, fetchList, goToFirstPage }
}
