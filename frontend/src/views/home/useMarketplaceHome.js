import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getActiveTopic,
  getAllTags,
  getCategories,
  getItemList,
  getItemRanking,
  toggleFavorite,
} from '@/api/item'
import { ITEM_TYPE_LABELS, ITEM_TYPE_OPTIONS } from '@/constants/domain'
import { isLoggedIn } from '@/utils/auth'

const PLACEHOLDER_CLASSES = ['ph-a', 'ph-b', 'ph-c', 'ph-d', 'ph-e', 'ph-f']
const PAGE_SIZE = 12

const FALLBACK_CATEGORIES = Object.freeze([
  { id: 1, name: '数码电子' },
  { id: 2, name: '教材书籍' },
  { id: 3, name: '服饰鞋包' },
  { id: 4, name: '生活日用' },
  { id: 5, name: '运动娱乐' },
  { id: 6, name: '零食饮品' },
  { id: 7, name: '学习用品' },
  { id: 8, name: '其他' },
])

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
    categoryName: '生活日用',
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
    keyword: '真题',
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
    keyword: '毕业',
  },
])

function isTopicActive(topic) {
  const now = new Date()
  const monthDay = (now.getMonth() + 1) * 100 + now.getDate()
  return topic.start <= topic.end
    ? monthDay >= topic.start && monthDay <= topic.end
    : monthDay >= topic.start || monthDay <= topic.end
}

function formatTopicDate(value) {
  if (!value) return ''
  const date = new Date(value)
  return `${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
}

/** 首页交易大厅的状态、查询副作用和交互。视图文件只负责渲染。 */
export function useMarketplaceHome() {
  const router = useRouter()
  const route = useRoute()
  const loggedIn = isLoggedIn()
  const categories = ref([...FALLBACK_CATEGORIES])
  const items = ref([])
  const ranking = ref([])
  const page = ref(1)
  const total = ref(0)
  const loading = ref(false)
  const favoriteBusyId = ref(null)
  const activeTopic = ref(CAMPUS_TOPICS.find(isTopicActive) || null)
  const allTags = ref([])
  const activeTag = ref('')
  const showTagCloud = ref(false)
  let priceFilterTimer = null
  let resettingFilters = false

  const filters = reactive({
    keyword: '',
    categoryId: '',
    minPrice: undefined,
    maxPrice: undefined,
    type: '',
    tag: '',
    sort: 'random',
  })

  function buildParams() {
    const params = { page: page.value, size: PAGE_SIZE, sort: filters.sort }
    if (filters.keyword?.trim()) params.keyword = filters.keyword.trim()
    if (filters.categoryId) params.categoryId = filters.categoryId
    if (filters.minPrice !== undefined && filters.minPrice !== null) params.minPrice = filters.minPrice
    if (filters.maxPrice !== undefined && filters.maxPrice !== null) params.maxPrice = filters.maxPrice
    if (filters.type) params.type = filters.type
    if (filters.tag) params.tag = filters.tag
    return params
  }

  async function fetchCategories() {
    try {
      const response = await getCategories()
      if (Array.isArray(response.data) && response.data.length) categories.value = response.data
    } catch {
      categories.value = [...FALLBACK_CATEGORIES]
    }
  }

  async function fetchAllTags() {
    if (!loggedIn) return
    try {
      const response = await getAllTags()
      allTags.value = Array.isArray(response.data) ? response.data : []
    } catch {
      allTags.value = []
    }
  }

  async function fetchRanking() {
    if (!loggedIn) return
    const response = await getItemRanking({ limit: 10 })
    ranking.value = response.data || []
  }

  async function fetchActiveTopic() {
    if (!loggedIn) return
    try {
      const response = await getActiveTopic()
      if (response.data) {
        const topic = response.data
        activeTopic.value = {
          ...topic,
          stamp: 'TOPIC',
          dateLabel: `${formatTopicDate(topic.startTime)} - ${formatTopicDate(topic.endTime)}`,
          description: topic.bannerText,
          action: '进入专题',
        }
      }
    } catch {
      // 网络失败时保留内置季节专题作为降级展示。
    }
  }

  async function fetchItems() {
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

  function handleSearch() {
    if (!loggedIn) {
      router.push({ path: '/login', query: { redirect: '/' } })
      return
    }
    page.value = 1
    fetchItems()
  }

  function filterByTag(tag, categoryId) {
    if (activeTag.value === tag) {
      activeTag.value = ''
      filters.tag = ''
      filters.categoryId = ''
    } else {
      activeTag.value = tag
      filters.tag = tag
      filters.categoryId = categoryId
      filters.keyword = ''
    }
    page.value = 1
    fetchItems()
  }

  function applyPriceFilterNow() {
    if (!priceFilterTimer) return
    window.clearTimeout(priceFilterTimer)
    priceFilterTimer = null
    handleSearch()
  }

  function schedulePriceFilter() {
    if (!loggedIn || resettingFilters) return
    window.clearTimeout(priceFilterTimer)
    priceFilterTimer = window.setTimeout(() => {
      priceFilterTimer = null
      handleSearch()
    }, 450)
  }

  function quickSearch(keyword) {
    filters.keyword = keyword
    handleSearch()
  }

  function clearKeyword() {
    filters.keyword = ''
    handleSearch()
  }

  function scrollToHall() {
    nextTick(() => document.querySelector('.hall')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
  }

  function searchByTag(tag) {
    filters.keyword = tag
    filters.tag = ''
    activeTag.value = ''
    router.replace({ path: '/', query: { keyword: tag } })
    handleSearch()
    scrollToHall()
  }

  function applyTopic(topic) {
    filters.keyword = topic.keyword || ''
    filters.tag = topic.filterTag || ''
    activeTag.value = topic.filterTag || ''
    filters.type = topic.filterType || ''
    const category = categories.value.find((item) => item.name === topic.categoryName)
    filters.categoryId = topic.filterCategoryId || category?.id || ''
    handleSearch()
    scrollToHall()
  }

  function resetFilters() {
    resettingFilters = true
    window.clearTimeout(priceFilterTimer)
    priceFilterTimer = null
    Object.assign(filters, {
      keyword: '',
      categoryId: '',
      minPrice: undefined,
      maxPrice: undefined,
      type: '',
      tag: '',
      sort: 'random',
    })
    activeTag.value = ''
    page.value = 1
    fetchItems()
    nextTick(() => { resettingFilters = false })
  }

  function selectCategory(id) {
    filters.categoryId = id
    handleSearch()
  }

  function goDetail(id) {
    router.push(`/item/${id}`)
  }

  async function handleFavorite(item) {
    if (!isLoggedIn()) {
      router.push({ path: '/login', query: { redirect: '/' } })
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

  function phClass(id) {
    return PLACEHOLDER_CLASSES[Number(id) % PLACEHOLDER_CLASSES.length]
  }

  function itemTypeLabel(type) {
    return ITEM_TYPE_LABELS[type] || type
  }

  watch(() => filters.sort, () => {
    if (!resettingFilters) handleSearch()
  })
  watch(() => filters.type, () => {
    if (!resettingFilters) handleSearch()
  })
  watch(() => [filters.minPrice, filters.maxPrice], schedulePriceFilter)

  onMounted(async () => {
    if (route.query.keyword) filters.keyword = String(route.query.keyword)
    if (!loggedIn) {
      await fetchCategories()
      return
    }
    await Promise.all([
      fetchCategories(),
      fetchItems(),
      fetchRanking(),
      fetchAllTags(),
      fetchActiveTopic(),
    ])
  })

  onBeforeUnmount(() => window.clearTimeout(priceFilterTimer))

  return {
    TYPE_OPTIONS: ITEM_TYPE_OPTIONS,
    activeTag,
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
    phClass,
    quickSearch,
    ranking,
    resetFilters,
    searchByTag,
    selectCategory,
    showTagCloud,
    toggleTagCloud: () => { showTagCloud.value = !showTagCloud.value },
    total,
  }
}
