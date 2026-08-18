import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { getRole, isLoggedIn } from '@/utils/auth'
import { ROUTE_NAME, ROUTE_PATH } from '@/constants/routes'

/**
 * 路由表 —— 统一在此注册所有页面
 * 每个模块的页面放到 views/<模块名>/ 下面
 */
const routes: RouteRecordRaw[] = [
  // ── 公共页面 ──
  {
    path: ROUTE_PATH.HOME,
    name: ROUTE_NAME.HOME,
    component: () => import('@/views/home/HomePage.vue'),
    meta: { title: '商品大厅' }
  },
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
    component: () => import('@/views/admin/ChatPage.vue'),
    meta: { title: '客服收件箱', requireAuth: true, requireAdmin: true }
  },
  {
    path: ROUTE_PATH.ADMIN_MANAGE,
    name: ROUTE_NAME.ADMIN_MANAGE,
    component: () => import('@/views/admin/ManagePage.vue'),
    meta: { title: '内容管理', requireAuth: true, requireAdmin: true }
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
  scrollBehavior: () => ({ top: 0 })
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

  if (isAdminPath && to.name !== ROUTE_NAME.ADMIN_LOGIN) {
    return authenticated ? { name: ROUTE_NAME.HOME } : { name: ROUTE_NAME.ADMIN_LOGIN, query: { redirect: to.fullPath } }
  }
  if (authenticated && to.name === ROUTE_NAME.ADMIN_LOGIN) {
    return { name: ROUTE_NAME.HOME }
  }
  return true
})

export default router
