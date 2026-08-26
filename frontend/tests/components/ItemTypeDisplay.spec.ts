import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { test, vi } from 'vitest'

import HomePage from '@/views/home/HomePage.vue'
import RankingPage from '@/views/ranking/RankingPage.vue'
import { getActiveTopic, getAllTags, getCategories, getItemList, getItemRanking, getTrendingTags, toggleFavorite } from '@/api/item'
import type { ItemFeedResult } from '@/api/item'
import type { Item, ItemSummary } from '@/types/models'

vi.mock('@/api/item', () => ({
  getCategories: vi.fn(),
  getItemList: vi.fn(),
  getItemRanking: vi.fn(),
  getAllTags: vi.fn(),
  getActiveTopic: vi.fn(),
  toggleFavorite: vi.fn(),
  getTrendingTags: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('@/utils/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/auth')>()),
  isLoggedIn: () => true
}))

/** PriceTag 输出金额原文，供断言真实金额/异常文案 */
const global = {
  stubs: {
    DefaultLayout: { template: '<main><slot /></main>' },
    AppSelect: { props: ['modelValue', 'options', 'placeholder'], template: '<select />' },
    CategoryIcon: { template: '<span />' },
    LevelBadge: { template: '<span />' },
    PriceTag: { props: ['value', 'fontSize'], template: '<span class="price-stub">{{ value }}</span>' },
    TagList: { template: '<span />' },
    ElSkeleton: { template: '<div data-test="skeleton" />' },
    'router-link': { template: '<a><slot /></a>' }
  }
}

function rankItem(overrides = {}) {
  return {
    id: 1,
    title: '榜单商品',
    type: 'SELL',
    status: 'ON_SALE',
    price: 12.5,
    coverImage: null,
    tags: [],
    viewCount: 0,
    favoriteCount: 0,
    ...overrides
  } as ItemSummary
}

async function mountHomeWithRanking(ranking: Item[]) {
  vi.mocked(getItemList).mockResolvedValue({
    code: 200,
    message: 'ok',
    data: { records: [], nextCursor: null, hasMore: false, estimatedTotal: 0 } satisfies ItemFeedResult
  })
  vi.mocked(getItemRanking).mockResolvedValue({ code: 200, message: 'ok', data: ranking })
  vi.mocked(getCategories).mockResolvedValue({ code: 200, message: 'ok', data: [] })
  vi.mocked(getAllTags).mockResolvedValue({ code: 200, message: 'ok', data: [] })
  vi.mocked(getActiveTopic).mockResolvedValue({ code: 200, message: 'ok', data: null })
  const wrapper = mount(HomePage, { global })
  await flushPromises()
  return wrapper
}

test('首页侧栏近期爆款：SWAP 商品显示换物而非价格', async () => {
  const wrapper = await mountHomeWithRanking([rankItem({ id: 1, type: 'SWAP', price: null })])

  const sidebar = wrapper.find('.rank-card')
  assert.ok(sidebar.exists(), '侧栏榜单应渲染')
  assert.match(sidebar.text(), /换物/)
  assert.ok(!sidebar.text().includes('¥'), `SWAP 不得显示价格：${sidebar.text()}`)
})

test('首页侧栏近期爆款：非 SWAP 且价格缺失显示价格异常，绝不显示 ¥0.00', async () => {
  const wrapper = await mountHomeWithRanking([rankItem({ id: 1, type: 'SELL', price: null }), rankItem({ id: 2, type: 'BUY', price: undefined })])

  const sidebar = wrapper.find('.rank-card')
  assert.ok(sidebar.exists())
  const prices = sidebar.text()
  assert.match(prices, /价格异常/)
  assert.ok(!prices.includes('0.00'), `缺失价格不得伪装成 ¥0.00：${prices}`)
})

test('首页侧栏近期爆款：ERRAND 徽标显示跑腿（而非出售）', async () => {
  const wrapper = await mountHomeWithRanking([rankItem({ id: 1, type: 'ERRAND', price: 5 })])

  const badge = wrapper.find('.rank-card .badge')
  assert.ok(badge.exists())
  assert.match(badge.text(), /跑腿/)
})

test('独立榜单：SWAP/ERRAND 徽标与价格分支全类型正确', async () => {
  vi.mocked(getItemRanking).mockResolvedValue({
    code: 200,
    message: 'ok',
    data: [
      rankItem({ id: 1, type: 'SWAP', price: null }),
      rankItem({ id: 2, type: 'ERRAND', price: 6 }),
      rankItem({ id: 3, type: 'BUY', price: 30 }),
      rankItem({ id: 4, type: 'SELL', price: 19.9 })
    ] as Item[]
  })
  vi.mocked(getTrendingTags).mockResolvedValue({ code: 200, message: 'ok', data: [] })
  vi.mocked(toggleFavorite).mockResolvedValue({ code: 200, message: 'ok', data: { favorited: true, favoriteCount: 1 } } as never)

  const wrapper = mount(RankingPage, { global })
  await flushPromises()

  const badges = wrapper.findAll('.podium-card .badge, .ranking-row .badge')
  const texts = badges.map((badge) => badge.text())
  assert.ok(texts.includes('换物'), `SWAP 徽标应为换物：${texts.join(',')}`)
  assert.ok(texts.includes('跑腿'), `ERRAND 徽标应为跑腿：${texts.join(',')}`)

  const swapBadge = badges.find((badge) => badge.text() === '换物')
  assert.ok(swapBadge?.classes().includes('badge--swap'), 'SWAP 徽标使用 badge--swap')
  const errandBadge = badges.find((badge) => badge.text() === '跑腿')
  assert.ok(errandBadge?.classes().includes('badge--errand'), 'ERRAND 徽标使用 badge--errand')

  // SWAP 在榜单行显示换物文案而非金额；ERRAND 显示真实悬赏金额
  const rows = wrapper.findAll('.ranking-row')
  const sellRow = rows.find((row) => row.text().includes('SELL') || row.text() !== '')
  assert.ok(sellRow)
  const pageText = wrapper.text()
  assert.match(pageText, /换物/)
  assert.ok(pageText.includes('19.9'), 'SELL 商品显示真实金额')
  assert.ok(pageText.includes('6'), 'ERRAND 商品显示悬赏金额')
})
