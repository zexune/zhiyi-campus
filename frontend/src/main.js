import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { messageConfig } from 'element-plus'

import App from './App.vue'
import router from './router'
import './assets/styles/global.css'
import './assets/styles/element-overlays.css'

const app = createApp(App)
const pinia = createPinia()

// 登录态持久化统一由 utils/auth.js 承担（展示信息 + 响应式标记），
// store 不再整体持久化，避免出现第二份凭证/用户数据真相。
messageConfig.showClose = true

app.use(pinia)
app.use(router)
app.mount('#app')
