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
    <el-option v-for="option in options" :key="option.key ?? option.value" :label="option.label" :value="option.value" :disabled="option.disabled" />
  </el-select>
</template>

<script lang="ts">
/** 下拉选项的通用形状（学校/类型/状态等选项共用；key、disabled 为可选扩展字段）。
    script setup 内不允许 export，类型置于并立 script 块导出供调用方引用。 */
export interface AppSelectOption {
  label: string
  value: string | number
  key?: string | number
  disabled?: boolean
}
</script>

<script setup lang="ts" generic="T extends string | number | object | null">
defineOptions({ inheritAttrs: false })

/* eslint-disable vue/require-default-prop -- v-model 泛型 prop 由全部调用方显式传入，默认值无意义 */

withDefaults(
  defineProps<{
    modelValue?: T
    options?: ReadonlyArray<AppSelectOption>
    placeholder?: string
    disabled?: boolean
    loading?: boolean
    clearable?: boolean
  }>(),
  {
    options: () => [],
    placeholder: '请选择',
    disabled: false,
    loading: false,
    clearable: false
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: T): void
  (e: 'change', value: T): void
}>()
</script>
