/**
 * 品牌图标生成（单一真相源：assets/brand/logo-source.png）：
 * 一张入库的高清源图 → public/ 下全部图标资产，保证浏览器标签、站内导航、iOS
 * 主屏幕图标视觉一致；更换品牌时覆盖源图后重跑即可全量刷新。
 *
 * 用法：npm run gen:icons                        —— 用仓库内品牌源图
 *       node scripts/generate-icons.mjs <源图路径> —— 临时用指定图替换（不入库）
 *
 * 产物（尺寸为各端约定值）：
 *   public/favicon.png          32×32   浏览器标签页，index.html rel=icon；保留透明底
 *   public/logo.png             96×96   站内导航与登录页，衬纸色/白底显示；保留透明底
 *   public/apple-touch-icon.png 180×180 iOS「添加到主屏幕」；必须白色不透明底——
 *                                       iOS 会把透明区域合成成黑色，不能直接保留 alpha
 *
 * 降采样：PNG 没有 JPEG 的 DCT 缩放，libvips 只能全解码后用核函数重采样；
 * 1254 → 32 单步缩小 39×，lanczos3 固定支撑半径会在高对比边缘留下振铃晕圈。
 * 因此逐级二分到目标两倍以内，再一步收敛（每级中间产物为无损 PNG，无累积损失）。
 *
 * 源图刻意放在 src/assets 之外：它是构建输入而非应用资产。src/assets 约定只放
 * 被模块图引用的静态资源，零引用的源图放进去语义不符，整理时也易被误当废弃资产清走。
 */

import { mkdir, stat } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import process from 'node:process'
import sharp from 'sharp'

const here = path.dirname(fileURLToPath(import.meta.url))
const publicDir = path.join(here, '../public')
const brandSource = path.join(here, '../assets/brand/logo-source.png')

const [overrideArg] = process.argv.slice(2)
// 显式传参按 cwd 解析（CLI 惯例）；默认源基于脚本位置，任何目录执行都有效
const source = overrideArg ? path.resolve(overrideArg) : brandSource
// 源图缺失时尽早失败，避免生成半套图标覆盖现有资产
await stat(source)

const targets = [
  { file: 'favicon.png', size: 32, flatten: null },
  { file: 'logo.png', size: 96, flatten: null },
  { file: 'apple-touch-icon.png', size: 180, flatten: '#ffffff' }
]

const RESIZE = { fit: 'cover', position: 'centre' }

/** 分级二分降采样：先压平透明底（需要时），逐级减半到目标两倍以内再一步收敛 */
async function renderIcon(file, target, flatten) {
  const meta = await sharp(source).metadata()
  const srcSize = Math.max(meta.width || target, meta.height || target)
  let input = flatten ? await sharp(source).flatten({ background: flatten }).png().toBuffer() : source
  let current = srcSize
  while (current > target * 2) {
    current = Math.max(target * 2, Math.ceil(current / 2))
    input = await sharp(input).resize(current, current, RESIZE).png().toBuffer()
  }
  return sharp(input).resize(target, target, RESIZE).png().toFile(path.join(publicDir, file))
}

await mkdir(publicDir, { recursive: true })
for (const { file, size, flatten } of targets) {
  const info = await renderIcon(file, size, flatten)
  console.log(`${path.join('public', file)}: ${info.width}×${info.height}, ${info.size} bytes`)
}
