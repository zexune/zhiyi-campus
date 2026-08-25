import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { test, vi } from 'vitest'

import HomePage from '@/views/home/HomePage.vue'
import { getActiveTopic, getAllTags, getCategories, getItemList, getItemRanking } from '@/api/item'
import type { ItemFeedResult } from '@/api/item'
import type { ApiResult } from '@/utils/request'
import type { Item } from '@/types/models'

vi.mock('@/api/item', () => ({
  getCategories: vi.fn(),
  getItemList: vi.fn(),
  getItemRanking: vi.fn(),
  getAllTags: vi.fn(),
  getActiveTopic: vi.fn(),
  toggleFavorite: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('@/utils/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/auth')>()),
  isLoggedIn: () => true
}))

const global = {
  stubs: {
    DefaultLayout: { template: '<main><slot /></main>' },
    AppSelect: { props: ['modelValue', 'options', 'placeholder'], template: '<select />' },
    CategoryIcon: { template: '<span />' },
    LevelBadge: { template: '<span />' },
    PriceTag: { template: '<span />' },
    TagList: { template: '<span />' },
    ElSkeleton: { template: '<div data-test="skeleton" />' },
    'router-link': { template: '<a><slot /></a>' }
  }
}

type FeedResult = ApiResult<ItemFeedResult>

function feedItem(overrides = {}) {
  return {
    id: 1,
    title: '智能推荐对照商品',
    type: 'SELL',
    status: 'ON_SALE',
    price: '9.90',
    tags: [],
    viewCount: 0,
    favoriteCount: 0,
    ...overrides
  } as Item
}

function feedPage(records: Item[]): FeedResult {
  return { code: 200, message: 'ok', data: { records, nextCursor: null, hasMore: false, estimatedTotal: records.length } }
}

test('挂载时榜单先于列表返回：feed 不被榜单作废，商品网格正常渲染', async () => {
  let resolveList!: (value: FeedResult) => void
  let resolveRanking!: (value: ApiResult<Item[]>) => void
  vi.mocked(getItemList).mockImplementation(
    () =>
      new Promise((resolve) => {
        resolveList = resolve
      })
  )
  vi.mocked(getItemRanking).mockImplementation(
    () =>
      new Promise((resolve) => {
        resolveRanking = resolve
      })
  )
  vi.mocked(getCategories).mockResolvedValue({ code: 200, message: 'ok', data: [] })
  vi.mocked(getAllTags).mockResolvedValue({ code: 200, message: 'ok', data: [] })
  vi.mocked(getActiveTopic).mockResolvedValue({ code: 200, message: 'ok', data: { title: '测试专题', startTime: '0101', endTime: '1231', enabled: true } })

  const wrapper = mount(HomePage, { global })
  await flushPromises()
  assert.ok(wrapper.find('[data-test="skeleton"]').exists())

  // 榜单先回（旧缺陷里它推进共享代数，把在途 feed 作废成永久骨架屏）
  resolveRanking({ code: 200, message: 'ok', data: [] })
  await flushPromises()
  resolveList(feedPage([feedItem()]))
  await flushPromises()

  assert.equal(vi.mocked(getItemList).mock.calls.length, 1)
  assert.equal(vi.mocked(getItemRanking).mock.calls.length, 1)
  assert.ok(!wrapper.find('[data-test="skeleton"]').exists())
  assert.equal(wrapper.findAll('.goods-card').length, 1)
  assert.match(wrapper.text(), /智能推荐对照商品/)
})

test('挂载时列表为空：显示空态而不是永久骨架屏', async () => {
  vi.mocked(getItemList).mockResolvedValue(feedPage([]))
  vi.mocked(getItemRanking).mockResolvedValue({ code: 200, message: 'ok', data: [] })
  vi.mocked(getCategories).mockResolvedValue({ code: 200, message: 'ok', data: [] })
  vi.mocked(getAllTags).mockResolvedValue({ code: 200, message: 'ok', data: [] })
  vi.mocked(getActiveTopic).mockResolvedValue({ code: 200, message: 'ok', data: { title: '测试专题', startTime: '0101', endTime: '1231', enabled: true } })

  const wrapper = mount(HomePage, { global })
  await flushPromises()

  assert.ok(!wrapper.find('[data-test="skeleton"]').exists())
  assert.match(wrapper.text(), /未找到相关商品/)
})
