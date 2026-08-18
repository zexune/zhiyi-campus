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

export function createOrder(itemId: number) {
  return request.post<Order>('/order/create', { itemId })
}

export function confirmReceipt(orderId: number) {
  return request.put<Order>(`/order/${orderId}/confirm`)
}

export function cancelOrder(orderId: number) {
  return request.put<Order>(`/order/${orderId}/cancel`)
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
