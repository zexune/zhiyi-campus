import assert from 'node:assert/strict'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, test, vi } from 'vitest'
import { ElMessage } from 'element-plus'

import ItemDetailPage from '@/views/item/ItemDetailPage.vue'
import { getItemDetail, getItemLineage, reportItem } from '@/api/item'
import type { ApiResult } from '@/utils/request'
import type { ItemDetail, ItemLineage } from '@/types/models'

vi.mock('@/api/item', () => ({
  getItemDetail: vi.fn(),
  getItemLineage: vi.fn(),
  reportItem: vi.fn(),
  toggleFavorite: vi.fn()
}))

vi.mock('@/api/auth', () => ({
  getSellerDetail: vi.fn(),
  getUserRelation: vi.fn(),
  getUserReputation: vi.fn()
}))

vi.mock('@/api/chat', () => ({
  startItemConversation: vi.fn()
}))

vi.mock('@/api/order', () => ({
  createOrder: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '1' }, query: {}, fullPath: '/item/1' }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() })
}))

vi.mock('@/utils/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/auth')>()),
  isLoggedIn: () => true,
  getUserId: () => 999
}))

const DialogStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<section v-if="modelValue" data-test="report-dialog"><slot /><slot name="footer" /></section>'
}

const InputStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<textarea data-test="report-details" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
}

const SelectStub = {
  props: ['modelValue', 'options'],
  emits: ['update:modelValue'],
  template:
    '<select data-test="report-type" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><option v-for="o in options" :key="o.value" :value="o.value">{{ o.label }}</option></select>'
}

const global = {
  stubs: {
    DefaultLayout: { template: '<main><slot /></main>' },
    RouterLink: { template: '<a><slot /></a>' },
    ElDialog: DialogStub,
    ElInput: InputStub,
    AppSelect: SelectStub,
    ElSkeleton: { template: '<div data-test="skeleton" />' },
    ElIcon: { template: '<i><slot /></i>' },
    LevelBadge: { template: '<span />' },
    PriceTag: { template: '<span class="price-tag" />' },
    UserAvatar: { template: '<span />' },
    SellerDetailDialog: { template: '<div />' }
  }
}

function detail(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    title: '九成新教材',
    type: 'SELL',
    status: 'ON_SALE',
    moderationStatus: 'PASSED',
    price: 25.5,
    images: ['/img/a.jpg', '/img/b.jpg', '/img/c.jpg'],
    coverImage: '/img/a.jpg',
    categoryId: 2,
    categoryName: '教材书籍',
    description: '干净无笔记',
    publisherId: 42,
    publisherNickname: '卖家昵称',
    viewCount: 10,
    favoriteCount: 3,
    ...overrides
  } as ItemDetail
}

const lineage: ItemLineage = {
  chain: [
    { userId: 1, nickname: '首届主人', role: 'PUBLISHER', time: '2025-09-01T10:00:00', price: 30 },
    { userId: 2, nickname: '二手同学', role: 'BUYER', time: '2026-01-01T10:00:00', price: 25 }
  ]
}

beforeEach(() => {
  vi.mocked(getItemDetail).mockResolvedValue({ code: 200, message: 'ok', data: detail() } as unknown as ApiResult<ItemDetail>)
  vi.mocked(getItemLineage).mockResolvedValue({ code: 200, message: 'ok', data: lineage })
  vi.mocked(reportItem).mockResolvedValue({ code: 200, message: 'ok', data: null })
  vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({}) as never)
})

function statusBadge(wrapper: ReturnType<typeof mount>) {
  const badges = wrapper.findAll('.info-head .badge')
  const badge = badges.find((item) => item.text().includes('在售中') || item.text().includes('审核中'))
  assert.ok(badge, '状态徽标应渲染在信息头')
  return badge
}

test('画廊渲染首图并可经左右切换与缩略图换图', async () => {
  const wrapper = mount(ItemDetailPage, { global })
  await flushPromises()

  const mainImage = () => wrapper.get('.gallery__main img')
  assert.equal(mainImage().attributes('src'), '/img/a.jpg')
  assert.equal(wrapper.get('.gallery__count').text(), '1 / 3')

  await wrapper.get('.gallery__nav--next').trigger('click')
  assert.equal(mainImage().attributes('src'), '/img/b.jpg')
  assert.equal(wrapper.get('.gallery__count').text(), '2 / 3')

  await wrapper.get('.gallery__nav--prev').trigger('click')
  assert.equal(mainImage().attributes('src'), '/img/a.jpg')

  await wrapper.findAll('.th')[2]!.trigger('click')
  assert.equal(mainImage().attributes('src'), '/img/c.jpg')
  assert.equal(wrapper.get('.gallery__count').text(), '3 / 3')
})

test('教材类商品渲染传承链时间轴并按角色区分文案', async () => {
  const wrapper = mount(ItemDetailPage, { global })
  await flushPromises()

  assert.deepEqual(vi.mocked(getItemLineage).mock.calls[0], ['1'])
  const nodes = wrapper.findAll('.lineage-timeline li')
  assert.equal(nodes.length, 2)
  assert.match(nodes[0]!.text(), /首届主人/)
  assert.match(nodes[0]!.text(), /最初发布/)
  assert.match(nodes[1]!.text(), /二手同学/)
  assert.match(nodes[1]!.text(), /完成接力/)
  assert.match(nodes[1]!.text(), /¥25\.00/)
})

test('状态徽标：在售商品显示在售中，待审核商品显示审核中', async () => {
  const wrapper = mount(ItemDetailPage, { global })
  await flushPromises()

  const badge = statusBadge(wrapper)
  assert.match(badge.text(), /在售中/)
  assert.ok(badge.classes().includes('badge--ok'))

  vi.mocked(getItemDetail).mockResolvedValue({ code: 200, message: 'ok', data: detail({ moderationStatus: 'PENDING' }) } as never)
  const pending = mount(ItemDetailPage, { global })
  await flushPromises()
  const pendingBadge = statusBadge(pending)
  assert.match(pendingBadge.text(), /审核中/)
  assert.ok(pendingBadge.classes().includes('badge--warn'))
})

test('举报弹窗开/关与提交参数：默认类型可提交，其他类型缺说明被拦截', async () => {
  const wrapper = mount(ItemDetailPage, { global })
  await flushPromises()

  const submitButton = () => wrapper.get('[data-test="report-dialog"] .btn--danger')
  const cancelButton = () => wrapper.get('[data-test="report-dialog"] .btn:not(.btn--danger)')

  await wrapper.get('[aria-label="举报商品"]').trigger('click')
  assert.ok(wrapper.find('[data-test="report-dialog"]').exists())

  // 默认类型 PRICE_FRAUD：补充说明留空按 null 提交，成功后弹窗自行关闭
  await submitButton().trigger('click')
  await flushPromises()
  assert.deepEqual(vi.mocked(reportItem).mock.calls[0], [1, { type: 'PRICE_FRAUD', details: null }])
  assert.equal(wrapper.find('[data-test="report-dialog"]').exists(), false)

  // 其他类型必须填写补充说明
  await wrapper.get('[aria-label="举报商品"]').trigger('click')
  await wrapper.get('[data-test="report-type"]').setValue('OTHER')
  await submitButton().trigger('click')
  await flushPromises()
  assert.equal(vi.mocked(reportItem).mock.calls.length, 1)
  assert.equal(vi.mocked(ElMessage.warning).mock.calls.length, 1)
  assert.ok(wrapper.find('[data-test="report-dialog"]').exists())

  // 取消直接关闭，不发起举报
  await wrapper.get('[data-test="report-details"]').setValue('页面展示与实物不符')
  await cancelButton().trigger('click')
  assert.equal(wrapper.find('[data-test="report-dialog"]').exists(), false)
  assert.equal(vi.mocked(reportItem).mock.calls.length, 1)
})
