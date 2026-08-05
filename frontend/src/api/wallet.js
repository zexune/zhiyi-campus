import request from '@/utils/request'

/** 钱包相关接口（D 负责） */

export function getWalletBalance() {
  return request.get('/wallet/balance')
}

export function rechargeWallet(amount) {
  return request.post('/wallet/recharge', { amount })
}

export function getWalletLogs(params) {
  return request.get('/wallet/logs', { params })
}
