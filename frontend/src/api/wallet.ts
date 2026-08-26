import { contracts } from '@/types/contracts'
import { mapPageData, mapRequiredData } from '@/api/mappers'
import type { WalletBalance, WalletLog } from '@/types/models'
import { IDEMPOTENCY_HEADER } from './order'

/** 钱包相关接口（D 负责） */

export function getWalletBalance() {
  return contracts.get('/api/wallet/balance').then((res) => mapRequiredData(res, '/api/wallet/balance', (wire) => wire as WalletBalance))
}

/** 充值：idempotencyKey 由调用方持久化管理（未决充值复用原键恢复结果） */
export function rechargeWallet(amount: number, idempotencyKey: string) {
  return contracts
    .post('/api/wallet/recharge', {
      body: { amount },
      headers: { [IDEMPOTENCY_HEADER]: idempotencyKey }
    })
    .then((res) => mapRequiredData(res, '/api/wallet/recharge', (wire) => wire as WalletBalance))
}

export function getWalletLogs(params: { page: number; size: number }) {
  return contracts.get('/api/wallet/logs', { query: params }).then((res) => mapPageData(res, '/api/wallet/logs', (row): WalletLog => row))
}
