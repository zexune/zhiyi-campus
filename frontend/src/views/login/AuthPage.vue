<template>
  <div class="auth-page">
    <!-- 顶栏（未登录态精简版，遵循 demo login.html） -->
    <header class="topbar">
      <div class="topbar__inner">
        <router-link class="logo" :to="ROUTE_PATH.HOME" aria-label="智易校园首页">
          <span class="logo__mark">智</span>
          智易
          <em>校园</em>
        </router-link>
        <nav class="nav-links" aria-label="主导航">
          <router-link :to="ROUTE_PATH.HOME">交易大厅</router-link>
        </nav>
        <div class="topbar__user">
          <router-link :to="ROUTE_PATH.HOME" class="btn btn--ghost btn--sm">先逛逛 →</router-link>
        </div>
      </div>
    </header>

    <main class="auth-wrap">
      <!-- 左侧宣传 -->
      <section class="auth-side rise">
        <h1>
          拎包入学，
          <br />
          <span class="hl">轻装毕业</span>
        </h1>
        <p>智易校园 —— 只属于本校同学的二手交易布告栏。学号注册，当面交易，平台担保。</p>

        <div class="feature-list">
          <div class="feature-item rise rise-2">
            <div class="feature-item__icon" style="background: #d6f2df">
              <svg viewBox="0 0 24 24" fill="none" stroke="#2F9E62" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />
                <path d="m9 12 2 2 4-4" />
              </svg>
            </div>
            <div>
              <b>平台担保交易</b>
              <span>确认收货后才打款给卖家，资金零风险</span>
            </div>
          </div>
          <div class="feature-item rise rise-3">
            <div class="feature-item__icon" style="background: #ffe1b8">
              <svg viewBox="0 0 24 24" fill="none" stroke="#F5562E" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 8V4H8" />
                <rect x="4" y="8" width="16" height="12" rx="2" />
                <path d="M2 14h2M20 14h2M15 13v2M9 13v2" />
              </svg>
            </div>
            <div>
              <b>本地合规检测</b>
              <span>确定性规则实时检测，风险内容转人工复核</span>
            </div>
          </div>
          <div class="feature-item rise rise-4">
            <div class="feature-item__icon" style="background: #cbe8ff">
              <svg viewBox="0 0 24 24" fill="none" stroke="#3B7BD8" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M11.5 8.5 14 4l2.5 4.5L21 10l-3.5 3 1 5-4.5-2.5L9.5 18l1-5L7 10Z" transform="translate(-2 1)" />
              </svg>
            </div>
            <div>
              <b>信誉等级体系</b>
              <span>诚信交易攒经验，Lv.5「校园传奇」等你解锁</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 右侧表单卡：三个面板共享学校数据，由本组件负责加载与面板间协调 -->
      <section class="card auth-card rise rise-1" aria-label="账户操作">
        <span class="auth-card__pin" aria-hidden="true"></span>

        <div class="auth-tabs" role="tablist">
          <button role="tab" :aria-selected="tab === 'login'" :class="{ active: tab === 'login' }" @click="switchTab('login')">登 录</button>
          <button role="tab" :aria-selected="tab === 'register'" :class="{ active: tab === 'register' }" @click="switchTab('register')">注 册</button>
          <button role="tab" :aria-selected="tab === 'forgot'" :class="{ active: tab === 'forgot' }" @click="switchTab('forgot')">找回密码</button>
        </div>

        <LoginPanel v-show="tab === 'login'" ref="loginPanel" @switch-tab="switchTab" />
        <RegisterPanel v-show="tab === 'register'" />
        <ForgotPanel v-show="tab === 'forgot'" ref="forgotPanel" @reset-done="handleResetDone" />
      </section>
    </main>

    <footer class="footer">
      <div class="footer__inner">
        <span>智易校园 · 本地内容治理与可信交易闭环的校园平台</span>
        <span><router-link :to="ROUTE_PATH.HOME">回到大厅</router-link></span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import ForgotPanel from './panels/ForgotPanel.vue'
import LoginPanel from './panels/LoginPanel.vue'
import RegisterPanel from './panels/RegisterPanel.vue'
import { readSavedSchoolId, useSchoolOptions } from '@/composables/useSchoolOptions'
import { ROUTE_PATH } from '@/constants/routes'

/**
 * 认证页（模块一 1.1/1.2/1.3）—— 登录 / 注册 / 密保找回三面板的编排层：
 * 只负责页壳、tab 切换、共享学校列表加载和面板间交接（找回成功回填登录表单）。
 */
const props = defineProps({
  initialTab: { type: String, default: 'login' } // login / register
})

const { fetchSchools } = useSchoolOptions()

const tab = ref(props.initialTab)
const loginPanel = ref(null)
const forgotPanel = ref(null)

function switchTab(name) {
  tab.value = name
  loginPanel.value?.clearBanMessage()
  if (name === 'forgot') {
    const schoolId = loginPanel.value?.currentSchoolId() || readSavedSchoolId()
    forgotPanel.value?.adoptSchoolId(schoolId)
  }
}

/** 找回密码成功：把学校与学号回填到登录面板并切换过去 */
function handleResetDone({ schoolId, studentId }) {
  loginPanel.value?.prefill({ schoolId, studentId })
  switchTab('login')
}

onMounted(fetchSchools)
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.auth-wrap {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  max-width: 1100px;
  width: 100%;
  margin: 40px auto;
  padding: 0 20px;
  gap: 48px;
  align-items: center;
}
@media (max-width: 900px) {
  .auth-wrap {
    grid-template-columns: 1fr;
    margin: 24px auto;
  }
  .auth-side {
    display: none;
  }
}

/* —— 左侧宣传面 —— */
.auth-side h1 {
  font-family: var(--font-display);
  font-size: clamp(36px, 4.5vw, 54px);
  line-height: 1.3;
  letter-spacing: 2px;
}
.auth-side h1 .hl {
  display: inline-block;
  background: var(--yellow);
  padding: 0 12px;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  box-shadow: var(--shadow-s);
  transform: rotate(-2deg);
}
.auth-side p {
  margin-top: 16px;
  color: var(--ink-soft);
  font-size: 16px;
  max-width: 400px;
}

.feature-list {
  margin-top: 32px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.feature-item {
  display: flex;
  align-items: center;
  gap: 14px;
  background: var(--white);
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-m);
  padding: 14px 18px;
  box-shadow: var(--shadow-s);
  max-width: 420px;
  transition: transform 0.2s;
}
.feature-item:hover {
  transform: translateX(6px);
}
.feature-item:nth-child(2) {
  transform: rotate(-0.8deg);
}
.feature-item:nth-child(3) {
  transform: rotate(0.8deg);
}
.feature-item__icon {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
}
.feature-item__icon svg {
  width: 22px;
  height: 22px;
}
.feature-item b {
  font-size: 15px;
  display: block;
}
.feature-item span {
  font-size: 13px;
  color: var(--ink-soft);
}

/* —— 右侧表单卡 —— */
.auth-card {
  padding: 36px 36px 30px;
  position: relative;
  max-width: 460px;
  width: 100%;
  justify-self: center;
  /* 三个面板统一最小高度：切 tab / 切步骤时卡片高度稳定，页面其余部件不偏移 */
  min-height: 560px;
  display: flex;
  flex-direction: column;
}
@media (max-width: 900px) {
  .auth-card {
    justify-self: stretch;
    max-width: none;
    padding: 28px 22px;
    min-height: 0;
  }
}

.auth-card__pin {
  position: absolute;
  top: -14px;
  left: 50%;
  translate: -50% 0;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--red);
  border: var(--bw) solid var(--ink);
  box-shadow: inset -3px -3px 0 rgba(0, 0, 0, 0.25);
}

/* 选项卡 */
.auth-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  margin-bottom: 26px;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  overflow: hidden;
}
.auth-tabs button {
  border: none;
  background: var(--white);
  padding: 11px 4px;
  cursor: pointer;
  font-family: inherit;
  font-weight: 700;
  font-size: 14.5px;
  color: var(--ink-soft);
  border-right: var(--bw) solid var(--ink);
  transition: all 0.18s;
}
.auth-tabs button:last-child {
  border-right: none;
}
.auth-tabs button:hover {
  background: var(--paper-deep);
  color: var(--ink);
}
.auth-tabs button.active {
  background: var(--primary);
  color: #fff;
}
.auth-tabs button:focus-visible {
  outline: 3px solid var(--blue);
  outline-offset: -3px;
}
</style>
