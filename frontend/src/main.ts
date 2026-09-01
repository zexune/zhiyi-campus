import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { messageConfig } from 'element-plus'

import App from './App.vue'
import router from './router'
import './assets/styles/global.css'
import './assets/styles/element-overlays.css'
// 脚本侧使用的 EP 命令式组件的按需样式：此前由 unplugin-auto-import 的
// ElementPlusResolver 在识别到隐式标识符时顺带注入；改为显式 import 后
// 注入不再触发（构建产物缺基础样式，message/message-box 定位与层叠全丢），
// 故在此显式引入，与标识符同源可见。模板侧组件（el-dialog 等）仍由
// unplugin-vue-components 注入，无需在此重复。
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'

const app = createApp(App)
const pinia = createPinia()

// 登录态持久化统一由 utils/auth.js 承担（展示信息 + 响应式标记），
// store 不再整体持久化，避免出现第二份凭证/用户数据真相。
messageConfig.showClose = true

app.use(pinia)
app.use(router)
app.mount('#app')
