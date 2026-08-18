<template>
  <div class="tool-card card">
    <h3 class="tool-card__title">🔑 强制重置密码</h3>
    <p class="tool-card__desc muted">
      将指定用户的密码重置为
      <code>123456</code>
      ，用户将被强制下线。
    </p>

    <div class="field">
      <label>搜索用户</label>
      <div class="search-row">
        <input v-model="form.keyword" class="input" placeholder="输入学号或昵称搜索" @keydown.enter="searchUsersAction" />
        <button class="btn btn--sm" :disabled="form.searching" @click="searchUsersAction">
          {{ form.searching ? '搜索中' : '搜索' }}
        </button>
      </div>
    </div>

    <!-- 搜索结果 -->
    <div v-if="form.users.length > 0" class="user-list">
      <div v-for="u in form.users" :key="u.id" class="user-item card card--flat" :class="{ active: form.selectedId === u.id }" @click="selectUser(u)">
        <div class="user-item__left">
          <span class="avatar avatar--s" :class="avatarColorClass(u.id)">{{ (u.nickname || '?')[0] }}</span>
          <div>
            <div class="user-item__name">{{ u.nickname }}</div>
            <div class="user-item__id muted">{{ u.studentId }}</div>
          </div>
        </div>
        <span class="badge" :class="u.role === 'ADMIN' ? 'badge--danger' : 'badge--ok'">{{ u.role === 'ADMIN' ? '管理员' : '用户' }}</span>
      </div>
    </div>
    <div v-else-if="form.searched" class="muted" style="font-size: 13px; margin-top: 8px">未找到用户</div>

    <!-- 已选用户 -->
    <div v-if="form.selected" class="preview-card card card--flat">
      <div class="preview-row">
        <span class="muted">昵称：</span>
        <strong>{{ form.selected.nickname }}</strong>
      </div>
      <div class="preview-row">
        <span class="muted">学号：</span>
        {{ form.selected.studentId }}
      </div>
      <div class="preview-row">
        <span class="muted">角色：</span>
        {{ form.selected.role }}
      </div>
    </div>

    <div class="tool-card__actions">
      <button v-if="form.selected" class="btn btn--sm btn--danger" :disabled="form.submitting || form.selected.role === 'ADMIN'" @click="handleResetPassword">
        {{ form.submitting ? '处理中' : '确认重置密码' }}
      </button>
    </div>
    <div v-if="form.result" class="tool-result" :class="form.resultType">
      {{ form.result }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { resetUserPassword, searchUsers } from '@/api/admin'
import type { AdminUser } from '@/types/models'
import { avatarColorClass } from '@/utils/format'
import './manage-cards.css'

interface ResetPasswordFormState {
  keyword: string
  searching: boolean
  searched: boolean
  users: AdminUser[]
  selectedId: number | null
  selected: AdminUser | null
  submitting: boolean
  result: string
  resultType: string
}

const form = reactive<ResetPasswordFormState>({
  keyword: '',
  searching: false,
  searched: false,
  users: [],
  selectedId: null,
  selected: null,
  submitting: false,
  result: '',
  resultType: ''
})

async function searchUsersAction() {
  const kw = form.keyword.trim()
  if (!kw) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  form.searching = true
  form.searched = false
  form.users = []
  try {
    const res = await searchUsers({ keyword: kw, page: 1, size: 10 })
    form.users = res.data?.records || []
    form.searched = true
  } catch {
    ElMessage.error('搜索失败')
  } finally {
    form.searching = false
  }
}

function selectUser(u: AdminUser) {
  form.selectedId = u.id
  form.selected = u
  form.result = ''
}

async function handleResetPassword() {
  const target = form.selected
  if (!target) return
  try {
    await ElMessageBox.confirm(`确认将「${target.nickname}」的密码重置为 123456？用户将被强制下线。`, '重置密码', {
      confirmButtonText: '确认重置',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  form.submitting = true
  form.result = ''
  try {
    await resetUserPassword({ userId: target.id })
    form.result = '✅ 密码已重置为 123456，用户下次登录需使用新密码'
    form.resultType = 'success'
  } catch (e) {
    // axios 错误形状（统一拦截器外抛出的原始错误）
    const err = e as { response?: { data?: { message?: string } } }
    form.result = '❌ ' + (err.response?.data?.message || '操作失败')
    form.resultType = 'error'
  } finally {
    form.submitting = false
  }
}
</script>
