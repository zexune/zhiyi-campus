import request from '@/utils/request'
import type { PageResult, WalletBalance, WalletLog } from '@/types/models'
import { IDEMPOTENCY_HEADER } from './order'

/** 钱包相关接口（D 负责） */

export function getWalletBalance() {
  return request.get<WalletBalance>('/wallet/balance')
}

/** 充值：idempotencyKey 由调用方持久化管理（未决充值复用原键恢复结果） */
export function rechargeWallet(amount: number, idempotencyKey: string) {
  return request.post<WalletBalance>('/wallet/recharge', { amount }, { headers: { [IDEMPOTENCY_HEADER]: idempotencyKey } })
}

export function getWalletLogs(params: { page: number; size: number }) {
  return request.get<PageResult<WalletLog>>('/wallet/logs', { params })
}
