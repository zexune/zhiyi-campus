import assert from 'node:assert/strict'
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { test } from 'vitest'

/**
 * vite.config.ts 配置了 transformAssetUrls.includeAbsolute: false：模板静态绝对路径
 * 不转模块导入、保持字面量，因此只允许指向 public/ 资产。若写成 src="/assets/x.png"
 * （指向 src/assets），构建不报错、运行时静默 404——本巡检在 npm test 阶段拦截：
 * 静态绝对路径（含 CSS url()）必须命中 public/ 下真实文件，否则一律视为违规。
 */

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const publicDir = path.join(frontendRoot, 'public')

/** 白名单：合法但非 public 文件的绝对路径 */
const EXACT_ALLOWED = new Set(['/src/main.ts']) // index.html 的 Vite 入口
const PREFIX_ALLOWED = ['/api/'] // 后端端点导航（如文件下载直达），不是文件资产

interface Ref {
  file: string
  line: number
  attr: string
  value: string
}

/** 静态资产属性。负向后顾排除 :src 动态绑定与 data-src 一类长属性 */
const ATTR_RE = /(?<![:@\w-])(src|href|poster|srcset)\s*=\s*"([^"]*)"/g
/** CSS url() 引用（.vue 内联 <style> 由整文件扫描天然覆盖） */
const URL_RE = /url\((['"]?)([^'")]*)\1\)/g

function isAllowed(value: string): boolean {
  return EXACT_ALLOWED.has(value) || PREFIX_ALLOWED.some((prefix) => value.startsWith(prefix))
}

function extractAbsoluteRefs(content: string, file: string): Ref[] {
  const lineOf = (index: number) => content.slice(0, index).split('\n').length
  const refs: Ref[] = []
  for (const [kind, re] of [
    ['attr', ATTR_RE],
    ['url()', URL_RE]
  ] as const) {
    // srcset 可含多候选（"/a.png 1x, /b.png 2x"）：逐候选取首个空格前 token
    for (const m of content.matchAll(re)) {
      const values = m[2]
        .split(',')
        .map((part) => part.trim().split(/\s+/)[0])
        .filter(Boolean)
      for (const value of values) {
        if (value.startsWith('/') && !isAllowed(value)) {
          refs.push({ file, line: lineOf(m.index ?? 0), attr: kind === 'attr' ? m[1] : 'url()', value })
        }
      }
    }
  }
  return refs
}

function collectFiles(dir: string, exts: readonly string[]): string[] {
  const files: string[] = []
  for (const name of readdirSync(dir)) {
    const full = path.join(dir, name)
    if (statSync(full).isDirectory()) files.push(...collectFiles(full, exts))
    else if (exts.includes(path.extname(name))) files.push(full)
  }
  return files
}

test('静态绝对路径资产必须命中 public/ 真实文件（includeAbsolute:false 巡检）', () => {
  const targets = [...collectFiles(path.join(frontendRoot, 'src'), ['.vue', '.css']), path.join(frontendRoot, 'index.html')]
  const violations = targets.flatMap((file) =>
    extractAbsoluteRefs(readFileSync(file, 'utf8'), path.relative(frontendRoot, file).replace(/\\/g, '/'))
      .filter((ref) => !existsSync(path.join(publicDir, ref.value.slice(1))))
      .map((ref) => `${ref.file}:${ref.line}  ${ref.attr}="${ref.value}"`)
  )
  assert.deepEqual(
    violations,
    [],
    `绝对路径未命中 public/（includeAbsolute:false 下不转导入，构建不报错、运行时 404）。\n引用 src/assets 请改相对/别名路径；新增合法例外请扩充白名单：\n${violations.join('\n')}`
  )
})

test('提取器自检：命中静态绝对引用，跳过动态绑定/长属性/相对路径', () => {
  const fixture = ['<img src="/logo.png" />', '<img :src="dynamicUrl" />', '<a data-src="/miss.png" href="/apple-touch-icon.png">', "background: url('/bg.png')", 'srcset="/a.png 1x, /b.png 2x"'].join(
    '\n'
  )
  const hits = extractAbsoluteRefs(fixture, 'fixture.vue').map((ref) => `${ref.attr}=${ref.value}`)
  // 提取按“属性一轮、url() 一轮”进行，期望序与扫描序一致
  assert.deepEqual(hits, ['src=/logo.png', 'href=/apple-touch-icon.png', 'srcset=/a.png', 'srcset=/b.png', 'url()=/bg.png'])
})
