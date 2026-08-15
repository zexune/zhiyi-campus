/**
 * 信誉雷达（A6）—— 纯几何与取值工具，不依赖浏览器，供 ReputationRadar 组件与单测复用。
 *
 * 后端 ReputationVO 六维（各 0-100）：
 *   completionRate 交易完成率 / responseSpeed 响应速度 / accuracy 描述准确度
 *   praise 历史好评 / activity 活跃度 / compliance 合规度
 */

/** 六个维度的固定顺序与中文标签（与后端字段一一对应） */
export const REPUTATION_DIMENSIONS = [
  { key: 'completionRate', label: '交易完成率' },
  { key: 'responseSpeed', label: '响应速度' },
  { key: 'accuracy', label: '描述准确度' },
  { key: 'praise', label: '历史好评' },
  { key: 'activity', label: '活跃度' },
  { key: 'compliance', label: '合规度' }
]

const AXES = REPUTATION_DIMENSIONS.length

/** 第 i 根轴的角度：正上方起（-90°），顺时针均匀分布。 */
export function axisAngle(i) {
  return -Math.PI / 2 + (i * 2 * Math.PI) / AXES
}

function clampScore(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return 0
  return Math.max(0, Math.min(100, n))
}

const round2 = (n) => Math.round(n * 100) / 100

/** 单个分值在第 i 根轴上的落点坐标 */
export function radarPoint(value, i, { radius, cx, cy }) {
  const r = (clampScore(value) / 100) * radius
  const a = axisAngle(i)
  return { x: round2(cx + r * Math.cos(a)), y: round2(cy + r * Math.sin(a)) }
}

/** 一组分值 → SVG polygon 的 points 串（"x,y x,y ..."） */
export function radarPolygon(values, geom) {
  return values
    .map((v, i) => {
      const p = radarPoint(v, i, geom)
      return `${p.x},${p.y}`
    })
    .join(' ')
}

/** ReputationVO → 规范顺序的六维数组 */
export function reputationValues(vo) {
  return REPUTATION_DIMENSIONS.map((d) => clampScore(vo?.[d.key]))
}

/** 六维均值（0-100，四舍五入），作为综合信誉分。 */
export function overallScore(vo) {
  const values = reputationValues(vo)
  const sum = values.reduce((acc, v) => acc + v, 0)
  return Math.round(sum / values.length)
}

/** 综合分 → 评级文案 */
export function reputationGrade(score) {
  if (score >= 90) return '信誉极佳'
  if (score >= 75) return '信誉良好'
  if (score >= 50) return '信誉一般'
  return '信誉待提升'
}
