/**
 * 信誉雷达（A6）—— 纯几何与取值工具，不依赖浏览器，供 ReputationRadar 组件与单测复用。
 *
 * 后端 ReputationVO 为维度明细形态：每维携带 key/label/score/samples/summary。
 * score 为 null 表示样本不足（低于最小样本门槛），与"表现差"从语义上区分：
 * 多边形该轴退回中心点、维度列表标注"样本不足"，综合分只平均已评分维度。
 * 综合分与评级是展示层派生值，不参与任何交易/风控判定。
 */

/** 六个维度的固定顺序与中文标签（与后端 ReputationVO.dimensions 顺序一致；键值联合由本数组派生） */
export const REPUTATION_DIMENSIONS = [
  { key: 'completionRate', label: '交易完成率' },
  { key: 'responseSpeed', label: '响应速度' },
  { key: 'accuracy', label: '描述准确度' },
  { key: 'praise', label: '历史好评' },
  { key: 'activity', label: '活跃度' },
  { key: 'compliance', label: '合规度' }
] as const

export type ReputationKey = (typeof REPUTATION_DIMENSIONS)[number]['key']

/** 维度明细的领域形状（wire 经 mapReputation 归一后） */
export interface ReputationDimension {
  key: string
  label: string
  /** 0-100；null = 样本不足 */
  score: number | null
  /** 参与该维计算的样本量 */
  samples: number
  /** 一句话归因（解释"为什么是这个分"） */
  summary: string
}

export interface ReputationVo {
  userId: number | null
  dimensions: ReputationDimension[]
}

/** 领域标签兜底：后端未带 label 或出现新维度时的本地映射 */
const LABEL_BY_KEY = new Map<string, string>(REPUTATION_DIMENSIONS.map((d) => [d.key, d.label]))

/**
 * wire（接口 data）→ 领域形状：score 缺失归一为 null（样本不足语义），
 * 未知维度键原样透传（前向兼容），缺失的已知维度补齐为"样本不足 0 样本"，
 * 保证雷达恒有六个轴。输入不是对象时返回空维度组。
 */
export function mapReputation(wire: unknown): ReputationVo {
  const source = wire as { userId?: unknown; dimensions?: unknown } | null
  const rows = Array.isArray(source?.dimensions) ? (source!.dimensions as Record<string, unknown>[]) : []
  const byKey = new Map<string, ReputationDimension>()
  for (const row of rows) {
    if (row === null || typeof row !== 'object' || typeof row.key !== 'string') continue
    const rawScore = row.score
    byKey.set(row.key, {
      key: row.key,
      label: typeof row.label === 'string' && row.label ? row.label : (LABEL_BY_KEY.get(row.key) ?? row.key),
      score: typeof rawScore === 'number' && Number.isFinite(rawScore) ? rawScore : null,
      samples: typeof row.samples === 'number' && Number.isFinite(row.samples) ? Math.max(0, Math.trunc(row.samples)) : 0,
      summary: typeof row.summary === 'string' ? row.summary : ''
    })
  }
  // 已知维度缺行时补空位，维持固定六轴几何
  for (const dimension of REPUTATION_DIMENSIONS) {
    if (!byKey.has(dimension.key)) {
      byKey.set(dimension.key, { key: dimension.key, label: dimension.label, score: null, samples: 0, summary: '' })
    }
  }
  const ordered = REPUTATION_DIMENSIONS.map((d) => byKey.get(d.key)!)
    .concat([...byKey.values()].filter((d) => !REPUTATION_DIMENSIONS.some((k) => k.key === d.key)))
  const userId = (source as { userId?: unknown } | null)?.userId
  return {
    userId: typeof userId === 'number' && Number.isFinite(userId) ? userId : null,
    dimensions: ordered
  }
}

/** 雷达图几何参数 */
export interface RadarGeometry {
  radius: number
  cx: number
  cy: number
}

const AXES = REPUTATION_DIMENSIONS.length

/** 第 i 根轴的角度：正上方起（-90°），顺时针均匀分布。 */
export function axisAngle(i: number): number {
  return -Math.PI / 2 + (i * 2 * Math.PI) / AXES
}

function clampScore(value: unknown): number {
  const n = Number(value)
  if (!Number.isFinite(n)) return 0
  return Math.max(0, Math.min(100, n))
}

const round2 = (n: number): number => Math.round(n * 100) / 100

/** 单个分值在第 i 根轴上的落点坐标 */
export function radarPoint(value: unknown, i: number, { radius, cx, cy }: RadarGeometry): { x: number; y: number } {
  const r = (clampScore(value) / 100) * radius
  const a = axisAngle(i)
  return { x: round2(cx + r * Math.cos(a)), y: round2(cy + r * Math.sin(a)) }
}

/** 一组分值 → SVG polygon 的 points 串（"x,y x,y ..."） */
export function radarPolygon(values: readonly unknown[], geom: RadarGeometry): string {
  return values
    .map((v, i) => {
      const p = radarPoint(v, i, geom)
      return `${p.x},${p.y}`
    })
    .join(' ')
}

/** 按固定轴顺序取维度明细；vo 为空时返回全"样本不足"空位 */
export function reputationDimensions(vo: ReputationVo | null | undefined): ReputationDimension[] {
  return mapReputation(vo ?? {}).dimensions.filter((d) => REPUTATION_DIMENSIONS.some((k) => k.key === d.key))
}

/** ReputationVO → 规范顺序的六维绘制值（样本不足按 0 画，多边形该轴退回中心） */
export function reputationValues(vo: ReputationVo | null | undefined): number[] {
  return reputationDimensions(vo).map((d) => (d.score === null ? 0 : clampScore(d.score)))
}

/** 与 reputationValues 同顺序的样本不足掩码（true = 该轴无分值） */
export function insufficientFlags(vo: ReputationVo | null | undefined): boolean[] {
  return reputationDimensions(vo).map((d) => d.score === null)
}

/**
 * 综合信誉分（0-100，四舍五入）：只平均已评分维度。
 * 全部维度样本不足时返回 null（前端展示"数据积累中"）。
 */
export function overallScore(vo: ReputationVo | null | undefined): number | null {
  const scored = reputationDimensions(vo).filter((d) => d.score !== null) as { score: number }[]
  if (scored.length === 0) return null
  return Math.round(scored.reduce((acc, d) => acc + clampScore(d.score), 0) / scored.length)
}

/** 综合分 → 评级文案（null = 尚无任何可评分维度） */
export function reputationGrade(score: number | null): '信誉极佳' | '信誉良好' | '信誉一般' | '信誉待提升' | '数据积累中' {
  if (score === null) return '数据积累中'
  if (score >= 90) return '信誉极佳'
  if (score >= 75) return '信誉良好'
  if (score >= 50) return '信誉一般'
  return '信誉待提升'
}
