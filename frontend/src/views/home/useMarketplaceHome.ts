import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import type { Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getActiveTopic, getAllTags, getCategories, getItemList, getItemRanking, toggleFavorite } from '@/api/item'
import type { ItemListQuery } from '@/api/item'
import type { Category, EventTopic, Item, TagCloudGroup } from '@/types/models'
import { ITEM_TYPE_LABELS, ITEM_TYPE_OPTIONS } from '@/constants/domain'
import type { ItemType, SelectOption } from '@/constants/domain'
import { isLoggedIn } from '@/utils/auth'
import { ROUTE_PATH } from '@/constants/routes'
import { placeholderClass } from '@/utils/format'

const PAGE_SIZE = 12

const FALLBACK_CATEGORIES = Object.freeze([
  { id: 1, name: '数码电子' },
  { id: 2, name: '教材书籍' },
  { id: 3, name: '服饰鞋包' },
  { id: 4, name: '生活日用' },
  { id: 5, name: '运动娱乐' },
  { id: 6, name: '零食饮品' },
  { id: 7, name: '学习用品' },
  { id: 8, name: '其他' }
])

/** 内置季节专题（接口专题不可用时的降级展示；元素形状满足下方 TopicBanner） */
const CAMPUS_TOPICS = Object.freeze([
  {
    id: 'new-student',
    start: 825,
    end: 915,
    stamp: 'NEW',
    dateLabel: '08.25 - 09.15',
    title: '新生入学季',
    description: '宿舍生活、学习用品和入门数码，一站配齐新学期。',
    action: '逛新生必备',
    categoryName: '生活日用'
  },
  {
    id: 'final-exam',
    start: 1220,
    end: 120,
    stamp: 'EXAM',
    dateLabel: '12.20 - 01.20',
    title: '期末备考季',
    description: '真题、笔记和教材集中上架，复习资料更快找到。',
    action: '找备考资料',
    categoryName: '教材书籍',
    keyword: '真题'
  },
  {
    id: 'graduation',
    start: 525,
    end: 630,
    stamp: 'SALE',
    dateLabel: '05.25 - 06.30',
    title: '毕业清仓季',
    description: '把带不走的好物留在校园，让下一位同学接着使用。',
    action: '查看毕业好物',
    keyword: '毕业'
  }
])

/**
 * 首页专题横幅的统一展示模型：
 * 内置季节专题（CampusTopic）与接口 EventTopic 合并 stamp/dateLabel/description/action
 * 等展示字段后，都满足此形状；applyTopic 消费的筛选字段两边都按可选声明。
 */
export interface TopicBanner {
  id?: string | number
  title: string
  stamp: string
  dateLabel: string
  description?: string
  action: string
  categoryName?: string
  keyword?: string
  filterTags?: string[] | null
  filterType?: string | null
  filterCategoryId?: number | null
}

/** 精细筛选标签云的真实返回形状：按大类分组，组内为标签名与计数 */
/** 交易大厅筛选条件；minPrice/maxPrice 由 v-model.number 写入，清空时为 '' */
export interface MarketFilters {
  keyword: string
  categoryId: string | number
  minPrice: number | '' | undefined
  maxPrice: number | '' | undefined
  type: string
  /** 标签筛选：数组以支持专题多标签（任一命中）；手动点选标签云时为单元素数组 */
  tags: string[]
  sort: string
}

/** useMarketplaceHome 的返回契约：refs 用 Ref<T>，方法显式标注参数与返回 */
export interface MarketplaceHomeReturn {
  TYPE_OPTIONS: SelectOption[]
  /** 当前生效的标签筛选（专题可多标签；手动点选为单元素） */
  activeTags: Ref<string[]>
  activeTopic: Ref<TopicBanner | null>
  allTags: Ref<TagCloudGroup[]>
  applyPriceFilterNow: () => void
  applyTopic: (topic: TopicBanner) => void
  categories: Ref<Category[]>
  clearKeyword: () => void
  favoriteBusyId: Ref<number | null>
  fetchItems: () => Promise<void>
  filterByTag: (tag: string, categoryId: number | string) => void
  filters: MarketFilters
  goDetail: (id: number | string) => void
  handleFavorite: (item: Item) => Promise<void>
  handleSearch: () => void
  itemTypeLabel: (type: string) => string
  items: Ref<Item[]>
  loading: Ref<boolean>
  loggedIn: boolean
  page: Ref<number>
  pageSize: number
  placeholderClass: typeof placeholderClass
  quickSearch: (keyword: string) => void
  ranking: Ref<Item[]>
  resetFilters: () => void
  searchByTag: (tag: string) => void
  selectCategory: (id: number | string) => void
  showTagCloud: Ref<boolean>
  toggleTagCloud: () => void
  total: Ref<number>
}

function isTopicActive(topic: { start: number; end: number }): boolean {
  const now = new Date()
  const monthDay = (now.getMonth() + 1) * 100 + now.getDate()
  return topic.start <= topic.end ? monthDay >= topic.start && monthDay <= topic.end : monthDay >= topic.start || monthDay <= topic.end
}

function formatTopicDate(value: string | null | undefined): string {
  if (!value) return ''
  const date = new Date(value)
  return `${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
}

/** 首页交易大厅的状态、查询副作用和交互。视图文件只负责渲染。 */
export function useMarketplaceHome(): MarketplaceHomeReturn {
  const router = useRouter()
  const route = useRoute()
  const loggedIn = isLoggedIn()
  const categories = ref<Category[]>([...FALLBACK_CATEGORIES])
  const items = ref<Item[]>([])
  const ranking = ref<Item[]>([])
  const page = ref(1)
  const total = ref(0)
  const loading = ref(false)
  const favoriteBusyId = ref<number | null>(null)
  const activeTopic = ref<TopicBanner | null>(CAMPUS_TOPICS.find(isTopicActive) || null)
  const allTags = ref<TagCloudGroup[]>([])
  const activeTags = ref<string[]>([])
  const showTagCloud = ref(false)
  let priceFilterTimer: number | undefined
  let resettingFilters = false

  const filters = reactive<MarketFilters>({
    keyword: '',
    categoryId: '',
    minPrice: undefined,
    maxPrice: undefined,
    type: '',
    tags: [],
    sort: 'random'
  })

  function buildParams(): ItemListQuery {
    const params: ItemListQuery = { page: page.value, size: PAGE_SIZE, sort: filters.sort }
    if (filters.keyword?.trim()) params.keyword = filters.keyword.trim()
    if (filters.categoryId) params.categoryId = filters.categoryId
    if (filters.minPrice !== undefined && filters.minPrice !== null) params.minPrice = filters.minPrice
    if (filters.maxPrice !== undefined && filters.maxPrice !== null) params.maxPrice = filters.maxPrice
    if (filters.type) params.type = filters.type
    // axios 对数组参数序列化为重复的 tag=a&tag=b，后端按 List 绑定并做 OR 匹配
    if (filters.tags.length) params.tag = filters.tags
    return params
  }

  async function fetchCategories(): Promise<void> {
    try {
      const response = await getCategories()
      if (Array.isArray(response.data) && response.data.length) categories.value = response.data
    } catch {
      categories.value = [...FALLBACK_CATEGORIES]
    }
  }

  async function fetchAllTags(): Promise<void> {
    if (!loggedIn) return
    try {
      const response = await getAllTags()
      allTags.value = Array.isArray(response.data) ? response.data : []
    } catch {
      allTags.value = []
    }
  }

  async function fetchRanking(): Promise<void> {
    if (!loggedIn) return
    const response = await getItemRanking({ limit: 10 })
    ranking.value = response.data || []
  }

  async function fetchActiveTopic(): Promise<void> {
    if (!loggedIn) return
    try {
      const response = await getActiveTopic()
      if (response.data) {
        const topic: EventTopic = response.data
        activeTopic.value = {
          ...topic,
          stamp: 'TOPIC',
          dateLabel: `${formatTopicDate(topic.startTime)} - ${formatTopicDate(topic.endTime)}`,
          description: topic.bannerText,
          action: '进入专题'
        }
      }
    } catch {
      // 网络失败时保留内置季节专题作为降级展示。
    }
  }

  async function fetchItems(): Promise<void> {
    if (!loggedIn) return
    loading.value = true
    try {
      const response = await getItemList(buildParams())
      items.value = response.data?.records || []
      total.value = Number(response.data?.total || 0)
    } finally {
      loading.value = false
    }
  }

  function handleSearch(): void {
    if (!loggedIn) {
      router.push({ path: ROUTE_PATH.LOGIN, query: { redirect: ROUTE_PATH.HOME } })
      return
    }
    page.value = 1
    fetchItems()
  }

  function filterByTag(tag: string, categoryId: number | string): void {
    // 手动点选标签云保持单选语义：再次点击取消，点其他标签替换
    if (activeTags.value.includes(tag)) {
      activeTags.value = []
      filters.tags = []
      filters.categoryId = ''
    } else {
      activeTags.value = [tag]
      filters.tags = [tag]
      filters.categoryId = categoryId
      filters.keyword = ''
    }
    page.value = 1
    fetchItems()
  }

  function applyPriceFilterNow(): void {
    if (!priceFilterTimer) return
    window.clearTimeout(priceFilterTimer)
    priceFilterTimer = undefined
    handleSearch()
  }

  function schedulePriceFilter(): void {
    if (!loggedIn || resettingFilters) return
    window.clearTimeout(priceFilterTimer)
    priceFilterTimer = window.setTimeout(() => {
      priceFilterTimer = undefined
      handleSearch()
    }, 450)
  }

  function quickSearch(keyword: string): void {
    filters.keyword = keyword
    handleSearch()
  }

  function clearKeyword(): void {
    filters.keyword = ''
    handleSearch()
  }

  function scrollToHall(): void {
    nextTick(() => document.querySelector('.hall')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
  }

  function searchByTag(tag: string): void {
    filters.keyword = tag
    filters.tags = []
    activeTags.value = []
    router.replace({ path: '/', query: { keyword: tag } })
    handleSearch()
    scrollToHall()
  }

  function applyTopic(topic: TopicBanner): void {
    filters.keyword = topic.keyword || ''
    filters.tags = topic.filterTags ?? []
    activeTags.value = [...(topic.filterTags ?? [])]
    filters.type = topic.filterType || ''
    const category = categories.value.find((item) => item.name === topic.categoryName)
    filters.categoryId = topic.filterCategoryId || category?.id || ''
    handleSearch()
    scrollToHall()
  }

  function resetFilters(): void {
    resettingFilters = true
    window.clearTimeout(priceFilterTimer)
    priceFilterTimer = undefined
    Object.assign(filters, {
      keyword: '',
      categoryId: '',
      minPrice: undefined,
      maxPrice: undefined,
      type: '',
      tags: [],
      sort: 'random'
    })
    activeTags.value = []
    page.value = 1
    fetchItems()
    nextTick(() => {
      resettingFilters = false
    })
  }

  function selectCategory(id: number | string): void {
    filters.categoryId = id
    handleSearch()
  }

  function goDetail(id: number | string): void {
    router.push(ROUTE_PATH.item(id))
  }

  async function handleFavorite(item: Item): Promise<void> {
    if (!isLoggedIn()) {
      router.push({ path: ROUTE_PATH.LOGIN, query: { redirect: ROUTE_PATH.HOME } })
      return
    }
    favoriteBusyId.value = item.id
    try {
      const response = await toggleFavorite(item.id)
      item.favoriteByCurrentUser = response.data.favorite
      item.favoriteCount = response.data.favoriteCount
      ElMessage.success(response.data.favorite ? '已收藏' : '已取消收藏')
      await fetchRanking()
    } finally {
      favoriteBusyId.value = null
    }
  }

  function itemTypeLabel(type: string): string {
    return ITEM_TYPE_LABELS[type as ItemType] || type
  }

  watch(
    () => filters.sort,
    () => {
      if (!resettingFilters) handleSearch()
    }
  )
  watch(
    () => filters.type,
    () => {
      if (!resettingFilters) handleSearch()
    }
  )
  watch(() => [filters.minPrice, filters.maxPrice], schedulePriceFilter)

  onMounted(async () => {
    if (route.query.keyword) filters.keyword = String(route.query.keyword)
    if (!loggedIn) {
      await fetchCategories()
      return
    }
    await Promise.all([fetchCategories(), fetchItems(), fetchRanking(), fetchAllTags(), fetchActiveTopic()])
  })

  onBeforeUnmount(() => window.clearTimeout(priceFilterTimer))

  return {
    // AppSelect 的 options prop 按可变数组形状声明；常量运行时仍为冻结对象，仅此处放宽类型
    TYPE_OPTIONS: ITEM_TYPE_OPTIONS as SelectOption[],
    activeTags,
    activeTopic,
    allTags,
    applyPriceFilterNow,
    applyTopic,
    categories,
    clearKeyword,
    favoriteBusyId,
    fetchItems,
    filterByTag,
    filters,
    goDetail,
    handleFavorite,
    handleSearch,
    itemTypeLabel,
    items,
    loading,
    loggedIn,
    page,
    pageSize: PAGE_SIZE,
    placeholderClass,
    quickSearch,
    ranking,
    resetFilters,
    searchByTag,
    selectCategory,
    showTagCloud,
    toggleTagCloud: () => {
      showTagCloud.value = !showTagCloud.value
    },
    total
  }
}
