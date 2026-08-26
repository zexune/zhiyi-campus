import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const elementPlusResolver = ElementPlusResolver({ importStyle: 'css' })
// Vitest 并行 worker 会同时运行多个插件实例：测试模式关闭 d.ts 写入，
// 只读取已提交的声明文件，消除竞争写导致的抖动与脏工作区
const writeDts = !process.env.VITEST
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
      dts: writeDts ? './auto-imports.d.ts' : false
    }),
    Components({
      dirs: ['./src/components'],
      extensions: ['vue'],
      deep: true,
      resolvers: [elementPlusResolver],
      dts: writeDts ? './components.d.ts' : false
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
