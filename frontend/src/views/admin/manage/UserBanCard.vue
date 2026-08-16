<template>
  <div class="tool-card card user-ban-card">
    <h3 class="tool-card__title">🛡️ 用户封禁管理</h3>
    <p class="tool-card__desc muted">封禁是独立的账号风控操作，不与商品下架或内容扣分联动。</p>

    <div class="search-row">
      <input v-model.trim="form.keyword" class="input" placeholder="输入学号或昵称搜索" @keydown.enter="searchBanUsers" />
      <button class="btn btn--sm" :disabled="form.searching" @click="searchBanUsers">{{ form.searching ? '搜索中' : '搜索' }}</button>
    </div>

    <div v-if="form.users.length" class="user-list">
      <div v-for="user in form.users" :key="user.id" class="user-item card card--flat" :class="{ active: form.selectedId === user.id }" @click="selectBanUser(user)">
        <div class="user-item__left">
          <span class="avatar avatar--s" :class="avatarColorClass(user.id)">{{ (user.nickname || '?')[0] }}</span>
          <div>
            <div class="user-item__name">{{ user.nickname }}</div>
            <div class="user-item__id muted">{{ user.studentId }}</div>
          </div>
        </div>
        <span class="badge" :class="user.status === USER_STATUS.ACTIVE ? 'badge--ok' : 'badge--danger'">{{ userStatusLabel(user.status) }}</span>
      </div>
    </div>
    <div v-else-if="form.searched" class="muted" style="font-size: 13px; margin-top: 8px">未找到用户</div>

    <template v-if="form.selected">
      <div class="preview-card card card--flat">
        <div class="preview-row">
          <span class="muted">用户：</span>
          <strong>{{ form.selected.nickname }}（{{ form.selected.studentId }}）</strong>
        </div>
        <div class="preview-row">
          <span class="muted">账号状态：</span>
          {{ userStatusLabel(form.selected.status) }}
        </div>
        <div v-if="form.selected.banUntilTime" class="preview-row">
          <span class="muted">封禁至：</span>
          {{ formatDateTime(form.selected.banUntilTime) }}
        </div>
      </div>

      <template v-if="form.selected.status === USER_STATUS.ACTIVE">
        <div class="form-pair ban-options">
          <div class="field">
            <label>封禁方式</label>
            <AppSelect v-model="form.type" :options="BAN_TYPE_OPTIONS" />
          </div>
          <div v-if="form.type === BAN_ACTION.TEMPORARY" class="field">
            <label>封禁天数</label>
            <input v-model.number="form.banDays" class="input" type="number" min="1" max="365" />
          </div>
        </div>
        <div class="field">
          <label>封禁原因</label>
          <textarea v-model.trim="form.reason" class="textarea" maxlength="500" placeholder="请填写独立、可追溯的账号封禁原因"></textarea>
        </div>
        <div class="tool-card__actions"><button class="btn btn--sm btn--danger" :disabled="form.submitting" @click="handleBanUser">确认封禁</button></div>
      </template>
      <div v-else-if="isBanned(form.selected)" class="tool-card__actions">
        <button class="btn btn--sm btn--green" :disabled="form.submitting" @click="handleUnbanUser">解除封禁</button>
      </div>
    </template>
    <div v-if="form.result" class="tool-result" :class="form.resultType">{{ form.result }}</div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import AppSelect from '@/components/common/AppSelect.vue'
import { banUser, searchUsers, unbanUser } from '@/api/admin'
import { BAN_ACTION, USER_STATUS, USER_STATUS_LABELS } from '@/constants/domain'
import { avatarColorClass, formatDateTime } from '@/utils/format'
import './manage-cards.css'

const BAN_TYPE_OPTIONS = [
  { label: '限时封禁', value: BAN_ACTION.TEMPORARY },
  { label: '永久封禁', value: BAN_ACTION.PERMANENT }
]

const form = reactive({
  keyword: '',
  searching: false,
  searched: false,
  users: [],
  selectedId: null,
  selected: null,
  type: BAN_ACTION.TEMPORARY,
  banDays: 7,
  reason: '',
  submitting: false,
  result: '',
  resultType: ''
})

function userStatusLabel(status) {
  return USER_STATUS_LABELS[status] || status
}
function isBanned(user) {
  return [USER_STATUS.BANNED_TEMP, USER_STATUS.BANNED_PERM].includes(user?.status)
}

async function searchBanUsers() {
  const keyword = form.keyword.trim()
  if (!keyword) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  form.searching = true
  form.searched = false
  form.users = []
  form.selected = null
  form.selectedId = null
  form.result = ''
  try {
    const res = await searchUsers({ keyword, page: 1, size: 10 })
    form.users = res.data?.records || []
    form.searched = true
  } finally {
    form.searching = false
  }
}

function selectBanUser(user) {
  form.selected = user
  form.selectedId = user.id
  form.type = BAN_ACTION.TEMPORARY
  form.banDays = 7
  form.reason = ''
  form.result = ''
}

async function handleBanUser() {
  if (!form.reason) {
    ElMessage.warning('请填写封禁原因')
    return
  }
  if (form.type === BAN_ACTION.TEMPORARY && (!Number.isInteger(form.banDays) || form.banDays < 1 || form.banDays > 365)) {
    ElMessage.warning('封禁天数须为 1-365 天')
    return
  }
  try {
    await ElMessageBox.confirm(`确认${form.type === BAN_ACTION.TEMPORARY ? `封禁 ${form.banDays} 天` : '永久封禁'}用户「${form.selected.nickname}」？`, '账号封禁', { type: 'warning' })
  } catch {
    return
  }
  form.submitting = true
  try {
    await banUser({ userId: form.selected.id, type: form.type, reason: form.reason, banDays: form.type === BAN_ACTION.TEMPORARY ? form.banDays : null })
    form.selected.status = form.type === BAN_ACTION.TEMPORARY ? USER_STATUS.BANNED_TEMP : USER_STATUS.BANNED_PERM
    form.result = '✅ 用户已封禁，现有登录令牌已失效'
    form.resultType = 'success'
  } catch (error) {
    form.result = '❌ ' + (error.response?.data?.message || '封禁失败')
    form.resultType = 'error'
  } finally {
    form.submitting = false
  }
}

async function handleUnbanUser() {
  try {
    await ElMessageBox.confirm(`确认解除用户「${form.selected.nickname}」的封禁？`, '解除封禁', { type: 'info' })
  } catch {
    return
  }
  form.submitting = true
  try {
    await unbanUser({ userId: form.selected.id })
    form.selected.status = USER_STATUS.ACTIVE
    form.selected.banUntilTime = null
    form.result = '✅ 用户封禁已解除'
    form.resultType = 'success'
  } catch (error) {
    form.result = '❌ ' + (error.response?.data?.message || '解封失败')
    form.resultType = 'error'
  } finally {
    form.submitting = false
  }
}
</script>
