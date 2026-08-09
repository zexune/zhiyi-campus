import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import { messageConfig } from 'element-plus'

import App from './App.vue'
import router from './router'
import './assets/styles/global.css'
import './assets/styles/element-overlays.css'

const app = createApp(App)
const pinia = createPinia()

pinia.use(piniaPluginPersistedstate)
messageConfig.showClose = true

app.use(pinia)
app.use(router)
app.mount('#app')
