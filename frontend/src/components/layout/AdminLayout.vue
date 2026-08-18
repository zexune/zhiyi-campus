<template>
  <div class="admin-shell">
    <header class="admin-header">
      <div class="admin-header__inner">
        <router-link class="admin-brand" :to="ROUTE_PATH.ADMIN_DASHBOARD" aria-label="智易校园管理后台">
          <span class="admin-brand__mark">智</span>
          <span>
            智易校园
            <small>管理后台</small>
          </span>
        </router-link>

        <nav class="admin-nav" aria-label="管理后台导航">
          <router-link :to="ROUTE_PATH.ADMIN_DASHBOARD">数据大盘</router-link>
          <router-link :to="ROUTE_PATH.ADMIN_VIOLATIONS">内容治理</router-link>
          <router-link :to="ROUTE_PATH.ADMIN_CHAT">客服收件箱</router-link>
          <router-link :to="ROUTE_PATH.ADMIN_MANAGE">用户与内容</router-link>
          <router-link :to="ROUTE_PATH.ADMIN_SCHOOLS">学校管理</router-link>
          <router-link :to="ROUTE_PATH.ADMIN_CATEGORIES">分类管理</router-link>
        </nav>

        <div class="admin-account">
          <span class="admin-account__name">{{ nickname }}</span>
          <button class="btn btn--sm" type="button" @click="passwordDialogVisible = true">修改密码</button>
          <button class="btn btn--sm" type="button" @click="logout">退出</button>
        </div>
      </div>
    </header>

    <main class="admin-main"><slot /></main>

    <footer class="admin-footer">智易校园管理后台 · 管理员账号与校园用户空间已隔离</footer>

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

function logout() {
  userStore.logout()
  router.replace(ROUTE_PATH.ADMIN_LOGIN)
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
  border-bottom: var(--bw) solid var(--ink);
  background: var(--ink);
  color: var(--white);
}
.admin-header__inner {
  width: min(1440px, 100%);
  min-height: 68px;
  margin: 0 auto;
  padding: 10px 22px;
  display: flex;
  align-items: center;
  gap: 24px;
}
.admin-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--white);
  font-family: var(--font-display);
  font-size: 19px;
  white-space: nowrap;
}
.admin-brand__mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border: 2px solid var(--white);
  border-radius: 9px;
  background: var(--primary);
  box-shadow: 3px 3px 0 var(--yellow);
}
.admin-brand small {
  display: block;
  color: var(--yellow);
  font-family: inherit;
  font-size: 10px;
  letter-spacing: 2px;
}
.admin-nav {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  overflow-x: auto;
  scrollbar-width: thin;
}
.admin-nav a {
  flex: 0 0 auto;
  padding: 8px 11px;
  border: 1.5px solid transparent;
  border-radius: 8px;
  color: #ede8de;
  font-size: 13px;
  font-weight: 800;
}
.admin-nav a:hover,
.admin-nav a.router-link-active {
  border-color: var(--white);
  background: var(--yellow);
  color: var(--ink);
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
  font-weight: 800;
}
.admin-main {
  width: min(1200px, 100%);
  flex: 1;
  margin: 0 auto;
  padding: var(--spacing-lg) 20px;
}
.admin-footer {
  padding: 22px;
  border-top: 1.5px solid #d8cebb;
  color: var(--ink-soft);
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
  font-weight: 800;
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
