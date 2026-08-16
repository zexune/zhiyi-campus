<template>
  <DefaultLayout>
    <div class="profile-page">
      <h1 class="page-title">
        个人中心
        <span class="stamp">MY PAGE</span>
      </h1>

      <div v-if="user" class="profile-grid">
        <!-- 左：身份卡 + 经验记录 -->
        <div class="left-col">
          <section class="card id-card sticker-tilt">
            <div class="id-card__head">
              <UserAvatar :nickname="user.nickname" :user-id="user.id" size="l" />
              <div>
                <div class="id-card__name">
                  {{ user.nickname }}
                  <LevelBadge :level="user.level" show-title />
                </div>
                <div class="muted">学号：{{ user.studentId }}</div>
                <div class="school-line">
                  <span class="muted">🏫 {{ user.schoolName || '未选择学校' }}</span>
                </div>
                <div class="muted">注册于 {{ formatDateTime(user.createdAt) }}</div>
              </div>
            </div>

            <hr class="doodle-hr" />

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
              <p class="hint">完成一笔订单（买/卖）+50 EXP；内容违规仅影响独立的合规分</p>
            </div>

            <hr class="doodle-hr" />

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
                <label for="p-email">
                  学校邮箱
                  <span class="opt">选填</span>
                </label>
                <input id="p-email" v-model.trim="editForm.schoolEmail" class="input" type="email" :placeholder="schoolEmailPlaceholder" autocomplete="email" />
              </div>
              <div class="field">
                <label for="p-phone">手机号</label>
                <input id="p-phone" v-model.trim="editForm.phone" class="input" type="tel" placeholder="选填，仅用于接收通知" />
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

            <hr class="doodle-hr" />

            <div class="sec-block">
              <div class="sec-block__head">
                <div>
                  <b class="danger-text">注销账号</b>
                  <p class="hint">注销后无法登录，学号保留占用；如需恢复请联系管理员。有进行中的订单时无法注销，在售商品将自动下架。</p>
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
          ⚠️ 此操作不可自助恢复：注销后立即退出登录，无法再使用该账号交易。
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

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppSelect from '@/components/common/AppSelect.vue'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import LevelBadge from '@/components/common/LevelBadge.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import PriceTag from '@/components/common/PriceTag.vue'
import ReputationRadar from '@/components/common/ReputationRadar.vue'
import { updateProfile, getExpLog, changePassword, cancelAccount, getUserReputation, getSchools } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { ROUTE_PATH } from '@/constants/routes'
import { formatDateTime } from '@/utils/format'

/**
 * 个人中心（模块一 1.5）—— 等级进度条 + 经验记录 + 资料编辑 + 账号安全（改密/注销）
 */
const router = useRouter()
const userStore = useUserStore()
const user = computed(() => userStore.user)

const saving = ref(false)
const editForm = reactive({
  nickname: '',
  phone: '',
  schoolId: null,
  schoolEmail: '',
  campus: '',
  college: '',
  grade: '',
  dormitory: ''
})
const schools = ref([])
const schoolOptions = computed(() => schools.value.map((school) => ({ label: school.name, value: school.id })))
const selectedSchool = computed(() => schools.value.find((school) => school.id === editForm.schoolId) || null)
const schoolEmailPlaceholder = computed(() => (selectedSchool.value?.emailDomain ? `学号${selectedSchool.value.emailDomain}` : '选择学校后填写对应学校邮箱'))

// 信誉雷达（A6）
const reputation = ref(null)

const expLogs = ref([])
const expPage = ref(1)
const expPageSize = 10
const expTotal = ref(0)

const progressPercent = computed(() => {
  const u = user.value
  if (!u) return 0
  if (!u.nextLevelExp) return 100 // 满级
  const base = u.currentLevelBaseExp ?? 0
  const span = u.nextLevelExp - base
  if (span <= 0) return 100
  return Math.min(100, Math.round(((u.exp - base) / span) * 100))
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
    await updateProfile({ ...editForm })
    ElMessage.success('保存成功')
    const profile = await userStore.fetchProfile()
    fillEditForm(profile)
  } catch {
    // 提示由 request.js 统一处理
  } finally {
    saving.value = false
  }
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

async function fetchExpLogs() {
  try {
    const res = await getExpLog({ page: expPage.value, size: expPageSize })
    expLogs.value = res.data.records || []
    expTotal.value = Number(res.data.total) || 0
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
    userStore.logout()
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
    userStore.logout()
    router.push(ROUTE_PATH.LOGIN)
  } catch {
    /* 提示由 request.js 处理 */
  } finally {
    cancelling.value = false
  }
}

function fillEditForm(profile) {
  if (profile) {
    editForm.nickname = profile.nickname
    editForm.phone = profile.phone || ''
    editForm.schoolId = profile.schoolId ?? null
    editForm.schoolEmail = profile.schoolEmail || ''
    editForm.campus = profile.campus || ''
    editForm.college = profile.college || ''
    editForm.grade = profile.grade || ''
    editForm.dormitory = profile.dormitory || ''
  }
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
  padding: 24px;
}
.id-card__head {
  display: flex;
  gap: 16px;
  align-items: center;
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
  border: var(--bw) solid var(--ink);
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
  border: 1.5px dashed var(--ink-soft);
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
  border: 1.5px dashed var(--ink-soft);
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
.field label .opt {
  color: var(--ink-soft);
  font-weight: 600;
  font-size: 12px;
  margin-left: 6px;
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
