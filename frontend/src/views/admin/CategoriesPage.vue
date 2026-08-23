<template>
  <AdminLayout>
    <div class="category-page rise">
      <div class="page-title">商品分类管理</div>

      <div class="category-layout">
        <section v-loading="loading" class="card category-list">
          <div class="section-head">
            <div>
              <h2>发布大类</h2>
            </div>
            <span class="count">{{ categories.length }} 个分类</span>
          </div>

          <div v-if="categories.length" class="rows">
            <button v-for="category in categories" :key="category.id" type="button" class="category-row" :class="{ active: form.id === category.id }" @click="edit(category)">
              <span class="category-icon">{{ category.icon || '📦' }}</span>
              <span class="category-name">{{ category.name }}</span>
              <span class="category-sort">排序 {{ category.sortOrder }}</span>
            </button>
          </div>
          <div v-else-if="!loading" class="empty muted">暂无分类，请先创建分类。</div>
        </section>

        <section class="card category-form">
          <h2>{{ form.id ? '编辑分类' : '新建分类' }}</h2>

          <div class="field">
            <label for="category-name">分类名称</label>
            <input id="category-name" v-model.trim="form.name" class="input" maxlength="50" placeholder="例如：数码电子" />
          </div>
          <div class="field">
            <label for="category-icon">分类图标</label>
            <input id="category-icon" v-model.trim="form.icon" class="input" maxlength="50" placeholder="📦" />
          </div>
          <div class="field">
            <label for="category-sort">排序值</label>
            <input id="category-sort" v-model.number="form.sortOrder" class="input" type="number" min="0" max="9999" />
          </div>

          <div class="actions">
            <button class="btn btn--primary" type="button" :disabled="saving" @click="save">
              {{ saving ? '保存中…' : form.id ? '保存修改' : '创建分类' }}
            </button>
            <button v-if="form.id" class="btn" type="button" @click="reset">取消编辑</button>
            <button v-if="form.id" class="btn btn--danger delete-btn" type="button" :disabled="saving" @click="remove">删除</button>
          </div>
        </section>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import { createCategory, deleteCategory, getAdminCategories, updateCategory } from '@/api/admin'
import type { Category } from '@/types/models'

interface CategoryFormState {
  id: number | null
  name: string
  icon: string
  sortOrder: number
}

const categories = ref<Category[]>([])
const loading = ref(false)
const saving = ref(false)
const form = reactive<CategoryFormState>({ id: null, name: '', icon: '📦', sortOrder: 0 })

async function load() {
  loading.value = true
  try {
    const res = await getAdminCategories()
    categories.value = res.data || []
  } finally {
    loading.value = false
  }
}

function edit(category: Category) {
  Object.assign(form, {
    id: category.id,
    name: category.name,
    icon: category.icon || '📦',
    sortOrder: Number(category.sortOrder || 0)
  })
}

function reset() {
  Object.assign(form, { id: null, name: '', icon: '📦', sortOrder: 0 })
}

async function save() {
  if (!form.name) {
    ElMessage.warning('请输入分类名称')
    return
  }
  saving.value = true
  try {
    const payload = { name: form.name, icon: form.icon || '📦', sortOrder: Number(form.sortOrder || 0) }
    if (form.id) await updateCategory(form.id, payload)
    else await createCategory(payload)
    ElMessage.success(form.id ? '分类修改成功' : '分类创建成功')
    reset()
    await load()
  } finally {
    saving.value = false
  }
}

async function remove() {
  if (form.id == null) return
  try {
    await ElMessageBox.confirm(`确认删除分类“${form.name}”？`, '删除分类', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  saving.value = true
  try {
    await deleteCategory(form.id)
    ElMessage.success('分类已删除')
    reset()
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.category-page {
  padding-top: 10px;
}
.category-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(300px, 0.8fr);
  gap: 24px;
  margin-top: 24px;
  align-items: start;
}
.category-list,
.category-form {
  padding: 26px;
}
.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.section-head h2,
.category-form h2 {
  font-family: var(--font-display);
  font-size: 21px;
}
.section-head p,
.category-form > p {
  margin-top: 5px;
  font-size: 13px;
}
.count {
  border: var(--bw) solid var(--line);
  border-radius: 999px;
  padding: 5px 10px;
  background: var(--yellow);
  font-weight: 800;
  font-size: 12px;
  white-space: nowrap;
}
.rows {
  margin-top: 20px;
  display: grid;
  gap: 10px;
}
.category-row {
  width: 100%;
  display: grid;
  grid-template-columns: 48px 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-s);
  background: var(--white);
  color: var(--ink);
  text-align: left;
  cursor: pointer;
  transition: 0.16s;
}
.category-row:hover,
.category-row.active {
  transform: translate(-2px, -2px);
  box-shadow: var(--shadow-s);
  background: var(--paper-deep);
}
.category-row.active {
  border-color: var(--primary);
}
.category-icon {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border: var(--bw) solid var(--line);
  border-radius: 10px;
  background: var(--yellow);
  font-size: 21px;
}
.category-name {
  font-weight: 900;
}
.category-sort {
  color: var(--ink-soft);
  font-size: 12px;
}
.empty {
  padding: 48px 0;
  text-align: center;
}
.category-form .field {
  margin-top: 18px;
}
.category-form label {
  display: block;
  margin-bottom: 6px;
  font-weight: 800;
  font-size: 14px;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
}
.delete-btn {
  margin-left: auto;
}
.hint {
  margin-top: 12px;
  color: var(--ink-soft);
  font-size: 12px;
}
@media (max-width: 850px) {
  .category-layout {
    grid-template-columns: 1fr;
  }
}
</style>
