<template>
  <div class="admin-shell">
    <header class="admin-header">
      <div class="admin-header__inner">
        <router-link class="admin-brand" :to="ROUTE_PATH.ADMIN_DASHBOARD" aria-label="智易校园管理后台">
          <img class="admin-brand__mark" src="/logo.png" alt="" width="30" height="30" />
          <span>
            智易校园
            <small>管理后台</small>
          </span>
        </router-link>

        <nav class="admin-nav" aria-label="管理后台导航">
          <router-link :to="ROUTE_PATH.ADMIN_DASHBOARD">
            <svg class="an-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="3" width="7" height="9" rx="1.5" />
              <rect x="14" y="3" width="7" height="5" rx="1.5" />
              <rect x="14" y="12" width="7" height="9" rx="1.5" />
              <rect x="3" y="16" width="7" height="5" rx="1.5" />
            </svg>
            数据大盘
          </router-link>
          <router-link :to="ROUTE_PATH.ADMIN_VIOLATIONS">
            <svg class="an-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 3 2.5 20h19Z" />
              <path d="M12 10v4M12 17.5h.01" />
            </svg>
            内容治理
          </router-link>
          <router-link :to="ROUTE_PATH.ADMIN_CHAT">
            <svg class="an-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4Z" />
            </svg>
            客服收件箱
          </router-link>
          <router-link :to="ROUTE_PATH.ADMIN_USERS">
            <svg class="an-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="9" cy="8" r="3.5" />
              <path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6" />
              <circle cx="17.5" cy="9.5" r="2.5" />
              <path d="M16 14.6c2.8-.4 5.5 1.5 5.5 4.4" />
            </svg>
            用户管理
          </router-link>
          <router-link :to="ROUTE_PATH.ADMIN_TOPICS">
            <svg class="an-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="5" width="18" height="16" rx="2" />
              <path d="M16 3v4M8 3v4M3 10h18M12 15l1.2 2.4 2.6.4-1.9 1.8.5 2.6L12 21l-2.4 1.2.5-2.6-1.9-1.8 2.6-.4Z" />
            </svg>
            事件专题
          </router-link>
          <router-link :to="ROUTE_PATH.ADMIN_SCHOOLS">
            <svg class="an-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="m4 6 8-4 8 4v5c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10Z" />
              <path d="M9 12h6M12 9v6" />
            </svg>
            学校管理
          </router-link>
          <router-link :to="ROUTE_PATH.ADMIN_CATEGORIES">
            <svg class="an-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="3" width="7" height="7" rx="1.5" />
              <rect x="14" y="3" width="7" height="7" rx="1.5" />
              <rect x="3" y="14" width="7" height="7" rx="1.5" />
              <rect x="14" y="14" width="7" height="7" rx="1.5" />
            </svg>
            分类管理
          </router-link>
        </nav>

        <div class="admin-account">
          <span class="admin-account__name">{{ nickname }}</span>
          <button class="btn btn--sm" type="button" @click="passwordDialogVisible = true">修改密码</button>
          <button class="btn btn--sm" type="button" @click="logout">退出</button>
        </div>
      </div>
    </header>

    <main class="admin-main"><slot /></main>

    <footer class="admin-footer">智易校园管理后台</footer>

    <el-dialog v-model="passwordDialogVisible" title="修改管理员密码" width="min(440px, 92vw)" append-to-body>
      <form class="password-form" @submit.prevent="changePassword">
        <label for="admin-old-password">当前密码</label>
        <input id="admin-old-password" v-model="passwordForm.oldPassword" class="input" type="password" autocomplete="current-password" />
        <label for="admin-new-password">新密码</label>
        <input id="admin-new-password" v-model="passwordForm.newPassword" class="input" type="password" minlength="6" maxlength="64" autocomplete="new-password" />
        <label for="admin-confirm-password">确认新密码</label>
        <input id="admin-confirm-password" v-model="passwordForm.confirmPassword" class="input" type="password" minlength="6" maxlength="64" autocomplete="new-password" />
        <div class="password-actions">
          <button class="btn" type="button" @click="passwordDialogVisible = false">取消</button>
          <button class="btn btn--primary" type="submit" :disabled="passwordSaving">{{ passwordSaving ? '保存中…' : '保存并重新登录' }}</button>
        </div>
      </form>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getNickname } from '@/utils/auth'
import { changeAdminPassword } from '@/api/admin'
import { ROUTE_PATH } from '@/constants/routes'

const router = useRouter()
const userStore = useUserStore()
const nickname = computed(() => userStore.user?.nickname || getNickname() || '管理员')
const passwordDialogVisible = ref(false)
const passwordSaving = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

/** 登出：必须等本地登录态清理完成后再导航，否则守卫仍视为已登录会把 /admin/login 弹回仪表盘 */
async function logout() {
  await userStore.logout()
  await router.replace(ROUTE_PATH.ADMIN_LOGIN)
}

async function changePassword() {
  if (!passwordForm.oldPassword || passwordForm.newPassword.length < 6) {
    ElMessage.warning('请输入当前密码，新密码不少于 6 位')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  passwordSaving.value = true
  try {
    await changeAdminPassword({ ...passwordForm })
    ElMessage.success('密码已修改，请重新登录')
    logout()
  } finally {
    passwordSaving.value = false
  }
}
</script>

<style scoped>
.admin-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--paper);
}
.admin-header {
  position: sticky;
  top: 0;
  z-index: 50;
  border-bottom: var(--bw) solid var(--line);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: var(--ink);
}
.admin-header__inner {
  width: min(1440px, 100%);
  min-height: 60px;
  margin: 0 auto;
  padding: 8px 22px;
  display: flex;
  align-items: center;
  gap: 24px;
}
.admin-brand {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: var(--ink);
  font-size: 17px;
  font-weight: 800;
  white-space: nowrap;
}
.admin-brand__mark {
  width: 30px;
  height: 30px;
  display: block;
}
.admin-brand small {
  display: block;
  color: var(--ink-soft);
  font-family: inherit;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 2px;
}
.admin-nav {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 2px;
  overflow-x: auto;
  scrollbar-width: thin;
}
.admin-nav a {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border-radius: var(--r-s);
  color: var(--ink-soft);
  font-size: 13.5px;
  font-weight: 500;
  transition:
    color 0.15s,
    background-color 0.15s;
}
.an-ic {
  width: 15px;
  height: 15px;
  flex: 0 0 15px;
}
.admin-nav a:hover {
  background: var(--paper-deep);
  color: var(--ink);
}
.admin-nav a.router-link-active {
  background: var(--primary-bg);
  color: var(--primary-deep);
  font-weight: 600;
}
.admin-account {
  display: flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
}
.admin-account__name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-soft);
}
.admin-main {
  width: min(1200px, 100%);
  flex: 1;
  margin: 0 auto;
  padding: var(--spacing-lg) 20px;
}
.admin-footer {
  padding: 20px;
  border-top: var(--bw) solid var(--line);
  color: var(--ink-faint);
  text-align: center;
  font-size: 12px;
}
.password-form {
  display: grid;
  gap: 9px;
}
.password-form label {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 600;
}
.password-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}
@media (max-width: 860px) {
  .admin-header__inner {
    flex-wrap: wrap;
    gap: 8px 16px;
  }
  .admin-nav {
    order: 3;
    flex-basis: 100%;
  }
  .admin-account {
    margin-left: auto;
  }
}
@media (max-width: 520px) {
  .admin-account__name {
    display: none;
  }
  .admin-main {
    padding: 20px 12px;
  }
}
</style>
