<template>
  <div class="auth-panel">
    <h2>创建账号</h2>
    <p class="sub">仅限本校学生，学号即账号</p>

    <div class="steps" aria-label="注册步骤">
      <span class="step" :class="stepClass(1)">
        <span class="step__no">{{ step > 1 ? '✓' : '1' }}</span>
        账号信息
      </span>
      <span class="step-line"></span>
      <span class="step" :class="stepClass(2)">
        <span class="step__no">2</span>
        密保设置
      </span>
    </div>

    <!-- 步骤1：学号 / 昵称 / 密码 / 确认密码（两列紧凑排布，与登录面板同高） -->
    <el-form v-if="step === 1" ref="step1FormRef" :model="form" :rules="step1Rules" @submit.prevent="handleNext">
      <div class="field-row">
        <el-form-item prop="studentId" class="field">
          <label for="r-sid">
            学号
            <span class="req">*</span>
          </label>
          <input id="r-sid" v-model.trim="form.studentId" class="input" type="text" placeholder="唯一登录凭证" autocomplete="username" />
        </el-form-item>
        <el-form-item class="field">
          <label for="r-nick">昵称</label>
          <input id="r-nick" v-model.trim="form.nickname" class="input" type="text" :placeholder="defaultNickname" />
        </el-form-item>
      </div>
      <div class="field-row">
        <el-form-item prop="password" class="field">
          <label for="r-pw">
            密码
            <span class="req">*</span>
          </label>
          <input id="r-pw" v-model="form.password" class="input" type="password" placeholder="不少于 6 位" autocomplete="new-password" />
          <div class="pw-strength" aria-hidden="true">
            <i v-for="n in 4" :key="n" :class="{ on: passwordStrength >= n }"></i>
          </div>
        </el-form-item>
        <el-form-item prop="confirmPassword" class="field">
          <label for="r-pw2">
            确认密码
            <span class="req">*</span>
          </label>
          <input id="r-pw2" v-model="form.confirmPassword" class="input" type="password" placeholder="再输入一次" autocomplete="new-password" />
        </el-form-item>
      </div>
      <el-form-item prop="schoolId" class="field">
        <label for="r-school">
          所属学校
          <span class="req">*</span>
        </label>
        <AppSelect
          id="r-school"
          v-model="form.schoolId"
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
      </el-form-item>
      <button class="btn btn--primary btn--lg btn--block" type="submit" :disabled="schoolsLoading">下一步</button>
    </el-form>

    <!-- 步骤2：密保问题（自由输入 + 随机填入）/ 密保答案 / 手机号 -->
    <el-form v-if="step === 2" ref="step2FormRef" :model="form" :rules="step2Rules" @submit.prevent="handleRegister">
      <el-form-item prop="securityQuestion" class="field">
        <label for="r-q">
          密保问题
          <span class="req">*</span>
        </label>
        <div class="question-input">
          <input id="r-q" v-model.trim="form.securityQuestion" class="input" type="text" maxlength="50" placeholder="自己出一道只有你知道答案的问题" />
          <button class="btn random-btn" type="button" title="从系统预设中随机抽一个" @click="randomQuestion">
            <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="3" width="18" height="18" rx="4" />
              <circle cx="8.5" cy="8.5" r="1.4" fill="currentColor" stroke="none" />
              <circle cx="15.5" cy="8.5" r="1.4" fill="currentColor" stroke="none" />
              <circle cx="12" cy="12" r="1.4" fill="currentColor" stroke="none" />
              <circle cx="8.5" cy="15.5" r="1.4" fill="currentColor" stroke="none" />
              <circle cx="15.5" cy="15.5" r="1.4" fill="currentColor" stroke="none" />
            </svg>
            随机
          </button>
        </div>
      </el-form-item>
      <el-form-item prop="securityAnswer" class="field">
        <label for="r-a">
          密保答案
          <span class="req">*</span>
        </label>
        <input id="r-a" v-model="form.securityAnswer" class="input" type="text" placeholder="不区分大小写" />
      </el-form-item>
      <p class="hint reg-hint">忘记密码时凭密保找回，请务必记住答案；拿不准就点「随机」用系统预设的问题</p>
      <div class="reg-actions">
        <button class="btn btn--lg" type="button" @click="step = 1">上一步</button>
        <button class="btn btn--primary btn--lg reg-actions__submit" type="submit" :disabled="loading">
          {{ loading ? '注册中…' : '注册并开始淘货' }}
        </button>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppSelect from '@/components/common/AppSelect.vue'
import { getSecurityQuestions, register } from '@/api/auth'
import { readSavedSchoolId, rememberSchoolId, useSchoolOptions } from '@/composables/useSchoolOptions'
import { useUserStore } from '@/stores/user'
import { ROUTE_PATH } from '@/constants/routes'
import { validateForm } from '@/utils/formValidate'
import '../auth.css'

/**
 * 注册面板（两步走：账号信息 → 密保设置）—— 校验全部声明在 rules，
 * 学校邮箱按所选学校的邮箱域名后缀做自定义校验（A2/A3）。
 */
const router = useRouter()
const userStore = useUserStore()

const { schoolOptions, schoolsLoading, schoolsError, fetchSchools, syncForm } = useSchoolOptions()

const step = ref(1)
const step1FormRef = ref(null)
const step2FormRef = ref(null)
const loading = ref(false)

/** 注册表单（两步共用；schoolId 允许未选，步骤1 rules 强制必填）。学校邮箱/手机号注册后到个人设置填写 */
interface RegisterFormState {
  studentId: string
  password: string
  confirmPassword: string
  nickname: string
  schoolId: number | null
  securityQuestion: string
  securityAnswer: string
}

const form = reactive<RegisterFormState>({
  studentId: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  schoolId: readSavedSchoolId(),
  securityQuestion: '',
  securityAnswer: ''
})
syncForm(form)

const defaultNickname = computed(() => {
  const sid = form.studentId
  return sid.length >= 4 ? `同学_${sid.slice(-4)}` : '默认生成，可修改'
})

const passwordStrength = computed(() => {
  const p = form.password
  if (!p) return 0
  let s = 0
  if (p.length >= 6) s++
  if (p.length >= 10) s++
  if (/[a-zA-Z]/.test(p) && /\d/.test(p)) s++
  if (/[^a-zA-Z0-9]/.test(p)) s++
  return s
})

const step1Rules = {
  studentId: [{ required: true, message: '请填写学号', trigger: 'blur' }],
  password: [
    { required: true, message: '请填写密码', trigger: 'blur' },
    { min: 6, message: '密码不少于 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再输入一次密码', trigger: 'blur' },
    { validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => (value === form.password ? callback() : callback(new Error('两次输入的密码不一致'))), trigger: 'blur' }
  ],
  schoolId: [{ required: true, message: '请选择所属学校', trigger: 'change' }]
}

const step2Rules = {
  securityQuestion: [{ required: true, message: '请填写密保问题（可点「随机」快速选一个）', trigger: 'blur' }],
  securityAnswer: [{ required: true, message: '请填写密保答案', trigger: 'blur' }]
}

// —— 预设密保问题（随机按钮数据源）——
const questions = ref<string[]>([])
let lastRandomIndex = -1

function randomQuestion() {
  const pool = questions.value
  if (!pool.length) return
  let idx: number
  if (pool.length === 1) {
    idx = 0
  } else {
    do {
      idx = Math.floor(Math.random() * pool.length)
    } while (idx === lastRandomIndex)
  }
  lastRandomIndex = idx
  form.securityQuestion = pool[idx]
}

function stepClass(n: number) {
  return { done: step.value > n, current: step.value === n }
}

async function handleNext() {
  if (schoolsLoading.value) {
    ElMessage.warning('学校列表仍在加载，请稍候')
    return
  }
  if (schoolsError.value) {
    ElMessage.warning('请先重新加载学校列表')
    return
  }
  const valid = await validateForm(step1FormRef)
  if (!valid) return
  step.value = 2
}

async function handleRegister() {
  // 入口同步互斥：await validateForm 存在异步窗口，重复提交可能在 loading 置位前穿透
  if (loading.value) return
  loading.value = true
  try {
    const valid = await validateForm(step2FormRef)
    if (!valid) return
    const res = await register({
      studentId: form.studentId,
      password: form.password,
      confirmPassword: form.confirmPassword,
      nickname: form.nickname,
      // 步骤1 rules 已强制选择学校；此处仅类型收窄，异常空值仍按原样提交由后端校验兜底
      schoolId: form.schoolId as number,
      // 学校邮箱已从注册流程移除，注册后到个人设置填写
      schoolEmail: null,
      securityQuestion: form.securityQuestion,
      securityAnswer: form.securityAnswer,
      // 手机号已从注册流程移除，注册后到个人设置填写
      phone: ''
    })
    rememberSchoolId(form.schoolId)
    userStore.setLogin(res.data)
    ElMessage.success('注册成功，欢迎加入智易校园！')
    router.push(ROUTE_PATH.HOME)
  } catch (e) {
    // 学号已注册等账号类错误发生在步骤1的字段上，退回步骤1便于修改
    if (String((e as Error).message || '').includes('学号')) {
      step.value = 1
    }
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const res = await getSecurityQuestions()
    questions.value = res.data
  } catch {
    // 兜底：接口异常时用前端预设，保证「随机」按钮可用
    questions.value = ['你的小学名称是？', '你最喜欢的老师姓什么？', '你的出生地是哪个城市？', '你第一只宠物叫什么？']
  }
})
</script>
