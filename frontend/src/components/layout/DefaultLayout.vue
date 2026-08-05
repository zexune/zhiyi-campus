<template>
  <div class="layout">
    <!-- 顶部导航栏（demo 设计：布告栏 topbar） -->
    <header class="topbar">
      <div class="topbar__inner">
        <router-link to="/" class="logo" aria-label="智易校园首页">
          <span class="logo__mark">智</span>
          智易<em>校园</em>
        </router-link>

        <nav class="nav-links" aria-label="主导航">
          <router-link to="/" :class="{ active: isActive('/') }">交易大厅</router-link>
          <router-link to="/ranking" :class="{ active: isActive('/ranking') }">爆款榜</router-link>
          <template v-if="loggedIn">
            <router-link to="/publish" :class="{ active: isActive('/publish') }">发布闲置</router-link>
            <router-link to="/chat" :class="{ active: isActive('/chat') }">
              消息
              <span v-if="unreadCount > 0" class="dot" :aria-label="`${unreadCount}条未读`">{{ unreadCount }}</span>
            </router-link>
            <router-link to="/wallet" :class="{ active: isActive('/wallet') }">钱包·订单</router-link>
            <router-link v-if="admin" to="/admin/dashboard" :class="{ active: isActive('/admin') }">管理后台</router-link>
          </template>
        </nav>

        <div class="topbar__user">
          <template v-if="loggedIn">
            <router-link to="/publish" class="btn btn--primary btn--sm">
              <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
              发闲置
            </router-link>
            <el-dropdown trigger="click" popper-class="app-dropdown">
              <span class="user-entry">
                <UserAvatar :nickname="nickname" :user-id="userId" size="s" />
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="go('/user/profile')">个人中心</el-dropdown-item>
                  <el-dropdown-item @click="go('/user/my-items')">我的发布</el-dropdown-item>
                  <el-dropdown-item @click="go('/user/my-favorites')">我的收藏</el-dropdown-item>
                  <el-dropdown-item v-if="admin" divided @click="go('/admin/dashboard')">管理后台</el-dropdown-item>
                  <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <router-link to="/login" class="btn btn--ghost btn--sm">登录</router-link>
            <router-link to="/register" class="btn btn--primary btn--sm">注册</router-link>
          </template>
        </div>
      </div>
    </header>

    <!-- 页面内容 -->
    <main class="layout-main">
      <slot />
    </main>

    <!-- 页脚（demo 设计） -->
    <footer class="footer">
      <div class="footer__inner">
        <span>智易校园 · AI 辅助审核与闭环生态的校园交易平台</span>
        <span><router-link to="/chat">联系客服</router-link> · <router-link to="/">回到大厅</router-link></span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isLoggedIn, isAdmin, getNickname, getUserId } from '@/utils/auth'
import { useUserStore } from '@/stores/user'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { getUnreadCount } from '@/api/chat'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loggedIn = computed(() => isLoggedIn())
const admin = computed(() => isAdmin())
const nickname = computed(() => userStore.user?.nickname || getNickname() || '?')
const userId = computed(() => userStore.user?.id || getUserId() || 0)

const unreadCount = ref(0)
let unreadTimer = null

function isActive(prefix) {
  if (prefix === '/') return route.path === '/'
  return route.path.startsWith(prefix)
}

function go(path) {
  router.push(path)
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

async function fetchUnreadCount() {
  if (!loggedIn.value) {
    unreadCount.value = 0
    return
  }
  try {
    const res = await getUnreadCount()
    unreadCount.value = Number(res.data || 0)
  } catch {
    unreadCount.value = 0
  }
}

onMounted(() => {
  fetchUnreadCount()
  unreadTimer = window.setInterval(fetchUnreadCount, 5000)
})

onUnmounted(() => {
  if (unreadTimer) window.clearInterval(unreadTimer)
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
</style>
