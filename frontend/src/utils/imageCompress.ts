/**
 * 上传前的客户端图片压缩 —— 用户手机直出的照片动辄 2000px+ / 数 MB，
 * 而列表卡片只有 ~300px 宽。长边压到 1600px、质量 0.85 后通常落到
 * 数百 KB，显著降低首屏 LCP 与移动流量（服务端 5MB 上限保持不变，
 * 压缩失败时原样返回原图，不阻断上传流程）。
 */
const MAX_LONG_EDGE = 1600
const JPEG_QUALITY = 0.85

export async function compressImage(file: File): Promise<File> {
  // PNG 透明通道经 canvas 重绘会丢失（toDataURL jpeg 无 alpha）；小图压缩无收益
  if (file.type === 'image/png' || file.size <= 300 * 1024) return file
  try {
    const bitmap = await createImageBitmap(file)
    const scale = Math.min(1, MAX_LONG_EDGE / Math.max(bitmap.width, bitmap.height))
    const width = Math.round(bitmap.width * scale)
    const height = Math.round(bitmap.height * scale)
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    if (!context) return file
    context.drawImage(bitmap, 0, 0, width, height)
    bitmap.close()
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY))
    // 压缩产物反而更大（极端 case：本就高度优化的 jpeg）时保留原图
    if (!blob || blob.size >= file.size) return file
    const name = file.name.replace(/\.[^.]+$/, '') + '.jpg'
    return new File([blob], name, { type: 'image/jpeg', lastModified: Date.now() })
  } catch {
    return file
  }
}
