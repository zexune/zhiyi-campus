import { computed, ref, watchEffect } from 'vue'
import { getSchools } from '@/api/auth'

/**
 * 学校下拉共享状态（登录 / 注册 / 找回密码三个面板复用同一份数据与请求）。
 *
 * 模块级单例：同一认证页内多次调用 useSchoolOptions() 只共享一次加载；
 * “记住的学校”持久化在 localStorage，供下次进入时回填。
 */

const LAST_SCHOOL_KEY = 'zhiyi:last-school-id'

const schools = ref([])
const schoolsLoading = ref(false)
const schoolsError = ref(false)

export function readSavedSchoolId() {
  const value = Number(localStorage.getItem(LAST_SCHOOL_KEY))
  return Number.isSafeInteger(value) && value > 0 ? value : null
}

export function rememberSchoolId(schoolId) {
  if (schoolId) localStorage.setItem(LAST_SCHOOL_KEY, String(schoolId))
}

async function fetchSchools() {
  schoolsLoading.value = true
  schoolsError.value = false
  try {
    const res = await getSchools()
    schools.value = res.data || []
  } catch {
    schools.value = []
    schoolsError.value = true
  } finally {
    schoolsLoading.value = false
  }
}

export function useSchoolOptions() {
  const schoolOptions = computed(() => schools.value.map((school) => ({ label: school.name, value: school.id })))

  /**
   * 把某个表单的 schoolId 绑定到共享列表：
   * 列表就绪后校正当前的选择（记忆学校优先），列表加载失败时清空选择。
   * 需在组件 setup 中调用。
   */
  function syncForm(form) {
    watchEffect(() => {
      if (schoolsLoading.value) return
      if (!schools.value.length) {
        if (schoolsError.value) form.schoolId = null
        return
      }
      if (form.schoolId != null && schools.value.some((school) => school.id === form.schoolId)) return
      const remembered = readSavedSchoolId()
      form.schoolId = remembered != null && schools.value.some((school) => school.id === remembered) ? remembered : null
    })
  }

  return { schools, schoolOptions, schoolsLoading, schoolsError, fetchSchools, syncForm }
}
