<template>
  <DefaultLayout>
    <div class="schools-page rise">
      <div class="page-title">
        🏫 学校管理
        <span class="stamp">Admin</span>
      </div>

      <div class="nav-tabs">
        <router-link to="/admin/dashboard" class="nav-tab">📊 数据大盘</router-link>
        <router-link to="/admin/violations" class="nav-tab">⚖️ 违规审核</router-link>
        <router-link to="/admin/chat" class="nav-tab">💬 客服收件箱</router-link>
        <router-link to="/admin/manage" class="nav-tab">🔧 内容管理</router-link>
        <span class="nav-tab active">🏫 学校管理</span>
      </div>

      <!-- 加载 -->
      <div v-if="loading" class="card card--flat state-card">
        <span class="muted">加载中...</span>
      </div>

      <template v-else>
        <!-- 学校列表 -->
        <div v-if="schools.length > 0" class="school-list">
          <div
            v-for="s in schools"
            :key="s.id"
            class="school-row card card--flat"
            :class="{ active: editingId === s.id }"
          >
            <div class="school-row__info">
              <span class="school-row__name">{{ s.name }}</span>
              <span class="school-row__code badge badge--ink">{{ s.code }}</span>
              <span class="school-row__domain muted">{{ s.emailDomain || '未配置邮箱域名' }}</span>
              <span class="badge" :class="schoolStatusClass(s.status)">
                {{ schoolStatusLabel(s.status) }}
              </span>
            </div>
            <div class="school-row__actions">
              <button v-if="s.status !== 'DELETED'" class="btn btn--sm" @click="startEdit(s)">✏️ 编辑</button>
              <button
                v-if="s.status !== 'DELETED'"
                class="btn btn--sm btn--danger"
                :aria-label="`删除学校${s.name}`"
                :disabled="deletingId === s.id"
                @click="handleDelete(s)"
              >
                {{ deletingId === s.id ? '删除中...' : '删除' }}
              </button>
            </div>
          </div>
        </div>
        <div v-else class="card card--flat state-card">
          <span class="muted">暂无学校数据</span>
        </div>

        <!-- 编辑弹窗传送到 body，避免 rise 动画形成的局部层叠上下文盖住浮层。 -->
        <Teleport to="body">
          <div v-if="dialog.visible" class="modal-overlay" @click.self="closeDialog">
            <div class="modal-card card" role="dialog" aria-modal="true" :aria-label="dialog.isCreate ? '新增学校' : '编辑学校'">
            <h3 class="modal-title">{{ dialog.isCreate ? '🏫 新增学校' : '✏️ 编辑学校' }}</h3>

            <div class="field">
              <label>学校名称 <span class="req">*</span></label>
              <input
                class="input"
                v-model="dialog.form.name"
                maxlength="100"
                placeholder="如：上海大学"
              />
            </div>

            <div class="field">
              <label>学校代码 <span class="req">*</span></label>
              <input
                class="input"
                v-model="dialog.form.code"
                maxlength="20"
                placeholder="如：SHU"
                style="text-transform:uppercase"
              />
              <p class="hint">英文大写缩写，如 SHU、DHU，全局唯一</p>
            </div>

            <div class="field">
              <label>邮箱域名</label>
              <input
                class="input"
                v-model="dialog.form.emailDomain"
                maxlength="100"
                placeholder="如：@stu.shu.edu.cn"
              />
              <p class="hint">用于学籍邮箱验证，如 @stu.shu.edu.cn</p>
            </div>

            <div class="field">
              <label>状态</label>
              <div class="radio-group">
                <button
                  type="button"
                  class="radio-card"
                  :class="{ active: dialog.form.status === 'ACTIVE' }"
                  @click="dialog.form.status = 'ACTIVE'"
                >
                  <span class="radio-card__label">🟢 启用</span>
                </button>
                <button
                  type="button"
                  class="radio-card"
                  :class="{ active: dialog.form.status === 'DISABLED' }"
                  @click="dialog.form.status = 'DISABLED'"
                >
                  <span class="radio-card__label">⚫ 停用</span>
                </button>
              </div>
            </div>

              <div class="modal-actions">
                <button class="btn" @click="closeDialog">取消</button>
                <button
                  class="btn btn--primary"
                  :disabled="dialog.submitting"
                  @click="handleSave"
                >
                  {{ dialog.submitting ? '保存中...' : (dialog.isCreate ? '创建' : '保存') }}
                </button>
              </div>
            </div>
          </div>
        </Teleport>

        <!-- 新增按钮 -->
        <div class="add-bar">
          <button class="btn btn--primary" @click="startCreate">
            ➕ 新增学校
          </button>
        </div>
      </template>
    </div>
  </DefaultLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import { getSchools, createSchool, updateSchool, deleteSchool } from '@/api/admin'

const schools = ref([])
const loading = ref(false)
const editingId = ref(null)
const deletingId = ref(null)

const dialog = reactive({
  visible: false,
  isCreate: true,
  submitting: false,
  form: {
    id: null,
    name: '',
    code: '',
    emailDomain: '',
    status: 'ACTIVE',
  },
})

async function fetchSchools() {
  loading.value = true
  try {
    const res = await getSchools()
    schools.value = res.data || []
  } catch {
    ElMessage.error('加载学校列表失败')
  } finally {
    loading.value = false
  }
}

function startCreate() {
  dialog.isCreate = true
  dialog.form = { id: null, name: '', code: '', emailDomain: '', status: 'ACTIVE' }
  dialog.visible = true
}

function startEdit(school) {
  dialog.isCreate = false
  editingId.value = school.id
  dialog.form = {
    id: school.id,
    name: school.name,
    code: school.code,
    emailDomain: school.emailDomain || '',
    status: school.status || 'ACTIVE',
  }
  dialog.visible = true
}

function closeDialog() {
  dialog.visible = false
  editingId.value = null
}

function schoolStatusLabel(status) {
  return { ACTIVE: '启用', DISABLED: '停用', DELETED: '已删除' }[status] || status
}

function schoolStatusClass(status) {
  if (status === 'ACTIVE') return 'badge--ok'
  if (status === 'DELETED') return 'badge--danger'
  return 'badge--muted'
}

async function handleSave() {
  const { id, name, code, emailDomain, status } = dialog.form
  if (!name.trim()) { ElMessage.warning('请输入学校名称'); return }
  if (!code.trim()) { ElMessage.warning('请输入学校代码'); return }

  dialog.submitting = true
  try {
    const payload = {
      name: name.trim(),
      code: code.trim().toUpperCase(),
      emailDomain: emailDomain.trim() || null,
      status,
    }
    if (dialog.isCreate) {
      await createSchool(payload)
      ElMessage.success('学校创建成功')
    } else {
      await updateSchool(id, payload)
      ElMessage.success('学校更新成功')
    }
    closeDialog()
    await fetchSchools()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally {
    dialog.submitting = false
  }
}

async function handleDelete(school) {
  try {
    await ElMessageBox.confirm(
      `确认删除学校“${school.name}”？仅无用户和商品关联的学校可以删除。`,
      '删除学校',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  deletingId.value = school.id
  try {
    await deleteSchool(school.id)
    ElMessage.success('学校已删除')
    await fetchSchools()
  } catch {
    // 具体依赖约束错误由统一请求拦截器提示。
  } finally {
    deletingId.value = null
  }
}

onMounted(fetchSchools)
</script>

<style scoped>
.schools-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.nav-tabs {
  display: flex; gap: 4px; margin: 18px 0 28px; flex-wrap: wrap;
}
.nav-tab {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 10px 20px; font-size: 15px; font-weight: 700;
  border: var(--bw) solid var(--ink); border-radius: var(--r-s);
  background: var(--paper-deep); color: var(--ink);
  cursor: pointer; text-decoration: none; transition: all .2s;
}
.nav-tab:hover { background: var(--white); box-shadow: var(--shadow-s); }
.nav-tab.active { background: var(--ink); color: var(--paper); }

.state-card {
  padding: 40px 24px; text-align: center;
}

.school-list {
  display: flex; flex-direction: column; gap: 8px;
}
.school-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 20px; gap: 12px;
}
.school-row__actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.school-row.active {
  border-color: var(--primary);
  box-shadow: 2px 2px 0 var(--primary);
}
.school-row__info {
  display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
}
.school-row__name {
  font-weight: 700; font-size: 16px;
}

.add-bar {
  margin-top: 20px;
}

/* 弹窗 */
.modal-overlay {
  position: fixed; inset: 0; z-index: var(--z-modal);
  background: rgba(38,34,28,.58);
  backdrop-filter: blur(3px);
  display: grid; place-items: center; padding: 24px;
  overflow-y: auto;
}
.modal-card {
  width: 100%; max-width: 480px; max-height: calc(100vh - 48px); overflow-y: auto;
  padding: 28px;
}
.modal-title {
  font-family: var(--font-display); font-size: 22px;
  letter-spacing: .5px; margin-bottom: 22px;
}
.modal-actions {
  display: flex; gap: 12px; justify-content: flex-end; margin-top: 22px;
}
.field {
  margin-bottom: 16px;
}
.field label {
  display: block; margin-bottom: 4px; font-weight: 700; font-size: 14px;
}
.hint {
  font-size: 12px; color: var(--ink-soft); margin-top: 4px;
}
.req { color: var(--red); }

.radio-group {
  display: grid; grid-template-columns: 1fr 1fr; gap: 10px;
}
.radio-card {
  display: flex; align-items: center; justify-content: center;
  padding: 12px;
  border: var(--bw) solid var(--ink); border-radius: var(--r-s);
  background: var(--paper-deep); cursor: pointer; transition: all .15s;
}
.radio-card:hover { background: var(--white); }
.radio-card.active {
  background: var(--ink); color: var(--paper);
  box-shadow: var(--shadow-s);
}
.radio-card__label { font-size: 14px; font-weight: 700; }
</style>
