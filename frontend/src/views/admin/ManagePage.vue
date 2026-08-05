<template>
  <DefaultLayout>
    <div class="manage-page rise">
      <div class="page-title">
        🔧 内容管理
        <span class="stamp">Admin</span>
      </div>

      <div class="nav-tabs">
        <router-link to="/admin/dashboard" class="nav-tab">📊 数据大盘</router-link>
        <router-link to="/admin/violations" class="nav-tab">⚖️ 违规审核</router-link>
        <router-link to="/admin/chat" class="nav-tab">💬 客服收件箱</router-link>
        <span class="nav-tab active">🔧 内容管理</span>
        <router-link to="/admin/schools" class="nav-tab">🏫 学校管理</router-link>
      </div>

      <div class="tool-grid">
        <!-- ===== 强制下架商品 ===== -->
        <div class="tool-card card">
          <h3 class="tool-card__title">📦 强制下架商品</h3>
          <p class="tool-card__desc muted">
            搜索商品后选择目标，将其强制下架。卖家将被扣除 30 经验值。
          </p>

          <!-- 搜索栏 -->
          <div class="search-row">
            <input
              class="input"
              v-model="itemForm.keyword"
              placeholder="搜索商品标题或输入 ID"
              @keydown.enter="searchItems"
            />
            <AppSelect
              v-model="itemForm.statusFilter"
              class="manage-status-select"
              :options="STATUS_FILTER_OPTIONS"
              aria-label="商品状态"
            />
            <button class="btn btn--sm" @click="searchItems" :disabled="itemForm.searching">
              {{ itemForm.searching ? '搜索中' : '搜索' }}
            </button>
          </div>

          <!-- 搜索结果列表 -->
          <div v-if="itemForm.items.length > 0" class="item-list">
            <div
              v-for="it in itemForm.items" :key="it.id"
              class="item-row card card--flat"
              :class="{ active: itemForm.selectedId === it.id }"
              @click="selectItem(it)"
            >
              <div class="item-row__left">
                <span class="item-row__id muted">#{{ it.id }}</span>
                <div>
                  <div class="item-row__title">{{ it.title }}</div>
                  <div class="item-row__meta muted">
                    {{ it.publisherNickname || '未知' }} · {{ formatTime(it.createdAt) }}
                  </div>
                </div>
              </div>
              <div class="item-row__right">
                <span class="price">¥{{ it.price }}</span>
                <span class="badge" :class="statusBadge(it.status)">{{ statusLabel(it.status) }}</span>
              </div>
            </div>
          </div>
          <div v-else-if="itemForm.searched" class="muted" style="font-size:13px;margin-top:8px">未找到商品</div>

          <!-- 已选商品预览 -->
          <div v-if="itemForm.selected" class="preview-card card card--flat">
            <div class="preview-row"><span class="muted">#{{ itemForm.selected.id }}</span></div>
            <div class="preview-row"><span class="muted">标题：</span><strong>{{ itemForm.selected.title }}</strong></div>
            <div class="preview-row">
              <span class="muted">状态：</span>
              <span class="badge" :class="statusBadge(itemForm.selected.status)">{{ statusLabel(itemForm.selected.status) }}</span>
            </div>
            <div class="preview-row"><span class="muted">价格：</span><span class="price">¥{{ itemForm.selected.price }}</span></div>
            <div class="preview-row"><span class="muted">发布者：</span>{{ itemForm.selected.publisherNickname || '未知' }}</div>
          </div>

          <div class="tool-card__actions">
            <button
              v-if="itemForm.selected"
              class="btn btn--sm"
              @click="showLineage(itemForm.selected)"
            >
              📜 传承链
            </button>
            <button
              v-if="itemForm.selected"
              class="btn btn--sm btn--danger"
              :disabled="itemForm.submitting || itemForm.selected.status === 'OFF_SHELF'"
              @click="handleForceOffShelf"
            >
              {{ itemForm.submitting ? '处理中' : '确认下架' }}
            </button>
          </div>
          <div v-if="itemForm.result" class="tool-result" :class="itemForm.resultType">
            {{ itemForm.result }}
          </div>
        </div>

        <!-- ===== 强制重置密码 ===== -->
        <div class="tool-card card">
          <h3 class="tool-card__title">🔑 强制重置密码</h3>
          <p class="tool-card__desc muted">将指定用户的密码重置为 <code>123456</code>，用户将被强制下线。</p>

          <div class="field">
            <label>搜索用户</label>
            <div class="search-row">
              <input
                class="input"
                v-model="pwdForm.keyword"
                placeholder="输入学号或昵称搜索"
                @keydown.enter="searchUsersAction"
              />
              <button class="btn btn--sm" @click="searchUsersAction" :disabled="pwdForm.searching">
                {{ pwdForm.searching ? '搜索中' : '搜索' }}
              </button>
            </div>
          </div>

          <!-- 搜索结果 -->
          <div v-if="pwdForm.users.length > 0" class="user-list">
            <div
              v-for="u in pwdForm.users" :key="u.id"
              class="user-item card card--flat"
              :class="{ active: pwdForm.selectedId === u.id }"
              @click="selectUser(u)"
            >
              <div class="user-item__left">
                <span class="avatar avatar--s" :class="avatarColor(u.id)">{{ (u.nickname || '?')[0] }}</span>
                <div>
                  <div class="user-item__name">{{ u.nickname }}</div>
                  <div class="user-item__id muted">{{ u.studentId }}</div>
                </div>
              </div>
              <span class="badge" :class="u.role === 'ADMIN' ? 'badge--danger' : 'badge--ok'">{{ u.role === 'ADMIN' ? '管理员' : '用户' }}</span>
            </div>
          </div>
          <div v-else-if="pwdForm.searched" class="muted" style="font-size:13px;margin-top:8px">未找到用户</div>

          <!-- 已选用户 -->
          <div v-if="pwdForm.selected" class="preview-card card card--flat">
            <div class="preview-row"><span class="muted">昵称：</span><strong>{{ pwdForm.selected.nickname }}</strong></div>
            <div class="preview-row"><span class="muted">学号：</span>{{ pwdForm.selected.studentId }}</div>
            <div class="preview-row"><span class="muted">角色：</span>{{ pwdForm.selected.role }}</div>
          </div>

          <div class="tool-card__actions">
            <button
              v-if="pwdForm.selected"
              class="btn btn--sm btn--danger"
              :disabled="pwdForm.submitting || pwdForm.selected.role === 'ADMIN'"
              @click="handleResetPassword"
            >
              {{ pwdForm.submitting ? '处理中' : '确认重置密码' }}
            </button>
          </div>
          <div v-if="pwdForm.result" class="tool-result" :class="pwdForm.resultType">
            {{ pwdForm.result }}
          </div>
        </div>

        <div class="tool-card card topic-card">
          <h3 class="tool-card__title">🎯 大事件专题</h3>
          <p class="tool-card__desc muted">配置专题生效时段、商品筛选规则和首页 Banner 文案。</p>
          <div class="field"><label>专题名称</label><input v-model.trim="topicForm.title" class="input" maxlength="100" placeholder="如：毕业季闲置循环"></div>
          <div class="form-pair">
            <div class="field"><label>开始时间</label><AppDateTimePicker v-model="topicForm.startTime" placeholder="选择专题开始时间" aria-label="专题开始时间" /></div>
            <div class="field"><label>结束时间</label><AppDateTimePicker v-model="topicForm.endTime" :min="topicForm.startTime" placeholder="选择专题结束时间" aria-label="专题结束时间" /></div>
          </div>
          <div class="form-pair">
            <div class="field"><label>商品类型</label><AppSelect v-model="topicForm.filterType" :options="TOPIC_TYPE_OPTIONS" placeholder="全部类型" /></div>
            <div class="field"><label>商品分类</label><AppSelect v-model="topicForm.filterCategoryId" :options="topicCategoryOptions" placeholder="全部分类" /></div>
          </div>
          <div class="field"><label>AI 标签（可选）</label><input v-model.trim="topicForm.filterTag" class="input" maxlength="50" placeholder="如：毕业季"></div>
          <div class="field"><label>Banner 文案</label><textarea v-model.trim="topicForm.bannerText" class="textarea" maxlength="255" placeholder="展示给用户的专题文案"></textarea></div>
          <label class="topic-enabled"><input v-model="topicForm.enabled" type="checkbox"> 启用专题</label>
          <div class="tool-card__actions">
            <button class="btn btn--sm btn--primary" :disabled="topicForm.submitting" @click="saveTopic">{{ topicForm.id ? '保存修改' : '创建专题' }}</button>
            <button v-if="topicForm.id" class="btn btn--sm" @click="resetTopicForm">取消编辑</button>
          </div>
          <div class="topic-list">
            <div v-for="topic in topics" :key="topic.id" class="topic-row card card--flat">
              <div><strong>{{ topic.title }}</strong><div class="muted topic-time">{{ formatDateTime(topic.startTime) }} — {{ formatDateTime(topic.endTime) }}</div></div>
              <div class="topic-actions"><span class="badge" :class="topic.enabled ? 'badge--ok' : 'badge--muted'">{{ topic.enabled ? '启用' : '停用' }}</span><button class="btn btn--sm" @click="editTopic(topic)">编辑</button><button class="btn btn--sm btn--danger" @click="removeTopic(topic)">删除</button></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== 传承链弹窗（D3） ========== -->
    <div v-if="lineageDialog.visible" class="modal-overlay" @click.self="lineageDialog.visible = false">
      <div class="modal-card card">
        <h3 class="modal-title">📜 商品传承链</h3>
        <p class="muted" style="margin-bottom:18px">{{ lineageDialog.data?.itemTitle }}</p>

        <div v-if="lineageDialog.loading" class="muted" style="text-align:center;padding:20px">加载中...</div>
        <div v-else-if="lineageDialog.data?.chain?.length" class="lineage-chain">
          <div
            v-for="(node, i) in lineageDialog.data.chain"
            :key="i"
            class="lineage-node"
          >
            <div class="lineage-node__dot" :class="node.role === 'PUBLISHER' ? 'dot-publisher' : 'dot-buyer'">
              {{ node.role === 'PUBLISHER' ? '📌' : '🤝' }}
            </div>
            <div class="lineage-node__content">
              <div class="lineage-node__name">
                {{ node.nickname }}
                <span class="badge" :class="node.role === 'PUBLISHER' ? 'badge--sell' : 'badge--buy'">
                  {{ node.role === 'PUBLISHER' ? '发布者' : '买家' }}
                </span>
              </div>
              <div class="lineage-node__meta muted">
                <template v-if="node.price">¥{{ node.price }} · </template>
                {{ formatTime(node.time) }}
              </div>
            </div>
          </div>
        </div>
        <div v-else class="muted" style="text-align:center;padding:20px">暂无传承记录（商品尚未交易）</div>

        <div class="modal-actions">
          <button class="btn" @click="lineageDialog.visible = false">关闭</button>
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppDateTimePicker from '@/components/common/AppDateTimePicker.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import { createEventTopic, deleteEventTopic, forceOffShelf, getEventTopics, resetUserPassword, searchUsers, searchAdminItems, getItemLineage, updateEventTopic } from '@/api/admin'
import { getCategories } from '@/api/item'

const TOPIC_TYPE_OPTIONS = [
  { label: '全部类型', value: '' }, { label: '出售', value: 'SELL' }, { label: '求购', value: 'BUY' },
  { label: '以物换物', value: 'SWAP' }, { label: '帮带跑腿', value: 'ERRAND' },
]
const topicCategories = ref([])
const topicCategoryOptions = computed(() => [{ label: '全部分类', value: '' }, ...topicCategories.value.map(c => ({ label: c.name, value: c.id }))])
const topics = ref([])
const emptyTopic = () => ({ id: null, title: '', startTime: '', endTime: '', filterType: '', filterCategoryId: '', filterTag: '', bannerText: '', enabled: true, submitting: false })
const topicForm = reactive(emptyTopic())

function resetTopicForm() { Object.assign(topicForm, emptyTopic()) }
function editTopic(topic) {
  Object.assign(topicForm, { ...topic, startTime: topic.startTime?.slice(0, 16) || '', endTime: topic.endTime?.slice(0, 16) || '', filterCategoryId: topic.filterCategoryId || '', submitting: false })
}
function formatDateTime(value) { return value ? value.replace('T', ' ').slice(0, 16) : '' }
async function loadTopics() { const res = await getEventTopics(); topics.value = res.data || [] }
async function saveTopic() {
  if (!topicForm.title || !topicForm.startTime || !topicForm.endTime || !topicForm.bannerText) { ElMessage.warning('请填写专题名称、时间段和 Banner 文案'); return }
  if (new Date(topicForm.endTime) <= new Date(topicForm.startTime)) { ElMessage.warning('结束时间必须晚于开始时间'); return }
  topicForm.submitting = true
  const data = { ...topicForm, filterType: topicForm.filterType || null, filterCategoryId: topicForm.filterCategoryId || null, filterTag: topicForm.filterTag || null }
  delete data.id; delete data.submitting
  try {
    if (topicForm.id) await updateEventTopic(topicForm.id, data); else await createEventTopic(data)
    ElMessage.success(topicForm.id ? '专题已更新' : '专题已创建'); resetTopicForm(); await loadTopics()
  } finally { topicForm.submitting = false }
}
async function removeTopic(topic) {
  try { await ElMessageBox.confirm(`确认删除专题「${topic.title}」？`, '删除专题', { type: 'warning' }) } catch { return }
  await deleteEventTopic(topic.id); ElMessage.success('专题已删除'); await loadTopics()
}

onMounted(async () => {
  const [, categories] = await Promise.all([loadTopics(), getCategories()])
  topicCategories.value = categories.data || []
})

// ---- 强制下架 ----
const STATUS_FILTER_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '在售', value: 'ON_SALE' },
  { label: '已下架', value: 'OFF_SHELF' },
  { label: '已售出', value: 'SOLD' },
]

const itemForm = reactive({
  keyword: '',
  statusFilter: '',
  searching: false,
  searched: false,
  items: [],
  selectedId: null,
  selected: null,
  submitting: false,
  result: '',
  resultType: '',
})

async function searchItems() {
  const kw = itemForm.keyword.trim()
  if (!kw) { ElMessage.warning('请输入商品标题或 ID'); return }
  itemForm.searching = true
  itemForm.searched = false
  itemForm.items = []
  itemForm.selected = null
  itemForm.selectedId = null
  itemForm.result = ''
  try {
    const res = await searchAdminItems({
      keyword: kw,
      status: itemForm.statusFilter || undefined,
      page: 1,
      size: 20,
    })
    itemForm.items = res.data?.records || []
    itemForm.searched = true
  } catch {
    ElMessage.error('搜索失败')
  } finally {
    itemForm.searching = false
  }
}

function selectItem(it) {
  itemForm.selectedId = it.id
  itemForm.selected = it
  itemForm.result = ''
}

function statusBadge(status) {
  return status === 'ON_SALE' ? 'badge--ok'
    : status === 'OFF_SHELF' ? 'badge--muted'
    : status === 'SOLD' ? 'badge--warn'
    : 'badge--warn'
}

function statusLabel(status) {
  return status === 'ON_SALE' ? '在售'
    : status === 'OFF_SHELF' ? '已下架'
    : status === 'SOLD' ? '已售出'
    : status || '未知'
}

function formatTime(dt) {
  if (!dt) return ''
  const d = new Date(dt)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function handleForceOffShelf() {
  const it = itemForm.selected
  if (!it) return
  try {
    await ElMessageBox.confirm(
      `确认强制下架「${it.title}」(#${it.id})？卖家将被扣除 30 经验值。`,
      '强制下架',
      { confirmButtonText: '确认下架', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }

  itemForm.submitting = true
  itemForm.result = ''
  try {
    await forceOffShelf(it.id)
    itemForm.result = '✅ 商品已强制下架，卖家已扣除 30 经验值'
    itemForm.resultType = 'success'
    itemForm.selected.status = 'OFF_SHELF'
    // 同步更新列表中同商品状态
    const inList = itemForm.items.find(i => i.id === it.id)
    if (inList) inList.status = 'OFF_SHELF'
  } catch (e) {
    itemForm.result = '❌ ' + (e.response?.data?.message || '操作失败')
    itemForm.resultType = 'error'
  } finally {
    itemForm.submitting = false
  }
}

// ---- 传承链（D3） ----
const lineageDialog = reactive({
  visible: false,
  loading: false,
  data: null,
})

async function showLineage(item) {
  lineageDialog.visible = true
  lineageDialog.loading = true
  lineageDialog.data = null
  try {
    const res = await getItemLineage(item.id)
    lineageDialog.data = res.data
  } catch {
    ElMessage.error('获取传承链失败')
  } finally {
    lineageDialog.loading = false
  }
}

// ---- 重置密码 ----
const pwdForm = reactive({
  keyword: '',
  searching: false,
  searched: false,
  users: [],
  selectedId: null,
  selected: null,
  submitting: false,
  result: '',
  resultType: '',
})

async function searchUsersAction() {
  const kw = pwdForm.keyword.trim()
  if (!kw) { ElMessage.warning('请输入搜索关键词'); return }
  pwdForm.searching = true
  pwdForm.searched = false
  pwdForm.users = []
  try {
    const res = await searchUsers({ keyword: kw, page: 1, size: 10 })
    pwdForm.users = res.data?.records || []
    pwdForm.searched = true
  } catch {
    ElMessage.error('搜索失败')
  } finally {
    pwdForm.searching = false
  }
}

function selectUser(u) {
  pwdForm.selectedId = u.id
  pwdForm.selected = u
  pwdForm.result = ''
}

async function handleResetPassword() {
  try {
    await ElMessageBox.confirm(
      `确认将「${pwdForm.selected.nickname}」的密码重置为 123456？用户将被强制下线。`,
      '重置密码',
      { confirmButtonText: '确认重置', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }

  pwdForm.submitting = true
  pwdForm.result = ''
  try {
    await resetUserPassword({ userId: pwdForm.selected.id })
    pwdForm.result = '✅ 密码已重置为 123456，用户下次登录需使用新密码'
    pwdForm.resultType = 'success'
  } catch (e) {
    pwdForm.result = '❌ ' + (e.response?.data?.message || '操作失败')
    pwdForm.resultType = 'error'
  } finally {
    pwdForm.submitting = false
  }
}

const AVATAR_COLORS = ['avatar--orange', 'avatar--green', 'avatar--blue', 'avatar--yellow', 'avatar--ink']
function avatarColor(id) {
  return AVATAR_COLORS[(id || 0) % AVATAR_COLORS.length]
}
</script>

<style scoped>
.manage-page {
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

.tool-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}
.topic-card { grid-column: 1 / -1; }
.form-pair { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.field { margin-bottom: 14px; }
.field label { display: block; font-weight: 700; font-size: 13px; margin-bottom: 6px; }
.topic-enabled { display: inline-flex; align-items: center; gap: 7px; font-weight: 700; }
.topic-list { display: flex; flex-direction: column; gap: 8px; margin-top: 20px; }
.topic-row { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 12px 14px; }
.topic-time { font-size: 12px; margin-top: 3px; }
.topic-actions { display: flex; align-items: center; gap: 8px; }
@media (max-width: 768px) {
  .tool-grid { grid-template-columns: 1fr; }
  .form-pair { grid-template-columns: 1fr; }
  .topic-row { align-items: flex-start; flex-direction: column; }
}

.tool-card {
  padding: 24px;
}
.tool-card__title {
  font-family: var(--font-display);
  font-size: 20px;
  letter-spacing: .5px;
  margin-bottom: 6px;
}
.tool-card__desc {
  font-size: 13px;
  margin-bottom: 20px;
  line-height: 1.5;
}
.tool-card__desc code {
  background: var(--paper-deep);
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 700;
  font-size: 12px;
}
.tool-card__actions {
  display: flex; gap: 10px; margin-top: 16px; flex-wrap: wrap;
}

.preview-card {
  padding: 14px 16px; margin-top: 12px;
  background: var(--paper-deep);
}
.preview-row {
  font-size: 14px; margin-bottom: 4px;
  display: flex; align-items: center; gap: 6px;
}
.preview-row:last-child { margin-bottom: 0; }

.tool-result {
  margin-top: 12px; padding: 10px 14px;
  font-size: 13px; font-weight: 700;
  border-radius: var(--r-s);
  border: var(--bw) solid var(--ink);
}
.tool-result.success { background: #D6F2DF; }
.tool-result.error { background: #FFD9D0; }

.search-row {
  display: flex; gap: 8px;
}
.search-row .input { flex: 1; }
.manage-status-select {
  width: 150px;
  flex: 0 0 150px;
}

@media (max-width: 520px) {
  .search-row { flex-wrap: wrap; }
  .manage-status-select {
    width: 100%;
    flex-basis: 100%;
  }
}

/* ---- 商品搜索结果列表 ---- */
.item-list {
  display: flex; flex-direction: column; gap: 6px;
  margin-top: 12px; max-height: 300px; overflow-y: auto;
}
.item-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px; cursor: pointer; transition: background .12s;
}
.item-row:hover { background: var(--paper-deep); }
.item-row.active {
  border-color: var(--primary);
  box-shadow: 2px 2px 0 var(--primary);
}
.item-row__left {
  display: flex; align-items: center; gap: 10px;
  min-width: 0;
}
.item-row__id {
  font-size: 12px;
  flex-shrink: 0;
}
.item-row__title {
  font-weight: 700; font-size: 14px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  max-width: 200px;
}
.item-row__meta {
  font-size: 12px;
}
.item-row__right {
  display: flex; align-items: center; gap: 10px;
  flex-shrink: 0;
}

/* 复用用户搜索样式 */
.user-list {
  display: flex; flex-direction: column; gap: 6px;
  margin-top: 12px; max-height: 200px; overflow-y: auto;
}
.user-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px; cursor: pointer; transition: background .12s;
}
.user-item:hover { background: var(--paper-deep); }
.user-item.active {
  border-color: var(--primary);
  box-shadow: 2px 2px 0 var(--primary);
}
.user-item__left {
  display: flex; align-items: center; gap: 10px;
}
.user-item__name { font-weight: 700; font-size: 14px; }
.user-item__id { font-size: 12px; }

/* 传承链（D3） */
.lineage-chain {
  display: flex; flex-direction: column;
  padding-left: 20px; border-left: 3px dashed var(--ink);
  margin-bottom: 10px;
}
.lineage-node {
  display: flex; align-items: flex-start; gap: 14px;
  padding: 12px 0; position: relative;
}
.lineage-node:not(:last-child) {
  border-bottom: 1px dashed rgba(38,34,28,.12);
}
.lineage-node__dot {
  width: 40px; height: 40px; border-radius: 50%;
  border: var(--bw) solid var(--ink);
  display: grid; place-items: center;
  font-size: 18px; flex-shrink: 0;
}
.dot-publisher { background: var(--yellow); }
.dot-buyer { background: var(--blue); color: #fff; }
.lineage-node__name {
  font-weight: 700; font-size: 15px;
  display: flex; align-items: center; gap: 8px;
}
.lineage-node__meta {
  font-size: 13px; margin-top: 2px;
}

/* 弹窗复用 */
.modal-overlay {
  position: fixed; inset: 0; z-index: var(--z-modal);
  background: rgba(38,34,28,.45);
  display: grid; place-items: center; padding: 20px;
}
.modal-card {
  width: 100%; max-width: 480px; max-height: 90vh; overflow-y: auto;
  padding: 28px;
}
.modal-title {
  font-family: var(--font-display); font-size: 22px;
  letter-spacing: .5px; margin-bottom: 8px;
}
.modal-actions {
  display: flex; gap: 12px; justify-content: flex-end; margin-top: 16px;
}
</style>
