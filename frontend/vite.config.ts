import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const elementPlusResolver = ElementPlusResolver({ importStyle: 'css' })
// Vitest 并行 worker 会同时运行多个插件实例：测试模式关闭 d.ts 写入，
// 只读取已提交的声明文件，消除竞争写导致的抖动与脏工作区。
// 容器 dev 同理（VITE_DTS_WRITE=0）：dev server 启动初期只按已访问页面
// 生成 d.ts，会把提交版里尚未访问到的条目（如 ElMessageBox）摘掉，
// 造成 CI 卫生门禁必挂的脏 diff——容器内只读不写，主机默认行为不变。
const writeDts = !process.env.VITEST && process.env.VITE_DTS_WRITE !== '0'

/**
 * 依赖预构建清单（仅 dev server 生效，Vitest 走自己的 deps.inline 管线）。
 *
 * ElementPlusResolver 按需注入的 "element-plus/es/components/[组件]/style/css"
 * 系列入口只在 .vue 模板被转换时才出现，启动扫描发现不了：首个访问该页面的导航会
 * 触发运行时依赖发现 → 重新预构建 → Vite 强制整页刷新，导航在组件就绪前被
 * 打断（表现为"首次点击停在原页面，再点一次才跳转"）。这里在启动时一次性
 * 预构建全部用到的组件样式入口，访问任何页面都不再产生新的优化轮次。
 *
 * 注意：element-plus 的 exports 只有 "./es/*" 兜底键，Vite 对 include 里的
 * glob 也是按 exports 键展开的，因此通配符写法无效，必须显式列出。
 * 新增页面用到清单外的 el-* 组件时，将其组件名追加到下方列表（可用
 * `grep -rhoE "<el-[a-z-]+" src | sort -u` 核对模板用法）。
 */
const elementPlusStyleComponents = [
  'base', // 全局基础样式（各组件样式入口的上游）
  'config-provider',
  'date-picker',
  'dialog',
  'dropdown',
  'dropdown-item',
  'dropdown-menu',
  'form',
  'form-item',
  'icon',
  'input',
  'loading',
  'message',
  'message-box',
  'option',
  'pagination',
  'select',
  'skeleton',
  'upload'
]
// 预构建清单与 d.ts 写入是两件互相独立的事，只取决于"是否测试模式"：
// 容器 dev 关掉 d.ts 写入时仍必须预构建，否则首个访问新页面的导航会被运行时
// 依赖发现打断（表现为"第一次点击停在原页面，再点一次才跳转"）。
const optimizeDepsInclude = process.env.VITEST ? undefined : ['element-plus/es', ...elementPlusStyleComponents.map((component) => `element-plus/es/components/${component}/style/css`)]
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
    // includeAbsolute: false —— 模板里的静态绝对路径（如 <img src="/logo.png">）保持字面量：
    // plugin-vue 在构建/测试模式默认把绝对路径也转成模块导入，public 目录资产会被
    // Vitest 当模块解析，Windows 上 file:///logo.png 触发 fileURLToPath 抛错（全组件测试挂）。
    // 注意这是全局开关：此后模板绝对路径只允许指向 public 目录资产；指向 src/assets 的
    // 绝对路径（如 src="/assets/x.png"）不会被转成导入，构建不报错、dev/build 运行时 404。
    // 引用 src/assets 请用相对/别名路径（../assets、@/assets），让 plugin-vue 正常转导入。
    vue({ template: { transformAssetUrls: { includeAbsolute: false } } }),
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
  optimizeDeps: {
    include: optimizeDepsInclude
  },
  build: {
    target: 'es2022',
    modulePreload: { polyfill: false },
    cssCodeSplit: true,
    rollupOptions: {
      output: {
        // Element Plus（含按需样式入口）聚合为单一稳定 vendor chunk：
        // 此前 EP 样式随首用页面拆散成多个 css-*.js 小分块，任何页面改动
        // 都会让它们的缓存集体失效；聚合后业务迭代不再动摇 EP 的缓存命中
        manualChunks(id) {
          if (id.includes('node_modules/element-plus') || id.includes('node_modules/@element-plus')) return 'element-plus'
        }
      }
    }
  },
  server: {
    host: process.env.VITE_HOST ?? '127.0.0.1',
    port: 3000,
    proxy: backendProxy
  },
  preview: {
    host: process.env.VITE_HOST ?? '127.0.0.1',
    port: 3000,
    proxy: backendProxy
  }
})
