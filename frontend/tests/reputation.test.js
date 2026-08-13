import { test } from 'vitest'
import assert from 'node:assert/strict'

import {
  REPUTATION_DIMENSIONS,
  axisAngle,
  radarPoint,
  radarPolygon,
  reputationValues,
  overallScore,
  reputationGrade,
} from '../src/utils/reputation.js'

const approx = (a, b, eps = 1e-6) =>
  assert.ok(Math.abs(a - b) <= eps, `${a} !~= ${b}`)

// ================================================================
// 维度定义
// ================================================================

test('exposes exactly the six backend reputation dimensions in order', () => {
  assert.deepEqual(
    REPUTATION_DIMENSIONS.map((d) => d.key),
    ['completionRate', 'responseSpeed', 'accuracy', 'praise', 'activity', 'compliance'],
  )
  REPUTATION_DIMENSIONS.forEach((d) => assert.ok(d.label && typeof d.label === 'string'))
})

// ================================================================
// 雷达几何：六轴从正上方起、顺时针每 60°
// ================================================================

test('axisAngle starts pointing straight up and steps 60° clockwise', () => {
  approx(axisAngle(0), -Math.PI / 2)
  approx(axisAngle(1), -Math.PI / 2 + (2 * Math.PI) / 6)
  approx(axisAngle(6), -Math.PI / 2 + 2 * Math.PI)
})

test('radarPoint at full score on the first axis lands straight above center', () => {
  const p = radarPoint(100, 0, { radius: 100, cx: 120, cy: 120 })
  approx(p.x, 120)
  approx(p.y, 20) // cy - radius
})

test('radarPoint scales distance linearly with the 0-100 value', () => {
  const half = radarPoint(50, 0, { radius: 100, cx: 0, cy: 0 })
  approx(half.x, 0)
  approx(half.y, -50)
  const zero = radarPoint(0, 2, { radius: 100, cx: 0, cy: 0 })
  approx(zero.x, 0)
  approx(zero.y, 0)
})

test('radarPoint clamps out-of-range values into 0-100', () => {
  const over = radarPoint(999, 0, { radius: 100, cx: 0, cy: 0 })
  approx(over.y, -100)
  const under = radarPoint(-50, 0, { radius: 100, cx: 0, cy: 0 })
  approx(under.y, 0)
  const nan = radarPoint('abc', 0, { radius: 100, cx: 0, cy: 0 })
  approx(nan.y, 0)
})

test('radarPolygon renders one rounded x,y pair per value', () => {
  const pts = radarPolygon([100, 0, 0, 0, 0, 0], { radius: 100, cx: 100, cy: 100 })
  const pairs = pts.trim().split(/\s+/)
  assert.equal(pairs.length, 6)
  assert.equal(pairs[0], '100,0')
})

// ================================================================
// VO → 有序取值 / 综合分 / 评级
// ================================================================

test('reputationValues pulls the six dimensions in canonical order', () => {
  const vo = {
    userId: 7,
    completionRate: 90,
    responseSpeed: 80,
    accuracy: 70,
    praise: 60,
    activity: 50,
    compliance: 40,
    reviewCount: 4,
  }
  assert.deepEqual(reputationValues(vo), [90, 80, 70, 60, 50, 40])
})

test('reputationValues defaults missing dimensions to 0', () => {
  assert.deepEqual(reputationValues({}), [0, 0, 0, 0, 0, 0])
  assert.deepEqual(reputationValues(null), [0, 0, 0, 0, 0, 0])
})

test('overallScore averages the six dimensions and rounds', () => {
  assert.equal(overallScore({ completionRate: 90, responseSpeed: 80, accuracy: 70, praise: 60, activity: 50, compliance: 40 }), 65)
  assert.equal(overallScore({ completionRate: 100, responseSpeed: 100, accuracy: 100, praise: 100, activity: 99, compliance: 100 }), 100)
  assert.equal(overallScore(null), 0)
})

test('reputationGrade maps overall score to a badge label', () => {
  assert.equal(reputationGrade(95), '信誉极佳')
  assert.equal(reputationGrade(80), '信誉良好')
  assert.equal(reputationGrade(60), '信誉一般')
  assert.equal(reputationGrade(30), '信誉待提升')
})
