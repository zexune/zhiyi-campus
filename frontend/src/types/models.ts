/**
 * 跨模块共享的领域 / 页面 / 表单模型。
 *
 * 分层约定（P1-5）：
 * - `api.gen.d.ts` 是机器产物，由 `npm run gen:api` 从根目录 openapi.json 快照生成，
 *   禁止手改；后端 DTO/VO 变更后重新导出 spec 并重跑生成即可；
 * - `contracts.ts` 用生成的 `paths` 约束 API 函数的响应类型（operation 级契约），
 *   API 模块不得手写 `request.get<T>` 冒充线上契约；
 * - 本文件只承载领域、页面与表单模型：生成 schema 的裸别名是"领域模型"，
 *   登录摘要、发布载荷等"客户端组装视图"在手写区——它们不是某个 VO 的镜像；
 * - wire → domain 的可选性/分页归一化一律走 api/mappers.ts 的显式 mapper；
 * - 后端 VO 已用 @Schema(requiredMode/nullable) 完整声明 nullability，
 *   可空 $ref 在快照规范化阶段改写为 anyOf，生成类型原生携带 `| null`
 *   （会话族 relatedItem 无需任何 Omit+覆写补丁）。
 */

import type { components } from './api.gen'
import type { ItemStatus, ItemType, ModerationStatus } from '@/constants/domain'

type Api = components['schemas']

/** 服务端分页响应的通用形状（由 mappers.mapPageData 从 PageResponseXxx 归一化） */
export interface PageResult<T> {
  records: T[]
  total: number
}

/** 分页查询参数 */
export interface PageQuery {
  page: number
  size: number
}

// ==================== 别名区：直接映射后端 schema ====================

export type Category = Api['CategoryResponse']
export type School = Api['SchoolVO']
/** 经验流水 */
export type ExpLog = Api['ExpLogResponse']
/**
 * 商品卡片（兼容适配层，P2）：收藏列表与发布/编辑/重新上架写接口仍返回
 * ItemCardVO 全量卡；feed/榜单走 ItemSummary，详情/我的发布走 ItemDetail。
 */
export type Item = Api['ItemCardVO']
/** 商品摘要（feed 游标 / 榜单 / swap / errand 族） */
export type ItemSummary = Api['ItemSummaryResponse']
/** 商品详情（/item/{id}、我的发布族；申诉字段仅发布者视角填充） */
export type ItemDetail = Api['ItemDetailResponse']
/** 商品传承链节点；nickname/price 对系统主体与发布者为 null */
export type LineageNode = Api['LineageNode']
export type ItemLineage = Api['ItemLineageVO']
/** 首页大事件专题（后台配置，前台只读） */
export type EventTopic = Api['EventTopicResponse']
/**
 * 标签计数（趋势榜 /item/ranking/tags 使用，字段名是 tag）。
 * 注意与分组接口 /item/tags 的子项结构 TagCountVO（字段名是 name）区分——
 * 后端存在两个形近实异的"标签+数量"形状，此处显式对齐各自 schema。
 */
export type TagCount = Api['TagTrendVO']
/** 标签云分组（/item/tags 按商品大类返回的分组结构） */
export type TagCloudGroup = Api['TagGroupVO']

/**
 * 订单列表行（我买的/我卖的）。reviewed 只在「我买的」有意义（评价入口显隐）；
 * 单订单操作（下单/确认/取消）返回 OrderDetail，不携带 reviewed。
 */
export type Order = Api['OrderVO']
/** 单订单操作响应（下单/确认收货/取消） */
export type OrderDetail = Api['OrderDetailResponse']
/**
 * 钱包流水。type 在生成类型中是精确字面量联合，但前端展示层必须容忍
 * 后端未来新增的枚举值（未知值原样透传给格式化兜底），故叠加开放字符串分支。
 */
export type WalletLog = Omit<Api['WalletLogResponse'], 'type'> & {
  type: Api['WalletLogResponse']['type'] | (string & {})
}
export type WalletBalance = Api['WalletBalanceVO']
/** 商品收藏切换结果 */
export type FavoriteToggleResult = Api['FavoriteToggleVO']

export type ChatUser = Api['ChatUserVO']
export type ChatMessage = Api['ChatMessageVO']
/**
 * 会话族。relatedItem 为可空 $ref，快照规范化（anyOf + type:null）后
 * 生成类型原生携带 `| null`，运行时真相与契约一致（无关联商品时为 null）。
 */
export type ChatItemSummary = Api['ChatItemSummaryVO']
export type Conversation = Api['ConversationVO']
export type ChatStartResult = Api['ChatStartVO']
export type ChatThread = Api['ChatThreadVO']

/** 管理端用户列表行 & 个人中心完整资料共用 UserVO */
export type AdminUser = Api['UserVO']
export type UserProfile = Api['UserVO']
/** 处罚评分统计（D4） */
export type PenaltyStats = Api['PenaltyStatsVO']
/** 管理后台数据大盘 */
export type DashboardStats = Api['AdminDashboardVO']
export type TrendPoint = Api['TradeTrendPoint']
export type RecentViolationRow = Api['RecentViolation']
export type TradeHeatEntry = Api['TradeHeatmapVO']
/** 申诉复核行 */
export type ViolationAppeal = Api['AppealVO']
/** 处罚记录行（P0-4 命名 DTO）：banDays 永久封禁为 null；用户已删除时学号/昵称为 null */
export type ViolationLogRow = Api['ViolationLogRowResponse']

// ==================== 手写区：客户端组装视图（非 VO 镜像） ====================

/**
 * 登录/注册响应中的用户摘要。
 * 不直接别名 LoginVO：登录态恢复与存储处需要一个"字段必填"的窄视图。
 */
export interface UserSummary {
  id: number
  studentId?: string
  nickname: string
  role: string
}

/** 登录/注册响应 */
export interface LoginResult {
  token: string
  user: UserSummary
}

/** 管理员登录响应（user.username 即学号） */
export interface AdminLoginResult {
  token: string
  user: UserSummary
}

/** 商品发布/编辑载荷（SWAP 价格为 null，ERRAND 悬赏 1-20；表单侧语义比 DTO 宽） */
export interface PublishItemPayload {
  type: ItemType | string
  title: string
  description: string
  categoryId: number
  price: number | null
  images: string[]
  tradeLocation?: string
  pickupLocation?: string
  deliveryLocation?: string
  tags?: string[]
}

/** 发布/整改后的审核结果提示（直接上架时携带新商品 id 供跳转详情） */
export interface ModerationResult {
  moderationStatus: ModerationStatus | string
  id?: number
}

/** 管理端违规治理工作台行（服务端 ViolationVO + 客户端展示派生字段） */
export interface ViolationReview {
  id: number
  source: string
  status: ViolationStatusLike | string
  ruleVersion?: string
  violationType?: string
  originalTitle: string
  originalDescription?: string
  violationReason?: string
  handleNote?: string
  matchedRules?: string[]
  sellerName?: string
  userId?: number
  reporterId?: number | null
  reporterName?: string | null
  handlerName?: string
  itemStatus?: ItemStatus | string
  createdAt: string
}

export type ViolationStatusLike = 'PENDING' | 'CONFIRMED' | 'DISMISSED' | 'OVERTURNED'
