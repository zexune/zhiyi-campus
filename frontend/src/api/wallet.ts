import request from '@/utils/request'
import type { PageResult, WalletBalance, WalletLog } from '@/types/models'

/** 钱包相关接口（D 负责） */

export function getWalletBalance() {
  return request.get<WalletBalance>('/wallet/balance')
}

export function rechargeWallet(amount: number) {
  return request.post<WalletBalance>('/wallet/recharge', { amount })
}

export function getWalletLogs(params: { page: number; size: number }) {
  return request.get<PageResult<WalletLog>>('/wallet/logs', { params })
}
