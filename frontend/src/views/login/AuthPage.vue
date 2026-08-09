<template>
  <div class="auth-page">
    <!-- 顶栏（未登录态精简版，遵循 demo login.html） -->
    <header class="topbar">
      <div class="topbar__inner">
        <router-link class="logo" to="/" aria-label="智易校园首页">
          <span class="logo__mark">智</span>
          智易<em>校园</em>
        </router-link>
        <nav class="nav-links" aria-label="主导航">
          <router-link to="/">交易大厅</router-link>
        </nav>
        <div class="topbar__user">
          <router-link to="/" class="btn btn--ghost btn--sm">先逛逛 →</router-link>
        </div>
      </div>
    </header>

    <main class="auth-wrap">
      <!-- 左侧宣传 -->
      <section class="auth-side rise">
        <h1>拎包入学，<br><span class="hl">轻装毕业</span></h1>
        <p>智易校园 —— 只属于本校同学的二手交易布告栏。学号注册，当面交易，平台担保。</p>

        <div class="feature-list">
          <div class="feature-item rise rise-2">
            <div class="feature-item__icon" style="background:#D6F2DF">
              <svg viewBox="0 0 24 24" fill="none" stroke="#2F9E62" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/><path d="m9 12 2 2 4-4"/></svg>
            </div>
            <div><b>平台担保交易</b><span>确认收货后才打款给卖家，资金零风险</span></div>
          </div>
          <div class="feature-item rise rise-3">
            <div class="feature-item__icon" style="background:#FFE1B8">
              <svg viewBox="0 0 24 24" fill="none" stroke="#F5562E" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 8V4H8"/><rect x="4" y="8" width="16" height="12" rx="2"/><path d="M2 14h2M20 14h2M15 13v2M9 13v2"/></svg>
            </div>
            <div><b>AI 智能审核</b><span>发布内容秒级机审，违规信息自动拦截</span></div>
          </div>
          <div class="feature-item rise rise-4">
            <div class="feature-item__icon" style="background:#CBE8FF">
              <svg viewBox="0 0 24 24" fill="none" stroke="#3B7BD8" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M11.5 8.5 14 4l2.5 4.5L21 10l-3.5 3 1 5-4.5-2.5L9.5 18l1-5L7 10Z" transform="translate(-2 1)"/></svg>
            </div>
            <div><b>信誉等级体系</b><span>诚信交易攒经验，Lv.5「校园传奇」等你解锁</span></div>
          </div>
        </div>
      </section>

      <!-- 右侧表单卡 -->
      <section class="card auth-card rise rise-1" aria-label="账户操作">
        <span class="auth-card__pin" aria-hidden="true"></span>

        <div class="auth-tabs" role="tablist">
          <button role="tab" :aria-selected="tab === 'login'" :class="{ active: tab === 'login' }" @click="switchTab('login')">登 录</button>
          <button role="tab" :aria-selected="tab === 'register'" :class="{ active: tab === 'register' }" @click="switchTab('register')">注 册</button>
          <button role="tab" :aria-selected="tab === 'forgot'" :class="{ active: tab === 'forgot' }" @click="switchTab('forgot')">找回密码</button>
        </div>

        <!-- ===== 登录面板 ===== -->
        <div v-show="tab === 'login'" class="auth-panel">
          <h2>欢迎回来</h2>
          <p class="sub">选择学校并用学号登录，继续你的淘货之旅</p>
          <form @submit.prevent="handleLogin">
            <div class="field">
              <label for="l-school">所属学校</label>
              <AppSelect
                id="l-school"
                v-model="loginForm.schoolId"
                :options="schoolOptions"
                :placeholder="schoolsLoading ? '学校列表加载中…' : '请选择你就读的学校'"
                :disabled="schoolsLoading"
                :loading="schoolsLoading"
                aria-label="登录学校"
              />
              <div v-if="schoolsError" class="school-load-error" role="alert">
                <span>学校列表加载失败</span>
                <button class="school-retry" type="button" :disabled="schoolsLoading" @click="fetchSchools">重新加载</button>
              </div>
            </div>
            <div class="field">
              <label for="l-sid">学号</label>
              <input id="l-sid" v-model.trim="loginForm.studentId" class="input" type="text" placeholder="例如：20240101234" autocomplete="username" />
            </div>
            <div class="field">
              <label for="l-pw">密码</label>
              <input id="l-pw" v-model="loginForm.password" class="input" type="password" placeholder="请输入密码" autocomplete="current-password" />
            </div>
            <div class="form-foot">
              <span class="muted">Token 有效期 24 小时</span>
              <a href="#" @click.prevent="switchTab('forgot')">忘记密码？</a>
            </div>
            <button class="btn btn--primary btn--lg btn--block" type="submit" :disabled="loading || schoolsLoading">
              {{ loading ? '登录中…' : '登录' }}
            </button>
          </form>
          <div v-if="banMessage" class="banned-tip" role="alert">
            <svg viewBox="0 0 24 24" fill="none" stroke-width="2.2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 8v4M12 16h.01"/></svg>
            <span><b>账户被封禁？</b>{{ banMessage }}临时封禁到期后会自动恢复。</span>
          </div>
        </div>

        <!-- ===== 注册面板（两步走，控制纵向高度）===== -->
        <div v-show="tab === 'register'" class="auth-panel">
          <h2>创建账号</h2>
          <p class="sub">仅限本校学生，学号即账号</p>

          <div class="steps" aria-label="注册步骤">
            <span class="step" :class="regStepClass(1)"><span class="step__no">{{ regStep > 1 ? '✓' : '1' }}</span>账号信息</span>
            <span class="step-line"></span>
            <span class="step" :class="regStepClass(2)"><span class="step__no">2</span>密保设置</span>
          </div>

          <!-- 步骤1：学号 / 昵称 / 密码 / 确认密码（两列紧凑排布，与登录面板同高） -->
          <form v-if="regStep === 1" @submit.prevent="handleRegNext">
            <div class="field-row">
              <div class="field">
                <label for="r-sid">学号 <span class="req">*</span></label>
                <input id="r-sid" v-model.trim="regForm.studentId" class="input" type="text" placeholder="唯一登录凭证" autocomplete="username" />
              </div>
              <div class="field">
                <label for="r-nick">昵称</label>
                <input id="r-nick" v-model.trim="regForm.nickname" class="input" type="text" :placeholder="defaultNickname" />
              </div>
            </div>
            <div class="field-row">
              <div class="field">
                <label for="r-pw">密码 <span class="req">*</span></label>
                <input id="r-pw" v-model="regForm.password" class="input" type="password" placeholder="不少于 6 位" autocomplete="new-password" />
                <div class="pw-strength" aria-hidden="true">
                  <i v-for="n in 4" :key="n" :class="{ on: passwordStrength >= n }"></i>
                </div>
              </div>
              <div class="field">
                <label for="r-pw2">确认密码 <span class="req">*</span></label>
                <input id="r-pw2" v-model="regForm.confirmPassword" class="input" type="password" placeholder="再输入一次" autocomplete="new-password" />
                <p v-if="regForm.confirmPassword && regForm.confirmPassword !== regForm.password" class="error-msg">两次输入不一致</p>
              </div>
            </div>
            <div class="field">
              <label for="r-school">所属学校 <span class="req">*</span></label>
              <AppSelect
                id="r-school"
                v-model="regForm.schoolId"
                :options="schoolOptions"
                :placeholder="schoolsLoading ? '学校列表加载中…' : '请选择你就读的学校'"
                :disabled="schoolsLoading"
                :loading="schoolsLoading"
                aria-label="所属学校"
              />
              <div v-if="schoolsError" class="school-load-error" role="alert">
                <span>学校列表加载失败</span>
                <button class="school-retry" type="button" :disabled="schoolsLoading" @click="fetchSchools">重新加载</button>
              </div>
            </div>
            <div class="field">
              <label for="r-email">学校邮箱 <span class="opt">选填</span></label>
              <input id="r-email" v-model.trim="regForm.schoolEmail" class="input" type="email"
                     :placeholder="emailPlaceholder" autocomplete="email" />
            </div>
            <button class="btn btn--primary btn--lg btn--block" type="submit" :disabled="schoolsLoading">下一步</button>
          </form>

          <!-- 步骤2：密保问题（自由输入 + 随机填入）/ 密保答案 / 手机号 -->
          <form v-if="regStep === 2" @submit.prevent="handleRegister">
            <div class="field">
              <label for="r-q">密保问题 <span class="req">*</span></label>
              <div class="question-input">
                <input id="r-q" v-model.trim="regForm.securityQuestion" class="input" type="text" maxlength="50"
                       placeholder="自己出一道只有你知道答案的问题" />
                <button class="btn random-btn" type="button" title="从系统预设中随机抽一个" @click="randomQuestion">
                  <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="4"/><circle cx="8.5" cy="8.5" r="1.4" fill="currentColor" stroke="none"/><circle cx="15.5" cy="8.5" r="1.4" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.4" fill="currentColor" stroke="none"/><circle cx="8.5" cy="15.5" r="1.4" fill="currentColor" stroke="none"/><circle cx="15.5" cy="15.5" r="1.4" fill="currentColor" stroke="none"/></svg>
                  随机
                </button>
              </div>
            </div>
            <div class="field-row">
              <div class="field">
                <label for="r-a">密保答案 <span class="req">*</span></label>
                <input id="r-a" v-model="regForm.securityAnswer" class="input" type="text" placeholder="不区分大小写" />
              </div>
              <div class="field">
                <label for="r-phone">手机号（选填）</label>
                <input id="r-phone" v-model.trim="regForm.phone" class="input" type="tel" placeholder="仅用于接收通知" />
              </div>
            </div>
            <p class="hint reg-hint">忘记密码时凭密保找回，请务必记住答案；拿不准就点「随机」用系统预设的问题</p>
            <div class="reg-actions">
              <button class="btn btn--lg" type="button" @click="regStep = 1">上一步</button>
              <button class="btn btn--primary btn--lg reg-actions__submit" type="submit" :disabled="loading">
                {{ loading ? '注册中…' : '注册并开始淘货' }}
              </button>
            </div>
          </form>
        </div>

        <!-- ===== 找回密码面板 ===== -->
        <div v-show="tab === 'forgot'" class="auth-panel">
          <h2>找回密码</h2>
          <p class="sub">回答密保问题，重置你的密码</p>

          <div class="steps" aria-label="找回密码步骤">
            <span class="step" :class="stepClass(1)"><span class="step__no">{{ forgotStep > 1 ? '✓' : '1' }}</span>确认账号</span>
            <span class="step-line"></span>
            <span class="step" :class="stepClass(2)"><span class="step__no">{{ forgotStep > 2 ? '✓' : '2' }}</span>答密保</span>
            <span class="step-line"></span>
            <span class="step" :class="stepClass(3)"><span class="step__no">3</span>设新密码</span>
          </div>

          <!-- 步骤1：选择学校并输入学号 -->
          <form v-if="forgotStep === 1" @submit.prevent="handleFetchQuestion">
            <div class="field">
              <label for="f-school">所属学校</label>
              <AppSelect
                id="f-school"
                v-model="forgotForm.schoolId"
                :options="schoolOptions"
                :placeholder="schoolsLoading ? '学校列表加载中…' : '请选择注册时的学校'"
                :disabled="schoolsLoading"
                :loading="schoolsLoading"
                aria-label="找回密码学校"
              />
              <div v-if="schoolsError" class="school-load-error" role="alert">
                <span>学校列表加载失败</span>
                <button class="school-retry" type="button" :disabled="schoolsLoading" @click="fetchSchools">重新加载</button>
              </div>
            </div>
            <div class="field">
              <label for="f-sid">学号</label>
              <input id="f-sid" v-model.trim="forgotForm.studentId" class="input" type="text" placeholder="请输入注册时的学号" autocomplete="username" />
            </div>
            <button class="btn btn--green btn--lg btn--block" type="submit" :disabled="loading || schoolsLoading">下一步</button>
          </form>

          <!-- 步骤2：答密保 -->
          <template v-if="forgotStep === 2">
            <div class="question-box">
              <svg viewBox="0 0 24 24" fill="none" stroke="#F5562E" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M9.1 9a3 3 0 0 1 5.8 1c0 2-3 2.5-3 4M12 17.5h.01"/></svg>
              {{ securityQuestion }}
            </div>
            <form @submit.prevent="forgotStep = 3">
              <div class="field">
                <label for="f-answer">密保答案</label>
                <input id="f-answer" v-model="forgotForm.securityAnswer" class="input" type="text" placeholder="不区分大小写、忽略首尾空格" />
              </div>
              <button class="btn btn--green btn--lg btn--block" type="submit" :disabled="!forgotForm.securityAnswer.trim()">验证并进入下一步</button>
            </form>
          </template>

          <!-- 步骤3：设新密码（答案随新密码一并提交后端验证） -->
          <form v-if="forgotStep === 3" @submit.prevent="handleReset">
            <div class="field">
              <label for="f-pw">新密码</label>
              <input id="f-pw" v-model="forgotForm.newPassword" class="input" type="password" placeholder="不少于 6 位" autocomplete="new-password" />
            </div>
            <div class="field">
              <label for="f-pw2">确认新密码</label>
              <input id="f-pw2" v-model="forgotForm.confirmPassword" class="input" type="password" placeholder="再输入一次" autocomplete="new-password" />
              <p v-if="forgotForm.confirmPassword && forgotForm.confirmPassword !== forgotForm.newPassword" class="error-msg">两次输入的密码不一致</p>
            </div>
            <button class="btn btn--green btn--lg btn--block" type="submit" :disabled="loading">
              {{ loading ? '提交中…' : '重置密码' }}
            </button>
          </form>
          <p class="hint" style="margin-top:14px;text-align:center">重置成功后，所有设备都需要重新登录</p>
        </div>
      </section>
    </main>

    <footer class="footer">
      <div class="footer__inner">
        <span>智易校园 · AI 辅助审核与闭环生态的校园交易平台</span>
        <span><router-link to="/">回到大厅</router-link></span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import AppSelect from '@/components/common/AppSelect.vue'
import { login, register, getSecurityQuestion, getSecurityQuestions, resetPassword, getSchools } from '@/api/auth'
import { useUserStore } from '@/stores/user'

/**
 * 认证页（模块一 1.1/1.2/1.3）—— 登录 / 注册 / 密保找回三合一，遵循 demo login.html 设计
 */
const props = defineProps({
  initialTab: { type: String, default: 'login' }, // login / register
})

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const tab = ref(props.initialTab)
const loading = ref(false)
const banMessage = ref('')
const LAST_SCHOOL_KEY = 'zhiyi:last-school-id'

function savedSchoolId() {
  const value = Number(localStorage.getItem(LAST_SCHOOL_KEY))
  return Number.isSafeInteger(value) && value > 0 ? value : null
}

function rememberSchool(schoolId) {
  if (schoolId) localStorage.setItem(LAST_SCHOOL_KEY, String(schoolId))
}

// —— 登录 ——
const loginForm = reactive({ schoolId: savedSchoolId(), studentId: '', password: '' })

async function handleLogin() {
  if (!loginForm.schoolId) {
    ElMessage.warning('请选择所属学校')
    return
  }
  if (!loginForm.studentId || !loginForm.password) {
    ElMessage.warning('请输入学号和密码')
    return
  }
  loading.value = true
  try {
    const res = await login({ ...loginForm })
    rememberSchool(loginForm.schoolId)
    userStore.setLogin(res.data)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/')
  } catch (e) {
    if (String(e.message || '').includes('封禁')) {
      banMessage.value = e.message
    }
  } finally {
    loading.value = false
  }
}

// —— 注册（两步走：账号信息 → 密保设置）——
const questions = ref([])
const regStep = ref(1)
const regForm = reactive({
  studentId: '', password: '', confirmPassword: '', nickname: '',
  schoolId: savedSchoolId(), schoolEmail: '',
  securityQuestion: '', securityAnswer: '', phone: '',
})

// —— 学校下拉 + 学校邮箱后缀校验（A2/A3）——
const schools = ref([])
const schoolOptions = computed(() =>
  schools.value.map((school) => ({ label: school.name, value: school.id }))
)
const schoolsLoading = ref(false)
const schoolsError = ref(false)

const selectedSchool = computed(() =>
  schools.value.find((s) => s.id === regForm.schoolId) || null
)
const emailPlaceholder = computed(() =>
  selectedSchool.value?.emailDomain
    ? `学号${selectedSchool.value.emailDomain}`
    : '先选择学校，再填写学校邮箱'
)

function schoolEmailIsValid() {
  const email = regForm.schoolEmail.trim().toLowerCase()
  if (!email) return true
  if (!/^[^@\s]+@[^@\s]+$/.test(email)) return false
  const domain = selectedSchool.value?.emailDomain?.trim().toLowerCase()
  return !domain || email.endsWith(domain)
}

async function fetchSchools() {
  schoolsLoading.value = true
  schoolsError.value = false
  try {
    const res = await getSchools()
    schools.value = res.data || []
    const rememberedSchoolId = savedSchoolId()
    for (const form of [loginForm, regForm, forgotForm]) {
      if (!schools.value.some((school) => school.id === form.schoolId)) {
        form.schoolId = schools.value.some((school) => school.id === rememberedSchoolId)
          ? rememberedSchoolId
          : null
      }
    }
  } catch {
    schools.value = []
    loginForm.schoolId = null
    regForm.schoolId = null
    forgotForm.schoolId = null
    schoolsError.value = true
  } finally {
    schoolsLoading.value = false
  }
}

/** 随机填入一个预设密保问题；相邻两次不重复（用户仍可自行修改） */
let lastRandomIndex = -1
function randomQuestion() {
  const pool = questions.value
  if (!pool.length) return
  let idx
  if (pool.length === 1) {
    idx = 0
  } else {
    do {
      idx = Math.floor(Math.random() * pool.length)
    } while (idx === lastRandomIndex)
  }
  lastRandomIndex = idx
  regForm.securityQuestion = pool[idx]
}

const defaultNickname = computed(() => {
  const sid = regForm.studentId
  return sid.length >= 4 ? `同学_${sid.slice(-4)}` : '默认生成，可修改'
})

const passwordStrength = computed(() => {
  const p = regForm.password
  if (!p) return 0
  let s = 0
  if (p.length >= 6) s++
  if (p.length >= 10) s++
  if (/[a-zA-Z]/.test(p) && /\d/.test(p)) s++
  if (/[^a-zA-Z0-9]/.test(p)) s++
  return s
})

const strengthText = computed(() => ['弱', '弱', '中', '强', '很强'][passwordStrength.value])

function regStepClass(n) {
  return { done: regStep.value > n, current: regStep.value === n }
}

/** 步骤1 → 步骤2：先校验账号信息 */
function handleRegNext() {
  if (schoolsLoading.value) {
    ElMessage.warning('学校列表仍在加载，请稍候')
    return
  }
  if (schoolsError.value) {
    ElMessage.warning('请先重新加载学校列表')
    return
  }
  if (!regForm.studentId || !regForm.password) {
    ElMessage.warning('请填写学号和密码')
    return
  }
  if (regForm.password.length < 6) {
    ElMessage.warning('密码不少于 6 位')
    return
  }
  if (regForm.password !== regForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (!regForm.schoolId) {
    ElMessage.warning('请选择所属学校')
    return
  }
  if (!schoolEmailIsValid()) {
    const domain = selectedSchool.value?.emailDomain
    ElMessage.warning(domain ? `学校邮箱须使用 ${domain} 后缀` : '学校邮箱格式不正确')
    return
  }
  regStep.value = 2
}

async function handleRegister() {
  if (!regForm.securityQuestion) {
    ElMessage.warning('请填写密保问题（可点「随机」快速选一个）')
    return
  }
  if (!regForm.securityAnswer) {
    ElMessage.warning('请填写密保答案')
    return
  }
  loading.value = true
  try {
    const res = await register({
      studentId: regForm.studentId,
      password: regForm.password,
      confirmPassword: regForm.confirmPassword,
      nickname: regForm.nickname,
      schoolId: regForm.schoolId,
      schoolEmail: regForm.schoolEmail || null,
      securityQuestion: regForm.securityQuestion,
      securityAnswer: regForm.securityAnswer,
      phone: regForm.phone,
    })
    rememberSchool(regForm.schoolId)
    userStore.setLogin(res.data)
    ElMessage.success('注册成功，欢迎加入智易校园！')
    router.push('/')
  } catch (e) {
    // 学号已注册等账号类错误发生在步骤1的字段上，退回步骤1便于修改
    if (String(e.message || '').includes('学号')) {
      regStep.value = 1
    }
  } finally {
    loading.value = false
  }
}

// —— 找回密码（三步）——
const forgotStep = ref(1)
const securityQuestion = ref('')
const forgotForm = reactive({
  schoolId: savedSchoolId(),
  studentId: '',
  securityAnswer: '',
  newPassword: '',
  confirmPassword: '',
})

function stepClass(n) {
  return { done: forgotStep.value > n, current: forgotStep.value === n }
}

async function handleFetchQuestion() {
  if (!forgotForm.schoolId) {
    ElMessage.warning('请选择注册时的学校')
    return
  }
  if (!forgotForm.studentId) {
    ElMessage.warning('请输入学号')
    return
  }
  loading.value = true
  try {
    const res = await getSecurityQuestion(forgotForm.schoolId, forgotForm.studentId)
    securityQuestion.value = res.data.question
    forgotStep.value = 2
  } catch (e) {
    // 提示由 request.js 统一处理
  } finally {
    loading.value = false
  }
}

async function handleReset() {
  if (forgotForm.newPassword.length < 6) {
    ElMessage.warning('新密码不少于 6 位')
    return
  }
  if (forgotForm.newPassword !== forgotForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await resetPassword({ ...forgotForm })
    const recoveredSchoolId = forgotForm.schoolId
    const recoveredStudentId = forgotForm.studentId
    rememberSchool(recoveredSchoolId)
    ElMessage.success('密码重置成功，请用新密码登录')
    forgotStep.value = 1
    Object.assign(forgotForm, {
      schoolId: recoveredSchoolId,
      studentId: '',
      securityAnswer: '',
      newPassword: '',
      confirmPassword: '',
    })
    Object.assign(loginForm, {
      schoolId: recoveredSchoolId,
      studentId: recoveredStudentId,
      password: '',
    })
    switchTab('login')
  } catch (e) {
    // 答案错误时退回步骤2重新作答
    if (String(e.message || '').includes('密保')) {
      forgotStep.value = 2
      forgotForm.securityAnswer = ''
    }
  } finally {
    loading.value = false
  }
}

function switchTab(name) {
  tab.value = name
  banMessage.value = ''
  if (name === 'forgot' && !forgotForm.schoolId) {
    forgotForm.schoolId = loginForm.schoolId || savedSchoolId()
  }
}

onMounted(async () => {
  try {
    const res = await getSecurityQuestions()
    questions.value = res.data
  } catch (e) {
    // 兜底：接口异常时用前端预设，保证「随机」按钮可用
    questions.value = ['你的小学名称是？', '你最喜欢的老师姓什么？', '你的出生地是哪个城市？', '你第一只宠物叫什么？']
  }
  await fetchSchools()
})
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
  .auth-wrap { grid-template-columns: 1fr; margin: 24px auto; }
  .auth-side { display: none; }
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
.auth-side p { margin-top: 16px; color: var(--ink-soft); font-size: 16px; max-width: 400px; }

.feature-list { margin-top: 32px; display: flex; flex-direction: column; gap: 14px; }
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
  transition: transform .2s;
}
.feature-item:hover { transform: translateX(6px); }
.feature-item:nth-child(2) { transform: rotate(-.8deg); }
.feature-item:nth-child(3) { transform: rotate(.8deg); }
.feature-item__icon {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
}
.feature-item__icon svg { width: 22px; height: 22px; }
.feature-item b { font-size: 15px; display: block; }
.feature-item span { font-size: 13px; color: var(--ink-soft); }

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
.auth-panel { flex: 1; display: flex; flex-direction: column; }
.auth-panel form { flex: 1; display: flex; flex-direction: column; }
.auth-panel form > .btn--block,
.auth-panel form > .reg-actions { margin-top: auto; }
@media (max-width: 900px) {
  .auth-card { justify-self: stretch; max-width: none; padding: 28px 22px; min-height: 0; }
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
  box-shadow: inset -3px -3px 0 rgba(0,0,0,.25);
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
  transition: all .18s;
}
.auth-tabs button:last-child { border-right: none; }
.auth-tabs button:hover { background: var(--paper-deep); color: var(--ink); }
.auth-tabs button.active { background: var(--primary); color: #fff; }
.auth-tabs button:focus-visible { outline: 3px solid var(--blue); outline-offset: -3px; }

.auth-panel h2 { font-family: var(--font-display); font-size: 26px; letter-spacing: 1px; margin-bottom: 4px; }
.auth-panel .sub { color: var(--ink-soft); font-size: 14px; margin-bottom: 22px; }

.pw-strength { display: flex; gap: 5px; margin-top: 7px; }
.pw-strength i { height: 6px; flex: 1; border-radius: 3px; background: #E8E0CF; border: 1px solid #D8CDB6; }
.pw-strength i.on { background: var(--green); border-color: var(--ink); }

.reg-actions { display: flex; gap: 12px; }
.reg-actions__submit { flex: 1; }

/* 注册面板：两列紧凑排布 */
.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.field-row .field { margin-bottom: 14px; }
.reg-hint { margin: 0 0 16px; }

/* 学校邮箱：可选标记 */
.field label .opt { color: var(--ink-soft); font-weight: 600; font-size: 12px; margin-left: 6px; }
.school-load-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 6px;
  color: var(--red);
  font-size: 12.5px;
}
.school-retry {
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--blue);
  font: inherit;
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}
.school-retry:disabled { cursor: wait; opacity: .55; }
@media (max-width: 480px) {
  .field-row { grid-template-columns: 1fr; gap: 0; }
}

/* 密保问题：输入框 + 随机按钮 */
.question-input { display: flex; gap: 10px; }
.question-input .input { flex: 1; }
.random-btn { flex-shrink: 0; padding: 10px 14px; background: var(--yellow); }
.random-btn .icon { width: 17px; height: 17px; }

.form-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 4px 0 18px;
  font-size: 13.5px;
}
.form-foot a { color: var(--blue); font-weight: 700; text-decoration: underline; text-underline-offset: 3px; }
.form-foot a:hover { color: var(--primary); }

/* 找回密码步骤条 */
.steps { display: flex; align-items: center; gap: 6px; margin-bottom: 22px; }
.step { display: flex; align-items: center; gap: 7px; font-size: 13px; font-weight: 700; color: var(--ink-soft); }
.step__no {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: var(--bw) solid var(--ink);
  display: grid;
  place-items: center;
  font-size: 13px;
  background: var(--white);
}
.step.done .step__no { background: var(--green); color: #fff; }
.step.current { color: var(--ink); }
.step.current .step__no { background: var(--yellow); box-shadow: 2px 2px 0 var(--ink); }
.step-line { flex: 1; height: 2px; background: var(--ink); opacity: .25; min-width: 14px; }

.question-box {
  background: var(--paper-deep);
  border: 1.5px dashed var(--ink);
  border-radius: var(--r-s);
  padding: 13px 16px;
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 18px;
  display: flex;
  gap: 10px;
  align-items: center;
}
.question-box svg { width: 20px; height: 20px; flex-shrink: 0; }

.banned-tip {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  background: #FDEBEB;
  border: var(--bw) solid var(--red);
  border-radius: var(--r-s);
  padding: 13px 15px;
  font-size: 13.5px;
  margin-top: 20px;
  color: #8C1D1D;
}
.banned-tip svg { width: 20px; height: 20px; flex-shrink: 0; stroke: var(--red); margin-top: 2px; }
</style>
