<template>
  <DefaultLayout>
    <div class="publish-page">
      <header class="publish-head rise">
        <div>
          <h1 class="page-title">
            {{ editMode ? '编辑商品信息' : '发布一件好物' }}
            <span class="stamp">{{ editMode ? '重新检测' : '本地检测' }}</span>
          </h1>
        </div>
        <router-link :to="ROUTE_PATH.MY_ITEMS" class="btn">{{ editMode ? '返回我的发布' : '我的发布' }}</router-link>
      </header>

      <div v-loading="pageLoading" class="pub-wrap">
        <el-form ref="formRef" class="card pub-card rise rise-1" :model="form" :rules="rules">
          <el-form-item prop="type" class="type-form-item">
            <div class="type-switch" role="radiogroup" aria-label="发布类型">
              <button class="type-option" :class="{ selected: form.type === ITEM_TYPE.SELL }" type="button" role="radio" :aria-checked="form.type === ITEM_TYPE.SELL" @click="setType(ITEM_TYPE.SELL)">
                <span class="t-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z" />
                    <path d="M3 6h18M16 10a4 4 0 0 1-8 0" />
                  </svg>
                </span>
                <span class="type-copy">
                  <b>我要出闲置</b>
                  <small>卖掉宿舍吃灰的宝贝</small>
                </span>
              </button>
              <button
                class="type-option type-option--buy"
                :class="{ selected: form.type === ITEM_TYPE.BUY }"
                type="button"
                role="radio"
                :aria-checked="form.type === ITEM_TYPE.BUY"
                @click="setType(ITEM_TYPE.BUY)"
              >
                <span class="t-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="11" cy="11" r="7" />
                    <path d="m21 21-4.3-4.3M11 8v6M8 11h6" />
                  </svg>
                </span>
                <span class="type-copy">
                  <b>我要求购</b>
                  <small>发布需求，等卖家找上门</small>
                </span>
              </button>
              <button
                class="type-option type-option--swap"
                :class="{ selected: form.type === ITEM_TYPE.SWAP }"
                type="button"
                role="radio"
                :aria-checked="form.type === ITEM_TYPE.SWAP"
                @click="setType(ITEM_TYPE.SWAP)"
              >
                <span class="t-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 8h12l-3-3M20 16H8l3 3" /></svg>
                </span>
                <span class="type-copy">
                  <b>以物换物</b>
                  <small>用闲置交换另一件好物</small>
                </span>
              </button>
              <button
                class="type-option type-option--errand"
                :class="{ selected: form.type === ITEM_TYPE.ERRAND }"
                type="button"
                role="radio"
                :aria-checked="form.type === ITEM_TYPE.ERRAND"
                @click="setType(ITEM_TYPE.ERRAND)"
              >
                <span class="t-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="13" r="4" />
                    <path d="M12 3v2M4.6 6.6 6 8M2 13h2M22 13h-2M19.4 6.6 18 8M9 21h6" />
                  </svg>
                </span>
                <span class="type-copy">
                  <b>帮带跑腿</b>
                  <small>发布校内取送任务</small>
                </span>
              </button>
            </div>
          </el-form-item>

          <el-form-item prop="title" class="field">
            <label for="publish-title">
              商品标题
              <span class="req">*</span>
            </label>
            <input id="publish-title" v-model.trim="form.title" class="input" maxlength="50" placeholder="例如：99新 iPad Air5，考研结束出" />
            <div class="char-count">{{ form.title.length }} / 50</div>
          </el-form-item>

          <el-form-item prop="description" class="field">
            <label for="publish-description">
              商品描述
              <span class="req">*</span>
            </label>
            <textarea
              id="publish-description"
              v-model.trim="form.description"
              class="textarea"
              maxlength="500"
              placeholder="讲讲它的故事：入手渠道、成色、使用时长、配件情况……描述越详细，买家越容易了解"
            />
            <div class="char-count">{{ form.description.length }} / 500</div>
          </el-form-item>

          <div class="field">
            <label for="publish-tags">商品标签（可选，最多 6 个）</label>
            <TagInput id="publish-tags" v-model="form.tags" :suggestions="tagSuggestions" aria-label="商品标签" placeholder="输入后回车添加，如：95新、可小刀" @update:model-value="markTagsTouched" />
          </div>

          <div class="form-pair">
            <el-form-item prop="categoryId" class="field">
              <label for="publish-category">
                所属大类
                <span class="req">*</span>
              </label>
              <AppSelect id="publish-category" v-model="form.categoryId" :options="categoryOptions" placeholder="选择一个大类" aria-label="所属大类" />
            </el-form-item>
            <el-form-item prop="price" class="field">
              <label for="publish-price">
                {{ form.type === ITEM_TYPE.ERRAND ? '悬赏' : '价格' }}（元）
                <span v-if="form.type !== ITEM_TYPE.SWAP" class="req">*</span>
              </label>
              <input
                v-if="form.type !== ITEM_TYPE.SWAP"
                id="publish-price"
                v-model.number="form.price"
                class="input price-input"
                type="number"
                step="0.01"
                :min="form.type === ITEM_TYPE.ERRAND ? 1 : 0.01"
                :max="form.type === ITEM_TYPE.ERRAND ? 20 : undefined"
              />
              <div v-else class="input disabled-price">换物不涉及钱包结算</div>
              <p class="hint">{{ form.type === ITEM_TYPE.ERRAND ? '悬赏范围 ¥1–20' : form.type === ITEM_TYPE.SWAP ? '价格将保存为空' : '精确到分，最低 ¥0.01' }}</p>
            </el-form-item>
          </div>

          <el-form-item prop="images" class="field image-field">
            <label>
              商品图片
              <span class="req">*</span>
            </label>
            <div class="upload-grid">
              <div v-for="(image, index) in form.images" :key="image" class="upload-thumb">
                <img :src="image" :alt="`商品图${index + 1}`" />
                <span v-if="index === 0" class="main-flag">封面主图</span>
                <button class="del" type="button" aria-label="删除这张图片" @click="removeImage(index)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12" /></svg>
                </button>
              </div>
              <el-upload
                v-if="form.images.length < 9"
                class="upload-control"
                :auto-upload="false"
                :show-file-list="false"
                accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
                :on-change="handleFileChange"
              >
                <div class="upload-add" :class="{ disabled: uploading }">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14M5 12h14" /></svg>
                  {{ uploading ? '上传中…' : `${form.images.length} / 9` }}
                </div>
              </el-upload>
            </div>
            <p class="hint">jpg / png / webp · 单张 ≤ 5MB · 首张为封面</p>
          </el-form-item>

          <el-form-item v-if="form.type !== ITEM_TYPE.ERRAND" prop="tradeLocation" class="field">
            <label for="publish-location">
              交易地点
              <span class="req">*</span>
            </label>
            <input id="publish-location" v-model.trim="form.tradeLocation" class="input" maxlength="255" placeholder="如：图书馆门口、食堂三楼" />
            <div class="location-tags">
              <button v-for="location in locations" :key="location" class="tag" type="button" @click="form.tradeLocation = location">{{ location }}</button>
            </div>
          </el-form-item>

          <div v-if="form.type === ITEM_TYPE.ERRAND" class="form-pair">
            <el-form-item prop="pickupLocation" class="field">
              <label for="pickup-location">
                取件地点
                <span class="req">*</span>
              </label>
              <input id="pickup-location" v-model.trim="form.pickupLocation" class="input" maxlength="255" placeholder="如：南门快递站" />
            </el-form-item>
            <el-form-item prop="deliveryLocation" class="field">
              <label for="delivery-location">
                送达地点
                <span class="req">*</span>
              </label>
              <input id="delivery-location" v-model.trim="form.deliveryLocation" class="input" maxlength="255" placeholder="如：3号宿舍楼" />
            </el-form-item>
          </div>

          <p class="compliance-note">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="9" />
              <path d="m4.9 4.9 14.2 14.2" />
            </svg>
            发布内容将经过本地合规检测：违禁品与代写、代考等学术不端服务不允许发布。
          </p>

          <div class="submit-bar">
            <span class="submit-note" aria-live="polite">
              <svg viewBox="0 0 24 24" fill="none" stroke="var(--green)" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />
                <path d="m9 12 2 2 4-4" />
              </svg>
              {{ submitNote }}
            </span>
            <div class="submit-actions">
              <button v-if="!editMode" class="btn" type="button" @click="saveDraft">存草稿</button>
              <router-link v-else :to="`/item/${route.params.id}`" class="btn">取消</router-link>
              <button class="btn btn--primary btn--lg submit-button" type="button" :disabled="submitting || uploading || pageLoading" :aria-busy="submitting" @click="handleSubmit">
                <svg v-if="submitting" class="icon submit-spinner" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
                  <circle cx="12" cy="12" r="9" opacity=".3" />
                  <path d="M21 12a9 9 0 0 0-9-9" />
                </svg>
                <svg v-else class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="m22 2-7 20-4-9-9-4Z" />
                  <path d="M22 2 11 13" />
                </svg>
                {{ submitButtonText }}
              </button>
            </div>
          </div>
        </el-form>
      </div>
    </div>
  </DefaultLayout>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import AppSelect from '@/components/common/AppSelect.vue'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import TagInput from '@/components/common/TagInput.vue'
import { getCategories, getItemTagSuggestions, getOwnItem, publishItem, updateItem, uploadItemImage } from '@/api/item'
import type { Category, PublishItemPayload } from '@/types/models'
import { ITEM_TYPE, MODERATION_STATUS } from '@/constants/domain'
import type { ItemType } from '@/constants/domain'
import { ROUTE_PATH } from '@/constants/routes'

const MAX_IMAGE_SIZE = 5 * 1024 * 1024
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const locations = ['图书馆门口', '一食堂', '南门快递站', '体育馆']

/**
 * 发布表单：type 可在四种发布类型间切换；price 换物时置 null；
 * categoryId 由下拉写入数字或空串。用 type 而非 interface 声明，
 * 以便草稿回填时可整体收窄为按字段索引的记录。
 */
type PublishForm = {
  type: ItemType | string
  title: string
  description: string
  categoryId: string | number
  price: number | null
  images: string[]
  /** 用户选择的商品标签：建议预选，可增删可自定义 */
  tags: string[]
  tradeLocation: string
  pickupLocation: string
  deliveryLocation: string
}

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance | null>(null)
const categories = ref<Category[]>([])
const categoryOptions = computed(() => categories.value.map((category) => ({ label: category.name, value: category.id })))
const uploading = ref(false)
const submitting = ref(false)
const pageLoading = ref(false)
const form = reactive<PublishForm>({ type: ITEM_TYPE.SELL, title: '', description: '', categoryId: '', price: 1, images: [], tags: [], tradeLocation: '', pickupLocation: '', deliveryLocation: '' })
/** 标签建议（来自本地规则引擎，按标题+分类生成） */
const tagSuggestions = ref<string[]>([])
/** 用户是否手动调整过标签：调整后不再自动覆盖选择 */
let tagsTouched = false
let suggestTimer: number | undefined
/** 标签建议请求代数：清空关键词/卸载时递增，使在途旧响应落地前被丢弃 */
let suggestSeq = 0
const editMode = computed(() => Boolean(route.params.id))
const submitButtonText = computed(() => {
  if (uploading.value) return '图片上传中'
  if (submitting.value) return editMode.value ? '重新检测中' : '检测中'
  if (pageLoading.value) return '数据加载中'
  return editMode.value ? '保存修改' : '提交发布'
})
const submitNote = computed(() => {
  if (uploading.value) return '图片上传中，完成后即可提交'
  if (submitting.value) return '正在执行合规检测…'
  return editMode.value ? '保存后将重新执行合规检测' : '提交后自动完成合规检测'
})

// 标签建议：标题（≥2字）变化时防抖拉取（分类可选，用于提高建议质量）；用户手动调整过标签后不再自动覆盖已选
watch(
  () => [form.title, form.categoryId] as const,
  ([title]) => {
    const keyword = title.trim()
    if (keyword.length < 2) {
      // 清空关键词：作废在途请求与定时器，避免迟到响应回写旧建议
      ++suggestSeq
      window.clearTimeout(suggestTimer)
      tagSuggestions.value = []
      return
    }
    window.clearTimeout(suggestTimer)
    suggestTimer = window.setTimeout(async () => {
      // 代数守卫：记录本次请求序号，快速连续输入时乱序返回的旧响应一律丢弃
      const seq = ++suggestSeq
      try {
        const res = await getItemTagSuggestions(keyword, form.categoryId)
        if (seq !== suggestSeq) return
        const fresh = res.data || []
        tagSuggestions.value = fresh
        if (!tagsTouched) form.tags = fresh.slice(0, 6)
      } catch {
        // 建议失败不影响手动输入
      }
    }, 400)
  }
)

onUnmounted(() => {
  // 卸载后不再回写任何建议（含已在途的请求）
  ++suggestSeq
  window.clearTimeout(suggestTimer)
})
function markTagsTouched(): void {
  tagsTouched = true
}
const rules: FormRules = {
  type: [{ required: true, message: '请选择发布类型', trigger: 'change' }],
  title: [
    { required: true, message: '请输入商品标题', trigger: 'blur' },
    { min: 2, max: 50, message: '标题需为2-50字', trigger: 'blur' }
  ],
  categoryId: [{ required: true, message: '请选择所属大类', trigger: 'change' }],
  price: [
    {
      validator: (_rule, value, callback) => {
        if (form.type === ITEM_TYPE.SWAP) return callback()
        if (typeof value !== 'number' || value < (form.type === ITEM_TYPE.ERRAND ? 1 : 0.01) || (form.type === ITEM_TYPE.ERRAND && value > 20))
          return callback(new Error(form.type === ITEM_TYPE.ERRAND ? '悬赏须在 ¥1–20 之间' : '请输入有效价格'))
        callback()
      },
      trigger: 'change'
    }
  ],
  tradeLocation: [{ validator: (_rule, value, callback) => (form.type === ITEM_TYPE.ERRAND || value ? callback() : callback(new Error('请输入交易地点'))), trigger: 'blur' }],
  pickupLocation: [{ validator: (_rule, value, callback) => (form.type !== ITEM_TYPE.ERRAND || value ? callback() : callback(new Error('请输入取件地点'))), trigger: 'blur' }],
  deliveryLocation: [{ validator: (_rule, value, callback) => (form.type !== ITEM_TYPE.ERRAND || value ? callback() : callback(new Error('请输入送达地点'))), trigger: 'blur' }],
  description: [
    { required: true, message: '请输入商品描述', trigger: 'blur' },
    { max: 500, message: '描述不能超过500字', trigger: 'blur' }
  ],
  images: [{ type: 'array', required: true, min: 1, message: '请至少上传1张图片', trigger: 'change' }]
}

async function fetchCategories(): Promise<void> {
  const res = await getCategories()
  categories.value = res.data || []
}
async function fetchOwnItem(): Promise<void> {
  pageLoading.value = true
  try {
    // 路由 /item/:id/edit 的 param 恒为单值字符串
    const res = await getOwnItem(route.params.id as string)
    const item = res.data
    Object.assign(form, {
      type: item.type,
      title: item.title || '',
      description: item.description || '',
      categoryId: item.categoryId,
      price: Number(item.price),
      images: Array.isArray(item.images) ? item.images : [],
      tags: Array.isArray(item.tags) ? [...item.tags] : [],
      tradeLocation: item.tradeLocation || '',
      pickupLocation: item.pickupLocation || '',
      deliveryLocation: item.deliveryLocation || ''
    })
    // 编辑已有商品：现有标签视为用户已确认的选择，不再被建议覆盖
    if (form.tags.length) tagsTouched = true
  } finally {
    pageLoading.value = false
  }
}
function setType(type: ItemType): void {
  form.type = type
  if (type === ITEM_TYPE.SWAP) form.price = null
  else if (type === ITEM_TYPE.ERRAND && (!form.price || form.price > 20)) form.price = 5
  else if (form.price == null) form.price = 1
  formRef.value?.clearValidate(['price', 'tradeLocation', 'pickupLocation', 'deliveryLocation'])
  formRef.value?.validateField('type')
}
function validateImage(file: File): boolean {
  if (!IMAGE_TYPES.includes(file.type)) {
    ElMessage.error('仅支持 jpg、png、webp 图片')
    return false
  }
  if (file.size > MAX_IMAGE_SIZE) {
    ElMessage.error('单张图片不能超过 5MB')
    return false
  }
  if (form.images.length >= 9) {
    ElMessage.error('最多上传 9 张图片')
    return false
  }
  return true
}
async function handleFileChange(uploadFile: UploadFile): Promise<void> {
  const file = uploadFile.raw
  if (!file || !validateImage(file)) return
  uploading.value = true
  try {
    const res = await uploadItemImage(file)
    form.images.push(res.data.url)
    formRef.value?.validateField('images')
    ElMessage.success('图片上传成功')
  } finally {
    uploading.value = false
  }
}
function removeImage(index: number): void {
  form.images.splice(index, 1)
  formRef.value?.validateField('images')
}
function saveDraft(): void {
  localStorage.setItem('zhiyi-publish-draft', JSON.stringify({ ...form, images: [] }))
  ElMessage.success('草稿已保存在本机')
}
async function handleSubmit(): Promise<void> {
  if (submitting.value) return
  if (!formRef.value) return // 提交按钮位于表单内部，表单挂载后才可点击；此行只为类型收窄
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('请检查并补全标红的必填项')
    await nextTick()
    document.querySelector('.pub-card .el-form-item.is-error')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    return
  }
  submitting.value = true
  try {
    // 校验通过后 categoryId 必为数字、tradeLocation 仅在非跑腿时携带，单点收窄到载荷契约
    const payload = {
      type: form.type,
      title: form.title,
      description: form.description,
      categoryId: form.categoryId,
      price: form.price,
      images: form.images,
      tags: form.tags,
      tradeLocation: form.type === ITEM_TYPE.ERRAND ? null : form.tradeLocation,
      pickupLocation: form.pickupLocation,
      deliveryLocation: form.deliveryLocation
    } as PublishItemPayload
    const res = editMode.value ? await updateItem(route.params.id as string, payload) : await publishItem(payload)
    if (!editMode.value) localStorage.removeItem('zhiyi-publish-draft')
    if (res.data?.moderationStatus === MODERATION_STATUS.PENDING) {
      ElMessage.warning(editMode.value ? '修改已提交，正在等待管理员复核' : '检测到风险内容，已提交管理员审核')
      router.push(ROUTE_PATH.MY_ITEMS)
    } else {
      ElMessage.success(editMode.value ? '修改成功，商品已通过本地检测' : '发布成功，商品已进入交易大厅')
      // 非待审分支后端必然返回新商品 id，供跳转详情
      router.push(ROUTE_PATH.item(res.data.id as number))
    }
  } catch {
    // 具体错误由统一请求拦截器提示。
  } finally {
    submitting.value = false
  }
}
onMounted(async () => {
  await fetchCategories()
  if (editMode.value) {
    await fetchOwnItem()
    return
  }
  const draft = localStorage.getItem('zhiyi-publish-draft')
  if (draft) {
    try {
      const saved: Record<string, unknown> = JSON.parse(draft)
      Object.keys(form).forEach((key) => {
        // 草稿是本机 JSON，按字段名回填；经 unknown 中转后整体按记录写入
        if (Object.hasOwn(saved, key)) (form as unknown as Record<string, unknown>)[key] = saved[key]
      })
    } catch {
      localStorage.removeItem('zhiyi-publish-draft')
    }
  }
})
</script>

<style scoped>
.publish-page {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.publish-head {
  margin-top: 10px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}
.publish-head .muted {
  margin-top: 6px;
}
.pub-wrap {
  /* 显式 width:100% —— 父级是列向 flex，仅靠 auto 外边距会收缩为内容宽度，导致版心不稳定 */
  width: 100%;
  max-width: 1200px;
  margin: 30px auto 0;
}
.pub-card {
  padding: 30px 32px;
}
.type-form-item {
  margin-bottom: 0;
}
.type-switch {
  width: 100%;
  display: grid;
  /* minmax(0,1fr)：显式压制列的最小内容宽度，保证四张类型卡严格等宽 */
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 28px;
}
.type-option {
  min-width: 0;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-m);
  padding: 18px 20px;
  cursor: pointer;
  background: var(--white);
  color: var(--ink);
  display: flex;
  gap: 14px;
  align-items: center;
  text-align: left;
  transition:
    color 0.15s,
    background-color 0.15s,
    border-color 0.15s,
    box-shadow 0.15s,
    transform 0.15s;
  position: relative;
  overflow: hidden;
}
.type-option:hover {
  transform: translate(-2px, -2px);
  box-shadow: var(--shadow-s);
}
.t-icon {
  width: 48px;
  height: 48px;
  flex: 0 0 48px;
  border: var(--bw) solid var(--line);
  border-radius: var(--r-s);
  display: grid;
  place-items: center;
  background: var(--paper-deep);
  color: var(--ink);
}
.t-icon svg {
  width: 26px;
  height: 26px;
}
.type-copy {
  min-width: 0;
}
.type-copy b {
  display: block;
  font-size: 17px;
  font-family: var(--font-display);
}
.type-copy small {
  display: block;
  font-size: 13px;
  color: var(--ink-soft);
}
.type-option.selected {
  background: var(--primary);
  color: var(--white);
  box-shadow: var(--shadow-m);
}
.type-option--buy.selected {
  background: var(--blue);
}
.type-option--swap.selected {
  background: #8b5cf6;
}
.type-option--errand.selected {
  background: var(--green);
}
.type-option.selected small {
  color: rgba(255, 255, 255, 0.85);
}
.type-option.selected .t-icon {
  background: var(--yellow);
}
.type-option.selected::after {
  content: '✓';
  position: absolute;
  top: 8px;
  right: 12px;
  font-weight: 900;
  font-size: 18px;
}
.field {
  display: block;
  margin-bottom: 18px;
}
.field :deep(.el-form-item__content) {
  display: block;
  line-height: 1.6;
}
.field label {
  display: block;
  font-weight: 700;
  font-size: 14px;
  margin-bottom: 6px;
}
.req {
  color: var(--primary);
}
.char-count {
  text-align: right;
  font-size: 12px;
  color: var(--ink-soft);
  margin-top: 4px;
}
.hint {
  font-size: 12px;
  color: var(--ink-soft);
  margin-top: 5px;
}
.form-pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}
.price-input {
  color: var(--primary);
  font-size: 17px;
  font-weight: 900;
}
.disabled-price {
  color: var(--ink-soft);
  background: var(--paper-deep);
  cursor: not-allowed;
}
.upload-grid {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: 12px;
}
.upload-thumb,
.upload-control {
  aspect-ratio: 1;
  min-width: 0;
}
.upload-thumb {
  border: var(--bw) solid var(--line);
  border-radius: var(--r-s);
  position: relative;
  overflow: hidden;
  background: var(--paper-deep);
}
.upload-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.main-flag {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  text-align: center;
  background: var(--ink);
  color: var(--paper);
  font-size: 11px;
  font-weight: 700;
  padding: 2px 0;
}
.del {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 24px;
  height: 24px;
  border: var(--bw) solid var(--line);
  border-radius: 50%;
  background: var(--white);
  display: grid;
  place-items: center;
  cursor: pointer;
}
.del:hover {
  background: var(--red);
  color: var(--white);
}
.del svg {
  width: 11px;
  height: 11px;
}
.upload-control :deep(.el-upload) {
  display: block;
  width: 100%;
  height: 100%;
}
.upload-add {
  width: 100%;
  height: 100%;
  border: var(--bw) dashed var(--line-strong);
  border-radius: var(--r-s);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
  color: var(--ink-soft);
  font-size: 12.5px;
  font-weight: 700;
  background: var(--paper-deep);
  transition:
    color 0.15s,
    background-color 0.15s,
    border-color 0.15s,
    box-shadow 0.15s,
    transform 0.15s;
}
.upload-add:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: #fff1e9;
}
.upload-add.disabled {
  pointer-events: none;
  opacity: 0.65;
}
.upload-add svg {
  width: 26px;
  height: 26px;
}
.location-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.submit-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
  padding-top: 22px;
  border-top: var(--bw) solid var(--line);
  flex-wrap: wrap;
}
.compliance-note {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 24px;
  color: var(--ink-soft);
  font-size: 12.5px;
  line-height: 1.6;
}
.compliance-note svg {
  width: 15px;
  height: 15px;
  flex: 0 0 15px;
  margin-top: 3px;
  color: var(--ink-faint);
}
.submit-note {
  color: var(--ink-soft);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 7px;
}
.submit-note svg {
  width: 17px;
  height: 17px;
  flex: 0 0 17px;
}
.submit-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.submit-button {
  min-width: 146px;
}
.submit-spinner {
  animation: submit-spin 0.8s linear infinite;
}
@keyframes submit-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (max-width: 1000px) {
  .pub-wrap {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 700px) {
  .pub-card {
    padding: 22px 18px;
  }
  .publish-head {
    align-items: stretch;
    flex-direction: column;
  }
  .type-switch,
  .form-pair {
    grid-template-columns: 1fr;
  }
  .submit-actions {
    width: 100%;
  }
  .submit-actions .btn--primary {
    flex: 1;
  }
}
</style>
