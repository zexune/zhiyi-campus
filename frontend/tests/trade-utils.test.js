import test from 'node:test'
import assert from 'node:assert/strict'

import {
  orderStatusLabel,
  orderStatusBadge,
  itemStatusLabel,
  itemStatusBadge,
  violationStatusLabel,
  walletLogTypeLabel,
  formatPrice,
  isExpense,
  buildOrderParams,
  expProgress,
} from '../src/utils/trade.js'

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
  assert.equal(itemStatusLabel('ON_SALE'), '在售')
  assert.equal(itemStatusLabel('PENDING'), '交易中')
  assert.equal(itemStatusLabel('SOLD'), '已售出')
  assert.equal(itemStatusLabel('OFF_SHELF'), '已下架')
  assert.equal(itemStatusLabel(''), '未知')
})

test('itemStatusBadge returns correct CSS classes', () => {
  assert.equal(itemStatusBadge('ON_SALE'), 'badge--ok')
  assert.equal(itemStatusBadge('PENDING'), 'badge--warn')
  assert.equal(itemStatusBadge('SOLD'), 'badge--muted')
  assert.equal(itemStatusBadge('OFF_SHELF'), 'badge--muted')
})

// ================================================================
// 违规状态
// ================================================================

test('violationStatusLabel maps all known statuses', () => {
  assert.equal(violationStatusLabel('PENDING'), '待处理')
  assert.equal(violationStatusLabel('CONFIRMED'), '已确认')
  assert.equal(violationStatusLabel('DISMISSED'), '已驳回')
})

// ================================================================
// 钱包流水
// ================================================================

test('walletLogTypeLabel maps all types', () => {
  assert.equal(walletLogTypeLabel('RECHARGE'), '充值')
  assert.equal(walletLogTypeLabel('PAYMENT'), '支付')
  assert.equal(walletLogTypeLabel('REFUND'), '退款')
  assert.equal(walletLogTypeLabel('INCOME'), '收入')
})

// ================================================================
// 金额格式化
// ================================================================

test('formatPrice handles normal values', () => {
  assert.equal(formatPrice(99), '¥99.00')
  assert.equal(formatPrice(0), '¥0.00')
  assert.equal(formatPrice(99.5), '¥99.50')
  assert.equal(formatPrice(1000), '¥1,000.00')
  assert.equal(formatPrice(1234567.89), '¥1,234,567.89')
})

test('formatPrice handles edge cases', () => {
  assert.equal(formatPrice(null), '¥0.00')
  assert.equal(formatPrice(undefined), '¥0.00')
  assert.equal(formatPrice('abc'), '¥0.00')
  assert.equal(formatPrice(''), '¥0.00')
})

test('formatPrice handles negative values', () => {
  assert.equal(formatPrice(-50), '¥-50.00')
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
  assert.deepEqual(
    buildOrderParams(2, 10, 'COMPLETED'),
    { page: 2, size: 10, status: 'COMPLETED' },
  )
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
