/**
 * 跨模块共享的 API 数据模型 —— 手写契约（迁移决策）。
 *
 * 字段以各调用方的实际使用与后端契约为准；调用方以 `?.` 防御的字段在此标记可选。
 * 后端契约变更由 WebMvcTest 契约测试与系统 E2E 兜底，此处类型随之同步。
 */

import type { AppealStatus, ItemStatus, ItemType, ModerationStatus, OrderStatus, UserStatus, WalletLogType } from '@/constants/domain'

/** 服务端分页响应的通用形状 */
export interface PageResult<T> {
  records: T[]
  total: number
}

/** 分页查询参数 */
export interface PageQuery {
  page: number
  size: number
}

// ==================== 用户与学校 ====================

/** 登录/注册响应中的用户摘要 */
export interface UserSummary {
  id: number
  studentId?: string
  nickname: string
  role: string
}

/** 个人中心完整资料（getProfile；localStorage 恢复时仅 id/nickname/role） */
export interface UserProfile extends UserSummary {
  level: number
  levelTitle?: string
  exp?: number
  nextLevelExp?: number
  walletBalance?: number
  createdAt?: string
  phone?: string
  schoolId?: number
  schoolName?: string
  schoolEmail?: string | null
  campus?: string
  college?: string
  grade?: string
  dormitory?: string
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

export interface School {
  id: number
  name: string
  code?: string
  emailDomain?: string | null
  status?: UserStatus | string
}

export interface Category {
  id: number
  name: string
  icon?: string
  sortOrder?: number
}

/** 经验流水 */
export interface ExpLog {
  id: number
  delta: number
  reason: string
  createdAt: string
}

/** 管理端用户搜索结果（封禁/重置密码选人） */
export interface AdminUser {
  id: number
  studentId: string
  nickname: string
  role: string
  status: UserStatus | string
  banUntilTime?: string | null
}

// ==================== 商品 ====================

/** 商品卡片（大厅/榜单/我的发布/收藏共用基础形状） */
export interface Item {
  id: number
  title: string
  type: ItemType | string
  price: number | string
  images?: string[]
  coverImage?: string | null
  categoryId?: number
  status: ItemStatus | string
  moderationStatus?: ModerationStatus | string
  viewCount?: number
  favoriteCount?: number
  /** 当前登录用户视角的收藏态与宿舍关系（大厅/榜单卡片按用户切换展示） */
  favoriteByCurrentUser?: boolean
  dormitoryRelation?: string | null
  tags?: string[]
  createdAt?: string
  reserved?: boolean
  appealStatus?: AppealStatus | string
  publisherId?: number
  publisherNickname?: string
  publisherLevel?: number
  /** 发布者是否已通过学校邮箱认证（列表卡片认证徽标） */
  publisherVerified?: boolean
}

/** 商品详情 */
export interface ItemDetail extends Item {
  description: string
  categoryName?: string
  tradeLocation?: string
  pickupLocation?: string
  deliveryLocation?: string
  favoriteByCurrentUser?: boolean
}

/** 商品发布/编辑载荷（SWAP 价格为 null，ERRAND 悬赏 1-20） */
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

/** 商品传承链节点 */
export interface LineageNode {
  userId: number
  nickname: string | null
  role: 'PUBLISHER' | string
  time: string
  price?: number | null
}

export interface ItemLineage {
  itemTitle?: string
  chain: LineageNode[]
}

/** 首页大事件专题（后台配置，前台只读） */
export interface EventTopic {
  id?: number
  title: string
  startTime: string
  endTime: string
  filterType?: ItemType | string | null
  filterCategoryId?: number | null
  filterTag?: string | null
  bannerText?: string
  enabled: boolean
}

/** 标签云条目（扁平形态，趋势标签接口使用） */
export interface TagCount {
  tag: string
  count: number
}

/** 标签云分组（/item/tags 按商品大类返回的分组结构） */
export interface TagCloudGroup {
  categoryId: number | string
  categoryName: string
  tags: { name: string; count: number }[]
}

// ==================== 订单与钱包 ====================

export interface Order {
  id: number
  itemId: number
  itemTitle: string
  itemCover?: string | null
  price: number | string
  buyerId?: number
  sellerId?: number
  peerNickname?: string | null
  status: OrderStatus | string
  reviewed?: boolean | null
  createdAt: string
  completedAt?: string | null
  cancelledAt?: string | null
}

export interface WalletLog {
  id: number
  type: WalletLogType | string
  amount: number
  balanceAfter: number
  remark?: string | null
  createdAt: string
}

export interface WalletBalance {
  balance: number
}

/** 商品收藏切换结果 */
export interface FavoriteToggleResult {
  favorite: boolean
  favoriteCount: number
}

// ==================== 聊天 ====================

export interface ChatUser {
  id: number
  nickname: string
  level?: number
  levelTitle?: string
}

export interface ChatItemSummary {
  id: number
  title: string
  price: number | string
  coverImage?: string
  status: string
}

export interface ChatMessage {
  id: number
  conversationId: string
  senderId: number
  receiverId: number
  content: string
  relatedItemId?: number | null
  isRead?: boolean
  mine: boolean
  createdAt: string
}

export interface Conversation {
  conversationId: string
  peer: ChatUser
  relatedItem?: ChatItemSummary | null
  lastMessage: string
  lastMessageTime: string
  unreadCount: number
}

export interface ChatStartResult {
  conversationId: string
  peer: ChatUser
  relatedItem?: ChatItemSummary | null
}

export interface ChatThread {
  conversationId: string
  peer?: ChatUser
  relatedItem?: ChatItemSummary | null
  messages: ChatMessage[]
  hasMore: boolean
}

// ==================== 管理端 ====================

export interface DashboardStats {
  totalUsers: number
  onSaleItems: number
  todayTradeAmount: string
  pendingViolations: number
  pendingAppeals?: number
  recentViolations: ViolationReview[]
  trend: TrendPoint[]
}

export interface TrendPoint {
  date: string
  count: number
  totalAmount?: number | string
}

export interface TradeHeatEntry {
  date?: string
  count: number
  totalAmount?: number | string
  location?: string
}

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

export interface ViolationAppeal {
  id: number
  status: AppealStatus | string
  itemTitle: string
  itemId?: number
  sellerName?: string
  userId?: number
  createdAt: string
  violationReason?: string
  reason: string
  handleNote?: string
  handlerName?: string
  handledAt?: string
}

/** 处罚评分统计（D4） */
export interface PenaltyStats {
  [key: string]: unknown
}
