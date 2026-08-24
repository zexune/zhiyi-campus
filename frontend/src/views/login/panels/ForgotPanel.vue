<template>
  <div class="auth-panel">
    <h2>找回密码</h2>
    <p class="sub">回答密保问题，重置你的密码</p>

    <div class="steps" aria-label="找回密码步骤">
      <span class="step" :class="stepClass(1)">
        <span class="step__no">{{ step > 1 ? '✓' : '1' }}</span>
        确认账号
      </span>
      <span class="step-line"></span>
      <span class="step" :class="stepClass(2)">
        <span class="step__no">{{ step > 2 ? '✓' : '2' }}</span>
        答密保
      </span>
      <span class="step-line"></span>
      <span class="step" :class="stepClass(3)">
        <span class="step__no">3</span>
        设新密码
      </span>
    </div>

    <!-- 步骤1：选择学校并输入学号 -->
    <el-form v-if="step === 1" ref="step1FormRef" :model="form" :rules="step1Rules" @submit.prevent="handleFetchQuestion">
      <el-form-item prop="schoolId" class="field">
        <label for="f-school">所属学校</label>
        <AppSelect
          id="f-school"
          v-model="form.schoolId"
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
      </el-form-item>
      <el-form-item prop="studentId" class="field">
        <label for="f-sid">学号</label>
        <input id="f-sid" v-model.trim="form.studentId" class="input" type="text" placeholder="请输入注册时的学号" autocomplete="username" />
      </el-form-item>
      <button class="btn btn--green btn--lg btn--block" type="submit" :disabled="loading || schoolsLoading">下一步</button>
    </el-form>

    <!-- 步骤2：答密保 -->
    <template v-if="step === 2">
      <div class="question-box">
        <svg viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="9" />
          <path d="M9.1 9a3 3 0 0 1 5.8 1c0 2-3 2.5-3 4M12 17.5h.01" />
        </svg>
        {{ securityQuestion }}
      </div>
      <el-form :model="form" @submit.prevent="step = 3">
        <el-form-item prop="securityAnswer" class="field" :rules="[{ required: true, message: '请输入密保答案', trigger: 'blur' }]">
          <label for="f-answer">密保答案</label>
          <input id="f-answer" v-model="form.securityAnswer" class="input" type="text" placeholder="不区分大小写、忽略首尾空格" />
        </el-form-item>
        <button class="btn btn--green btn--lg btn--block" type="submit" :disabled="!form.securityAnswer.trim()">验证并进入下一步</button>
      </el-form>
    </template>

    <!-- 步骤3：设新密码（答案随新密码一并提交后端验证） -->
    <el-form v-if="step === 3" ref="step3FormRef" :model="form" :rules="step3Rules" @submit.prevent="handleReset">
      <el-form-item prop="newPassword" class="field">
        <label for="f-pw">新密码</label>
        <input id="f-pw" v-model="form.newPassword" class="input" type="password" placeholder="不少于 6 位" autocomplete="new-password" />
      </el-form-item>
      <el-form-item prop="confirmPassword" class="field">
        <label for="f-pw2">确认新密码</label>
        <input id="f-pw2" v-model="form.confirmPassword" class="input" type="password" placeholder="再输入一次" autocomplete="new-password" />
      </el-form-item>
      <button class="btn btn--green btn--lg btn--block" type="submit" :disabled="loading">
        {{ loading ? '提交中…' : '重置密码' }}
      </button>
    </el-form>
    <p class="hint" style="margin-top: 14px; text-align: center">重置成功后，所有设备都需要重新登录</p>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import AppSelect from '@/components/common/AppSelect.vue'
import { getSecurityQuestion, resetPassword } from '@/api/auth'
import { readSavedSchoolId, rememberSchoolId, useSchoolOptions } from '@/composables/useSchoolOptions'
import { validateForm } from '@/utils/formValidate'
import '../auth.css'

/**
 * 找回密码面板（三步：确认账号 → 答密保 → 设新密码）。
 * 重置成功后 emit('reset-done', { schoolId, studentId })，由 AuthPage 回填登录面板。
 */
const emit = defineEmits<{
  (e: 'reset-done', payload: { schoolId: number | null; studentId: string }): void
}>()

const { schoolOptions, schoolsLoading, schoolsError, fetchSchools, syncForm } = useSchoolOptions()

const step = ref(1)
const step1FormRef = ref(null)
const step3FormRef = ref(null)
const loading = ref(false)
const securityQuestion = ref('')

/** 找回密码表单（schoolId 允许未选，步骤1 rules 强制必填） */
interface ForgotFormState {
  schoolId: number | null
  studentId: string
  securityAnswer: string
  newPassword: string
  confirmPassword: string
}

const form = reactive<ForgotFormState>({
  schoolId: readSavedSchoolId(),
  studentId: '',
  securityAnswer: '',
  newPassword: '',
  confirmPassword: ''
})
syncForm(form)

const step1Rules = {
  schoolId: [{ required: true, message: '请选择注册时的学校', trigger: 'change' }],
  studentId: [{ required: true, message: '请输入学号', trigger: 'blur' }]
}

const step3Rules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码不少于 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再输入一次新密码', trigger: 'blur' },
    { validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => (value === form.newPassword ? callback() : callback(new Error('两次输入的密码不一致'))), trigger: 'blur' }
  ]
}

function stepClass(n: number) {
  return { done: step.value > n, current: step.value === n }
}

async function handleFetchQuestion() {
  // 入口同步互斥：await validateForm 存在异步窗口，重复提交可能在 loading 置位前穿透
  if (loading.value) return
  loading.value = true
  try {
    const valid = await validateForm(step1FormRef)
    if (!valid) return
    // 步骤1 rules 已强制选择学校；此处仅类型收窄，异常空值仍按原样提交由后端校验兜底
    const res = await getSecurityQuestion(form.schoolId as number, form.studentId)
    securityQuestion.value = res.data.question
    step.value = 2
  } catch {
    // 提示由 request.js 统一处理
  } finally {
    loading.value = false
  }
}

async function handleReset() {
  if (loading.value) return
  loading.value = true
  try {
    const valid = await validateForm(step3FormRef)
    if (!valid) return
    await resetPassword({ ...form, schoolId: form.schoolId as number })
    const { schoolId, studentId } = form
    rememberSchoolId(schoolId)
    ElMessage.success('密码重置成功，请用新密码登录')
    step.value = 1
    Object.assign(form, { schoolId, studentId: '', securityAnswer: '', newPassword: '', confirmPassword: '' })
    emit('reset-done', { schoolId, studentId })
  } catch (e) {
    // 答案错误时退回步骤2重新作答
    if (String((e as Error).message || '').includes('密保')) {
      step.value = 2
      form.securityAnswer = ''
    }
  } finally {
    loading.value = false
  }
}

/** AuthPage 切换到本面板时继承登录面板的学校选择 */
function adoptSchoolId(schoolId: number | null) {
  if (!form.schoolId && schoolId) {
    form.schoolId = schoolId
  }
}

defineExpose({ adoptSchoolId })
</script>
