import { contracts } from '@/types/contracts'
import type { ApiResult, QueryOf, Schemas } from '@/types/contracts'
import { mapNullableData, mapPageData, mapRequiredData, mapVoidData, ProtocolViolationError } from '@/api/mappers'
import type { Category, EventTopic, FavoriteToggleResult, Item, ItemDetail, ItemLineage, ItemSummary, PublishItemPayload, TagCloudGroup, TagCount } from '@/types/models'
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

/**
 * 大厅 Feed 游标查询：cursor 为空取首屏；翻页提交上一响应的 nextCursor。
 * 游标对客户端不透明：不得解析、修改或自行构造。
 */
export interface ItemFeedQuery {
  sort?: string
  keyword?: string
  categoryId?: number | string
  minPrice?: number | string
  maxPrice?: number | string
  type?: string
  tag?: string | string[]
  cursor?: string
  size?: number
}

/**
 * 大厅 Feed 游标响应：total 为首屏估算值（estimated），
 * 不承诺跨页精确；hasMore + nextCursor 驱动"加载更多"。
 */
export interface ItemFeedResult {
  records: ItemSummary[]
  nextCursor: string | null
  hasMore: boolean
  estimatedTotal: number
}

type FeedQuery = QueryOf<'/api/item/list', 'get'>

/** 页面输入（路由参数/表单值可能是字符串）→ 生成契约的 path id（number） */
function toPathId(id: number | string): number {
  return typeof id === 'number' ? id : Number(id)
}

function toOptionalNumber(value: number | string | null | undefined): number | undefined {
  if (value === null || value === undefined || value === '') return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

/** 页面输入（表单值可能是字符串/空串）→ 生成契约的 query 形状 */
function toFeedQuery(params: ItemFeedQuery): FeedQuery {
  return {
    keyword: params.keyword || undefined,
    categoryId: toOptionalNumber(params.categoryId),
    minPrice: toOptionalNumber(params.minPrice),
    maxPrice: toOptionalNumber(params.maxPrice),
    type: params.type || undefined,
    tag: params.tag === undefined ? undefined : Array.isArray(params.tag) ? params.tag : [params.tag],
    sort: params.sort || undefined,
    cursor: params.cursor || undefined,
    size: params.size
  }
}

/** 发布/编辑载荷：SWAP 的 price 为 null，契约侧归一为字段缺省 */
function toPublishBody(data: PublishItemPayload) {
  return { ...data, price: data.price ?? undefined }
}

function toReportBody(data: ReportPayload) {
  return { type: data.type, details: data.details ?? undefined }
}

export function getItemList(params: ItemFeedQuery): Promise<ApiResult<ItemFeedResult>> {
  return contracts.get('/api/item/list', { query: toFeedQuery(params) }).then((res) => mapRequiredData(res, '/api/item/list', (wire) => toFeedResult(wire, '/api/item/list')))
}

export function searchItems(params: ItemFeedQuery): Promise<ApiResult<ItemFeedResult>> {
  return contracts.get('/api/item/search', { query: toFeedQuery(params) }).then((res) => mapRequiredData(res, '/api/item/search', (wire) => toFeedResult(wire, '/api/item/search')))
}

export function getItemDetail(id: number | string) {
  return contracts.get('/api/item/{id}', { path: { id: toPathId(id) } }).then((res) => mapRequiredData(res, '/api/item/{id}', (wire) => wire as ItemDetail))
}

export function getItemLineage(id: number | string) {
  return contracts.get('/api/item/{id}/lineage', { path: { id: toPathId(id) } }).then((res) => mapRequiredData(res, '/api/item/{id}/lineage', (wire) => wire as ItemLineage))
}

export function getCategories() {
  return contracts.get('/api/category/list').then((res) => mapRequiredData(res, '/api/category/list', (wire) => wire as Category[]))
}

/** 标签建议：按标题与分类生成候选，仅供选择，不落库 */
export function getItemTagSuggestions(title: string, categoryId?: number | string | null) {
  return contracts
    .post('/api/item/tag-suggestions', {
      body: { title, categoryId: toOptionalNumber(categoryId) }
    })
    .then((res) => mapRequiredData(res, '/api/item/tag-suggestions', (wire) => wire as string[]))
}

export function getItemRanking(params: { limit: number }) {
  return contracts.get('/api/item/ranking', { query: params }).then((res) => mapRequiredData(res, '/api/item/ranking', (wire) => wire as ItemSummary[]))
}

export function getTrendingTags(params: { limit: number }) {
  return contracts.get('/api/item/ranking/tags', { query: params }).then((res) => mapRequiredData(res, '/api/item/ranking/tags', (wire) => wire as TagCount[]))
}

export function getSwapMatches() {
  return contracts.get('/api/item/swap-matches').then((res) => mapRequiredData(res, '/api/item/swap-matches', (wire) => wire as ItemSummary[]))
}

export function getErrands() {
  return contracts.get('/api/item/errands').then((res) => mapRequiredData(res, '/api/item/errands', (wire) => wire as ItemSummary[]))
}

/** 当前活动专题；"没有活动专题"是正常结果（data 为真实 null） */
export function getActiveTopic(): Promise<ApiResult<EventTopic | null>> {
  return contracts.get('/api/item/active-topic').then((res) => mapNullableData(res, (wire) => wire as EventTopic))
}

/** 获取本地生成标签及出现次数（按商品大类分组，供精细筛选标签云） */
export function getAllTags() {
  return contracts.get('/api/item/tags').then((res) => mapRequiredData(res, '/api/item/tags', (wire) => wire as TagCloudGroup[]))
}

/** 图片上传：受控 postFile（multipart 契约由生成类型约束，binary 字段即 File） */
export function uploadItemImage(file: File) {
  return contracts.postFile('/api/item/upload-image', file).then((res) => mapRequiredData(res, '/api/item/upload-image', (wire) => wire as { url: string }))
}

export function publishItem(data: PublishItemPayload) {
  return contracts.post('/api/item/publish', { body: toPublishBody(data) }).then((res) => mapRequiredData(res, '/api/item/publish', (wire) => wire as Item))
}

export function getOwnItem(id: number | string) {
  return contracts.get('/api/item/my-items/{id}', { path: { id: toPathId(id) } }).then((res) => mapRequiredData(res, '/api/item/my-items/{id}', (wire) => wire as ItemDetail))
}

export function updateItem(id: number | string, data: PublishItemPayload) {
  return contracts.put('/api/item/{id}', { path: { id: toPathId(id) }, body: toPublishBody(data) }).then((res) => mapRequiredData(res, '/api/item/{id}', (wire) => wire as Item))
}

export function getMyItems(params: PageQuery & { status?: string }) {
  return contracts.get('/api/item/my-items', { query: params }).then((res) => mapPageData(res, '/api/item/my-items', (row): ItemDetail => row))
}

export function getMyFavorites(params: PageQuery) {
  return contracts.get('/api/item/my-favorites', { query: params }).then((res) => mapPageData(res, '/api/item/my-favorites', (row): Item => row))
}

export function toggleFavorite(id: number | string) {
  return contracts.post('/api/item/{id}/favorite', { path: { id: toPathId(id) } }).then((res) => mapRequiredData(res, '/api/item/{id}/favorite', (wire) => wire as FavoriteToggleResult))
}

/** 下架是 void 操作：成功信封 data 为 null，不再断言成商品对象 */
export function offShelfItem(id: number | string): Promise<ApiResult<null>> {
  return contracts.put('/api/item/{id}/off-shelf', { path: { id: toPathId(id) } }).then(mapVoidData)
}

export function relistItem(id: number | string) {
  return contracts.put('/api/item/{id}/relist', { path: { id: toPathId(id) } }).then((res) => mapRequiredData(res, '/api/item/{id}/relist', (wire) => wire as Item))
}

export function reportItem(id: number | string, data: ReportPayload) {
  return contracts.post('/api/item/{id}/reports', { path: { id: toPathId(id) }, body: toReportBody(data) }).then(mapVoidData)
}

/** 申诉成功后后端回传完整 AppealVO（生成契约），消费方按需读取 */
export function submitItemAppeal(id: number | string, data: { reason: string }): Promise<ApiResult<Schemas['AppealVO']>> {
  return contracts.post('/api/item/{id}/appeals', { path: { id: toPathId(id) }, body: data }).then((res) => mapRequiredData(res, '/api/item/{id}/appeals', (wire) => wire))
}

export function deleteItem(id: number | string) {
  return contracts.delete('/api/item/{id}', { path: { id: toPathId(id) } }).then(mapVoidData)
}

function toFeedResult(wire: Schemas['MarketplaceFeedVO'], operation: '/api/item/list' | '/api/item/search'): ItemFeedResult {
  if (!Array.isArray(wire.records)) {
    throw new ProtocolViolationError(`${operation} 的 MarketplaceFeedVO.records 不是数组`)
  }
  const invalidRecordIndex = wire.records.findIndex((row) => row === null || row === undefined)
  if (invalidRecordIndex >= 0) {
    throw new ProtocolViolationError(`${operation} 的 MarketplaceFeedVO.records[${invalidRecordIndex}] 不能为空`)
  }
  if (wire.nextCursor !== null && typeof wire.nextCursor !== 'string') {
    throw new ProtocolViolationError(`${operation} 的 MarketplaceFeedVO.nextCursor 必须为字符串或 null`)
  }
  if (typeof wire.hasMore !== 'boolean') {
    throw new ProtocolViolationError(`${operation} 的 MarketplaceFeedVO.hasMore 不是布尔值`)
  }
  if (typeof wire.estimatedTotal !== 'number' || !Number.isFinite(wire.estimatedTotal) || wire.estimatedTotal < 0) {
    throw new ProtocolViolationError(`${operation} 的 MarketplaceFeedVO.estimatedTotal 非法：${String(wire.estimatedTotal)}`)
  }
  return {
    records: wire.records,
    nextCursor: wire.nextCursor,
    hasMore: wire.hasMore,
    estimatedTotal: wire.estimatedTotal
  }
}
