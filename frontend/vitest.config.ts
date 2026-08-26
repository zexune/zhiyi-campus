import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'happy-dom',
      setupFiles: ['./tests/setup.ts'],
      include: ['./tests/**/*.{test,spec}.ts'],
      exclude: ['./tests/e2e/**', './node_modules/**', './dist/**'],
      clearMocks: true,
      restoreMocks: true,
      // CI 冷缓存下首个用例需完成模块转译（request.test.ts 每用例重置模块图）
      testTimeout: 30000,
      server: {
        deps: {
          // Element Plus 从包内导入样式；内联后由 Vite 处理 CSS，而不是交给 Node 直接加载。
          inline: [/element-plus/]
        }
      },
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html', 'lcov'],
        reportsDirectory: './coverage',
        include: ['src/**/*.{ts,js,vue}'],
        exclude: ['src/main.ts'],
        thresholds: {
          lines: 12,
          functions: 12,
          branches: 8,
          statements: 12,
          'src/components/trade/OrderReviewDialog.vue': {
            lines: 90,
            functions: 80,
            branches: 75,
            statements: 90
          },
          'src/views/admin/DashboardPage.vue': {
            lines: 80,
            functions: 80,
            branches: 65,
            statements: 75
          },
          // DashboardPage 拆出的热力图区块（D5）：纯展示计算，要求全行覆盖
          'src/views/admin/components/TradeHeatmap.vue': {
            lines: 100,
            functions: 100,
            branches: 100,
            statements: 100
          },
          'src/views/wallet/OrdersBoughtPage.vue': {
            lines: 80,
            functions: 80,
            branches: 65,
            statements: 80
          },
          'src/views/wallet/WalletPage.vue': {
            lines: 90,
            functions: 80,
            branches: 75,
            statements: 85
          },
          'src/utils/trade.ts': {
            lines: 100,
            functions: 100,
            branches: 80,
            statements: 100
          },
          // 前端基础设施（拆分/去重改造引入）：utils 与 composables 必须保持高覆盖
          'src/utils/format.ts': {
            lines: 100,
            functions: 100,
            branches: 85,
            statements: 100
          },
          'src/utils/formValidate.ts': {
            lines: 100,
            functions: 100,
            branches: 100,
            statements: 100
          },
          'src/composables/usePagedList.ts': {
            lines: 100,
            functions: 100,
            branches: 85,
            statements: 100
          },
          'src/constants/routes.ts': {
            lines: 100,
            functions: 100,
            branches: 100,
            statements: 100
          }
        }
      }
    }
  })
)
