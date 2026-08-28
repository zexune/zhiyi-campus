import { test } from 'vitest'
import assert from 'node:assert/strict'

import { orderStatusLabel, orderStatusBadge, itemStatusLabel, itemStatusBadge, itemTypeLabel, walletLogTypeLabel, formatPriceYuan, isExpense, buildOrderParams, expProgress } from '../src/utils/trade'

// ================================================================
// 订单状态
// ================================================================

test('orderStatusLabel maps all known statuses', () => {
  assert.equal(orderStatusLabel('WAITING_MEET'), '待见面')
  assert.equal(orderStatusLabel('COMPLETED'), '已完成')
  assert.equal(orderStatusLabel('CANCELLED'), '已取消')
  assert.equal(orderStatusLabel('BOGUS'), 'BOGUS')
  assert.equal(orderStatusLabel(null), '未知')
  assert.equal(orderStatusLabel(undefined), '未知')
})

test('orderStatusBadge returns correct CSS classes', () => {
  assert.equal(orderStatusBadge('WAITING_MEET'), 'badge--warn')
  assert.equal(orderStatusBadge('COMPLETED'), 'badge--ok')
  assert.equal(orderStatusBadge('CANCELLED'), 'badge--muted')
  assert.equal(orderStatusBadge('UNKNOWN'), 'badge--muted')
})

// ================================================================
// 商品状态
// ================================================================

test('itemStatusLabel maps all known statuses', () => {
  assert.equal(itemStatusLabel('ON_SALE'), '在售中')
  assert.equal(itemStatusLabel('REVIEWING'), '审核中')
  assert.equal(itemStatusLabel('SOLD'), '已售出')
  assert.equal(itemStatusLabel('OFF_SHELF'), '已下架')
  assert.equal(itemStatusLabel(''), '未知')
})

test('itemStatusBadge returns correct CSS classes', () => {
  assert.equal(itemStatusBadge('ON_SALE'), 'badge--ok')
  assert.equal(itemStatusBadge('REVIEWING'), 'badge--warn')
  assert.equal(itemStatusBadge('SOLD'), 'badge--muted')
  assert.equal(itemStatusBadge('OFF_SHELF'), 'badge--muted')
})

test('itemTypeLabel maps all types and passes through unknown values', () => {
  assert.equal(itemTypeLabel('SELL'), '出售')
  assert.equal(itemTypeLabel('BUY'), '求购')
  assert.equal(itemTypeLabel('SWAP'), '换物')
  assert.equal(itemTypeLabel('ERRAND'), '跑腿')
  assert.equal(itemTypeLabel('BOGUS'), 'BOGUS')
})

// ================================================================
// 钱包流水
// ================================================================

test('walletLogTypeLabel maps all types', () => {
  assert.equal(walletLogTypeLabel('RECHARGE'), '充值')
  assert.equal(walletLogTypeLabel('PAYMENT'), '支出')
  assert.equal(walletLogTypeLabel('REFUND'), '退款')
  assert.equal(walletLogTypeLabel('INCOME'), '收入')
})

// ================================================================
// 金额格式化
// ================================================================

test('formatPriceYuan handles normal values', () => {
  assert.equal(formatPriceYuan(99), '¥99.00')
  assert.equal(formatPriceYuan(0), '¥0.00')
  assert.equal(formatPriceYuan(99.5), '¥99.50')
  assert.equal(formatPriceYuan(1000), '¥1,000.00')
  assert.equal(formatPriceYuan(1234567.89), '¥1,234,567.89')
})

test('formatPriceYuan handles edge cases', () => {
  assert.equal(formatPriceYuan(null), '¥0.00')
  assert.equal(formatPriceYuan(undefined), '¥0.00')
  assert.equal(formatPriceYuan('abc'), '¥0.00')
  assert.equal(formatPriceYuan(''), '¥0.00')
})

test('formatPriceYuan handles negative values', () => {
  assert.equal(formatPriceYuan(-50), '¥-50.00')
})

// ================================================================
// isExpense
// ================================================================

test('isExpense detects negative amounts', () => {
  assert.equal(isExpense(-50), true)
  assert.equal(isExpense(50), false)
  assert.equal(isExpense(0), false)
  assert.equal(isExpense(null), false)
  assert.equal(isExpense(undefined), false)
})

// ================================================================
// buildOrderParams
// ================================================================

test('buildOrderParams includes status when provided', () => {
  assert.deepEqual(buildOrderParams(2, 10, 'COMPLETED'), { page: 2, size: 10, status: 'COMPLETED' })
})

test('buildOrderParams omits status when empty', () => {
  assert.deepEqual(buildOrderParams(1, 10, ''), { page: 1, size: 10 })
  assert.deepEqual(buildOrderParams(1, 10, null), { page: 1, size: 10 })
})

// ================================================================
// expProgress 经验值进度
// ================================================================

test('expProgress calculates level progress correctly', () => {
  // Lv.2: 100-300 → 50 exp in = 25%
  assert.equal(expProgress(150, 2), 25)
  // Lv.3: 300-600 → 450 exp = 50%
  assert.equal(expProgress(450, 3), 50)
  // Lv.1: 0-100 → 50 exp = 50%
  assert.equal(expProgress(50, 1), 50)
})

test('expProgress caps at 100 for max level', () => {
  assert.equal(expProgress(2000, 5), 100)
})

test('expProgress handles edge cases', () => {
  assert.equal(expProgress(0, 1), 0)
  assert.equal(expProgress(null, 1), 0)
  assert.equal(expProgress(100, null), 0)
})
