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
        <span class="eyebrow">ADMIN CONSOLE</span>
        <h1>独立管理入口</h1>
      </section>

      <section class="card login-card" aria-labelledby="admin-login-title">
        <h2 id="admin-login-title">管理员登录</h2>
        <p class="muted">请输入后台账号和密码</p>
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

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminLogin } from '@/api/admin'
import { useUserStore } from '@/stores/user'
import { ROUTE_PATH } from '@/constants/routes'
import { validateForm } from '@/utils/formValidate'

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
  background: linear-gradient(135deg, var(--ink) 0 46%, var(--paper) 46% 100%);
}
.login-header {
  min-height: 72px;
  padding: 14px max(22px, calc((100vw - 1200px) / 2));
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 2px solid var(--ink);
  background: var(--white);
}
.admin-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 20px;
}
.admin-brand__mark {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border: 2px solid var(--ink);
  border-radius: 9px;
  background: var(--primary);
  color: var(--white);
  box-shadow: 3px 3px 0 var(--ink);
}
.admin-brand small {
  display: block;
  color: var(--primary);
  font-size: 10px;
  letter-spacing: 2px;
}
.user-login {
  color: var(--blue);
  font-size: 13px;
  font-weight: 800;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.login-main {
  width: min(1080px, calc(100% - 40px));
  flex: 1;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr minmax(320px, 420px);
  align-items: center;
  gap: 80px;
  padding: 64px 0;
}
.login-copy {
  color: var(--white);
}
.eyebrow {
  display: inline-block;
  margin-bottom: 14px;
  color: var(--yellow);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 3px;
}
.login-copy h1 {
  font-family: var(--font-display);
  font-size: clamp(42px, 6vw, 72px);
  line-height: 1;
}
.login-card {
  padding: 34px;
  box-shadow: 8px 8px 0 var(--primary);
}
.login-card h2 {
  font-family: var(--font-display);
  font-size: 28px;
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
  font-weight: 800;
}
@media (max-width: 760px) {
  .admin-login-page {
    background: var(--paper);
  }
  .login-main {
    grid-template-columns: 1fr;
    gap: 28px;
    padding: 36px 0;
  }
  .login-copy {
    color: var(--ink);
  }
  .eyebrow {
    color: var(--primary);
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
    box-shadow: 5px 5px 0 var(--primary);
  }
}
</style>
