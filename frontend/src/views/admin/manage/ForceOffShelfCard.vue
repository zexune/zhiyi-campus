<template>
  <div class="tool-card card">
    <h3 class="tool-card__title">📦 强制下架商品</h3>
    <p class="tool-card__desc muted">搜索商品后选择目标并执行运营下架。该操作只改变商品状态，不自动处罚或封禁卖家。</p>

    <!-- 搜索栏 -->
    <div class="search-row">
      <input v-model="form.keyword" class="input" placeholder="搜索商品标题或输入 ID" @keydown.enter="searchItems" />
      <AppSelect v-model="form.statusFilter" class="manage-status-select" :options="STATUS_FILTER_OPTIONS" aria-label="商品状态" />
      <button class="btn btn--sm" :disabled="form.searching" @click="searchItems">
        {{ form.searching ? '搜索中' : '搜索' }}
      </button>
    </div>

    <!-- 搜索结果列表 -->
    <div v-if="form.items.length > 0" class="item-list">
      <div v-for="it in form.items" :key="it.id" class="item-row card card--flat" :class="{ active: form.selectedId === it.id }" @click="selectItem(it)">
        <div class="item-row__left">
          <span class="item-row__id muted">#{{ it.id }}</span>
          <div>
            <div class="item-row__title">{{ it.title }}</div>
            <div class="item-row__meta muted">{{ it.publisherNickname || '未知' }} · {{ formatDate(it.createdAt) }}</div>
          </div>
        </div>
        <div class="item-row__right">
          <span class="price">¥{{ it.price }}</span>
          <span class="badge" :class="itemStatusBadge(it.status)">{{ itemStatusLabel(it.status) }}</span>
        </div>
      </div>
    </div>
    <div v-else-if="form.searched" class="muted" style="font-size: 13px; margin-top: 8px">未找到商品</div>

    <!-- 已选商品预览 -->
    <div v-if="form.selected" class="preview-card card card--flat">
      <div class="preview-row">
        <span class="muted">#{{ form.selected.id }}</span>
      </div>
      <div class="preview-row">
        <span class="muted">标题：</span>
        <strong>{{ form.selected.title }}</strong>
      </div>
      <div class="preview-row">
        <span class="muted">状态：</span>
        <span class="badge" :class="itemStatusBadge(form.selected.status)">{{ itemStatusLabel(form.selected.status) }}</span>
      </div>
      <div class="preview-row">
        <span class="muted">价格：</span>
        <span class="price">¥{{ form.selected.price }}</span>
      </div>
      <div class="preview-row">
        <span class="muted">发布者：</span>
        {{ form.selected.publisherNickname || '未知' }}
      </div>
    </div>

    <div class="tool-card__actions">
      <button v-if="form.selected" class="btn btn--sm" @click="showLineage(form.selected)">📜 传承链</button>
      <button v-if="form.selected" class="btn btn--sm btn--danger" :disabled="form.submitting || form.selected.status === ITEM_STATUS.OFF_SHELF" @click="handleForceOffShelf">
        {{ form.submitting ? '处理中' : '确认下架' }}
      </button>
    </div>
    <div v-if="form.result" class="tool-result" :class="form.resultType">
      {{ form.result }}
    </div>

    <!-- 传承链弹窗（D3） -->
    <div v-if="lineageDialog.visible" class="modal-overlay" @click.self="lineageDialog.visible = false">
      <div class="modal-card card">
        <h3 class="modal-title">📜 商品传承链</h3>
        <p class="muted" style="margin-bottom: 18px">{{ lineageDialog.data?.itemTitle }}</p>

        <div v-if="lineageDialog.loading" class="muted" style="text-align: center; padding: 20px">加载中...</div>
        <div v-else-if="lineageDialog.data?.chain?.length" class="lineage-chain">
          <div v-for="(node, i) in lineageDialog.data.chain" :key="i" class="lineage-node">
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
                <template v-if="node.price">¥{{ node.price }} ·</template>
                {{ formatDateTime(node.time) }}
              </div>
            </div>
          </div>
        </div>
        <div v-else class="muted" style="text-align: center; padding: 20px">暂无传承记录（商品尚未交易）</div>

        <div class="modal-actions">
          <button class="btn" @click="lineageDialog.visible = false">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import AppSelect from '@/components/common/AppSelect.vue'
import { forceOffShelf, getItemLineage, searchAdminItems } from '@/api/admin'
import type { Item, ItemLineage } from '@/types/models'
import { ITEM_STATUS, ITEM_STATUS_OPTIONS } from '@/constants/domain'
import { itemStatusBadge, itemStatusLabel } from '@/utils/trade'
import { formatDate, formatDateTime } from '@/utils/format'
import './manage-cards.css'

const STATUS_FILTER_OPTIONS = [{ label: '全部状态', value: '' }, ...ITEM_STATUS_OPTIONS.filter(({ value }) => value !== ITEM_STATUS.REVIEWING)]

interface ForceOffShelfFormState {
  keyword: string
  statusFilter: string
  searching: boolean
  searched: boolean
  items: Item[]
  selectedId: number | null
  selected: Item | null
  submitting: boolean
  result: string
  resultType: string
}

const form = reactive<ForceOffShelfFormState>({
  keyword: '',
  statusFilter: '',
  searching: false,
  searched: false,
  items: [],
  selectedId: null,
  selected: null,
  submitting: false,
  result: '',
  resultType: ''
})

async function searchItems() {
  const kw = form.keyword.trim()
  if (!kw) {
    ElMessage.warning('请输入商品标题或 ID')
    return
  }
  form.searching = true
  form.searched = false
  form.items = []
  form.selected = null
  form.selectedId = null
  form.result = ''
  try {
    const res = await searchAdminItems({
      keyword: kw,
      status: form.statusFilter || undefined,
      page: 1,
      size: 20
    })
    form.items = res.data?.records || []
    form.searched = true
  } catch {
    ElMessage.error('搜索失败')
  } finally {
    form.searching = false
  }
}

function selectItem(it: Item) {
  form.selectedId = it.id
  form.selected = it
  form.result = ''
}

async function handleForceOffShelf() {
  const it = form.selected
  if (!it) return
  try {
    await ElMessageBox.confirm(`确认强制下架「${it.title}」(#${it.id})？此操作只改变商品状态，不会自动扣分或封禁卖家。`, '强制下架', {
      confirmButtonText: '确认下架',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  form.submitting = true
  form.result = ''
  try {
    await forceOffShelf(it.id)
    form.result = '✅ 商品已强制下架，未对卖家账号执行处罚'
    form.resultType = 'success'
    it.status = ITEM_STATUS.OFF_SHELF
    // 同步更新列表中同商品状态
    const inList = form.items.find((i) => i.id === it.id)
    if (inList) inList.status = ITEM_STATUS.OFF_SHELF
  } catch (e) {
    // axios 错误形状（统一拦截器外抛出的原始错误）
    const err = e as { response?: { data?: { message?: string } } }
    form.result = '❌ ' + (err.response?.data?.message || '操作失败')
    form.resultType = 'error'
  } finally {
    form.submitting = false
  }
}

// ---- 传承链（D3） ----
const lineageDialog = reactive({
  visible: false,
  loading: false,
  data: null as ItemLineage | null
})

async function showLineage(item: Item) {
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
</script>
