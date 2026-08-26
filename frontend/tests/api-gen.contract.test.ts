import { expectTypeOf, test } from 'vitest'
import type { components } from '@/types/api.gen'

/**
 * P3-6：对生成文件（api.gen.d.ts）的契约断言——快照与类型的成对卫生。
 * 断言全部使用纯类型形式（不取值），由 vue-tsc 在 typecheck 阶段求值；
 * 快照回归导致字段弱化（可选化/丢类型/混入 null）时立即失败。
 */
type Schemas = components['schemas']

test('ApiFailure：code 是 number、message 是 string、requestOutcome 是精确联合、无 body 级 retryAfterSeconds', () => {
  expectTypeOf<Schemas['ApiFailure']['code']>().toEqualTypeOf<number>()
  expectTypeOf<Schemas['ApiFailure']['message']>().toEqualTypeOf<string>()
  expectTypeOf<Schemas['ApiFailure']['meta']['requestOutcome']>().toEqualTypeOf<'PROCESSING' | 'REJECTED' | 'UNKNOWN'>()
  // body 级 retryAfterSeconds 已删除（退避只走 Retry-After 响应头）
  expectTypeOf<Schemas['ApiFailure']['meta'][]>().not.toHaveProperty('retryAfterSeconds')
})

test('成功信封：字段必填（无 ?）、code 收窄为字面量 200、Void.data 为显式 null', () => {
  expectTypeOf<Schemas['ApiSuccessLoginVO']['code']>().toEqualTypeOf<200>()
  expectTypeOf<Schemas['ApiSuccessLoginVO']['message']>().toEqualTypeOf<string>()
  // data 必填且非空：Exclude 掉 null 后类型不变
  expectTypeOf<Exclude<Schemas['ApiSuccessLoginVO']['data'], null>>().toEqualTypeOf<Schemas['ApiSuccessLoginVO']['data']>()

  expectTypeOf<Schemas['ApiSuccessVoid']['data']>().toEqualTypeOf<null>()
})

test('登录负载：token、user 及管理员核心身份字段均为必填', () => {
  expectTypeOf<Schemas['LoginVO']['token']>().toEqualTypeOf<string>()
  expectTypeOf<Schemas['LoginVO']['user']>().toEqualTypeOf<Schemas['UserVO']>()
  expectTypeOf<Schemas['AdminLoginVO']['token']>().toEqualTypeOf<string>()
  expectTypeOf<Schemas['AdminLoginVO']['user']>().toEqualTypeOf<Schemas['AdminUserVO']>()
  expectTypeOf<Schemas['AdminUserVO']['id']>().toEqualTypeOf<number>()
  expectTypeOf<Schemas['AdminUserVO']['username']>().toEqualTypeOf<string>()
  expectTypeOf<Schemas['AdminUserVO']['nickname']>().toEqualTypeOf<string>()
  expectTypeOf<Schemas['AdminUserVO']['role']>().toEqualTypeOf<string>()
})

test('active-topic 使用独立的可空 EventTopic 表达，管理端写接口不受影响', () => {
  expectTypeOf<Schemas['ApiSuccessEventTopicNullable']['data']>().toEqualTypeOf<Schemas['EventTopicResponse'] | null>()

  expectTypeOf<Exclude<Schemas['ApiSuccessEventTopicResponse']['data'], null>>().toEqualTypeOf<Schemas['ApiSuccessEventTopicResponse']['data']>()
})
