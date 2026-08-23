<template>
  <div class="auth-panel">
    <h2>欢迎回来</h2>
    <p class="sub">选择学校并用学号登录，继续你的淘货之旅</p>
    <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleLogin">
      <el-form-item prop="schoolId" class="field">
        <label for="l-school">所属学校</label>
        <AppSelect
          id="l-school"
          v-model="form.schoolId"
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
      </el-form-item>
      <el-form-item prop="studentId" class="field">
        <label for="l-sid">学号</label>
        <input id="l-sid" v-model.trim="form.studentId" class="input" type="text" placeholder="例如：20240101234" autocomplete="username" />
      </el-form-item>
      <el-form-item prop="password" class="field">
        <label for="l-pw">密码</label>
        <input id="l-pw" v-model="form.password" class="input" type="password" placeholder="请输入密码" autocomplete="current-password" />
      </el-form-item>
      <div class="form-foot">
        <a href="#" @click.prevent="emit('switch-tab', 'forgot')">忘记密码？</a>
      </div>
      <button class="btn btn--primary btn--lg btn--block" type="submit" :disabled="loading || schoolsLoading">
        {{ loading ? '登录中…' : '登录' }}
      </button>
      <p class="admin-entry">
        平台管理员？
        <router-link :to="ROUTE_PATH.ADMIN_LOGIN">进入独立管理后台</router-link>
      </p>
    </el-form>
    <div v-if="banMessage" class="banned-tip" role="alert">
      <svg viewBox="0 0 24 24" fill="none" stroke-width="2.2" stroke-linecap="round">
        <circle cx="12" cy="12" r="9" />
        <path d="M12 8v4M12 16h.01" />
      </svg>
      <span>
        <b>账户被封禁？</b>
        {{ banMessage }}临时封禁到期后会自动恢复。
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppSelect from '@/components/common/AppSelect.vue'
import { login } from '@/api/auth'
import { rememberSchoolId, useSchoolOptions } from '@/composables/useSchoolOptions'
import { useUserStore } from '@/stores/user'
import { ROUTE_PATH } from '@/constants/routes'
import { validateForm } from '@/utils/formValidate'
import '../auth.css'

/**
 * 登录面板 —— 学校 + 学号 + 密码；封禁账号在表单下方给出解释性提示。
 * 校验为声明式 rules，提交时统一 validate。
 */
const emit = defineEmits<{
  (e: 'switch-tab', value: 'forgot'): void
}>()

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const { schoolOptions, schoolsLoading, schoolsError, fetchSchools, syncForm } = useSchoolOptions()

const formRef = ref(null)
const loading = ref(false)
const banMessage = ref('')

/** 登录表单（schoolId 允许未选，rules 强制必填） */
interface LoginFormState {
  schoolId: number | null
  studentId: string
  password: string
}

const form = reactive<LoginFormState>({ schoolId: null, studentId: '', password: '' })
syncForm(form)

const rules = {
  schoolId: [{ required: true, message: '请选择所属学校', trigger: 'change' }],
  studentId: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await validateForm(formRef)
  if (!valid) return
  loading.value = true
  try {
    // rules 已强制选择学校；此处仅类型收窄，异常空值仍按原样提交由后端校验兜底
    const res = await login({ ...form, schoolId: form.schoolId as number })
    rememberSchoolId(form.schoolId)
    userStore.setLogin(res.data)
    ElMessage.success('登录成功')
    // redirect 查询参数此处必为单值字符串（多值数组属退化场景）
    router.push((route.query.redirect as string) || ROUTE_PATH.HOME)
  } catch (e) {
    if (String((e as Error).message || '').includes('封禁')) {
      banMessage.value = (e as Error).message
    }
  } finally {
    loading.value = false
  }
}

/** 找回密码成功后回填学校与学号（AuthPage 协调调用） */
function prefill({ schoolId, studentId }: { schoolId: number | null; studentId: string }) {
  form.schoolId = schoolId
  form.studentId = studentId
}

function clearBanMessage() {
  banMessage.value = ''
}

/** 供切到找回密码面板时继承学校选择 */
function currentSchoolId() {
  return form.schoolId
}

defineExpose({ prefill, clearBanMessage, currentSchoolId })
</script>
