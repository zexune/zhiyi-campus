import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import configPrettier from 'eslint-config-prettier'
import globals from 'globals'

/**
 * ESLint 10 扁平配置 —— 质量规则为主，格式化交给 Prettier
 * （eslint-config-prettier 置于末尾，关闭与其冲突的样式类规则）。
 */
export default [
  {
    ignores: ['dist/**', 'coverage/**', 'node_modules/**', 'playwright-report/**', 'test-results/**']
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  configPrettier,
  {
    files: ['**/*.{js,mjs,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
        // unplugin-auto-import 注入的全局标识符（vite.config.js AutoImport 配置）
        ElMessage: 'readonly',
        ElMessageBox: 'readonly',
        useUserStore: 'readonly'
      }
    },
    rules: {
      // 根组件与页面级单文件组件不受多词命名约束
      'vue/multi-word-component-names': ['error', { ignores: ['App'] }]
    }
  },
  {
    files: ['vite.config.js', 'vitest.config.js', 'playwright.config.js', 'eslint.config.js', 'tests/**'],
    languageOptions: {
      globals: {
        ...globals.node
      }
    }
  },
  {
    files: ['tests/e2e/**'],
    languageOptions: {
      globals: {
        ...globals.browser,
        // Playwright 注入的测试对象
        test: 'readonly',
        expect: 'readonly',
        page: 'readonly',
        request: 'readonly'
      }
    }
  }
]
