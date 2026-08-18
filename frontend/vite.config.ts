import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const elementPlusResolver = ElementPlusResolver({ importStyle: 'css' })
const backendProxy = {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true
  },
  '/uploads': {
    target: 'http://localhost:8080',
    changeOrigin: true
  }
}

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      dirs: ['./src/stores'],
      resolvers: [elementPlusResolver],
      vueTemplate: true,
      // 生成的 auto-imports.d.ts 提交进仓库：CI 的 vue-tsc 先于 build，不能依赖 dev 时生成
      dts: './auto-imports.d.ts'
    }),
    Components({
      dirs: ['./src/components'],
      extensions: ['vue'],
      deep: true,
      resolvers: [elementPlusResolver],
      dts: './components.d.ts'
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    target: 'es2022',
    modulePreload: { polyfill: false },
    cssCodeSplit: true
  },
  server: {
    host: '127.0.0.1',
    port: 3000,
    proxy: backendProxy
  },
  preview: {
    host: '127.0.0.1',
    port: 3000,
    proxy: backendProxy
  }
})
