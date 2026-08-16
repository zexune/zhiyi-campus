/**
 * el-form 声明式校验的统一入口：validate() 的 Promise 包装。
 * 通过返回 true；未通过（字段级错误已内联展示）返回 false，不向调用方抛出。
 */
export function validateForm(formRef) {
  const promise = formRef.value?.validate()
  if (!promise) return Promise.resolve(false)
  return promise.then(
    () => true,
    () => false
  )
}
