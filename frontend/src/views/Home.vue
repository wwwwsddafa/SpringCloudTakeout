<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getProductList } from '@/api/product'
import { searchFood } from '@/api/search'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import type { Product, SearchResultItem } from '@/types'
import AppLayout from '@/components/AppLayout.vue'
import { getErrorMsg } from '@/utils/error'

const router = useRouter()
const cartStore = useCartStore()
const userStore = useUserStore()

const products = ref<Product[]>([])
const loading = ref(true)
const loadError = ref('')
const addingFid = ref<string | null>(null)

const keyword = ref('')
const category = ref('')
const sortBy = ref('')
const searchMode = ref(false)
const searchTotal = ref(0)
const searchPage = ref(1)
const searchSize = ref(10)

function getFirstPhoto(photo: string) {
  if (!photo) return ''
  return photo.split(',')[0] || ''
}

function goToDetail(fid: string) {
  router.push(`/product/${fid}`)
}

async function handleAddToCart(product: Product, e: Event) {
  e.stopPropagation()
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  addingFid.value = product.fid
  try {
    await cartStore.addItem({
      fid: product.fid,
      fname: product.fname,
      realprice: product.realprice,
      fphoto: getFirstPhoto(product.fphoto),
      num: 1,
    })
    alert('已加入购物车')
  } catch (err: any) {
    alert(getErrorMsg(err, '添加失败'))
  } finally {
    addingFid.value = null
  }
}

async function fetchProducts() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getProductList()
    products.value = res.data.data
  } catch (err: any) {
    loadError.value = getErrorMsg(err, '加载商品列表失败')
    console.error('加载商品列表失败', err)
  } finally {
    loading.value = false
  }
}

async function doSearch() {
  if (!keyword.value.trim() && !category.value) {
    searchMode.value = false
    await fetchProducts()
    return
  }
  loading.value = true
  loadError.value = ''
  searchMode.value = true
  searchPage.value = 1
  try {
    const res = await searchFood({
      keyword: keyword.value || undefined,
      category: category.value || undefined,
      page: searchPage.value,
      size: searchSize.value,
      sort: sortBy.value || undefined,
    })
    const result = res.data.data
    products.value = result.records as unknown as Product[]
    searchTotal.value = result.total
  } catch (err: any) {
    products.value = []
    loadError.value = getErrorMsg(err, '搜索商品失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  doSearch()
}

function onSortChange(sort: string) {
  sortBy.value = sort
  if (searchMode.value) {
    doSearch()
  }
}

function getHighlightHtml(item: Product, field: string): string | null {
  const searchItem = item as unknown as SearchResultItem
  if (searchItem.highlights && searchItem.highlights[field]) {
    return searchItem.highlights[field][0]
  }
  return null
}

onMounted(() => {
  fetchProducts()
})
</script>

<template>
  <AppLayout>
    <div class="home-page">
      <div class="hero-banner">
        <h1>美味外卖，即刻送达</h1>
        <p>精选优质商家，热乎美食送到家</p>
        <div class="search-box">
          <input
            v-model="keyword"
            type="text"
            placeholder="搜索美食..."
            @keyup.enter="onSearch"
            class="search-input"
          />
          <button class="btn-search" @click="onSearch">搜索</button>
        </div>
      </div>

      <div class="content-wrapper">
        <div class="filter-bar">
          <div class="category-filters">
            <button
              class="filter-btn"
              :class="{ active: category === '' }"
              @click="category = ''; onSearch()"
            >
              全部
            </button>
            <button
              class="filter-btn"
              :class="{ active: category === '热菜' }"
              @click="category = '热菜'; onSearch()"
            >
              热菜
            </button>
            <button
              class="filter-btn"
              :class="{ active: category === '凉菜' }"
              @click="category = '凉菜'; onSearch()"
            >
              凉菜
            </button>
            <button
              class="filter-btn"
              :class="{ active: category === '主食' }"
              @click="category = '主食'; onSearch()"
            >
              主食
            </button>
          </div>
          <div class="sort-options">
            <select v-model="sortBy" @change="onSortChange(sortBy)">
              <option value="">默认排序</option>
              <option value="realprice_asc">价格升序</option>
              <option value="realprice_desc">价格降序</option>
              <option value="likeCount_desc">热度降序</option>
            </select>
          </div>
        </div>

        <div v-if="searchMode" class="search-info">
          找到 {{ searchTotal }} 个相关商品
        </div>

        <div v-if="loading" class="loading-state">
          <div class="spinner"></div>
          <p>加载中...</p>
        </div>

        <div v-else-if="loadError" class="error-state">
          <p class="error-icon">🍽️</p>
          <p>{{ loadError }}</p>
          <button class="btn-retry" @click="fetchProducts">重试</button>
        </div>

        <div v-else-if="products.length === 0" class="empty-state">
          <p class="empty-icon">🍽️</p>
          <p>暂无在售商品</p>
        </div>

        <div v-else class="product-grid">
          <div
            v-for="product in products"
            :key="product.fid"
            class="product-card"
            @click="goToDetail(product.fid)"
          >
            <div class="card-img-wrap">
              <img
                :src="getFirstPhoto(product.fphoto)"
                :alt="product.fname"
                class="card-img"
                @error="($event.target as HTMLImageElement).src = 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%22200%22 height=%22200%22><rect fill=%22%23eee%22 width=%22200%22 height=%22200%22/><text x=%2250%25%22 y=%2250%25%22 text-anchor=%22middle%22 dy=%22.3em%22 fill=%22%23999%22 font-size=%2216%22>暂无图片</text></svg>'"
              />
              <span v-if="product.normprice > product.realprice" class="discount-tag">
                {{ Math.round((1 - product.realprice / product.normprice) * 100) }}% OFF
              </span>
            </div>
            <div class="card-body">
              <span class="category-tag">{{ product.category }}</span>
              <h3 class="card-title">
                <span v-if="getHighlightHtml(product, 'fname')" v-html="getHighlightHtml(product, 'fname')"></span>
                <span v-else>{{ product.fname }}</span>
              </h3>
              <p class="card-desc">
                <span v-if="getHighlightHtml(product, 'detail')" v-html="getHighlightHtml(product, 'detail')"></span>
                <span v-else>{{ product.detail }}</span>
              </p>
              <div class="card-footer">
                <div class="price-wrap">
                  <span class="real-price">¥{{ product.realprice }}</span>
                  <span v-if="product.normprice > product.realprice" class="norm-price">
                    ¥{{ product.normprice }}
                  </span>
                </div>
                <button
                  class="btn-add-cart"
                  :disabled="addingFid === product.fid"
                  @click="handleAddToCart(product, $event)"
                >
                  {{ addingFid === product.fid ? '...' : '+' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
}

.hero-banner {
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  color: #fff;
  text-align: center;
  padding: 48px 20px 36px;
}

.hero-banner h1 {
  font-size: 2rem;
  margin: 0 0 8px;
}

.hero-banner p {
  font-size: 1.05rem;
  opacity: 0.9;
  margin: 0 0 20px;
}

.search-box {
  display: flex;
  max-width: 480px;
  margin: 0 auto;
  gap: 0;
}

.search-input {
  flex: 1;
  padding: 12px 16px;
  border: 2px solid transparent;
  border-radius: 8px 0 0 8px;
  font-size: 0.95rem;
  outline: none;
  box-sizing: border-box;
}

.search-input:focus {
  border-color: #fff;
}

.btn-search {
  padding: 12px 24px;
  background: #fff;
  color: #ff6b35;
  border: none;
  border-radius: 0 8px 8px 0;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-search:hover {
  background: #fff5f0;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.category-filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-btn {
  padding: 6px 16px;
  border: 1px solid #e0e0e0;
  background: #fff;
  border-radius: 20px;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.2s;
  color: #666;
}

.filter-btn:hover {
  border-color: #ff6b35;
  color: #ff6b35;
}

.filter-btn.active {
  background: #ff6b35;
  color: #fff;
  border-color: #ff6b35;
}

.sort-options select {
  padding: 6px 12px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 0.85rem;
  outline: none;
  cursor: pointer;
  color: #666;
  background: #fff;
}

.search-info {
  font-size: 0.85rem;
  color: #999;
  margin-bottom: 12px;
}

.content-wrapper {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 20px;
}

.loading-state,
.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: #999;
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 12px;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #eee;
  border-top-color: #ff6b35;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto 12px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
}

.product-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.product-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.card-img-wrap {
  position: relative;
  width: 100%;
  height: 200px;
  overflow: hidden;
  background: #f0f0f0;
}

.card-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.discount-tag {
  position: absolute;
  top: 10px;
  right: 10px;
  background: #ff4444;
  color: #fff;
  font-size: 0.75rem;
  font-weight: 700;
  padding: 4px 8px;
  border-radius: 4px;
}

.card-body {
  padding: 16px;
}

.category-tag {
  display: inline-block;
  background: #fff3e0;
  color: #ff6b35;
  font-size: 0.75rem;
  padding: 2px 8px;
  border-radius: 4px;
  margin-bottom: 6px;
}

.card-title {
  font-size: 1.1rem;
  margin: 0 0 6px;
  color: #333;
}

.card-title :deep(em) {
  font-style: normal;
  color: #ff6b35;
  background: #fff3e0;
  padding: 0 2px;
  border-radius: 2px;
}

.card-desc {
  font-size: 0.85rem;
  color: #999;
  margin: 0 0 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc :deep(em) {
  font-style: normal;
  color: #ff6b35;
  background: #fff3e0;
  padding: 0 2px;
  border-radius: 2px;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.price-wrap {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.real-price {
  font-size: 1.2rem;
  font-weight: 700;
  color: #ff6b35;
}

.norm-price {
  font-size: 0.8rem;
  color: #bbb;
  text-decoration: line-through;
}

.btn-add-cart {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 2px solid #ff6b35;
  background: #fff;
  color: #ff6b35;
  font-size: 1.3rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.btn-add-cart:hover:not(:disabled) {
  background: #ff6b35;
  color: #fff;
}

.btn-add-cart:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .hero-banner h1 {
    font-size: 1.5rem;
  }

  .product-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 12px;
  }

  .card-img-wrap {
    height: 150px;
  }
}

.error-state {
  text-align: center;
  padding: 60px 20px;
  color: #c53030;
}

.error-icon {
  font-size: 3rem;
  margin: 0 0 12px;
}

.error-state p {
  margin: 0 0 16px;
  color: #c53030;
}

.error-box {
  color: #c53030;
}

.btn-retry {
  padding: 8px 24px;
  background: #fff;
  color: #c53030;
  border: 1px solid #c53030;
  border-radius: 8px;
  font-size: 0.9rem;
  cursor: pointer;
}

.btn-retry:hover {
  background: #c53030;
  color: #fff;
}

</style>