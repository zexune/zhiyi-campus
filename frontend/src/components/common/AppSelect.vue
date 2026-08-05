<template>
  <el-select
    v-bind="$attrs"
    :model-value="modelValue"
    class="app-select"
    popper-class="app-select-popper"
    :placeholder="placeholder"
    :disabled="disabled"
    :loading="loading"
    :clearable="clearable"
    no-data-text="暂无可选项"
    @update:model-value="emit('update:modelValue', $event)"
    @change="emit('change', $event)"
  >
    <el-option
      v-for="option in options"
      :key="option.key ?? option.value"
      :label="option.label"
      :value="option.value"
      :disabled="option.disabled"
    />
  </el-select>
</template>

<script setup>
defineOptions({ inheritAttrs: false })

defineProps({
  modelValue: { default: null },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择' },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  clearable: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'change'])
</script>
