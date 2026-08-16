<template>
  <DefaultLayout>
    <div class="publish-page">
      <header class="publish-head rise">
        <div>
          <h1 class="page-title">
            {{ editMode ? '编辑商品信息' : '发布一件好物' }}
            <span class="stamp">{{ editMode ? '重新检测' : '本地检测' }}</span>
          </h1>
          <p class="muted">{{ editMode ? '修改后重新执行本地合规检测；违规整改会交由管理员复核' : '提交后在本机服务内完成规则检测与标签生成，命中风险再转人工审核' }}</p>
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
                <span class="t-icon">🔄</span>
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
                <span class="t-icon">🏃</span>
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

          <div class="form-pair">
            <el-form-item prop="categoryId" class="field">
              <label for="publish-category">
                所属大类
                <span class="req">*</span>
              </label>
              <AppSelect id="publish-category" v-model="form.categoryId" :options="categoryOptions" placeholder="选择一个大类" aria-label="所属大类" />
              <p class="hint">系统会根据分类与文本在本地生成普通商品标签</p>
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
                  {{ uploading ? '上传中…' : `添加图片 ${form.images.length}/9` }}
                </div>
              </el-upload>
            </div>
            <p class="hint">支持 jpg / png / webp，单张 ≤ 5MB，最多 9 张；首张自动作为封面</p>
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

          <div class="submit-bar">
            <span class="submit-note" aria-live="polite">
              <svg viewBox="0 0 24 24" fill="none" stroke="#2F9E62" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
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

        <aside class="card review-panel rise rise-2">
          <h2>
            <span class="review-mark">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />
                <path d="m9 12 2 2 4-4" />
              </svg>
            </span>
            本地合规检测
          </h2>
          <div class="review-flow">
            <div v-for="(step, index) in reviewSteps" :key="step.title" class="review-step">
              <div class="review-step__rail">
                <span class="review-step__dot">{{ index + 1 }}</span>
                <span v-if="index < reviewSteps.length - 1" class="review-step__line" />
              </div>
              <div class="review-step__body">
                <b>{{ step.title }}</b>
                <p>{{ step.description }}</p>
              </div>
            </div>
          </div>
          <div class="tag-demo">
            <p>
              「{{ form.title || '99新苹果平板 iPad Air5，考研结束出' }}」
              <br />
              可能生成的商品标签 ↓
            </p>
            <div>
              <span v-for="tag in previewTags" :key="tag" class="tag">{{ tag }}</span>
            </div>
          </div>
          <ul class="rule-list">
            <li v-for="rule in rulesText" :key="rule">
              <svg viewBox="0 0 24 24" fill="none" stroke="#E23B3B" stroke-width="2.4" stroke-linecap="round">
                <circle cx="12" cy="12" r="9" />
                <path d="m4.9 4.9 14.2 14.2" />
              </svg>
              {{ rule }}
            </li>
          </ul>
        </aside>
      </div>
    </div>
  </DefaultLayout>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppSelect from '@/components/common/AppSelect.vue'
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
import { getCategories, getOwnItem, publishItem, updateItem, uploadItemImage } from '@/api/item'
import { ITEM_TYPE, MODERATION_STATUS } from '@/constants/domain'
import { ROUTE_PATH } from '@/constants/routes'

const MAX_IMAGE_SIZE = 5 * 1024 * 1024
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const locations = ['图书馆门口', '一食堂', '南门快递站', '体育馆']
const reviewSteps = [
  { title: '规则检测', description: '使用确定性的本地规则识别违禁品、代考代写等风险内容' },
  { title: '生成标签', description: '从分类、标题与描述中提取普通标签，便于搜索和发现' },
  { title: '分流处理', description: '未命中风险直接上架，命中风险则隐藏并交由管理员复核' }
]
const rulesText = ['违禁品、管制物品会转入人工审核', '代写论文、代考等学术不端服务不允许发布', '价格、图片等无法由文本规则确认的问题通过用户举报核实']

const router = useRouter()
const route = useRoute()
const formRef = ref(null)
const categories = ref([])
const categoryOptions = computed(() => categories.value.map((category) => ({ label: category.name, value: category.id })))
const uploading = ref(false)
const submitting = ref(false)
const pageLoading = ref(false)
const form = reactive({ type: ITEM_TYPE.SELL, title: '', description: '', categoryId: '', price: 1, images: [], tradeLocation: '', pickupLocation: '', deliveryLocation: '' })
const editMode = computed(() => Boolean(route.params.id))
const submitButtonText = computed(() => {
  if (uploading.value) return '图片上传中'
  if (submitting.value) return editMode.value ? '重新检测中' : '检测中'
  if (pageLoading.value) return '数据加载中'
  return editMode.value ? '保存修改' : '提交发布'
})
const submitNote = computed(() => {
  if (uploading.value) return '图片正在上传，完成后即可提交'
  if (submitting.value) return '正在执行本地规则检测并生成商品标签，请稍候'
  return editMode.value ? '保存后将重新执行本地合规检测' : '提交后将在服务端本地完成合规检测'
})
const previewTags = computed(() => {
  const words = form.title.match(/[A-Za-z][A-Za-z0-9]*|[\u4e00-\u9fa5]{2,4}/g) || []
  return [...new Set(words)].slice(0, 4).length ? [...new Set(words)].slice(0, 4) : ['校园闲置', '当面交易', '好物']
})
const rules = {
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

async function fetchCategories() {
  const res = await getCategories()
  categories.value = res.data || []
}
async function fetchOwnItem() {
  pageLoading.value = true
  try {
    const res = await getOwnItem(route.params.id)
    const item = res.data
    Object.assign(form, {
      type: item.type,
      title: item.title || '',
      description: item.description || '',
      categoryId: item.categoryId,
      price: Number(item.price),
      images: Array.isArray(item.images) ? item.images : [],
      tradeLocation: item.tradeLocation || '',
      pickupLocation: item.pickupLocation || '',
      deliveryLocation: item.deliveryLocation || ''
    })
  } finally {
    pageLoading.value = false
  }
}
function setType(type) {
  form.type = type
  if (type === ITEM_TYPE.SWAP) form.price = null
  else if (type === ITEM_TYPE.ERRAND && (!form.price || form.price > 20)) form.price = 5
  else if (form.price == null) form.price = 1
  formRef.value?.clearValidate(['price', 'tradeLocation', 'pickupLocation', 'deliveryLocation'])
  formRef.value?.validateField('type')
}
function validateImage(file) {
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
async function handleFileChange(uploadFile) {
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
function removeImage(index) {
  form.images.splice(index, 1)
  formRef.value?.validateField('images')
}
function saveDraft() {
  localStorage.setItem('zhiyi-publish-draft', JSON.stringify({ ...form, images: [] }))
  ElMessage.success('草稿已保存在本机')
}
async function handleSubmit() {
  if (submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('请检查并补全标红的必填项')
    await nextTick()
    document.querySelector('.pub-card .el-form-item.is-error')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    return
  }
  submitting.value = true
  try {
    const payload = {
      type: form.type,
      title: form.title,
      description: form.description,
      categoryId: form.categoryId,
      price: form.price,
      images: form.images,
      tradeLocation: form.type === ITEM_TYPE.ERRAND ? null : form.tradeLocation,
      pickupLocation: form.pickupLocation,
      deliveryLocation: form.deliveryLocation
    }
    const res = editMode.value ? await updateItem(route.params.id, payload) : await publishItem(payload)
    if (!editMode.value) localStorage.removeItem('zhiyi-publish-draft')
    if (res.data?.moderationStatus === MODERATION_STATUS.PENDING) {
      ElMessage.warning(editMode.value ? '修改已提交，正在等待管理员复核' : '检测到风险内容，已提交管理员审核')
      router.push(ROUTE_PATH.MY_ITEMS)
    } else {
      ElMessage.success(editMode.value ? '修改成功，商品已通过本地检测' : '发布成功，商品已进入交易大厅')
      router.push(ROUTE_PATH.item(res.data.id))
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
      const saved = JSON.parse(draft)
      Object.keys(form).forEach((key) => {
        if (Object.hasOwn(saved, key)) form[key] = saved[key]
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
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 28px;
  margin-top: 30px;
  align-items: start;
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
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 28px;
}
.type-option {
  min-width: 0;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-m);
  padding: 18px 20px;
  cursor: pointer;
  background: var(--white);
  color: var(--ink);
  display: flex;
  gap: 14px;
  align-items: center;
  text-align: left;
  transition: all 0.18s;
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
  border: var(--bw) solid var(--ink);
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
  border: var(--bw) solid var(--ink);
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
  border: 1.5px solid var(--ink);
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
  border: 2px dashed var(--ink-soft);
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
  transition: all 0.18s;
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
  margin-top: 28px;
  padding-top: 22px;
  border-top: 1.5px dashed #e0d6c2;
  flex-wrap: wrap;
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
.review-panel {
  padding: 24px;
  position: sticky;
  top: 84px;
}
.review-panel h2 {
  font-family: var(--font-display);
  font-size: 21px;
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 14px;
}
.review-mark {
  width: 34px;
  height: 34px;
  border: var(--bw) solid var(--ink);
  border-radius: var(--r-s);
  background: var(--yellow);
  display: grid;
  place-items: center;
  transform: rotate(-5deg);
  box-shadow: 2px 2px 0 var(--ink);
}
.review-mark svg {
  width: 20px;
  height: 20px;
}
.review-flow {
  display: flex;
  flex-direction: column;
}
.review-step {
  display: flex;
  gap: 12px;
}
.review-step__rail {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.review-step__dot {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border: var(--bw) solid var(--ink);
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--white);
  font-weight: 900;
  font-family: var(--font-display);
}
.review-step:nth-child(1) .review-step__dot {
  background: var(--yellow);
}
.review-step:nth-child(2) .review-step__dot {
  background: #cbe8ff;
}
.review-step:nth-child(3) .review-step__dot {
  background: #d6f2df;
}
.review-step__line {
  width: 2px;
  flex: 1;
  min-height: 18px;
  background: var(--ink);
  opacity: 0.3;
}
.review-step__body {
  padding-bottom: 18px;
}
.review-step__body b {
  font-size: 14.5px;
}
.review-step__body p {
  margin-top: 2px;
  color: var(--ink-soft);
  font-size: 12.5px;
}
.tag-demo {
  margin-top: 8px;
  border: 1.5px dashed var(--ink);
  border-radius: var(--r-s);
  background: var(--paper-deep);
  padding: 14px 16px;
}
.tag-demo p {
  color: var(--ink-soft);
  font-size: 12.5px;
}
.tag-demo div {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.tag-demo .tag {
  animation: pop-tag 0.4s both;
}
@keyframes pop-tag {
  from {
    opacity: 0;
    transform: scale(0.6);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
.rule-list {
  margin-top: 16px;
  color: var(--ink-soft);
  font-size: 12.5px;
}
.rule-list li {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin-bottom: 6px;
  list-style: none;
}
.rule-list svg {
  width: 15px;
  height: 15px;
  flex: 0 0 15px;
  margin-top: 3px;
}
@media (max-width: 1000px) {
  .pub-wrap {
    grid-template-columns: 1fr;
  }
  .review-panel {
    position: static;
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
