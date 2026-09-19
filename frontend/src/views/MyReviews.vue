<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyReviews, deleteReview } from '@/api/review'
import type { Review } from '@/types'
import AppLayout from '@/components/AppLayout.vue'
import { getErrorMsg } from '@/utils/error'

const router = useRouter()

const reviews = ref<Review[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const loadError = ref('')
const deletingId = ref<string | null>(null)

function formatTime(time: string) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

function parseReviewImages(images: string): string[] {
  if (!images) return []
  try {
    if (Array.isArray(images)) return images
    const parsed = JSON.parse(images)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function goToProduct(fid: string) {
  router.push(`/product/${fid}`)
}

async function fetchReviews() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getMyReviews(page.value, size)
    reviews.value = res.data.data.records
    total.value = res.data.data.total
  } catch (err: any) {
    reviews.value = []
    loadError.value = getErrorMsg(err, '加载评价失败')
  } finally {
    loading.value = false
  }
}

async function handleDelete(reviewId: string) {
  if (!confirm('确定要删除该评价吗？')) return
  deletingId.value = reviewId
  try {
    await deleteReview(reviewId)
    await fetchReviews()
  } catch (err: any) {
    alert(getErrorMsg(err, '删除失败'))
  } finally {
    deletingId.value = null
  }
}

function changePage(newPage: number) {
  page.value = newPage
  fetchReviews()
}

const totalPages = () => Math.ceil(total.value / size)

onMounted(() => {
  fetchReviews()
})
</script>

<template>
  <AppLayout>
    <div class="my-reviews-page">
      <div class="content-wrapper">
        <div class="page-header">
          <button class="btn-back" @click="router.back()">← 返回</button>
          <h2>我的评价</h2>
        </div>

        <div v-if="loading" class="loading-state">
          <div class="spinner"></div>
          <p>加载中...</p>
        </div>

        <div v-else-if="loadError" class="error-state">
          <p>{{ loadError }}</p>
          <button class="btn-retry" @click="fetchReviews">重试</button>
        </div>

        <div v-else-if="reviews.length === 0" class="empty-state">
          <p class="empty-icon">📝</p>
          <p>暂无评价</p>
          <router-link to="/" class="btn-go-shop">去逛逛</router-link>
        </div>

        <div v-else class="review-list">
          <div v-for="review in reviews" :key="review.reviewId" class="review-card">
            <div class="review-header">
              <div class="review-header-left">
                <span class="review-stars">{{ '★'.repeat(review.starRating) }}{{ '☆'.repeat(5 - review.starRating) }}</span>
                <span class="review-time">{{ formatTime(review.createTime) }}</span>
              </div>
              <div class="review-header-right">
                <button
                  class="btn-delete"
                  :disabled="deletingId === review.reviewId"
                  @click="handleDelete(review.reviewId)"
                >
                  {{ deletingId === review.reviewId ? '删除中...' : '删除' }}
                </button>
              </div>
            </div>
            <div v-if="review.reviewText" class="review-body">{{ review.reviewText }}</div>
            <div v-if="review.reviewImages" class="review-images">
              <img
                v-for="(img, idx) in parseReviewImages(review.reviewImages)"
                :key="idx"
                :src="img"
                class="review-img"
                @error="($event.target as HTMLImageElement).style.display = 'none'"
              />
            </div>
            <div class="review-footer">
              <button class="btn-to-product" @click="goToProduct(review.fid)">
                查看商品
              </button>
            </div>
          </div>

          <div v-if="totalPages() > 1" class="pagination">
            <button
              class="page-btn"
              :disabled="page <= 1"
              @click="changePage(page - 1)"
            >
              上一页
            </button>
            <span class="page-info">{{ page }} / {{ totalPages() }}</span>
            <button
              class="page-btn"
              :disabled="page >= totalPages()"
              @click="changePage(page + 1)"
            >
              下一页
            </button>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.my-reviews-page {
  min-height: calc(100vh - 60px);
}

.content-wrapper {
  max-width: 800px;
  margin: 0 auto;
  padding: 32px 20px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 1.5rem;
  color: #333;
}

.btn-back {
  background: none;
  border: none;
  color: #ff6b35;
  font-size: 1rem;
  cursor: pointer;
  padding: 4px 8px;
}

.loading-state,
.error-state,
.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: #999;
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
  to { transform: rotate(360deg); }
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 12px;
}

.btn-retry,
.btn-go-shop {
  display: inline-block;
  margin-top: 12px;
  padding: 8px 24px;
  border-radius: 8px;
  font-size: 0.95rem;
  cursor: pointer;
  border: none;
  text-decoration: none;
}

.btn-retry {
  background: #ff6b35;
  color: #fff;
}

.btn-go-shop {
  background: #ff6b35;
  color: #fff;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.review-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.review-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.review-stars {
  color: #f5a623;
  font-size: 1rem;
}

.review-time {
  color: #999;
  font-size: 0.85rem;
}

.btn-delete {
  background: none;
  border: 1px solid #e0e0e0;
  color: #999;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-delete:hover:not(:disabled) {
  border-color: #e53e3e;
  color: #e53e3e;
}

.btn-delete:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.review-body {
  color: #555;
  line-height: 1.7;
  font-size: 0.95rem;
  margin-bottom: 12px;
}

.review-images {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.review-img {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 8px;
}

.review-footer {
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
}

.btn-to-product {
  background: none;
  border: none;
  color: #ff6b35;
  font-size: 0.9rem;
  cursor: pointer;
  padding: 0;
}

.btn-to-product:hover {
  text-decoration: underline;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  padding: 24px 0;
}

.page-btn {
  padding: 8px 20px;
  border-radius: 8px;
  border: 1px solid #ddd;
  background: #fff;
  color: #555;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  border-color: #ff6b35;
  color: #ff6b35;
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  color: #666;
  font-size: 0.9rem;
}
</style>