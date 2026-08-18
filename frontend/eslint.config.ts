import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import configPrettier from 'eslint-config-prettier'
import globals from 'globals'

/**
 * ESLint 10 扁平配置 —— 质量规则为主，格式化交给 Prettier
 * （eslint-config-prettier 置于末尾，关闭与其冲突的样式类规则）。
 * TypeScript 解析由 @vue/eslint-config-typescript 提供（vue 文件走 vue-eslint-parser + TS 子解析器）。
 */
export default defineConfigWithVueTs(
  {
    ignores: ['dist/**', 'coverage/**', 'node_modules/**', 'playwright-report/**', 'test-results/**', 'auto-imports.d.ts', 'components.d.ts']
  },
  js.configs.recommended,
  pluginVue.configs['flat/recommended'],
  vueTsConfigs.recommended,
  configPrettier,
  {
    files: ['**/*.{mjs,ts,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
        // unplugin-auto-import 注入的全局标识符（vite.config.ts AutoImport 配置）
        ElMessage: 'readonly',
        ElMessageBox: 'readonly',
        useUserStore: 'readonly'
      }
    },
    rules: {
      // 根组件与页面级单文件组件不受多词命名约束
      'vue/multi-word-component-names': ['error', { ignores: ['App'] }],
      // 强制 .vue 使用 TypeScript（全量迁移已完成）
      'vue/block-lang': ['error', { script: { lang: 'ts' } }]
    }
  },
  {
    files: ['vite.config.ts', 'vitest.config.ts', 'playwright.config.ts', 'eslint.config.ts', 'tests/**'],
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
)
