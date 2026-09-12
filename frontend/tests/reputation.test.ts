import { test } from 'vitest'
import assert from 'node:assert/strict'

import {
  REPUTATION_DIMENSIONS,
  axisAngle,
  radarPoint,
  radarPolygon,
  mapReputation,
  reputationValues,
  reputationDimensions,
  insufficientFlags,
  overallScore,
  reputationGrade
} from '../src/utils/reputation'
import type { ReputationVo } from '../src/utils/reputation'

const approx = (a: number, b: number, eps = 1e-6) => assert.ok(Math.abs(a - b) <= eps, `${a} !~= ${b}`)

/** 后端 wire 形态样例：score 缺失 = 样本不足 */
function wireVo(rows: Array<[string, number | null, number, string]>): unknown {
  return {
    userId: 7,
    dimensions: rows.map(([key, score, samples, summary]) => ({
      key,
      label: REPUTATION_DIMENSIONS.find((d) => d.key === key)?.label ?? key,
      ...(score === null ? {} : { score }),
      samples,
      summary
    }))
  }
}

// ================================================================
// 维度定义
// ================================================================

test('exposes exactly the six backend reputation dimensions in order', () => {
  assert.deepEqual(
    REPUTATION_DIMENSIONS.map((d) => d.key),
    ['completionRate', 'responseSpeed', 'accuracy', 'praise', 'activity', 'compliance']
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
// wire → 领域形状归一
// ================================================================

test('mapReputation normalizes wire rows and keeps unknown dimensions at the tail', () => {
  const vo = mapReputation(wireVo([
    ['completionRate', 92, 12, '近 180 天 12 单成交 11 单'],
    ['responseSpeed', null, 2, '样本不足'],
    ['accuracy', null, 1, ''],
    ['praise', 90, 6, '平均 4.5 星'],
    ['activity', 35, 13, '近 30 天发布 10 件'],
    ['compliance', 100, 0, '无有效处罚'],
    ['futureDim', 55, 9, '后端新增维度']
  ])) as ReputationVo

  assert.equal(vo.userId, 7)
  assert.equal(vo.dimensions.length, 7)
  // score 缺失归一为 null（样本不足），其余原样
  assert.equal(vo.dimensions[0].score, 92)
  assert.equal(vo.dimensions[1].score, null)
  assert.equal(vo.dimensions[1].samples, 2)
  assert.equal(vo.dimensions[1].summary, '样本不足')
  // 未知维度原样透传并排在六个已知维度之后
  assert.equal(vo.dimensions[6].key, 'futureDim')
  assert.equal(vo.dimensions[6].score, 55)
})

test('mapReputation fills missing known dimensions as insufficient placeholders', () => {
  const vo = mapReputation({ dimensions: [{ key: 'praise', label: '历史好评', samples: 3 }] })
  assert.equal(vo.dimensions.length, 6)
  // 行存在但 score 缺失同样是样本不足；缺行的维度补 0 样本空位
  assert.deepEqual(
    insufficientFlags(vo),
    [true, true, true, true, true, true]
  )
  // 已提供但 score 缺失 → 样本不足，samples 原样保留
  assert.equal(vo.dimensions[3].score, null)
  assert.equal(vo.dimensions[3].samples, 3)
  assert.equal(vo.dimensions[0].samples, 0)
})

test('mapReputation tolerates empty and malformed input', () => {
  assert.equal(mapReputation(null).dimensions.length, 6)
  assert.equal(mapReputation({}).dimensions.length, 6)
  assert.equal(insufficientFlags(mapReputation({ dimensions: 'oops' })).every(Boolean), true)
})

// ================================================================
// VO → 有序取值 / 样本不足 / 综合分 / 评级
// ================================================================

test('reputationValues pulls scored dimensions in canonical order', () => {
  const vo = mapReputation(wireVo([
    ['completionRate', 90, 9, ''],
    ['responseSpeed', 80, 9, ''],
    ['accuracy', 70, 9, ''],
    ['praise', 60, 9, ''],
    ['activity', 50, 9, ''],
    ['compliance', 40, 9, '']
  ]))
  assert.deepEqual(reputationValues(vo), [90, 80, 70, 60, 50, 40])
})

test('insufficient dimensions draw as zero but keep the mask', () => {
  const vo = mapReputation(wireVo([
    ['completionRate', 90, 9, ''],
    ['responseSpeed', null, 2, ''],
    ['accuracy', null, 0, ''],
    ['praise', 60, 9, ''],
    ['activity', 50, 9, ''],
    ['compliance', null, 0, '']
  ]))
  assert.deepEqual(reputationValues(vo), [90, 0, 0, 60, 50, 0])
  assert.deepEqual(insufficientFlags(vo), [false, true, true, false, false, true])
})

test('overallScore averages only scored dimensions', () => {
  const partial = mapReputation(wireVo([
    ['completionRate', 90, 9, ''],
    ['responseSpeed', null, 2, ''],
    ['accuracy', null, 0, ''],
    ['praise', 60, 9, ''],
    ['activity', 50, 9, ''],
    ['compliance', null, 0, '']
  ]))
  // (90 + 60 + 50) / 3 ≈ 67
  assert.equal(overallScore(partial), 67)
})

test('overallScore returns null when every dimension lacks samples', () => {
  assert.equal(overallScore(mapReputation({})), null)
  assert.equal(overallScore(null), null)
})

test('overallScore rounds full six-dimension averages', () => {
  const full = mapReputation(wireVo([
    ['completionRate', 90, 9, ''],
    ['responseSpeed', 80, 9, ''],
    ['accuracy', 70, 9, ''],
    ['praise', 60, 9, ''],
    ['activity', 50, 9, ''],
    ['compliance', 40, 9, '']
  ]))
  assert.equal(overallScore(full), 65)
})

test('reputationGrade maps overall score to a badge label', () => {
  assert.equal(reputationGrade(95), '信誉极佳')
  assert.equal(reputationGrade(80), '信誉良好')
  assert.equal(reputationGrade(60), '信誉一般')
  assert.equal(reputationGrade(30), '信誉待提升')
  assert.equal(reputationGrade(null), '数据积累中')
})

test('reputationDimensions returns exactly the six known axes', () => {
  const vo = mapReputation(wireVo([
    ['completionRate', 90, 9, ''],
    ['responseSpeed', 80, 9, ''],
    ['accuracy', 70, 9, ''],
    ['praise', 60, 9, ''],
    ['activity', 50, 9, ''],
    ['compliance', 40, 9, ''],
    ['futureDim', 10, 9, '']
  ]))
  assert.equal(reputationDimensions(vo).length, 6)
})
