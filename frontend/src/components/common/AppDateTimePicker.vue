<template>
  <el-date-picker
    ref="pickerRef"
    v-bind="$attrs"
    :model-value="modelValue || null"
    class="app-date-time"
    type="datetime"
    format="YYYY年MM月DD日 HH:mm"
    date-format="YYYY年MM月DD日"
    time-format="HH:mm"
    value-format="YYYY-MM-DDTHH:mm"
    :popper-class="popperClass"
    :placeholder="placeholder"
    :clearable="clearable"
    :show-now="false"
    :disabled-date="disabledDate"
    @update:model-value="emitValue"
    @change="emitChange"
    @visible-change="handleVisibleChange"
  />
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

let pickerSequence = 0

defineOptions({ inheritAttrs: false })

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '请选择日期和时间' },
  clearable: { type: Boolean, default: true },
  min: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue', 'change'])

const pickerRef = ref(null)
const popperInstanceClass = `app-date-picker-${++pickerSequence}`
const popperClass = `app-date-picker ${popperInstanceClass}`
let emptyTimeSession = false

const minimumDate = computed(() => {
  if (!props.min) return null
  const value = new Date(props.min)
  return Number.isNaN(value.getTime()) ? null : value
})

function ownPanelFor(target) {
  if (!(target instanceof Element)) return null
  return target.closest(`.${popperInstanceClass}`)
}

function markEmptyTimeSession(event) {
  if (props.modelValue) return
  const panel = ownPanelFor(event.target)
  if (!panel) return
  const timeInput = event.target.closest(
    '.el-date-picker__time-header .el-date-picker__editor-wrap:last-child input',
  )
  if (timeInput) emptyTimeSession = true
}

function handlePanelClick(event) {
  const panel = ownPanelFor(event.target)
  if (!emptyTimeSession) return
  if (!panel) {
    emptyTimeSession = false
    return
  }

  const confirmButton = event.target.closest('.el-time-panel__btn.confirm')
  if (confirmButton) {
    emptyTimeSession = false
    return
  }

  const cancelButton = event.target.closest('.el-time-panel__btn.cancel')
  if (!cancelButton) {
    const insideTimePanel = event.target.closest('.el-time-panel')
    const insideTimeInput = event.target.closest(
      '.el-date-picker__time-header .el-date-picker__editor-wrap:last-child',
    )
    if (!insideTimePanel && !insideTimeInput) emptyTimeSession = false
    return
  }

  // Element Plus passes null into its time-panel cancel handler when the form
  // is empty. The session keeps its auto-seeded value as a draft; cancellation
  // closes the picker without committing it or invoking the broken path.
  event.preventDefault()
  event.stopImmediatePropagation()
  emptyTimeSession = false
  pickerRef.value?.handleClose()
}

function handleVisibleChange(visible) {
  if (!visible) emptyTimeSession = false
}

onMounted(() => {
  document.addEventListener('focusin', markEmptyTimeSession, true)
  document.addEventListener('click', handlePanelClick, true)
})
onBeforeUnmount(() => {
  document.removeEventListener('focusin', markEmptyTimeSession, true)
  document.removeEventListener('click', handlePanelClick, true)
})

function disabledDate(date) {
  if (!minimumDate.value) return false
  const endOfDay = new Date(date)
  endOfDay.setHours(23, 59, 59, 999)
  return endOfDay < minimumDate.value
}

function emitValue(value) {
  if (emptyTimeSession) return
  emit('update:modelValue', value || '')
}

function emitChange(value) {
  if (emptyTimeSession) return
  emit('change', value || '')
}
</script>
