/**
 * 路由契约 —— 路径与命名路由的唯一出处。
 *
 * 约定：
 * - 编程式导航用 `router.push({ name: ROUTE_NAME.X, params, query })`；
 * - 模板跳转用 `:to="ROUTE_PATH.X"`（静态）或 `:to="{ name: ROUTE_NAME.X, params }"`（动态）；
 * - 修改路由表时同步维护本文件，页面中不得再硬编码路径字符串。
 */

export const ROUTE_NAME = Object.freeze({
  HOME: 'Home',
  RANKING: 'Ranking',
  LOGIN: 'Login',
  REGISTER: 'Register',
  ITEM_DETAIL: 'ItemDetail',
  EDIT_ITEM: 'EditItem',
  PUBLISH_ITEM: 'PublishItem',
  USER_PROFILE: 'UserProfile',
  MY_ITEMS: 'MyItems',
  MY_FAVORITES: 'MyFavorites',
  CHAT_LIST: 'ChatList',
  WALLET: 'Wallet',
  ORDERS_BOUGHT: 'OrdersBought',
  ORDERS_SOLD: 'OrdersSold',
  ADMIN_LOGIN: 'AdminLogin',
  ADMIN_DASHBOARD: 'AdminDashboard',
  ADMIN_VIOLATIONS: 'AdminViolations',
  ADMIN_CHAT: 'AdminChat',
  ADMIN_MANAGE: 'AdminManage',
  ADMIN_SCHOOLS: 'AdminSchools',
  ADMIN_CATEGORIES: 'AdminCategories',
  NOT_FOUND: 'NotFound'
})

export const ROUTE_PATH = Object.freeze({
  HOME: '/',
  RANKING: '/ranking',
  LOGIN: '/login',
  REGISTER: '/register',
  USER_PROFILE: '/user/profile',
  MY_ITEMS: '/user/my-items',
  MY_FAVORITES: '/user/my-favorites',
  CHAT: '/chat',
  WALLET: '/wallet',
  ORDERS_BOUGHT: '/orders/bought',
  ORDERS_SOLD: '/orders/sold',
  ADMIN_LOGIN: '/admin/login',
  ADMIN_DASHBOARD: '/admin/dashboard',
  ADMIN_VIOLATIONS: '/admin/violations',
  ADMIN_CHAT: '/admin/chat',
  ADMIN_MANAGE: '/admin/manage',
  ADMIN_SCHOOLS: '/admin/schools',
  ADMIN_CATEGORIES: '/admin/categories',

  /** 商品详情：/item/:id */
  item: (id) => `/item/${id}`,
  /** 编辑商品：/item/:id/edit */
  editItem: (id) => `/item/${id}/edit`,
  /** 发布商品 */
  PUBLISH: '/publish'
})
