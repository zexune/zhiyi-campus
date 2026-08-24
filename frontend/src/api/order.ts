import request from '@/utils/request'
import type { Order, PageQuery, PageResult } from '@/types/models'

/** 订单相关接口（D 负责） */

export interface OrderListQuery extends PageQuery {
  status?: string
}

export interface ReviewPayload {
  rating: number
  accurate: boolean
  comment: string
}

/** 资金操作幂等键请求头（后端 OrderController.IDEMPOTENCY_HEADER） */
export const IDEMPOTENCY_HEADER = 'X-Idempotency-Key'

/** 下单：idempotencyKey 由调用方持久化管理（结果不明时复用原键重试） */
export function createOrder(itemId: number, idempotencyKey: string) {
  return request.post<Order>('/order/create', { itemId }, { headers: { [IDEMPOTENCY_HEADER]: idempotencyKey } })
}

export function confirmReceipt(orderId: number, idempotencyKey: string) {
  return request.put<Order>(`/order/${orderId}/confirm`, undefined, { headers: { [IDEMPOTENCY_HEADER]: idempotencyKey } })
}

export function cancelOrder(orderId: number, idempotencyKey: string) {
  return request.put<Order>(`/order/${orderId}/cancel`, undefined, { headers: { [IDEMPOTENCY_HEADER]: idempotencyKey } })
}

export function getBoughtOrders(params: OrderListQuery) {
  return request.get<PageResult<Order>>('/order/my-bought', { params })
}

export function getSoldOrders(params: OrderListQuery) {
  return request.get<PageResult<Order>>('/order/my-sold', { params })
}

/** 买家确认收货后对卖家评价（A7） */
export function reviewOrder(orderId: number, data: ReviewPayload) {
  return request.post<void>(`/order/${orderId}/review`, data)
}
