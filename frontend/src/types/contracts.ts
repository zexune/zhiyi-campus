/**
 * operation 级契约层（P1-5 / P5 分层）：
 *
 *   OpenAPI → api.gen.d.ts（仅生成）→ contracts.ts（本文件）→ api 模块 → mappers → models.ts
 *
 * - 动态路径用模板路径 + path 参数表达：`contracts.put('/api/order/{id}/confirm',
 *   { path: { id }, headers: { 'X-Idempotency-Key': key } })`；运行时统一
 *   `encodeURIComponent` 替换，并拒绝未填/多余占位符；
 * - path 参数、query 参数、header、request body、response data 全部从生成的
 *   operation 推导：必填 body/header/path 缺失编译失败，错误 query 名、错误
 *   类型和不存在的路径编译失败；
 * - timeout/signal 等传输选项单独放在 `transport` 字段，不能借
 *   AxiosRequestConfig 绕过 query/header 契约；
 * - 文件上传走独立、受控的 postFile（路径必须是生成契约中 required
 *   multipart/form-data 的 operation），不会把所有 body 重新放宽为 unknown；
 * - models.ts 保留手写领域/页面/表单模型，wire → domain 的转换一律经
 *   api/mappers.ts 的显式 mapper。
 */
import type { AxiosRequestConfig } from 'axios'
import request from '@/utils/request'
import type { ApiResult } from '@/utils/request'
import type { components, paths } from './api.gen'

/** 生成契约的全部 operation 路径（openapi-typescript 的 paths 是纯类型） */
export type ApiPaths = paths

/** 统一响应信封类型（再导出：API 模块不得直接依赖 @/utils/request） */
export type { ApiResult }

/** 生成 schema 的受控再导出：API 模块不得直接 import api.gen */
export type Schemas = components['schemas']

type Methods = 'get' | 'post' | 'put' | 'delete'

/** 拥有指定 HTTP 方法 operation 的路径字面量集合 */
export type PathOf<M extends Methods> = keyof {
  [K in keyof ApiPaths as ApiPaths[K] extends { [m in M]: unknown } ? K : never]: never
} &
  string

type Operation<P extends keyof ApiPaths, M extends Methods> = ApiPaths[P][M]

/** operation 200 响应的 JSON 信封（springdoc 对未显式 produces 的接口用通配 media type 键） */
type JsonEnvelope<C> = C extends { 'application/json': infer B } ? B : C extends { '*/*': infer B } ? B : never

/** operation 200 响应信封的 data 负载——API 模块消费的 wire 类型（成功信封 data 必填） */
export type WireData<P extends keyof ApiPaths, M extends Methods> =
  Operation<P, M> extends {
    responses: { 200: { content: infer C } }
  }
    ? JsonEnvelope<C> extends { data: infer D }
      ? D
      : never
    : never

/**
 * 从生成的 operation 提取参数规格（path/query/header）。
 * 无该类参数时返回 never——对应字段归一为 `xxx?: never`，
 * 传入任何对象都是编译错误（不再用接受任意对象的 Record 形状）。
 */
type ParamSpec<P extends keyof ApiPaths, M extends Methods, K extends 'path' | 'query' | 'header'> =
  Operation<P, M> extends { parameters: { [k in K]?: infer T } } ? (NonNullable<T> extends Record<string, unknown> ? NonNullable<T> : never) : never

/** operation 的 query 参数规格（api 模块归一化页面输入时使用） */
export type QueryOf<P extends keyof ApiPaths, M extends Methods> = ParamSpec<P, M, 'query'>

/** 必填键集合（可选属性的类型携带 undefined，不算必填）；无必填键或规格为 never 时为 never */
type RequiredKeys<T> = [T] extends [never] ? never : Extract<{ [K in keyof T]-?: undefined extends T[K] ? never : K }[keyof T], string>

/** spec 有必填键时字段必填，否则可选——漏必填 path/header 编译失败 */
type ArgField<Spec, Key extends string> = RequiredKeys<Spec> extends never ? { [k in Key]?: Spec } : { [k in Key]: Spec }

/** requestBody 非可选（必填）时 body 必填；可选时 body 可选；无 body 时禁止传 */
type BodyField<P extends keyof ApiPaths, M extends Methods> =
  Operation<P, M> extends { requestBody: { content: { 'application/json': infer B } } }
    ? { body: B }
    : Operation<P, M> extends { requestBody?: { content: { 'application/json': infer B } } }
      ? { body?: B }
      : { body?: never }

/** 必填 JSON requestBody（multipart 上传的必填性由 postFile 的 file 参数单独承载） */
type HasRequiredBody<P extends keyof ApiPaths, M extends Methods> = Operation<P, M> extends { requestBody: { content: { 'application/json': unknown } } } ? true : false

/**
 * 存在必填 body/path/query/header 任一时，options 整体必传；
 * 仅有可选参数或 transport 时才允许省略 options。
 */
type HasRequiredOptions<P extends keyof ApiPaths, M extends Methods> =
  HasRequiredBody<P, M> extends true
    ? true
    : [RequiredKeys<ParamSpec<P, M, 'path'>>] extends [never]
      ? [RequiredKeys<ParamSpec<P, M, 'query'>>] extends [never]
        ? [RequiredKeys<ParamSpec<P, M, 'header'>>] extends [never]
          ? false
          : true
        : true
      : true

/** 条件 rest tuple：必填时 options 不可省略，否则可省略 */
type OptionsArgs<P extends keyof ApiPaths, M extends Methods> = HasRequiredOptions<P, M> extends true ? [options: RequestOptions<P, M>] : [options?: RequestOptions<P, M>]

/**
 * 传输层安全白名单：只允许影响超时/取消与进度回调的选项。
 * 不把整个 AxiosRequestConfig 暴露出来，避免 transformRequest、adapter、
 * validateStatus、responseType 等字段改写已校验的信封/请求体或绕过错误处理。
 */
export type TransportOptions = Pick<AxiosRequestConfig, 'timeout' | 'signal' | 'onUploadProgress' | 'onDownloadProgress'> & {
  skipAuthRedirect?: boolean
}

export type RequestOptions<P extends keyof ApiPaths, M extends Methods> = ArgField<ParamSpec<P, M, 'path'>, 'path'> &
  ArgField<ParamSpec<P, M, 'query'>, 'query'> &
  ArgField<ParamSpec<P, M, 'header'>, 'headers'> &
  BodyField<P, M> & { transport?: TransportOptions }

/** post operation 拥有 required multipart/form-data requestBody 的路径（上传专用，由生成契约推导） */
export type UploadPathOf = keyof {
  [
    K in keyof ApiPaths as ApiPaths[K] extends {
      post: { requestBody: { content: { 'multipart/form-data': unknown } } }
    }
      ? K
      : never
  ]: never
} &
  string

export type FormRequestOptions<P extends UploadPathOf> = ArgField<ParamSpec<P, 'post', 'path'>, 'path'> &
  ArgField<ParamSpec<P, 'post', 'query'>, 'query'> &
  ArgField<ParamSpec<P, 'post', 'header'>, 'headers'> & { transport?: TransportOptions }

type FormOptionsArgs<P extends UploadPathOf> = [RequiredKeys<ParamSpec<P, 'post', 'path'>>] extends [never]
  ? [RequiredKeys<ParamSpec<P, 'post', 'query'>>] extends [never]
    ? [RequiredKeys<ParamSpec<P, 'post', 'header'>>] extends [never]
      ? [options?: FormRequestOptions<P>]
      : [options: FormRequestOptions<P>]
    : [options: FormRequestOptions<P>]
  : [options: FormRequestOptions<P>]

/** 运行时剥离 /api 前缀：request 实例 baseURL 已含 /api，路径键保持与 OpenAPI 一致 */
function relative<P extends string>(path: P): string {
  return path.startsWith('/api/') ? path.slice(4) : path
}

const PATH_PLACEHOLDER = /\{([^}/]+)\}/g

/**
 * 模板路径填充：每个占位符必须提供且只能提供已声明的参数，
 * 值一律 encodeURIComponent，未填/多余占位符直接抛错。
 */
export function fillPathParams<P extends string>(path: P, pathParams?: Record<string, string | number | boolean>): string {
  const placeholders = [...path.matchAll(PATH_PLACEHOLDER)].map((match) => match[1])
  const given = pathParams ?? {}
  const missing = placeholders.filter((name) => !(name in given) || given[name] === undefined)
  const extra = Object.keys(given).filter((name) => !placeholders.includes(name))
  if (missing.length > 0 || extra.length > 0) {
    const problems = [...missing.map((name) => `缺少路径参数 ${name}`), ...extra.map((name) => `多余路径参数 ${name}`)].join('；')
    throw new Error(`路径 ${path} 参数不匹配：${problems}`)
  }
  return path.replace(PATH_PLACEHOLDER, (_, name: string) => encodeURIComponent(String(given[name])))
}

function axiosConfig(options: { query?: unknown; headers?: unknown; transport?: TransportOptions } | undefined): AxiosRequestConfig {
  const query = options?.query as Record<string, unknown> | undefined
  const transport = options?.transport
  const config: AxiosRequestConfig = {}
  if (transport) {
    if (transport.timeout !== undefined) config.timeout = transport.timeout
    if (transport.signal !== undefined) config.signal = transport.signal
    if (transport.onUploadProgress !== undefined) config.onUploadProgress = transport.onUploadProgress
    if (transport.onDownloadProgress !== undefined) config.onDownloadProgress = transport.onDownloadProgress
    if (transport.skipAuthRedirect !== undefined) config.skipAuthRedirect = transport.skipAuthRedirect
  }
  // 过滤 undefined 值，避免可选 query 被序列化成字面量 "undefined"
  if (query) {
    const filtered: Record<string, unknown> = {}
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined) filtered[key] = value
    }
    config.params = filtered
  }
  if (options?.headers) {
    config.headers = options.headers as Record<string, string>
  }
  return config
}

function pathParamsOf(options: { path?: unknown } | undefined): Record<string, string | number | boolean> | undefined {
  return options?.path as Record<string, string | number | boolean> | undefined
}

function bodyOf(options: { body?: unknown } | undefined): unknown {
  return options !== undefined && 'body' in options ? options.body : undefined
}

/** paths 约束的类型化客户端：请求与响应类型全部来自生成契约 */
export const contracts = {
  get: <P extends PathOf<'get'>>(path: P, ...args: OptionsArgs<P, 'get'>): Promise<ApiResult<WireData<P, 'get'>>> => {
    const options = args[0] as RequestOptions<P, 'get'> | undefined
    return request.get(relative(fillPathParams(path, pathParamsOf(options))), axiosConfig(options))
  },

  post: <P extends PathOf<'post'>>(path: P, ...args: OptionsArgs<P, 'post'>): Promise<ApiResult<WireData<P, 'post'>>> => {
    const options = args[0] as RequestOptions<P, 'post'> | undefined
    return request.post(relative(fillPathParams(path, pathParamsOf(options))), bodyOf(options), axiosConfig(options))
  },

  put: <P extends PathOf<'put'>>(path: P, ...args: OptionsArgs<P, 'put'>): Promise<ApiResult<WireData<P, 'put'>>> => {
    const options = args[0] as RequestOptions<P, 'put'> | undefined
    return request.put(relative(fillPathParams(path, pathParamsOf(options))), bodyOf(options), axiosConfig(options))
  },

  delete: <P extends PathOf<'delete'>>(path: P, ...args: OptionsArgs<P, 'delete'>): Promise<ApiResult<WireData<P, 'delete'>>> => {
    const options = args[0] as RequestOptions<P, 'delete'> | undefined
    return request.delete(relative(fillPathParams(path, pathParamsOf(options))), axiosConfig(options))
  },

  /**
   * 受控文件上传：路径必须是生成契约中带 required multipart/form-data
   * requestBody 的 operation（JSON operation 无法通过类型约束），当前
   * 接口只有一个必填 `file` 字段——由本方法固定构造，调用方不能添加
   * 任意 multipart 字段，也不能把 body 宽松成 unknown。
   */
  postFile: <P extends UploadPathOf>(path: P, file: File, ...args: FormOptionsArgs<P>): Promise<ApiResult<WireData<P, 'post'>>> => {
    const options = args[0] as FormRequestOptions<P> | undefined
    const formData = new FormData()
    formData.append('file', file)
    return request.post(relative(fillPathParams(path, pathParamsOf(options))), formData, axiosConfig(options))
  }
}
