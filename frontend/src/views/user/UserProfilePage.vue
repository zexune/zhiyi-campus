<template>
  <DefaultLayout>
    <div class="profile-page">
      <h1 class="page-title">个人中心</h1>

      <div v-if="user" class="profile-grid">
        <!-- 左：身份卡 + 经验记录 -->
        <div class="left-col">
          <section class="card id-card">
            <i class="tape tape--center" aria-hidden="true"></i>
            <div class="id-card__head">
              <div class="id-card__avatar">
                <UserAvatar :nickname="user.nickname" :user-id="user.id" size="l" :src="user.avatar" />
                <!-- 更换头像：点击弹出文件选择，单文件替换语义（非多图列表）；客户端先预校验再上传。
                     触发器用 role="button" 而非 label：避免原生 label→input 转发与 openAvatarPicker 的
                     input.click() 叠加导致文件选择器被触发两次。 -->
                <div
                  class="avatar-edit"
                  role="button"
                  tabindex="0"
                  aria-label="更换头像"
                  :aria-disabled="avatarUploading"
                  @click="openAvatarPicker"
                  @keydown.enter.prevent="openAvatarPicker"
                  @keydown.space.prevent="openAvatarPicker"
                >
                  <template v-if="avatarUploading">
                    <span class="avatar-edit__loading" aria-label="上传中">上传中…</span>
                  </template>
                  <template v-else>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2Z" />
                      <circle cx="12" cy="13" r="4" />
                    </svg>
                    更换
                  </template>
                </div>
                <input ref="avatarInput" class="avatar-file" type="file" accept="image/jpeg,image/png,image/webp" :disabled="avatarUploading" tabindex="-1" @change="onAvatarChange" />
              </div>
              <div>
                <div class="id-card__name">
                  {{ user.nickname }}
                  <LevelBadge :level="user.level" show-title :title="user.levelTitle || ''" />
                </div>
                <div class="muted">学号：{{ user.studentId }}</div>
                <div class="school-line">
                  <span class="muted">
                    <svg class="school-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="m4 6 8-4 8 4v5c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10Z" />
                      <path d="M9 13h6M12 10v6" />
                    </svg>
                    {{ user.schoolName || '未选择学校' }}
                  </span>
                </div>
                <div class="muted">注册于 {{ formatDateTime(user.createdAt) }}</div>
              </div>
            </div>

            <!-- 等级进度条（需求 1.5 个人主页展示） -->
            <div class="level-progress">
              <div class="level-progress__label">
                <b>Lv.{{ user.level }} {{ user.levelTitle }}</b>
                <span v-if="user.nextLevelExp" class="muted">{{ user.exp }} / {{ user.nextLevelExp }} EXP</span>
                <span v-else class="muted">已满级 · {{ user.exp }} EXP</span>
              </div>
              <div class="level-progress__track">
                <div class="level-progress__fill" :style="{ width: progressPercent + '%' }"></div>
              </div>
              <p class="hint">完成订单 +50 EXP</p>
            </div>

            <div class="wallet-line">
              <span>钱包余额</span>
              <PriceTag :value="user.walletBalance" font-size="24px" />
            </div>

            <div class="quick-links">
              <router-link :to="ROUTE_PATH.MY_ITEMS" class="btn btn--sm">我的发布</router-link>
              <router-link :to="ROUTE_PATH.MY_FAVORITES" class="btn btn--sm">我的收藏</router-link>
              <router-link :to="ROUTE_PATH.WALLET" class="btn btn--sm btn--green">去钱包</router-link>
            </div>
          </section>

          <section class="card panel">
            <h3>经验值记录</h3>
            <template v-if="expLogs.length">
              <ul class="exp-list">
                <li v-for="log in expLogs" :key="log.id">
                  <span class="exp-delta" :class="log.delta >= 0 ? 'plus' : 'minus'">{{ log.delta >= 0 ? '+' : '' }}{{ log.delta }}</span>
                  <span class="exp-reason">{{ log.reason }}</span>
                  <span class="muted exp-time">{{ formatDate(log.createdAt) }}</span>
                </li>
              </ul>
              <el-pagination v-if="expTotal > expPageSize" v-model:current-page="expPage" :page-size="expPageSize" :total="expTotal" layout="prev, pager, next" @current-change="fetchExpLogs" />
            </template>
            <p v-else class="muted empty-tip">还没有经验记录，完成一笔交易即可获得 +50 EXP</p>
          </section>

          <section class="card panel">
            <h3>信誉雷达</h3>
            <ReputationRadar v-if="reputation" :reputation="reputation" />
            <p v-else class="muted empty-tip">信誉数据加载中…</p>
          </section>
        </div>

        <!-- 右：资料编辑 + 账号安全 -->
        <div class="right-col">
          <section class="card panel">
            <h3>编辑资料</h3>
            <!-- M3 乐观并发冲突提示：保留用户输入，展示服务端最新资料并要求确认 -->
            <div v-if="conflictProfile" class="conflict-banner" role="alert">
              <p>
                资料已被其他设备修改（最新昵称：{{ conflictProfile.nickname }}
                <template v-if="conflictProfile.schoolName">，学校：{{ conflictProfile.schoolName }}</template>
                ）。您当前的输入不会被覆盖。
              </p>
              <div class="conflict-actions">
                <button type="button" class="btn btn--sm" @click="applyConflictProfile">载入最新资料</button>
                <button type="button" class="btn btn--sm btn--ghost" @click="conflictProfile = null">保留我的输入</button>
              </div>
            </div>
            <form @submit.prevent="handleSave">
              <div class="field">
                <label for="p-nick">昵称</label>
                <input id="p-nick" v-model.trim="editForm.nickname" class="input" type="text" maxlength="50" />
              </div>
              <div class="field">
                <label for="p-school">所属学校</label>
                <AppSelect id="p-school" v-model="editForm.schoolId" :options="schoolOptions" placeholder="请选择你当前就读的学校" aria-label="所属学校" />
              </div>
              <div class="field">
                <label for="p-email">学校邮箱</label>
                <input id="p-email" v-model.trim="editForm.schoolEmail" class="input" type="email" :placeholder="schoolEmailPlaceholder" autocomplete="email" />
              </div>
              <div class="field">
                <label for="p-phone">手机号</label>
                <input id="p-phone" v-model.trim="editForm.phone" class="input" type="tel" placeholder="仅用于接收通知" />
              </div>
              <div class="field-row">
                <div class="field">
                  <label for="p-college">学院</label>
                  <input id="p-college" v-model.trim="editForm.college" class="input" type="text" maxlength="50" placeholder="如：计算机学院" />
                </div>
                <div class="field">
                  <label for="p-grade">年级</label>
                  <input id="p-grade" v-model.trim="editForm.grade" class="input" type="text" maxlength="10" placeholder="如：2024级" />
                </div>
              </div>
              <div class="field-row">
                <div class="field">
                  <label for="p-campus">校区</label>
                  <input id="p-campus" v-model.trim="editForm.campus" class="input" type="text" maxlength="50" placeholder="如：宝山校区" />
                </div>
                <div class="field">
                  <label for="p-dorm">宿舍楼</label>
                  <input id="p-dorm" v-model.trim="editForm.dormitory" class="input" type="text" maxlength="50" placeholder="如：南区3号楼" />
                </div>
              </div>
              <button class="btn btn--primary" type="submit" :disabled="saving">
                {{ saving ? '保存中…' : '保存修改' }}
              </button>
            </form>
          </section>

          <!-- 账号安全：修改密码 + 注销账号 -->
          <section class="card panel">
            <h3>账号安全</h3>

            <div class="sec-block">
              <div class="sec-block__head">
                <div>
                  <b>修改密码</b>
                  <p class="hint">修改成功后所有设备需重新登录</p>
                </div>
                <button class="btn btn--sm" @click="pwVisible = !pwVisible">
                  {{ pwVisible ? '收起' : '修改' }}
                </button>
              </div>
              <form v-if="pwVisible" class="sec-form" @submit.prevent="handleChangePassword">
                <div class="field">
                  <label for="cp-old">原密码</label>
                  <input id="cp-old" v-model="pwForm.oldPassword" class="input" type="password" autocomplete="current-password" />
                </div>
                <div class="field">
                  <label for="cp-new">新密码</label>
                  <input id="cp-new" v-model="pwForm.newPassword" class="input" type="password" placeholder="不少于 6 位，且不能与原密码相同" autocomplete="new-password" />
                  <p v-if="pwForm.newPassword && pwForm.newPassword === pwForm.oldPassword" class="error-msg">新密码不能与原密码相同</p>
                </div>
                <div class="field">
                  <label for="cp-new2">确认新密码</label>
                  <input id="cp-new2" v-model="pwForm.confirmPassword" class="input" type="password" autocomplete="new-password" />
                  <p v-if="pwForm.confirmPassword && pwForm.confirmPassword !== pwForm.newPassword" class="error-msg">两次输入的密码不一致</p>
                </div>
                <button class="btn btn--primary btn--sm" type="submit" :disabled="changingPw">
                  {{ changingPw ? '提交中…' : '确认修改' }}
                </button>
              </form>
            </div>

            <div class="sec-block">
              <div class="sec-block__head">
                <div>
                  <b class="danger-text">注销账号</b>
                  <p class="hint">注销后学号将被占用且无法恢复；在售商品将自动下架。</p>
                </div>
                <button class="btn btn--sm btn--danger" @click="openCancel">注销</button>
              </div>
            </div>
          </section>
        </div>
      </div>

      <!-- 注销确认弹窗（密码二次确认） -->
      <el-dialog
        v-model="cancelVisible"
        class="app-dialog"
        modal-class="app-modal"
        title="注销账号"
        width="420px"
        append-to-body
        align-center
        :show-close="!cancelling"
        :close-on-click-modal="!cancelling"
        :close-on-press-escape="!cancelling"
      >
        <p class="cancel-warn">
          此操作不可自助恢复：注销后立即退出登录，无法再使用该账号交易。
          <br />
          钱包余额
          <b>¥{{ user?.walletBalance ?? 0 }}</b>
          将随账号冻结，请确认已处理完毕。
        </p>
        <div class="field">
          <label for="ca-pw">输入登录密码以确认</label>
          <input id="ca-pw" v-model="cancelPassword" class="input" type="password" autocomplete="current-password" />
        </div>
        <template #footer>
          <button class="btn btn--sm" @click="cancelVisible = false">我再想想</button>
          <button class="btn btn--sm btn--danger" :disabled="cancelling || !cancelPassword" @click="handleCancelAccount">
            {{ cancelling ? '注销中…' : '确认注销' }}
          </button>
        </template>
      </el-dialog>
    </div>
  </DefaultLayout>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppSelect from '@/components/common/AppSelect.vue'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import LevelBadge from '@/components/common/LevelBadge.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import PriceTag from '@/components/common/PriceTag.vue'
import ReputationRadar from '@/components/common/ReputationRadar.vue'
import { updateProfile, getExpLog, changePassword, cancelAccount, getUserReputation, getSchools, uploadUserAvatar } from '@/api/auth'
import { ApiError } from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { ROUTE_PATH } from '@/constants/routes'
import { formatDate, formatDateTime } from '@/utils/format'
import { usePagedList } from '@/composables/usePagedList'
import type { ReputationVo } from '@/utils/reputation'
import type { ExpLog, School, UserProfile } from '@/types/models'

/**
 * 个人中心（模块一 1.5）—— 等级进度条 + 经验记录 + 资料编辑 + 账号安全（改密/注销）
 */
const router = useRouter()
const userStore = useUserStore()
// 本页展示完整资料字段；localStorage 恢复的摘要（仅 id/nickname/role）在 fetchProfile 后升级为 UserProfile
const user = computed((): UserProfile | null => userStore.user as UserProfile | null)

/** 编辑资料表单（schoolId 允许未选择，保存前校验必填） */
interface ProfileEditForm {
  nickname: string
  phone: string
  schoolId: number | null
  schoolEmail: string
  campus: string
  college: string
  grade: string
  dormitory: string
}

const saving = ref(false)
const editForm = reactive<ProfileEditForm>({
  nickname: '',
  phone: '',
  schoolId: null,
  schoolEmail: '',
  campus: '',
  college: '',
  grade: '',
  dormitory: ''
})
/** 资料乐观并发版本（M3）：加载时保存，提交时携带；409 冲突时以服务端最新版本更新 */
const profileVersion = ref<number | null>(null)
/** 冲突时展示的服务端最新资料（用户确认合并用） */
const conflictProfile = ref<UserProfile | null>(null)
const schools = ref<School[]>([])
const schoolOptions = computed(() => schools.value.map((school) => ({ label: school.name, value: school.id })))
const selectedSchool = computed(() => schools.value.find((school) => school.id === editForm.schoolId) || null)
const schoolEmailPlaceholder = computed(() => (selectedSchool.value?.emailDomain ? `学号${selectedSchool.value.emailDomain}` : '选择学校后填写对应学校邮箱'))

// 信誉雷达（A6）
const reputation = ref<ReputationVo | null>(null)

// 经验流水走统一分页状态机：翻页竞态中乱序返回的旧响应由代数守卫丢弃（F1）
const { records: expLogs, currentPage: expPage, pageSize: expPageSize, total: expTotal, fetchList: fetchExpLogs } = usePagedList<ExpLog>(getExpLog)

const progressPercent = computed(() => {
  const u = user.value
  if (!u) return 0
  if (!u.nextLevelExp) return 100 // 满级
  // 后端随完整档案返回当前等级经验基线；摘要恢复阶段无此字段，按 0 兜底
  const base = (u as UserProfile & { currentLevelBaseExp?: number | null }).currentLevelBaseExp ?? 0
  const span = u.nextLevelExp - base
  if (span <= 0) return 100
  return Math.min(100, Math.round((((u.exp ?? 0) - base) / span) * 100))
})

async function handleSave() {
  if (!editForm.nickname) {
    ElMessage.warning('昵称不能为空')
    return
  }
  if (!editForm.schoolId) {
    ElMessage.warning('请选择所属学校')
    return
  }
  const email = editForm.schoolEmail.trim().toLowerCase()
  if (email && !/^[^@\s]+@[^@\s]+$/.test(email)) {
    ElMessage.warning('学校邮箱格式不正确')
    return
  }
  const domain = selectedSchool.value?.emailDomain?.trim().toLowerCase()
  if (email && domain && !email.endsWith(domain)) {
    ElMessage.warning(`学校邮箱须使用 ${domain} 后缀`)
    return
  }
  saving.value = true
  try {
    // 前置校验已保证 schoolId 有值；显式覆盖以让收窄后的类型通过展开
    await updateProfile({ ...editForm, schoolId: editForm.schoolId, profileVersion: profileVersion.value ?? 0 })
    ElMessage.success('保存成功')
    conflictProfile.value = null
    const profile = await userStore.fetchProfile()
    fillEditForm(profile)
  } catch (error) {
    // 资料版本冲突（1010）：不覆盖用户输入，展示服务端最新资料并要求确认合并
    if (error instanceof ApiError && error.code === 1010) {
      const latest = (error.detail as UserProfile | null) || (await userStore.fetchProfile())
      conflictProfile.value = latest
      if (latest?.profileVersion !== undefined) profileVersion.value = latest.profileVersion
      ElMessage.warning('资料已被其他设备修改，请核对最新资料后重新保存')
    }
    // 其他错误提示由 request.ts 统一处理
  } finally {
    saving.value = false
  }
}

/** 用户确认采用服务端最新资料：回填表单（本地未保存的输入由用户自行调整） */
function applyConflictProfile() {
  if (!conflictProfile.value) return
  fillEditForm(conflictProfile.value)
  conflictProfile.value = null
  ElMessage.success('已载入最新资料，可修改后重新保存')
}

// —— 信誉雷达（A6）——
async function fetchReputation() {
  const uid = user.value?.id
  if (!uid) return
  try {
    const res = await getUserReputation(uid)
    reputation.value = res.data
  } catch {
    // 忽略，页面其余部分可用
  }
}

// —— 账号安全：修改密码 ——
const pwVisible = ref(false)
const changingPw = ref(false)
const pwForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

async function handleChangePassword() {
  if (!pwForm.oldPassword || !pwForm.newPassword) {
    ElMessage.warning('请填写原密码和新密码')
    return
  }
  if (pwForm.newPassword.length < 6) {
    ElMessage.warning('新密码不少于 6 位')
    return
  }
  if (pwForm.newPassword === pwForm.oldPassword) {
    ElMessage.warning('新密码不能与原密码相同')
    return
  }
  if (pwForm.newPassword !== pwForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  changingPw.value = true
  try {
    await changePassword({ ...pwForm })
    ElMessage.success('密码修改成功，请重新登录')
    // 等本地登录态清理完成后再导航，避免守卫读到残留登录态
    await userStore.logout()
    router.push(ROUTE_PATH.LOGIN)
  } catch {
    /* 提示由 request.js 处理 */
  } finally {
    changingPw.value = false
  }
}

// —— 账号安全：注销账号 ——
const cancelVisible = ref(false)
const cancelling = ref(false)
const cancelPassword = ref('')

function openCancel() {
  cancelPassword.value = ''
  cancelVisible.value = true
}

async function handleCancelAccount() {
  cancelling.value = true
  try {
    await cancelAccount({ password: cancelPassword.value })
    ElMessage.success('账号已注销，感谢使用智易校园')
    cancelVisible.value = false
    // 等本地登录态清理完成后再导航，避免守卫读到残留登录态
    await userStore.logout()
    router.push(ROUTE_PATH.LOGIN)
  } catch {
    /* 提示由 request.js 处理 */
  } finally {
    cancelling.value = false
  }
}

function fillEditForm(profile: UserProfile | null) {
  if (profile) {
    editForm.nickname = profile.nickname
    editForm.phone = profile.phone || ''
    editForm.schoolId = profile.schoolId ?? null
    editForm.schoolEmail = profile.schoolEmail || ''
    editForm.campus = profile.campus || ''
    editForm.college = profile.college || ''
    editForm.grade = profile.grade || ''
    editForm.dormitory = profile.dormitory || ''
    if (profile.profileVersion !== undefined) profileVersion.value = profile.profileVersion
  }
}

// —— 头像上传 / 恢复默认 ——
// 单文件替换语义：隐藏 file input + 客户端预校验（类型/大小），符合后端契约（jpg/jpeg/png/webp，≤2MB）
const avatarInput = ref<HTMLInputElement | null>(null)
const avatarUploading = ref(false)

/** 后端魔数校验的类型白名单（客户端先按 MIME + 扩展名拦截，魔数以服务端为准） */
const AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const AVATAR_MAX_SIZE = 2 * 1024 * 1024

function openAvatarPicker() {
  if (avatarUploading.value) return
  avatarInput.value?.click()
}

async function onAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 同一文件重复选择不触发 change：重置 input 以便再次选择同一文件
  input.value = ''
  if (!file || avatarUploading.value) return

  // 预校验：文件大小超限直接拒绝，避免无谓的上传往返
  if (file.size > AVATAR_MAX_SIZE) {
    ElMessage.error('头像图片不能超过 2MB')
    return
  }
  if (!AVATAR_TYPES.includes(file.type)) {
    ElMessage.error('头像仅支持 JPG / PNG / WebP 格式')
    return
  }
  // 魔数二次校验：MIME 可能被伪造（如 .jpeg 改名 .png），用文件头字节确认真实类型
  if (!(await hasAllowedMagic(file))) {
    ElMessage.error('头像文件内容与图片格式不符')
    return
  }
  // 可解码性校验：仅有合法文件头的残缺图片（如截断的 PNG）能过后端魔数校验，
  // 但浏览器无法解码渲染，上传后所有访问者只能看到文字头像回退——在源头拦截
  if (!(await canBrowserDecode(file))) {
    ElMessage.error('头像图片文件已损坏，无法显示，请换一张试试')
    return
  }

  avatarUploading.value = true
  try {
    const res = await uploadUserAvatar(file)
    applyAvatarProfile(res.data)
    ElMessage.success('头像已更新')
  } catch {
    // 其余错误提示由 request.ts 统一处理
  } finally {
    avatarUploading.value = false
  }
}

/** 读取文件头部幻数字节，确认是 JPEG/PNG/WebP 之一 */
function hasAllowedMagic(file: File): Promise<boolean> {
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.onload = () => {
      const bytes = new Uint8Array(reader.result as ArrayBuffer)
      // JPEG: FF D8 FF；PNG: 89 50 4E 47 0D 0A 1A 0A；WebP: RIFF....WEBP
      const isJpeg = bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff
      const isPng = bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47 && bytes[4] === 0x0d && bytes[5] === 0x0a && bytes[6] === 0x1a && bytes[7] === 0x0a
      const isWebp = bytes[0] === 0x52 && bytes[1] === 0x49 && bytes[2] === 0x46 && bytes[3] === 0x46 && bytes[8] === 0x57 && bytes[9] === 0x45 && bytes[10] === 0x42 && bytes[11] === 0x50
      resolve(isJpeg || isPng || isWebp)
    }
    reader.onerror = () => resolve(false)
    reader.readAsArrayBuffer(file.slice(0, 12))
  })
}

/** 让浏览器实际解码一次图片：截断/损坏文件（合法魔数但无完整图像数据）会触发 onerror */
function canBrowserDecode(file: File): Promise<boolean> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve(img.naturalWidth > 0 && img.naturalHeight > 0)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      resolve(false)
    }
    img.src = url
  })
}

/** 头像上传成功后：同步 store 与编辑表单，推进 profileVersion，避免后续保存资料 409 */
function applyAvatarProfile(profile: UserProfile | null) {
  if (!profile) return
  userStore.user = profile
  // 上传会推进 profileVersion：必须用返回的最新资料刷新表单中的版本，否则 PUT /profile 触发 1010 冲突
  if (profile.profileVersion !== undefined) profileVersion.value = profile.profileVersion
}

onMounted(async () => {
  const [profile] = await Promise.all([
    userStore.fetchProfile(),
    getSchools()
      .then((res) => {
        schools.value = res.data || []
      })
      .catch(() => {
        schools.value = []
      })
  ])
  fillEditForm(profile)
  fetchExpLogs()
  fetchReputation()
})
</script>

<style scoped>
.conflict-banner {
  margin-bottom: 14px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-left: 4px solid var(--yellow-deep, #d4a017);
  border-radius: 8px;
  background: var(--paper-deep);
  font-size: 13px;
}
.conflict-banner p {
  margin: 0 0 10px;
  line-height: 1.6;
}
.conflict-actions {
  display: flex;
  gap: 8px;
}

.profile-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.profile-grid {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: var(--spacing-lg);
  align-items: start;
}
@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
  .id-card {
    transform: none;
  }
}

.left-col {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.id-card {
  position: relative;
  padding: 24px;
  /* 校牌：像挂在布告栏上的学生证，微微倾斜（窄屏回正，见上方媒体查询） */
  transform: rotate(-1deg);
}
.id-card__head {
  display: flex;
  gap: 16px;
  align-items: center;
}

/* —— 头像卡片：图片/文字头像 + 更换/恢复默认 —— */
.id-card__avatar {
  position: relative;
  flex-shrink: 0;
}
.avatar-file {
  /* 隐藏的文件选择器，由 label 触发；保留可访问的焦点与键盘触发 */
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
.avatar-edit {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  gap: 2px;
  border-radius: 50%;
  background: rgba(38, 34, 28, 0.5);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  text-align: center;
  line-height: 1.1;
  opacity: 0;
  transition: opacity 0.15s ease;
  z-index: 1;
}
.avatar-edit svg {
  width: 18px;
  height: 18px;
}
.id-card__avatar:hover .avatar-edit,
.avatar-edit:focus-visible {
  opacity: 1;
}
.avatar-edit__loading {
  padding: 0 4px;
  font-size: 12px;
}
.avatar-edit:disabled {
  cursor: default;
  opacity: 0.4;
}
.id-card__name {
  font-family: var(--font-display);
  font-size: 22px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.level-progress {
  margin: 6px 0;
}
.level-progress__label {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  margin-bottom: 8px;
}
.level-progress__track {
  height: 14px;
  border: var(--bw) solid var(--line);
  border-radius: 999px;
  background: var(--paper-deep);
  overflow: hidden;
}
.level-progress__fill {
  height: 100%;
  background: var(--green);
  border-radius: 999px;
  transition: width 0.4s ease;
}

.wallet-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 700;
  margin: 6px 0 14px;
}

.quick-links {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.right-col {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}
.panel {
  padding: 24px;
}
.panel h3 {
  font-family: var(--font-display);
  font-size: 20px;
  margin-bottom: 16px;
  letter-spacing: 1px;
}

.exp-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}
.exp-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-s);
  background: var(--paper-deep);
  font-size: 14px;
}
.exp-delta {
  font-weight: 900;
  font-family: var(--font-display);
  min-width: 46px;
}
.exp-delta.plus {
  color: var(--green);
}
.exp-delta.minus {
  color: var(--red);
}
.exp-reason {
  flex: 1;
}
.exp-time {
  font-size: 12px;
}
.empty-tip {
  font-size: 14px;
}

/* —— 账号安全 —— */
.sec-block {
  padding: 4px 0;
}
.sec-block__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.sec-block__head b {
  font-size: 15px;
}
.sec-block__head .hint {
  margin-top: 4px;
  max-width: 420px;
}
.sec-form {
  margin-top: 14px;
  padding: 16px;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-s);
  background: var(--paper-deep);
}
.danger-text {
  color: var(--red);
}

/* —— 学校归属 —— */
.school-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.school-ic {
  width: 14px;
  height: 14px;
  vertical-align: -2px;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
@media (max-width: 480px) {
  .field-row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
.cancel-warn {
  font-size: 13.5px;
  line-height: 1.8;
  background: #fdebeb;
  border: var(--bw) solid var(--red);
  border-radius: var(--r-s);
  padding: 12px 14px;
  margin-bottom: 16px;
  color: #8c1d1d;
}
</style>
