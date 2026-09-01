import { nextTick } from 'vue'
import { createRouter, createWebHistory, START_LOCATION } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getRole, isLoggedIn } from '@/utils/auth'
import { ROUTE_NAME, ROUTE_PATH } from '@/constants/routes'
import { announceRoute } from '@/utils/routeAnnouncer'

/**
 * 路由表 —— 统一在此注册所有页面
 * 每个模块的页面放到 views/<模块名>/ 下面
 */
const routes: RouteRecordRaw[] = [
  {
    path: ROUTE_PATH.HOME,
    name: ROUTE_NAME.HOME,
    component: () => import('@/views/home/HomePage.vue'),
    meta: { title: '商品大厅', requireAuth: true }
  },
  // ── 公共页面 ──
  {
    path: ROUTE_PATH.RANKING,
    name: ROUTE_NAME.RANKING,
    component: () => import('@/views/ranking/RankingPage.vue'),
    meta: { title: '近期爆款榜 - 智易校园', requireAuth: true }
  },
  {
    path: ROUTE_PATH.LOGIN,
    name: ROUTE_NAME.LOGIN,
    component: () => import('@/views/login/LoginPage.vue'),
    meta: { title: '登录 - 智易校园' }
  },
  {
    path: ROUTE_PATH.REGISTER,
    name: ROUTE_NAME.REGISTER,
    component: () => import('@/views/login/RegisterPage.vue'),
    meta: { title: '注册 - 智易校园' }
  },

  // ── 商品相关（模块二 / 三）──
  {
    path: '/item/:id',
    name: ROUTE_NAME.ITEM_DETAIL,
    component: () => import('@/views/item/ItemDetailPage.vue'),
    // 免登录可读需后端配合：详情/榜单接口强依赖登录态（@RequestAttribute userId +
    // SchoolScopeGuard 跨校隔离），仅放开前端路由会让分享链接得到 401 + 登录跳转，
    // 体验更差。待后端定义匿名访客的跨校策略后再两端同步放开。
    meta: { title: '商品详情', requireAuth: true }
  },
  {
    path: '/item/:id/edit',
    name: ROUTE_NAME.EDIT_ITEM,
    component: () => import('@/views/item/PublishItemPage.vue'),
    meta: { title: '编辑商品', requireAuth: true }
  },
  {
    path: ROUTE_PATH.PUBLISH,
    name: ROUTE_NAME.PUBLISH_ITEM,
    component: () => import('@/views/item/PublishItemPage.vue'),
    meta: { title: '发布商品', requireAuth: true }
  },

  // ── 用户中心（模块一）──
  {
    path: ROUTE_PATH.USER_PROFILE,
    name: ROUTE_NAME.USER_PROFILE,
    component: () => import('@/views/user/UserProfilePage.vue'),
    meta: { title: '个人中心', requireAuth: true }
  },
  {
    path: ROUTE_PATH.MY_ITEMS,
    name: ROUTE_NAME.MY_ITEMS,
    component: () => import('@/views/user/MyItemsPage.vue'),
    meta: { title: '我的发布', requireAuth: true }
  },
  {
    path: ROUTE_PATH.MY_FAVORITES,
    name: ROUTE_NAME.MY_FAVORITES,
    component: () => import('@/views/user/MyFavoritesPage.vue'),
    meta: { title: '我的收藏', requireAuth: true }
  },

  // ── 聊天（模块三）──
  {
    path: ROUTE_PATH.CHAT,
    name: ROUTE_NAME.CHAT_LIST,
    component: () => import('@/views/chat/ChatListPage.vue'),
    meta: { title: '消息', requireAuth: true }
  },
  {
    path: '/chat/:conversationId',
    redirect: (to) => ({
      path: ROUTE_PATH.CHAT,
      query: {
        conversationId: String(to.params.conversationId),
        peerId: typeof to.query.peerId === 'string' ? to.query.peerId : undefined,
        relatedItemId: typeof to.query.relatedItemId === 'string' ? to.query.relatedItemId : undefined
      }
    })
  },

  // ── 钱包 & 订单（模块四）──
  {
    path: ROUTE_PATH.WALLET,
    name: ROUTE_NAME.WALLET,
    component: () => import('@/views/wallet/WalletPage.vue'),
    meta: { title: '我的钱包', requireAuth: true }
  },
  {
    path: ROUTE_PATH.ORDERS_BOUGHT,
    name: ROUTE_NAME.ORDERS_BOUGHT,
    component: () => import('@/views/wallet/OrdersBoughtPage.vue'),
    meta: { title: '我买的', requireAuth: true }
  },
  {
    path: ROUTE_PATH.ORDERS_SOLD,
    name: ROUTE_NAME.ORDERS_SOLD,
    component: () => import('@/views/wallet/OrdersSoldPage.vue'),
    meta: { title: '我卖的', requireAuth: true }
  },

  // ── 管理后台（模块四）──
  {
    path: ROUTE_PATH.ADMIN_LOGIN,
    name: ROUTE_NAME.ADMIN_LOGIN,
    component: () => import('@/views/admin/AdminLoginPage.vue'),
    meta: { title: '管理员登录 - 智易校园' }
  },
  {
    path: ROUTE_PATH.ADMIN_DASHBOARD,
    name: ROUTE_NAME.ADMIN_DASHBOARD,
    component: () => import('@/views/admin/DashboardPage.vue'),
    meta: { title: '管理后台', requireAuth: true, requireAdmin: true }
  },
  {
    path: ROUTE_PATH.ADMIN_VIOLATIONS,
    name: ROUTE_NAME.ADMIN_VIOLATIONS,
    component: () => import('@/views/admin/ViolationsPage.vue'),
    meta: { title: '内容治理', requireAuth: true, requireAdmin: true }
  },
  {
    path: ROUTE_PATH.ADMIN_CHAT,
    name: ROUTE_NAME.ADMIN_CHAT,
    component: () => import('@/views/admin/AdminChatInboxPage.vue'),
    meta: { title: '客服收件箱', requireAuth: true, requireAdmin: true }
  },
  {
    path: ROUTE_PATH.ADMIN_USERS,
    name: ROUTE_NAME.ADMIN_USERS,
    component: () => import('@/views/admin/UsersPage.vue'),
    meta: { title: '用户管理', requireAuth: true, requireAdmin: true }
  },
  {
    path: ROUTE_PATH.ADMIN_TOPICS,
    name: ROUTE_NAME.ADMIN_TOPICS,
    component: () => import('@/views/admin/topics/TopicsPage.vue'),
    meta: { title: '事件专题', requireAuth: true, requireAdmin: true }
  },
  {
    path: ROUTE_PATH.ADMIN_SCHOOLS,
    name: ROUTE_NAME.ADMIN_SCHOOLS,
    component: () => import('@/views/admin/SchoolsPage.vue'),
    meta: { title: '学校管理', requireAuth: true, requireAdmin: true }
  },
  {
    path: ROUTE_PATH.ADMIN_CATEGORIES,
    name: ROUTE_NAME.ADMIN_CATEGORIES,
    component: () => import('@/views/admin/CategoriesPage.vue'),
    meta: { title: '分类管理', requireAuth: true, requireAdmin: true }
  },

  // ── 兜底：404 ──
  {
    path: '/:pathMatch(.*)*',
    name: ROUTE_NAME.NOT_FOUND,
    component: () => import('@/views/NotFoundPage.vue'),
    meta: { title: '404' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 返回/前进恢复原滚动位置（从详情返回大厅不再被拉回顶部）；
  // 仅 query 变化的同路径导航（如聊天切会话）不滚动，交给页面自管
  scrollBehavior(to, from, savedPosition) {
    if (!savedPosition && to.path === from.path) return false
    return savedPosition ?? { top: 0 }
  }
})

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requireAuth?: boolean
    requireAdmin?: boolean
  }
}

// ── 全局路由守卫：管理员与普通用户使用完全独立的页面空间 ──
router.beforeEach((to) => {
  document.title = to.meta.title || '智易校园'

  // isLoggedIn 来自 auth.ts 的响应式登录态（httpOnly Cookie 凭证，前端不持有 token）
  const authenticated = isLoggedIn()
  const role = getRole()
  const isAdminPath = to.path === '/admin' || to.path.startsWith('/admin/')

  if (!authenticated && to.meta.requireAuth) {
    const loginRoute = to.meta.requireAdmin ? ROUTE_NAME.ADMIN_LOGIN : ROUTE_NAME.LOGIN
    return { name: loginRoute, query: { redirect: to.fullPath } }
  }

  if (authenticated && role === 'ADMIN') {
    if (!isAdminPath || to.name === ROUTE_NAME.ADMIN_LOGIN) {
      return { name: ROUTE_NAME.ADMIN_DASHBOARD }
    }
    return true
  }

  // 已登录普通用户不停留在登录/注册页（进入这两页只应发生在未登录时）：回交易大厅
  if (authenticated && (to.name === ROUTE_NAME.LOGIN || to.name === ROUTE_NAME.REGISTER)) {
    return { name: ROUTE_NAME.HOME }
  }

  if (isAdminPath && to.name !== ROUTE_NAME.ADMIN_LOGIN) {
    return authenticated ? { name: ROUTE_NAME.HOME } : { name: ROUTE_NAME.ADMIN_LOGIN, query: { redirect: to.fullPath } }
  }
  if (authenticated && to.name === ROUTE_NAME.ADMIN_LOGIN) {
    return { name: ROUTE_NAME.HOME }
  }
  return true
})

// ── 懒加载分块失败兜底（分级策略）──
// 路由组件按需加载，网络抖动、dev 依赖重优化或发版后旧 chunk 失效时导航会静默中止，
// 表现为"点击后停在原页面，再点一次才跳转"。恢复策略：
// 1. 应用内重放一次导航：vue-router 在组件加载失败时不会替换路由记录上的加载函数，
//    直接重放即可重新发起请求。注意浏览器会把失败的动态 import 缓存在模块表里
//    （连接失败类错误重放不会重新发请求），此级只对超时/中断类未缓存失败有效；
// 2. 重放仍失败 → 整页直达目标路由：新文档的模块表是空的，浏览器必然重新请求
//    （发版后旧 chunk 失效也靠这级恢复，拿到新 index.html 的最新分块引用）；
// 3. 循环防护：首屏（直接打开/刷新目标 URL）时 chunk 持续失效会让整页直达无限
//    刷新自身（实测 12s 内 47 次），用 sessionStorage 预算限制每次会话至多一次
//    自动整页直达，仍失败则提示用户手动刷新。
const CHUNK_ERROR_RE =
  /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|Loading chunk|Loading CSS chunk|Unable to preload CSS|Load failed|NetworkError|ERR_/i
/** 同一目标的导航重放预算（内存级，成功导航后清零） */
const CHUNK_REPLAY_BUDGET = 1
/** 整页直达预算的存储键（sessionStorage 级，跨刷新但随会话结束清空） */
const CHUNK_RELOAD_KEY = 'zhiyi:chunk-reloaded'

function isChunkLoadError(error: unknown): boolean {
  return CHUNK_ERROR_RE.test(String((error as Error)?.message || error))
}

const chunkReplayCounts = new Map<string, number>()

router.onError((error, to) => {
  if (!to?.fullPath || !isChunkLoadError(error)) return

  const replays = chunkReplayCounts.get(to.fullPath) ?? 0
  if (replays < CHUNK_REPLAY_BUDGET) {
    chunkReplayCounts.set(to.fullPath, replays + 1)
    router.replace(to).catch(() => {})
    return
  }

  const currentDocPath = window.location.pathname + window.location.search
  const isInitialLoad = currentDocPath === to.fullPath
  // 预算按目标路径记录：同一首屏地址每次会话至多自动整页直达一次，不同地址互不影响
  if (isInitialLoad && sessionStorage.getItem(CHUNK_RELOAD_KEY) === to.fullPath) {
    ElMessage.error('页面资源加载失败，请刷新页面重试')
    return
  }
  sessionStorage.setItem(CHUNK_RELOAD_KEY, to.fullPath)
  window.location.replace(to.fullPath)
})

/** 导航成功后复位该目标的重放预算，后续再次进入仍可享受完整兜底 */
router.afterEach((to, from) => {
  chunkReplayCounts.delete(to.fullPath)
  // 成功到达即清除整页直达预算（正常导航到此页不再受限）
  if (sessionStorage.getItem(CHUNK_RELOAD_KEY) === to.fullPath) sessionStorage.removeItem(CHUNK_RELOAD_KEY)

  // 路由切换反馈三件套（仅真实换页；同路径 query 变化不重复播报/移焦点）：
  // 1) live region 播报新页面标题；2) 焦点移到 <main>（tabindex=-1），
  //    读屏与键盘用户不必再从全局导航 Tab 穿一遍。
  // 焦点必须包在 nextTick 里：afterEach 触发时 router-view 的响应式 DOM 更新
  // 尚未 flush，同步 focus 会落在即将卸载的旧页 <main> 上，下一 tick 元素被移除、
  // 焦点丢失回 body。首屏直达（from === START_LOCATION）不打断初始焦点——
  // 直接比较起始路由对象，避免"首次点击导航不移动焦点"的累加状态缺陷
  // （首屏打开 / 时初始导航 to.path === from.path，若用布尔标记会漏掉下一次导航）
  if (to.path !== from.path) {
    if (to.meta.title) announceRoute(`${to.meta.title}，已加载`)
    if (from !== START_LOCATION) {
      nextTick(() => document.querySelector<HTMLElement>('main')?.focus())
    }
  }
})

export default router
