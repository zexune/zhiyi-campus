/**
 * 构造“我的发布”分页查询参数。
 * 空状态表示全部，不发送 status，避免后端把空字符串当作实际筛选值。
 */
export function buildMyItemsParams(page, size, status) {
  const params = { page, size }
  if (status) params.status = status
  return params
}
