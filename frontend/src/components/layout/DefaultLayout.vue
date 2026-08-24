<template>
  <div class="layout">
    <!-- 顶部导航栏（demo 设计：布告栏 topbar） -->
    <header class="topbar">
      <div class="topbar__inner">
        <router-link :to="ROUTE_PATH.HOME" class="logo" aria-label="智易校园首页">
          <span class="logo__mark">智</span>
          智易
          <em>校园</em>
        </router-link>

        <nav class="nav-links" aria-label="主导航">
          <router-link :to="ROUTE_PATH.HOME" :class="{ active: isActive('/') }" :aria-current="isActive('/') ? 'page' : undefined">
            <svg class="nav-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
              <path d="M3 11 12 4l9 7" />
              <path d="M5 10v10h14V10" />
              <path d="M9 20v-6h6v6" />
            </svg>
            交易大厅
          </router-link>
          <router-link :to="ROUTE_PATH.RANKING" :class="{ active: isActive(ROUTE_PATH.RANKING) }" :aria-current="isActive(ROUTE_PATH.RANKING) ? 'page' : undefined">
            <svg class="nav-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
              <path d="M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0Z" />
              <path d="M7 6H4v2a4 4 0 0 0 4 4M17 6h3v2a4 4 0 0 1-4 4" />
            </svg>
            爆款榜
          </router-link>
          <template v-if="loggedIn">
            <router-link :to="ROUTE_PATH.PUBLISH" :class="{ active: isActive(ROUTE_PATH.PUBLISH) }" :aria-current="isActive(ROUTE_PATH.PUBLISH) ? 'page' : undefined">
              <svg class="nav-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round"><path d="M12 5v14M5 12h14" /></svg>
              发布闲置
            </router-link>
            <router-link :to="ROUTE_PATH.CHAT" :class="{ active: isActive(ROUTE_PATH.CHAT) }" :aria-current="isActive(ROUTE_PATH.CHAT) ? 'page' : undefined">
              <svg class="nav-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4Z" />
              </svg>
              消息
              <span v-if="unreadCount > 0" class="dot" :aria-label="`${unreadCount}条未读`">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
            </router-link>
            <router-link :to="ROUTE_PATH.WALLET" :class="{ active: isActive(ROUTE_PATH.WALLET) }" :aria-current="isActive(ROUTE_PATH.WALLET) ? 'page' : undefined">
              <svg class="nav-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="6" width="18" height="13" rx="2" />
                <path d="M3 10h18M16 15h.01" />
              </svg>
              钱包·订单
            </router-link>
          </template>
        </nav>

        <div class="topbar__user">
          <template v-if="loggedIn">
            <router-link :to="ROUTE_PATH.PUBLISH" class="btn btn--primary btn--sm publish-btn">
              <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M12 5v14M5 12h14" /></svg>
              发闲置
            </router-link>
            <el-dropdown trigger="click" popper-class="app-dropdown">
              <span class="user-entry">
                <UserAvatar :nickname="nickname" :user-id="userId" size="s" />
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="go('/user/profile')">
                    <svg class="dd-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
                      <circle cx="12" cy="8" r="4" />
                      <path d="M4 21c0-4 3.6-6 8-6s8 2 8 6" />
                    </svg>
                    个人中心
                  </el-dropdown-item>
                  <el-dropdown-item @click="go('/user/my-items')">
                    <svg class="dd-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z" />
                      <path d="M3 6h18M16 10a4 4 0 0 1-8 0" />
                    </svg>
                    我的发布
                  </el-dropdown-item>
                  <el-dropdown-item @click="go('/user/my-favorites')">
                    <svg class="dd-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2C10.5 3.5 9.3 3 7.5 3A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z" />
                    </svg>
                    我的收藏
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleLogout">
                    <svg class="dd-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                      <path d="m16 17 5-5-5-5M21 12H9" />
                    </svg>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <router-link :to="ROUTE_PATH.LOGIN" class="btn btn--ghost btn--sm">登录</router-link>
            <router-link :to="ROUTE_PATH.REGISTER" class="btn btn--primary btn--sm">注册</router-link>
          </template>
          <button class="nav-toggle" type="button" :aria-expanded="mobileNavOpen" :aria-label="mobileNavOpen ? '关闭导航菜单' : '打开导航菜单'" @click="mobileNavOpen = !mobileNavOpen">
            <svg v-if="!mobileNavOpen" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="M4 7h16M4 12h16M4 17h16" /></svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="m6 6 12 12M18 6 6 18" /></svg>
          </button>
        </div>
      </div>

      <!-- 移动端导航抽屉 -->
      <nav v-show="mobileNavOpen" class="mobile-nav" aria-label="移动端导航">
        <router-link :to="ROUTE_PATH.HOME" :class="{ active: isActive('/') }" :aria-current="isActive('/') ? 'page' : undefined" @click="closeMobileNav">
          <svg class="nav-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 11 12 4l9 7" />
            <path d="M5 10v10h14V10" />
            <path d="M9 20v-6h6v6" />
          </svg>
          交易大厅
        </router-link>
        <router-link :to="ROUTE_PATH.RANKING" :class="{ active: isActive(ROUTE_PATH.RANKING) }" :aria-current="isActive(ROUTE_PATH.RANKING) ? 'page' : undefined" @click="closeMobileNav">
          <svg class="nav-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
            <path d="M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0Z" />
            <path d="M7 6H4v2a4 4 0 0 0 4 4M17 6h3v2a4 4 0 0 1-4 4" />
          </svg>
          爆款榜
        </router-link>
        <template v-if="loggedIn">
          <router-link :to="ROUTE_PATH.PUBLISH" :class="{ active: isActive(ROUTE_PATH.PUBLISH) }" :aria-current="isActive(ROUTE_PATH.PUBLISH) ? 'page' : undefined" @click="closeMobileNav">
            <svg class="nav-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round"><path d="M12 5v14M5 12h14" /></svg>
            发布闲置
          </router-link>
          <router-link :to="ROUTE_PATH.CHAT" :class="{ active: isActive(ROUTE_PATH.CHAT) }" :aria-current="isActive(ROUTE_PATH.CHAT) ? 'page' : undefined" @click="closeMobileNav">
            <svg class="nav-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4Z" />
            </svg>
            消息
            <span v-if="unreadCount > 0" class="dot">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </router-link>
          <router-link :to="ROUTE_PATH.WALLET" :class="{ active: isActive(ROUTE_PATH.WALLET) }" :aria-current="isActive(ROUTE_PATH.WALLET) ? 'page' : undefined" @click="closeMobileNav">
            <svg class="nav-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="6" width="18" height="13" rx="2" />
              <path d="M3 10h18M16 15h.01" />
            </svg>
            钱包·订单
          </router-link>
          <span class="mobile-nav__divider" aria-hidden="true"></span>
          <router-link :to="ROUTE_PATH.USER_PROFILE" :class="{ active: isActive(ROUTE_PATH.USER_PROFILE) }" @click="closeMobileNav">
            <svg class="nav-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="8" r="4" />
              <path d="M4 21c0-4 3.6-6 8-6s8 2 8 6" />
            </svg>
            个人中心
          </router-link>
          <router-link :to="ROUTE_PATH.MY_ITEMS" @click="closeMobileNav">
            <svg class="nav-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
              <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z" />
              <path d="M3 6h18M16 10a4 4 0 0 1-8 0" />
            </svg>
            我的发布
          </router-link>
          <router-link :to="ROUTE_PATH.MY_FAVORITES" @click="closeMobileNav">
            <svg class="nav-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
              <path d="M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2C10.5 3.5 9.3 3 7.5 3A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z" />
            </svg>
            我的收藏
          </router-link>
          <a href="#" @click.prevent="mobileLogout">
            <svg class="nav-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <path d="m16 17 5-5-5-5M21 12H9" />
            </svg>
            退出登录
          </a>
        </template>
        <template v-else>
          <span class="mobile-nav__divider" aria-hidden="true"></span>
          <router-link :to="ROUTE_PATH.LOGIN" @click="closeMobileNav">登录</router-link>
          <router-link :to="ROUTE_PATH.REGISTER" @click="closeMobileNav">注册</router-link>
        </template>
      </nav>
    </header>

    <!-- 页面内容 -->
    <main class="layout-main">
      <slot />
    </main>

    <!-- 页脚 -->
    <footer class="footer">
      <div class="footer__inner">
        <span class="footer__brand">© 智易校园 ZHIYI CAMPUS</span>
        <span>
          <router-link :to="ROUTE_PATH.CHAT">联系客服</router-link>
        </span>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isLoggedIn, getNickname, getUserId } from '@/utils/auth'
import { useUserStore } from '@/stores/user'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { getUnreadCount } from '@/api/chat'
import { ROUTE_PATH } from '@/constants/routes'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loggedIn = computed(() => isLoggedIn())
const nickname = computed(() => userStore.user?.nickname || getNickname() || '?')
const userId = computed(() => userStore.user?.id || getUserId() || 0)

const unreadCount = ref(0)
const mobileNavOpen = ref(false)
let unreadTimer: number | undefined

function isActive(prefix: string) {
  if (prefix === '/') return route.path === '/'
  return route.path.startsWith(prefix)
}

function go(path: string) {
  router.push(path)
}

function closeMobileNav() {
  mobileNavOpen.value = false
}

/** 登出：必须等本地登录态清理完成后再导航，避免守卫读到残留登录态导致跳转被弹回 */
async function handleLogout() {
  closeMobileNav()
  await userStore.logout()
  await router.push(ROUTE_PATH.LOGIN)
}

function mobileLogout(e: Event) {
  e.preventDefault()
  handleLogout()
}

// Esc 关闭移动端抽屉，符合常见交互习惯
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && mobileNavOpen.value) mobileNavOpen.value = false
}

// 路由变化时收起移动端抽屉，避免返回时菜单仍展开
watch(
  () => route.fullPath,
  () => {
    mobileNavOpen.value = false
  }
)

/** M12 根因修复：递归 setTimeout 轮询——在途请求未完成则跳过本拍，绝不重叠 */
let unreadInFlight = false

async function fetchUnreadCount() {
  if (!loggedIn.value || document.visibilityState === 'hidden') return
  if (unreadInFlight) return
  unreadInFlight = true
  try {
    // 后台静默轮询：401 时不触发全局登录跳转，避免打断用户正在进行的导航
    const res = await getUnreadCount({ skipAuthRedirect: true })
    unreadCount.value = Number(res.data || 0)
  } catch {
    // 失败静默（不打断页面），下一拍继续
  } finally {
    unreadInFlight = false
  }
}

function scheduleUnreadPoll() {
  // 低频轮询；页面不可见时 fetchUnreadCount 自行跳过但仍保持节奏，恢复可见即恢复拉取
  unreadTimer = window.setTimeout(async () => {
    await fetchUnreadCount()
    scheduleUnreadPoll()
  }, 15000)
}

onMounted(() => {
  fetchUnreadCount()
  scheduleUnreadPoll()
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  if (unreadTimer) window.clearTimeout(unreadTimer)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.layout-main {
  flex: 1;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: var(--spacing-lg) 20px;
}

.user-entry {
  cursor: pointer;
  display: flex;
  align-items: center;
  outline: none;
}

.footer {
  margin-top: var(--spacing-xl);
}
.footer__brand {
  font-weight: 700;
  letter-spacing: 0.5px;
}

/* 导航与下拉中的行内图标 */
.nav-ic,
.dd-ic {
  width: 17px;
  height: 17px;
  flex: 0 0 17px;
}
.dd-ic {
  width: 16px;
  height: 16px;
  margin-right: 8px;
}

@media (max-width: 900px) {
  .publish-btn {
    display: none;
  }
}
@media (max-width: 480px) {
  .topbar__user .btn--ghost {
    display: none;
  }
}
</style>
