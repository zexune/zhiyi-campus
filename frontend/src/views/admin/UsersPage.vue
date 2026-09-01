<template>
  <AdminLayout>
    <div class="users-page rise">
      <div class="page-title">用户管理</div>

      <!-- 筛选：学校精确 + 学号/昵称/邮箱/手机号模糊 -->
      <div class="card filter-card">
        <div class="filter-grid">
          <div class="field">
            <label>学校</label>
            <AppSelect v-model="filters.schoolId" :options="schoolOptions" placeholder="全部学校" clearable />
          </div>
          <div class="field">
            <label>学号</label>
            <input v-model.trim="filters.studentId" class="input" placeholder="模糊搜索学号" @keydown.enter="applySearch" />
          </div>
          <div class="field">
            <label>昵称</label>
            <input v-model.trim="filters.nickname" class="input" placeholder="模糊搜索昵称" @keydown.enter="applySearch" />
          </div>
          <div class="field">
            <label>邮箱</label>
            <input v-model.trim="filters.email" class="input" placeholder="模糊搜索学校邮箱" @keydown.enter="applySearch" />
          </div>
          <div class="field">
            <label>手机号</label>
            <input v-model.trim="filters.phone" class="input" placeholder="模糊搜索手机号" @keydown.enter="applySearch" />
          </div>
        </div>
        <div class="filter-actions">
          <button class="btn btn--sm btn--primary" :disabled="loading" @click="applySearch">{{ loading ? '查询中…' : '搜索' }}</button>
          <button class="btn btn--sm" :disabled="loading" @click="resetSearch">重置</button>
        </div>
      </div>

      <!-- 加载 / 错误 / 空态 -->
      <div v-if="loading" class="card card--flat state-card">
        <span class="muted">加载中...</span>
      </div>
      <div v-else-if="loadError" class="card card--flat state-card">
        <span class="muted">用户列表加载失败</span>
        <button class="btn btn--sm" @click="fetchList">重试</button>
      </div>
      <div v-else-if="!records.length" class="card card--flat state-card">
        <span class="muted">没有符合条件的用户</span>
      </div>

      <!-- 用户列表：语义化表格，列宽由同一张表统一解算，天然跨行对齐 -->
      <!-- tabindex：窄屏横向滚动容器对键盘可达（“操作”列在最右侧，WCAG 2.1.1） -->
      <div v-else class="card table-card" tabindex="0" role="region" aria-label="用户列表表格，可左右滚动">
        <table class="user-table">
          <thead>
            <tr>
              <th>用户</th>
              <th>学校</th>
              <th>联系方式</th>
              <th>等级</th>
              <th>状态</th>
              <th>注册时间</th>
              <th class="ops-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in records" :key="user.id">
              <td>
                <div class="user-cell__user">
                  <UserAvatar :nickname="user.nickname || '?'" :user-id="user.id" size="s" :src="user.avatar" />
                  <div class="user-cell__who">
                    <div class="user-cell__name">{{ user.nickname }}</div>
                    <div class="muted user-cell__id">{{ user.studentId }}</div>
                  </div>
                </div>
              </td>
              <td>{{ user.schoolName || '未填写学校' }}</td>
              <td class="contact-col">
                <div>{{ user.schoolEmail || '未绑定邮箱' }}</div>
                <div class="muted">{{ user.phone || '未绑定手机' }}</div>
              </td>
              <td>{{ user.levelTitle || '—' }}</td>
              <td>
                <span class="badge" :class="statusBadgeClass(user.status)">{{ statusLabel(user.status) }}</span>
                <span v-if="user.status === USER_STATUS.BANNED_TEMP && user.banUntilTime" class="muted ban-until">{{ formatDateTime(user.banUntilTime) }} 解除</span>
              </td>
              <td class="muted">{{ formatDateTime(user.createdAt) }}</td>
              <td class="ops-cell">
                <button class="btn btn--sm" :disabled="actingId === user.id" @click="handleResetPassword(user)">重置密码</button>
                <button v-if="user.status === USER_STATUS.ACTIVE" class="btn btn--sm btn--danger" :disabled="actingId === user.id" @click="openBanDialog(user)">封禁</button>
                <button v-else-if="isBanned(user)" class="btn btn--sm btn--green" :disabled="actingId === user.id" @click="handleUnban(user)">解除封禁</button>
                <button v-else-if="user.status === USER_STATUS.CANCELLED" class="btn btn--sm btn--green" :disabled="actingId === user.id" @click="handleUnban(user)">恢复账号</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <el-pagination v-if="total > pageSize" v-model:current-page="currentPage" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="fetchList" />

      <!-- 封禁弹窗传送到 body，避免 rise 动画形成的局部层叠上下文盖住浮层。
           焦点管理（Esc/Tab 循环/焦点归还）由 useModalA11y 提供 -->
      <Teleport to="body">
        <div v-if="banDialog.visible" class="modal-overlay" @click.self="closeBanDialog">
          <div :ref="banModal.bindSheet" class="modal-card card" role="dialog" aria-modal="true" aria-label="账号封禁">
            <h3 class="modal-title">封禁用户「{{ banDialog.target?.nickname }}」</h3>
            <div class="form-pair">
              <div class="field">
                <label>封禁方式</label>
                <AppSelect v-model="banDialog.type" :options="BAN_TYPE_OPTIONS" />
              </div>
              <div v-if="banDialog.type === BAN_ACTION.TEMPORARY" class="field">
                <label>封禁天数</label>
                <input v-model.number="banDialog.banDays" class="input" type="number" min="1" max="365" />
              </div>
            </div>
            <div class="field">
              <label>封禁原因</label>
              <textarea v-model.trim="banDialog.reason" class="textarea" maxlength="500" placeholder="请填写独立、可追溯的账号封禁原因"></textarea>
            </div>
            <p class="hint">封禁后该用户现有登录令牌立即失效。</p>
            <div class="modal-actions">
              <button class="btn" @click="closeBanDialog">取消</button>
              <button class="btn btn--danger" :disabled="banDialog.submitting" @click="submitBan">{{ banDialog.submitting ? '执行中…' : '确认封禁' }}</button>
            </div>
          </div>
        </div>
      </Teleport>
    </div>
  </AdminLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { banUser, resetUserPassword, searchAdminUsers, unbanUser } from '@/api/admin'
import type { AdminUser } from '@/types/models'
import { BAN_ACTION, USER_STATUS, USER_STATUS_LABELS } from '@/constants/domain'
import type { UserStatus } from '@/constants/domain'
import { formatDateTime } from '@/utils/format'
import { usePagedList } from '@/composables/usePagedList'
import { useModalA11y } from '@/composables/useModalA11y'
import { useSchoolOptions } from '@/composables/useSchoolOptions'

const BAN_TYPE_OPTIONS = [
  { label: '限时封禁', value: BAN_ACTION.TEMPORARY },
  { label: '永久封禁', value: BAN_ACTION.PERMANENT }
]
/** 表单里的筛选值（'' 表示不筛选；提交前清洗为接口载荷） */
const filters = reactive({ schoolId: null as number | null, studentId: '', nickname: '', email: '', phone: '' })
/** 点击「搜索」时固化的筛选快照，分页翻页沿用同一份 */
const applied = ref<UserFilterPayload>({})
const actingId = ref<number | null>(null)

const { records, currentPage, pageSize, total, loading, loadError, fetchList, goToFirstPage } = usePagedList<AdminUser, UserFilterPayload>(searchAdminUsers, {
  size: 10,
  // 返回 null 表示无额外条件，避免空字符串参与请求参数
  params: () => (Object.keys(applied.value).length ? applied.value : null)
})

interface UserFilterPayload {
  schoolId?: number | null
  studentId?: string
  nickname?: string
  email?: string
  phone?: string
}

/** 学校下拉与认证页共享同一份模块级缓存 */
const { schoolOptions, fetchSchools } = useSchoolOptions()

function buildPayload(): UserFilterPayload {
  const payload: UserFilterPayload = {}
  if (filters.schoolId != null) payload.schoolId = filters.schoolId
  if (filters.studentId) payload.studentId = filters.studentId
  if (filters.nickname) payload.nickname = filters.nickname
  if (filters.email) payload.email = filters.email
  if (filters.phone) payload.phone = filters.phone
  return payload
}

function applySearch() {
  applied.value = buildPayload()
  goToFirstPage()
  void fetchList()
}

function resetSearch() {
  Object.assign(filters, { schoolId: null, studentId: '', nickname: '', email: '', phone: '' })
  applied.value = {}
  goToFirstPage()
  void fetchList()
}

// ---- 状态展示 ----

function statusLabel(status: AdminUser['status']) {
  // 越界值经查表兜底原样回显（与 utils/trade 的模式一致）
  return USER_STATUS_LABELS[status as UserStatus] || status
}
function statusBadgeClass(status: AdminUser['status']) {
  if (status === USER_STATUS.ACTIVE) return 'badge--ok'
  if (status === USER_STATUS.BANNED_TEMP || status === USER_STATUS.BANNED_PERM) return 'badge--danger'
  return 'badge--muted'
}
function isBanned(user: AdminUser) {
  const bannedStatuses: readonly UserStatus[] = [USER_STATUS.BANNED_TEMP, USER_STATUS.BANNED_PERM]
  return bannedStatuses.includes(user.status as UserStatus)
}

// ---- 强制重置密码 ----

async function handleResetPassword(user: AdminUser) {
  try {
    await ElMessageBox.confirm(`确认将「${user.nickname}」（${user.studentId}）的密码重置为 123456？该用户将被强制下线。`, '强制重置密码', {
      confirmButtonText: '确认重置',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  actingId.value = user.id
  try {
    await resetUserPassword({ userId: user.id })
    ElMessage.success('密码已重置为 123456，该用户需使用新密码登录')
  } finally {
    actingId.value = null
  }
}

// ---- 账号封禁 / 解封 ----

const banDialog = reactive({
  visible: false,
  target: null as AdminUser | null,
  type: BAN_ACTION.TEMPORARY,
  banDays: 7,
  reason: '',
  submitting: false
})

function openBanDialog(user: AdminUser) {
  banDialog.target = user
  banDialog.type = BAN_ACTION.TEMPORARY
  banDialog.banDays = 7
  banDialog.reason = ''
  banDialog.visible = true
}
function closeBanDialog() {
  banDialog.visible = false
  banDialog.target = null
}

// 封禁弹窗的焦点管理（Esc 关闭 / Tab 循环 / 焦点归还）：此前自绘 modal 三件套全缺
const banVisible = computed({
  get: () => banDialog.visible,
  set: (value: boolean) => {
    banDialog.visible = value
  }
})
const banModal = useModalA11y(banVisible, closeBanDialog)

async function submitBan() {
  const target = banDialog.target
  if (!target) return
  if (!banDialog.reason) {
    ElMessage.warning('请填写封禁原因')
    return
  }
  if (banDialog.type === BAN_ACTION.TEMPORARY && (!Number.isInteger(banDialog.banDays) || banDialog.banDays < 1 || banDialog.banDays > 365)) {
    ElMessage.warning('封禁天数须为 1-365 天')
    return
  }
  // 弹窗内「确认封禁」即为最终确认，不再叠加 MessageBox
  banDialog.submitting = true
  try {
    await banUser({
      userId: target.id,
      type: banDialog.type,
      reason: banDialog.reason,
      banDays: banDialog.type === BAN_ACTION.TEMPORARY ? banDialog.banDays : null
    })
    // 就地更新行状态，避免整页重新拉取
    target.status = banDialog.type === BAN_ACTION.TEMPORARY ? USER_STATUS.BANNED_TEMP : USER_STATUS.BANNED_PERM
    if (banDialog.type === BAN_ACTION.TEMPORARY) {
      const until = new Date(Date.now() + banDialog.banDays * 24 * 60 * 60 * 1000)
      target.banUntilTime = until.toISOString().slice(0, 19)
    }
    ElMessage.success('用户已封禁，其现有登录令牌已失效')
    closeBanDialog()
  } finally {
    banDialog.submitting = false
  }
}

async function handleUnban(user: AdminUser) {
  const restoringCancelled = user.status === USER_STATUS.CANCELLED
  try {
    await ElMessageBox.confirm(`确认${restoringCancelled ? '恢复已注销的账号' : '解除用户'}「${user.nickname}」的限制？`, restoringCancelled ? '恢复账号' : '解除封禁', { type: 'info' })
  } catch {
    return
  }
  actingId.value = user.id
  try {
    await unbanUser({ userId: user.id })
    user.status = USER_STATUS.ACTIVE
    user.banUntilTime = null
    ElMessage.success(restoringCancelled ? '账号已恢复' : '用户封禁已解除')
  } finally {
    actingId.value = null
  }
}

onMounted(() => {
  void fetchSchools()
  void fetchList()
})
</script>

<style scoped>
.users-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}
.state-card {
  padding: 40px 24px;
  text-align: center;
  display: grid;
  justify-items: center;
  gap: 12px;
}

/* ---- 筛选区 ---- */
.filter-card {
  padding: 18px 20px;
  margin-bottom: 16px;
}
.filter-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
}
.filter-actions {
  display: flex;
  gap: 10px;
  margin-top: 6px;
}

/* ---- 列表：语义化表格，列宽由同一张表统一解算，天然跨行对齐 ---- */
.table-card {
  overflow-x: auto;
}
/* 键盘滚动区域获得焦点时的可见提示（窄屏下“操作”列需滚动到达） */
.table-card:focus-visible {
  outline: 2px solid var(--blue);
  outline-offset: -2px;
}
.user-table {
  width: 100%;
  min-width: 880px;
  border-collapse: collapse;
}
.user-table th,
.user-table td {
  padding: 12px 16px;
  text-align: left;
  vertical-align: middle;
  font-size: 14px;
}
.user-table thead th {
  font-size: 12px;
  font-weight: 700;
  color: var(--ink-soft);
  white-space: nowrap;
  border-bottom: var(--bw) solid var(--line);
}
.user-table tbody tr + tr td {
  border-top: var(--bw) solid var(--line);
}
.ops-col,
.ops-cell {
  text-align: right;
  white-space: nowrap;
}
.ops-cell .btn + .btn {
  margin-left: 8px;
}
.contact-col div {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ban-until {
  font-size: 12px;
  margin-left: 8px;
}
.el-pagination {
  margin-top: 16px;
  justify-content: center;
}
.user-cell__user {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.user-cell__who {
  min-width: 0;
}
.user-cell__name {
  font-weight: 700;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-cell__id {
  font-size: 12px;
}

/* ---- 弹窗 ---- */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  background: rgba(38, 34, 28, 0.58);
  backdrop-filter: blur(3px);
  display: grid;
  place-items: center;
  padding: 24px;
  overflow-y: auto;
}
.modal-card {
  width: 100%;
  max-width: 480px;
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  padding: 28px;
}
.modal-title {
  font-family: var(--font-display);
  font-size: 22px;
  letter-spacing: 0.5px;
  margin-bottom: 22px;
}
.modal-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 22px;
}
.field label {
  display: block;
  margin-bottom: 4px;
  font-weight: 700;
  font-size: 14px;
}
.hint {
  font-size: 12px;
  color: var(--ink-soft);
}

/* 表格在窄窗口下横向滚动（.table-card overflow-x），筛选区降为两列 */
@media (max-width: 1080px) {
  .filter-grid {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 520px) {
  .filter-grid {
    grid-template-columns: 1fr;
  }
}
</style>
