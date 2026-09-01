/**
 * IME 安全的 Enter 处理 —— 聊天发送与标签提交共用。
 *
 * 背景：中文输入法组词时按 Enter 是"上屏候选词"，此时 keydown 事件带
 * isComposing=true（Safari 旧版用 keyCode 229 表达同一含义）。若直接把
 * keydown.enter 绑定到发送/提交，会把尚未上屏的拼音或半截内容误发出去。
 * 用法：@keydown.enter.exact="onEnter"（不加 .prevent），由本 composable
 * 在非合成输入时才 preventDefault 并执行动作；合成期放行默认行为（上屏）。
 */
export function useImeSafeEnter(handler: () => void) {
  // 参数按 Event 声明以兼容 el-input 的按键处理器签名（内部按 KeyboardEvent 消费）
  function onEnter(event: Event): void {
    const e = event as KeyboardEvent
    // isComposing / keyCode 229：输入法合成期间的 Enter，属于选词上屏而非提交
    if (e.isComposing || e.keyCode === 229) return
    e.preventDefault()
    handler()
  }
  return { onEnter }
}
