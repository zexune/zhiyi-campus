<template>
  <el-dialog v-model="dialogVisible" title="举报商品" width="min(520px, 92vw)" :close-on-click-modal="!submitting">
    <div class="report-form">
      <label>
        <span>举报类型</span>
        <AppSelect v-model="type" :options="REPORT_TYPE_OPTIONS" />
      </label>
      <label>
        <span>补充说明</span>
        <el-input v-model="details" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="请说明具体问题；选择“其他”时必填" />
      </label>
    </div>
    <template #footer>
      <button class="btn" :disabled="submitting" @click="dialogVisible = false">取消</button>
      <button class="btn btn--danger" :disabled="submitting" @click="submitReport">
        {{ submitting ? '提交中...' : '提交举报' }}
      </button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppSelect from '@/components/common/AppSelect.vue'
import { reportItem } from '@/api/item'

/**
 * 举报弹窗：自持表单状态与提交逻辑，成功后自行关闭。
 * 打开入口的登录守卫留在父级；每次 visible 置真时重置表单。
 */
const props = defineProps<{
  itemId: number
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const REPORT_TYPE_OPTIONS = [
  { label: '价格欺诈', value: 'PRICE_FRAUD' },
  { label: '违禁物品', value: 'PROHIBITED_ITEM' },
  { label: '图片违规', value: 'IMAGE_VIOLATION' },
  { label: '广告引流', value: 'ADVERTISING' },
  { label: '其他问题', value: 'OTHER' }
]

const type = ref('PRICE_FRAUD')
const details = ref('')
const submitting = ref(false)

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value)
})

watch(
  () => props.visible,
  (open) => {
    if (open) {
      type.value = 'PRICE_FRAUD'
      details.value = ''
    }
  }
)

async function submitReport(): Promise<void> {
  const trimmed = details.value.trim()
  if (type.value === 'OTHER' && !trimmed) {
    ElMessage.warning('选择“其他问题”时请填写补充说明')
    return
  }
  submitting.value = true
  try {
    await reportItem(props.itemId, { type: type.value, details: trimmed || null })
    dialogVisible.value = false
    ElMessage.success('举报已提交，管理员核实前不会影响商品展示')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.report-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.report-form label {
  display: flex;
  flex-direction: column;
  gap: 7px;
  font-weight: 600;
}
</style>
