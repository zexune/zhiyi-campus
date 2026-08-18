/**
 * el-form 声明式校验的统一入口：validate() 的 Promise 包装。
 * 通过返回 true；未通过（字段级错误已内联展示）返回 false，不向调用方抛出。
 *
 * 入参用结构化最小类型（持有 validate 的模板 ref），避免与 Element Plus
 * 具体类型耦合，测试里也能用普通对象替身。
 */
export interface ValidatableFormRef {
  value: { validate: () => Promise<unknown> } | null | undefined
}

export function validateForm(formRef: ValidatableFormRef): Promise<boolean> {
  const promise = formRef.value?.validate()
  if (!promise) return Promise.resolve(false)
  return promise.then(
    () => true,
    () => false
  )
}
