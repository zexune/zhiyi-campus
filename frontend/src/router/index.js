import { createRouter, createWebHistory } from 'vue-router'
import { getRole, isLoggedIn } from '@/utils/auth'
import { ROUTE_NAME, ROUTE_PATH } from '@/constants/routes'

/**
 * 路由表 —— 统一在此注册所有页面
 * 每个模块的页面放到 views/<模块名>/ 下面
 */
const routes = [
  // ── 公共页面 ──
  {
    path: ROUTE_PATH.HOME,
    name: 'Home',
    component: () => import('@/views/home/HomePage.vue'),
    meta: { title: '商品大厅' }
  },
  {
    path: ROUTE_PATH.RANKING,
    name: 'Ranking',
    component: () => import('@/views/ranking/RankingPage.vue'),
    meta: { title: '近期爆款榜 - 智易校园', requireAuth: true }
  },
  {
    path: ROUTE_PATH.LOGIN,
    name: 'Login',
    component: () => import('@/views/login/LoginPage.vue'),
    meta: { title: '登录 - 智易校园' }
  },
  {
    path: ROUTE_PATH.REGISTER,
    name: 'Register',
    component: () => import('@/views/login/RegisterPage.vue'),
    meta: { title: '注册 - 智易校园' }
  },

  // ── 商品相关（模块二 / 三）──
  {
    path: '/item/:id',
    name: 'ItemDetail',
    component: () => import('@/views/item/ItemDetailPage.vue'),
    meta: { title: '商品详情', requireAuth: true }
  },
  {
    path: '/item/:id/edit',
    name: 'EditItem',
    component: () => import('@/views/item/PublishItemPage.vue'),
    meta: { title: '编辑商品', requireAuth: true }
  },
  {
    path: ROUTE_PATH.PUBLISH,
    name: 'PublishItem',
    component: () => import('@/views/item/PublishItemPage.vue'),
    meta: { title: '发布商品', requireAuth: true }
  },

  // ── 用户中心（模块一）──
  {
    path: ROUTE_PATH.USER_PROFILE,
    name: 'UserProfile',
    component: () => import('@/views/user/UserProfilePage.vue'),
    meta: { title: '个人中心', requireAuth: true }
  },
  {
    path: ROUTE_PATH.MY_ITEMS,
    name: 'MyItems',
    component: () => import('@/views/user/MyItemsPage.vue'),
    meta: { title: '我的发布', requireAuth: true }
  },
  {
    path: ROUTE_PATH.MY_FAVORITES,
    name: 'MyFavorites',
    component: () => import('@/views/user/MyFavoritesPage.vue'),
    meta: { title: '我的收藏', requireAuth: true }
  },

  // ── 聊天（模块三）──
  {
    path: ROUTE_PATH.CHAT,
    name: 'ChatList',
    component: () => import('@/views/chat/ChatListPage.vue'),
    meta: { title: '消息', requireAuth: true }
  },
  {
    path: '/chat/:conversationId',
    redirect: (to) => ({
      path: '/chat',
      query: {
        conversationId: to.params.conversationId,
        peerId: to.query.peerId,
        relatedItemId: to.query.relatedItemId
      }
    })
  },

  // ── 钱包 & 订单（模块四）──
  {
    path: ROUTE_PATH.WALLET,
    name: 'Wallet',
    component: () => import('@/views/wallet/WalletPage.vue'),
    meta: { title: '我的钱包', requireAuth: true }
  },
  {
    path: ROUTE_PATH.ORDERS_BOUGHT,
    name: 'OrdersBought',
    component: () => import('@/views/wallet/OrdersBoughtPage.vue'),
    meta: { title: '我买的', requireAuth: true }
  },
  {
    path: ROUTE_PATH.ORDERS_SOLD,
    name: 'OrdersSold',
    component: () => import('@/views/wallet/OrdersSoldPage.vue'),
    meta: { title: '我卖的', requireAuth: true }
  },

  // ── 管理后台（模块四）──
  {
    path: ROUTE_PATH.ADMIN_LOGIN,
    name: 'AdminLogin',
    component: () => import('@/views/admin/AdminLoginPage.vue'),
    meta: { title: '管理员登录 - 智易校园' }
  },
  {
    path: ROUTE_PATH.ADMIN_DASHBOARD,
    name: 'AdminDashboard',
    component: () => import('@/views/admin/DashboardPage.vue'),
    meta: { title: '管理后台', requireAuth: true, requireAdmin: true }
  },
  {
    path: ROUTE_PATH.ADMIN_VIOLATIONS,
    name: 'AdminViolations',
    component: () => import('@/views/admin/ViolationsPage.vue'),
    meta: { title: '内容治理', requireAuth: true, requireAdmin: true }
  },
  {
    path: ROUTE_PATH.ADMIN_CHAT,
    name: 'AdminChat',
    component: () => import('@/views/admin/ChatPage.vue'),
    meta: { title: '客服收件箱', requireAuth: true, requireAdmin: true }
  },
  {
    path: ROUTE_PATH.ADMIN_MANAGE,
    name: 'AdminManage',
    component: () => import('@/views/admin/ManagePage.vue'),
    meta: { title: '内容管理', requireAuth: true, requireAdmin: true }
  },
  {
    path: ROUTE_PATH.ADMIN_SCHOOLS,
    name: 'AdminSchools',
    component: () => import('@/views/admin/SchoolsPage.vue'),
    meta: { title: '学校管理', requireAuth: true, requireAdmin: true }
  },
  {
    path: ROUTE_PATH.ADMIN_CATEGORIES,
    name: 'AdminCategories',
    component: () => import('@/views/admin/CategoriesPage.vue'),
    meta: { title: '分类管理', requireAuth: true, requireAdmin: true }
  },

  // ── 兜底：404 ──
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundPage.vue'),
    meta: { title: '404' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

// ── 全局路由守卫：管理员与普通用户使用完全独立的页面空间 ──
router.beforeEach((to) => {
  document.title = to.meta.title || '智易校园'

  // isLoggedIn 来自 auth.js 的响应式登录态（httpOnly Cookie 凭证，前端不持有 token）
  const authenticated = isLoggedIn()
  const role = getRole()
  const isAdminPath = to.path === '/admin' || to.path.startsWith('/admin/')

  if (!authenticated && to.meta.requireAuth) {
    const loginRoute = to.meta.requireAdmin ? ROUTE_NAME.ADMIN_LOGIN : ROUTE_NAME.LOGIN
    return { name: loginRoute, query: { redirect: to.fullPath } }
  }

  if (authenticated && role === 'ADMIN') {
    if (!isAdminPath || to.name === 'AdminLogin') {
      return { name: 'AdminDashboard' }
    }
    return true
  }

  if (isAdminPath && to.name !== 'AdminLogin') {
    return authenticated ? { name: 'Home' } : { name: 'AdminLogin', query: { redirect: to.fullPath } }
  }
  if (authenticated && to.name === 'AdminLogin') {
    return { name: 'Home' }
  }
  return true
})

export default router
