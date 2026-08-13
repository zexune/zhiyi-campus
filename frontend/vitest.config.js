import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.js'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'happy-dom',
      setupFiles: ['./tests/setup.js'],
      include: ['./tests/**/*.{test,spec}.js'],
      exclude: ['./tests/e2e/**', './node_modules/**', './dist/**'],
      clearMocks: true,
      restoreMocks: true,
      server: {
        deps: {
          // Element Plus 从包内导入样式；内联后由 Vite 处理 CSS，而不是交给 Node 直接加载。
          inline: [/element-plus/],
        },
      },
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html', 'lcov'],
        reportsDirectory: './coverage',
        include: ['src/**/*.{js,vue}'],
        exclude: ['src/main.js'],
        thresholds: {
          lines: 12,
          functions: 12,
          branches: 8,
          statements: 12,
          'src/components/trade/OrderReviewDialog.vue': {
            lines: 90,
            functions: 80,
            branches: 75,
            statements: 90,
          },
          'src/views/admin/DashboardPage.vue': {
            lines: 80,
            functions: 80,
            branches: 65,
            statements: 75,
          },
          'src/views/wallet/OrdersBoughtPage.vue': {
            lines: 80,
            functions: 80,
            branches: 65,
            statements: 80,
          },
          'src/views/wallet/WalletPage.vue': {
            lines: 90,
            functions: 80,
            branches: 75,
            statements: 85,
          },
          'src/utils/trade.js': {
            lines: 100,
            functions: 100,
            branches: 80,
            statements: 100,
          },
        },
      },
    },
  }),
)
