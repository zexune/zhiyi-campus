import { contracts } from '@/types/contracts'
import { mapPageData, mapRequiredData, mapVoidData } from '@/api/mappers'
import type { Order, OrderDetail, PageQuery } from '@/types/models'

/** 订单相关接口（D 负责） */

export interface OrderListQuery extends PageQuery {
  status?: string
}

export interface ReviewPayload {
  rating: number
  accurate: boolean
  comment: string
}

/** 资金操作幂等键请求头（单一来源：后端 ApiHeaders.IDEMPOTENCY_KEY） */
export const IDEMPOTENCY_HEADER = 'X-Idempotency-Key'

/** 下单：idempotencyKey 由调用方持久化管理（结果不明时复用原键重试） */
export function createOrder(itemId: number, idempotencyKey: string) {
  return contracts
    .post('/api/order/create', {
      body: { itemId },
      headers: { [IDEMPOTENCY_HEADER]: idempotencyKey }
    })
    .then((res) => mapRequiredData(res, '/api/order/create', (wire) => wire as OrderDetail))
}

export function confirmReceipt(orderId: number, idempotencyKey: string) {
  return contracts
    .put('/api/order/{id}/confirm', {
      path: { id: orderId },
      headers: { [IDEMPOTENCY_HEADER]: idempotencyKey }
    })
    .then((res) => mapRequiredData(res, '/api/order/{id}/confirm', (wire) => wire as OrderDetail))
}

export function cancelOrder(orderId: number, idempotencyKey: string) {
  return contracts
    .put('/api/order/{id}/cancel', {
      path: { id: orderId },
      headers: { [IDEMPOTENCY_HEADER]: idempotencyKey }
    })
    .then((res) => mapRequiredData(res, '/api/order/{id}/cancel', (wire) => wire as OrderDetail))
}

export function getBoughtOrders(params: OrderListQuery) {
  return contracts.get('/api/order/my-bought', { query: params }).then((res) => mapPageData(res, '/api/order/my-bought', (row): Order => row))
}

export function getSoldOrders(params: OrderListQuery) {
  return contracts.get('/api/order/my-sold', { query: params }).then((res) => mapPageData(res, '/api/order/my-sold', (row): Order => row))
}

/** 买家确认收货后对卖家评价（A7） */
export function reviewOrder(orderId: number, data: ReviewPayload) {
  return contracts.post('/api/order/{id}/review', { path: { id: orderId }, body: data }).then(mapVoidData)
}
