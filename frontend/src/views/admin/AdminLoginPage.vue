<template>
  <div class="admin-login-page">
    <header class="login-header">
      <router-link class="admin-brand" :to="ROUTE_PATH.ADMIN_LOGIN" aria-label="智易校园管理后台">
        <span class="admin-brand__mark">智</span>
        <span>
          智易校园
          <small>管理后台</small>
        </span>
      </router-link>
      <router-link class="user-login" :to="ROUTE_PATH.LOGIN">返回学生登录</router-link>
    </header>

    <main class="login-main">
      <section class="login-copy">
        <span class="login-copy__mark" aria-hidden="true">智</span>
        <div>
          <h1>智易校园 · 管理后台</h1>
          <p>仅限授权运营人员使用</p>
          <ul class="login-scope" aria-label="后台能力范围">
            <li>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3 2.5 20h19Z" /><path d="M12 10v4M12 17.5h.01" /></svg>
              内容审核
            </li>
            <li>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4" /><path d="M4 21c0-4 3.6-6 8-6s8 2 8 6" /></svg>
              用户风控
            </li>
            <li>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="9" rx="1.5" /><rect x="14" y="3" width="7" height="5" rx="1.5" /><rect x="14" y="12" width="7" height="9" rx="1.5" /><rect x="3" y="16" width="7" height="5" rx="1.5" /></svg>
              数据看板
            </li>
          </ul>
        </div>
      </section>

      <section class="card login-card" aria-labelledby="admin-login-title">
        <h2 id="admin-login-title">管理员登录</h2>
        <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="submit">
          <el-form-item prop="username" class="field">
            <label for="admin-username">管理员账号</label>
            <input id="admin-username" v-model.trim="form.username" class="input" maxlength="50" autocomplete="username" autofocus placeholder="请输入管理员账号" />
          </el-form-item>
          <el-form-item prop="password" class="field">
            <label for="admin-password">密码</label>
            <input id="admin-password" v-model="form.password" class="input" type="password" maxlength="128" autocomplete="current-password" placeholder="请输入密码" />
          </el-form-item>
          <button class="btn btn--primary btn--lg btn--block" type="submit" :disabled="loading">
            {{ loading ? '验证中…' : '进入管理后台' }}
          </button>
        </el-form>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminLogin } from '@/api/admin'
import { useUserStore } from '@/stores/user'
import { ROUTE_PATH } from '@/constants/routes'
import { validateForm } from '@/utils/formValidate'
import { resetAuthRedirect } from '@/utils/request'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 登录页挂载：解除 401 单飞跳转标记
onMounted(() => {
  resetAuthRedirect()
})

async function submit() {
  const valid = await validateForm(formRef)
  if (!valid) return
  loading.value = true
  try {
    const res = await adminLogin({ username: form.username, password: form.password })
    userStore.setLogin(res.data)
    const requested = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const target = requested.startsWith('/admin/') && requested !== ROUTE_PATH.ADMIN_LOGIN ? requested : ROUTE_PATH.ADMIN_DASHBOARD
    await router.replace(target)
    ElMessage.success('管理员登录成功')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.admin-login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f1ece1 0%, var(--paper) 60%);
}
.login-header {
  min-height: 60px;
  padding: 10px max(22px, calc((100vw - 1200px) / 2));
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border-bottom: var(--bw) solid var(--line);
  background: rgba(255, 255, 255, 0.92);
}
.admin-brand {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: var(--ink);
  font-size: 17px;
  font-weight: 800;
}
.admin-brand__mark {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: var(--r-s);
  background: var(--primary);
  color: var(--white);
  font-size: 16px;
}
.admin-brand small {
  display: block;
  color: var(--ink-soft);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 2px;
}
.user-login {
  color: var(--ink-soft);
  font-size: 13.5px;
  font-weight: 500;
  transition: color 0.15s;
}
.user-login:hover {
  color: var(--primary);
}
.login-main {
  width: min(1000px, calc(100% - 40px));
  flex: 1;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr minmax(320px, 400px);
  align-items: center;
  gap: 72px;
  padding: 64px 0;
}
.login-copy {
  display: flex;
  align-items: flex-start;
  gap: 18px;
}
.login-copy__mark {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: var(--r-l);
  background: var(--primary);
  color: var(--white);
  font-size: 27px;
  font-weight: 700;
  box-shadow: var(--shadow-m);
}
.login-copy h1 {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.3px;
  line-height: 1.25;
  color: var(--ink);
}
.login-copy p {
  margin-top: 8px;
  color: var(--ink-soft);
  font-size: 14.5px;
}
.login-scope {
  margin-top: 22px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.login-scope li {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: var(--bw) solid var(--line);
  border-radius: 999px;
  background: var(--white);
  color: var(--ink-soft);
  font-size: 13px;
  font-weight: 500;
}
.login-scope li svg {
  width: 15px;
  height: 15px;
  color: var(--primary);
}
.login-card {
  padding: 32px;
  box-shadow: var(--shadow-m);
}
.login-card h2 {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 20px;
}
.login-card > .muted {
  margin: 6px 0 24px;
}
.login-card .field {
  margin-bottom: 18px;
}
.login-card .field.el-form-item {
  display: block;
}
.login-card .field :deep(.el-form-item__content) {
  display: block;
  line-height: 1.6;
  margin-left: 0;
}
.login-card .field :deep(.el-form-item__error) {
  position: static;
  padding-top: 3px;
}
.login-card label {
  display: block;
  margin-bottom: 7px;
  font-size: 13px;
  font-weight: 600;
}
@media (max-width: 760px) {
  .login-main {
    grid-template-columns: 1fr;
    gap: 32px;
    padding: 36px 0;
  }
  .login-copy__mark {
    width: 44px;
    height: 44px;
    border-radius: var(--r-m);
    font-size: 21px;
  }
  .login-copy h1 {
    font-size: 22px;
  }
}
@media (max-width: 480px) {
  .login-header {
    padding: 12px 14px;
  }
  .admin-brand {
    font-size: 16px;
  }
  .login-main {
    width: min(100% - 24px, 420px);
  }
  .login-card {
    padding: 26px 20px;
  }
}
</style>
