import request from '@/utils/request'

/** 商品相关接口（B/C 负责后端；A 的「我的发布/我的收藏」页面按附录B契约调用） */

export function getItemList(params) {
  return request.get('/item/list', { params })
}

export function searchItems(params) {
  return request.get('/item/search', { params })
}

export function getItemDetail(id) {
  return request.get(`/item/${id}`)
}

export function getItemLineage(id) {
  return request.get(`/item/${id}/lineage`)
}

export function getCategories() {
  return request.get('/category/list')
}

export function getItemRanking(params) {
  return request.get('/item/ranking', { params })
}

export function getTrendingAiTags(params) {
  return request.get('/item/ranking/tags', { params })
}

export function getSwapMatches() {
  return request.get('/item/swap-matches')
}

export function getErrands() {
  return request.get('/item/errands')
}

export function getActiveTopic() {
  return request.get('/item/active-topic')
}

/** 获取全部 AI 标签及出现次数（精细筛选标签云） */
export function getAllTags() {
  return request.get('/item/tags')
}

export function uploadItemImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/item/upload-image', formData)
}

export function publishItem(data) {
  return request.post('/item/publish', data)
}

export function getOwnItem(id) {
  return request.get(`/item/my-items/${id}`)
}

export function updateItem(id, data) {
  return request.put(`/item/${id}`, data)
}

export function getMyItems(params) {
  return request.get('/item/my-items', { params })
}

export function getMyFavorites(params) {
  return request.get('/item/my-favorites', { params })
}

export function toggleFavorite(id) {
  return request.post(`/item/${id}/favorite`)
}

export function offShelfItem(id) {
  return request.put(`/item/${id}/off-shelf`)
}

export function relistItem(id) {
  return request.put(`/item/${id}/relist`)
}

export function deleteItem(id) {
  return request.delete(`/item/${id}`)
}
