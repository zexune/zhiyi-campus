import request from '@/utils/request'
import type { Category, EventTopic, FavoriteToggleResult, Item, ItemDetail, ItemLineage, ModerationResult, PageResult, PublishItemPayload, TagCloudGroup, TagCount } from '@/types/models'
import type { PageQuery } from '@/types/models'

/** 商品相关接口（B/C 负责后端；A 的「我的发布/我的收藏」页面按附录B契约调用） */

export interface ItemListQuery extends PageQuery {
  sort?: string
  keyword?: string
  categoryId?: number | string
  minPrice?: number | string
  maxPrice?: number | string
  type?: string
  /** 标签筛选：可传多个，任一命中即入选（专题多标签场景） */
  tag?: string | string[]
}

export interface ReportPayload {
  type: string
  details: string | null
}

export function getItemList(params: ItemListQuery) {
  return request.get<PageResult<Item>>('/item/list', { params })
}

export function searchItems(params: ItemListQuery) {
  return request.get<PageResult<Item>>('/item/search', { params })
}

export function getItemDetail(id: number | string) {
  return request.get<ItemDetail>(`/item/${id}`)
}

export function getItemLineage(id: number | string) {
  return request.get<ItemLineage>(`/item/${id}/lineage`)
}

export function getCategories() {
  return request.get<Category[]>('/category/list')
}

/** 标签建议：按标题与分类生成候选，仅供选择，不落库 */
export function getItemTagSuggestions(title: string, categoryId?: number | string | null) {
  return request.post<string[]>('/item/tag-suggestions', {
    title,
    categoryId: categoryId === '' ? null : (categoryId ?? null)
  })
}

export function getItemRanking(params: { limit: number }) {
  return request.get<Item[]>('/item/ranking', { params })
}

export function getTrendingTags(params: { limit: number }) {
  return request.get<TagCount[]>('/item/ranking/tags', { params })
}

export function getSwapMatches() {
  return request.get<Item[]>('/item/swap-matches')
}

export function getErrands() {
  return request.get<Item[]>('/item/errands')
}

export function getActiveTopic() {
  return request.get<EventTopic>('/item/active-topic')
}

/** 获取本地生成标签及出现次数（按商品大类分组，供精细筛选标签云） */
export function getAllTags() {
  return request.get<TagCloudGroup[]>('/item/tags')
}

export function uploadItemImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<{ url: string }>('/item/upload-image', formData)
}

export function publishItem(data: PublishItemPayload) {
  return request.post<ModerationResult>('/item/publish', data)
}

export function getOwnItem(id: number | string) {
  return request.get<ItemDetail>(`/item/my-items/${id}`)
}

export function updateItem(id: number | string, data: PublishItemPayload) {
  return request.put<ModerationResult>(`/item/${id}`, data)
}

export function getMyItems(params: PageQuery & { status?: string }) {
  return request.get<PageResult<Item>>('/item/my-items', { params })
}

export function getMyFavorites(params: PageQuery) {
  return request.get<PageResult<Item>>('/item/my-favorites', { params })
}

export function toggleFavorite(id: number | string) {
  return request.post<FavoriteToggleResult>(`/item/${id}/favorite`)
}

export function offShelfItem(id: number | string) {
  return request.put<ModerationResult>(`/item/${id}/off-shelf`)
}

export function relistItem(id: number | string) {
  return request.put<ModerationResult>(`/item/${id}/relist`)
}

export function reportItem(id: number | string, data: ReportPayload) {
  return request.post<void>(`/item/${id}/reports`, data)
}

export function submitItemAppeal(id: number | string, data: { reason: string }) {
  return request.post<void>(`/item/${id}/appeals`, data)
}

export function deleteItem(id: number | string) {
  return request.delete<void>(`/item/${id}`)
}
