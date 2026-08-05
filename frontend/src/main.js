import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus, { messageConfig } from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './assets/styles/global.css'
import './assets/styles/element-overlays.css'

const app = createApp(App)

// 顶部操作提示保留默认 3 秒自动关闭，同时允许用户立即手动关闭。
messageConfig.showClose = true

// 统一注册所有 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
