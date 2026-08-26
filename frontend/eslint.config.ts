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
    // src/types/api.gen.d.ts 由 openapi-typescript 生成，不参与 lint
    ignores: ['dist/**', 'coverage/**', 'node_modules/**', 'playwright-report/**', 'test-results/**', 'auto-imports.d.ts', 'components.d.ts', 'src/types/api.gen.d.ts']
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
    files: ['vite.config.ts', 'vitest.config.ts', 'playwright.config.ts', 'eslint.config.ts', 'scripts/**', 'tests/**'],
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
  },
  {
    // P6：业务 API 模块的传输契约只经 contracts.ts——禁止直接导入生成文件
    // 或借底层 request.get<T> 手写响应类型（mappers 仅允许类型导入 ApiResult）
    files: ['src/api/**/*.ts'],
    ignores: ['src/api/mappers.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/api.gen', '**/api.gen.*', '@/types/api.gen'],
              message: 'API 模块不得直接导入生成契约：生成 schema 统一经 @/types/contracts 再导出（Schemas/QueryOf）'
            },
            {
              group: ['@/utils/request'],
              message: 'API 模块不得直接调用底层 request.*：请求与响应类型必须来自 @/types/contracts 的 operation 契约'
            }
          ]
        }
      ]
    }
  },
  {
    files: ['src/api/mappers.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/api.gen', '**/api.gen.*', '@/types/api.gen'],
              message: 'mapper 只消费 ApiResult 信封，不感知生成 schema'
            }
          ]
        }
      ]
    }
  }
)
