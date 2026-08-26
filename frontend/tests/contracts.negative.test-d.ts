/**
 * 类型负测试（P5-7）：用 @ts-expect-error 固化 operation 级请求契约的
 * 编译期约束——拼错路径、漏幂等头、漏必填 body、path id 类型错误、
 * 传入不存在的 query、完全省略必填 options、上传漏文件、JSON operation
 * 错用 postFile 都必须编译失败。本文件仅做类型检查（vue-tsc），
 * 运行时不执行任何请求；若某处不再报错，@ts-expect-error 自身会报
 * "Unused '@ts-expect-error' directive"，契约弱化立即暴露。
 */
import { contracts } from '@/types/contracts'

declare const imageFile: File

export function negativeContractCases(): void {
  // 拼错路径（不存在的 operation）
  // @ts-expect-error 路径必须存在于生成的 paths
  contracts.get('/api/order/not-exist')

  // 完全省略必填 options（/api/order/create 有必填 body + 必填 header）
  // @ts-expect-error 必填 body/header 存在时 options 不可省略
  contracts.post('/api/order/create')

  // 漏必填 header（资金操作幂等键）
  // @ts-expect-error X-Idempotency-Key 是必填 header
  contracts.post('/api/order/create', { body: { itemId: 9 } })

  // 漏必填 body
  // @ts-expect-error CreateOrderDTO.itemId 必填
  contracts.post('/api/order/create', { headers: { 'X-Idempotency-Key': 'k' } })

  // path id 类型错误（模板路径要求 number）
  // @ts-expect-error path 参数 id 必须是 number
  contracts.get('/api/item/{id}', { path: { id: 'not-a-number' } })

  // 传入不存在的 query
  // @ts-expect-error query 名必须来自生成的 operation
  contracts.get('/api/order/my-bought', { query: { noSuchParam: 1 } })

  // 无 query 参数的 operation 传入 query
  // @ts-expect-error /api/item/{id} 没有 query 参数（query?: never）
  contracts.get('/api/item/{id}', { path: { id: 1 }, query: { page: 1 } })

  // 无 header 参数的 operation 传入 headers
  // @ts-expect-error /api/order/my-bought 没有声明任何 header
  contracts.get('/api/order/my-bought', { headers: { 'X-Idempotency-Key': 'k' } })

  // 无 path 参数的 operation 传入 path
  // @ts-expect-error /api/item/tag-suggestions 是静态路径，没有 path 参数
  contracts.post('/api/item/tag-suggestions', { body: { title: '教材' }, path: { id: 1 } })

  // 模板路径漏 path 参数（连同必填 header，options 本身必传）
  // @ts-expect-error {id} 占位符必须填充
  contracts.put('/api/order/{id}/confirm', { headers: { 'X-Idempotency-Key': 'k' } })

  // 多余 path 参数
  // @ts-expect-error 只能提供已声明的 path 参数
  contracts.get('/api/item/{id}', { path: { id: 1, extra: 2 } })

  // body 类型错误（金额应为数值）
  // @ts-expect-error RechargeDTO.amount 必须是 number
  contracts.post('/api/wallet/recharge', { body: { amount: 'many' }, headers: { 'X-Idempotency-Key': 'k' } })

  // 借 transport 绕过契约（params/headers/data 被禁）
  // @ts-expect-error transport 不接受 params 等契约字段
  contracts.get('/api/order/my-bought', { transport: { params: { page: 2 } } })
  // @ts-expect-error transformRequest 不能改写契约已校验的请求体
  contracts.post('/api/order/create', { body: { itemId: 9 }, transport: { transformRequest: [] } })
  // @ts-expect-error adapter 不能接管契约客户端的请求与错误处理
  contracts.get('/api/category/list', { transport: { adapter: 'fetch' } })

  // 上传接口漏文件（file 是必填参数）
  // @ts-expect-error postFile 的 file 参数必填
  contracts.postFile('/api/item/upload-image')

  // JSON operation 错用 postFile（路径必须是 required multipart/form-data operation）
  // @ts-expect-error /api/item/publish 的 requestBody 是 application/json
  contracts.postFile('/api/item/publish', imageFile)

  // 正例（对照组）：完整调用必须无错通过
  contracts.post('/api/order/create', {
    body: { itemId: 9 },
    headers: { 'X-Idempotency-Key': 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee' }
  })
  contracts.get('/api/item/{id}', { path: { id: 42 } })
  contracts.get('/api/order/my-bought', { query: { page: 1, size: 10 } })
  // 无任何参数的 operation 允许完全省略 options
  contracts.get('/api/category/list')
  // 受控上传：required multipart operation + 必填 File
  contracts.postFile('/api/item/upload-image', imageFile)
}
