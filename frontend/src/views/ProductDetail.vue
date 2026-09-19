<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProductDetail } from '@/api/product'
import { likeProduct, getLikeCount, getLikeStatus, type LikeResult } from '@/api/like'
import { createReview, deleteReview, getReviewList, getReviewRating, getReviewCheck } from '@/api/review'
import { checkPurchase } from '@/api/order'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import type { Product, Review, ReviewRating } from '@/types'
import AppLayout from '@/components/AppLayout.vue'
import { getErrorMsg } from '@/utils/error'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()
const userStore = useUserStore()

const product = ref<Product | null>(null)
const loading = ref(true)
const quantity = ref(1)
const addingCart = ref(false)
const currentImageIndex = ref(0)

const likeCount = ref(0)
const dislikeCount = ref(0)
const likeStatus = ref<number | null>(null)
const likeLoading = ref(false)

watch(
  () => [likeCount.value, dislikeCount.value, likeStatus.value],
  ([likes, dislikes, status]) => {
    console.log('🔄 响应式更新触发！likeCount:', likes, 'dislikeCount:', dislikes, 'status:', status)
  }
)

const hasPurchased = ref(false)
const myReview = ref<Review | null>(null)

const reviewTab = ref<'list' | 'write'>('list')
const reviewList = ref<Review[]>([])
const reviewTotal = ref(0)
const reviewPage = ref(1)
const reviewLoading = ref(false)
const rating = ref<ReviewRating>({ avgStar: 0, reviewCount: 0, distribution: [0, 0, 0, 0, 0] })
const reviewForm = ref({ starRating: 5, reviewText: '', reviewImages: [] as File[] })
const reviewSubmitting = ref(false)
const reviewError = ref('')

const images = computed(() => {
  if (!product.value?.fphoto) return []
  return product.value.fphoto
    .split(',')
    .filter((url) => url.trim())
})

async function fetchDetail() {
  loading.value = true
  try {
    const fid = route.params.fid as string
    const res = await getProductDetail(fid)
    product.value = res.data.data
  } catch (err: any) {
    alert(getErrorMsg(err, '商品不存在或已下架'))
    router.push('/')
  } finally {
    loading.value = false
  }
}

async function fetchLikeCount() {
  try {
    const fid = route.params.fid as string
    const res = await getLikeCount(fid)
    const [likes, dislikes] = res.data.data
    likeCount.value = likes
    dislikeCount.value = dislikes
  } catch {
    likeCount.value = 0
    dislikeCount.value = 0
  }
}

async function fetchLikeStatus() {
  if (!userStore.isLoggedIn) return
  try {
    const fid = route.params.fid as string
    const res = await getLikeStatus(fid)
    likeStatus.value = res.data.data
  } catch {
    likeStatus.value = null
  }
}

async function fetchPurchaseStatus() {
  if (!userStore.isLoggedIn) return
  try {
    const fid = route.params.fid as string
    await checkPurchase(fid)
    hasPurchased.value = true
  } catch {
    hasPurchased.value = false
  }
}

async function fetchReviewCheck() {
  if (!userStore.isLoggedIn) return
  try {
    const fid = route.params.fid as string
    const res = await getReviewCheck(fid)
    myReview.value = res.data.data
  } catch {
    myReview.value = null
  }
}

async function fetchRating() {
  try {
    const fid = route.params.fid as string
    const res = await getReviewRating(fid)
    rating.value = res.data.data
  } catch {
    rating.value = { avgStar: 0, reviewCount: 0, distribution: [0, 0, 0, 0, 0] }
  }
}

async function fetchReviews() {
  reviewLoading.value = true
  try {
    const fid = route.params.fid as string
    const res = await getReviewList(fid, reviewPage.value)
    reviewList.value = res.data.data.records
    reviewTotal.value = res.data.data.total
  } catch {
    reviewList.value = []
    reviewTotal.value = 0
  } finally {
    reviewLoading.value = false
  }
}

async function handleAddCart() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  if (!product.value) return
  addingCart.value = true
  try {
    await cartStore.addItem({
      fid: product.value.fid,
      fname: product.value.fname,
      realprice: product.value.realprice,
      fphoto: product.value.fphoto,
      num: quantity.value,
    })
    alert('已加入购物车')
  } catch (err: any) {
    alert(getErrorMsg(err, '添加失败'))
  } finally {
    addingCart.value = false
  }
}

async function handleLike(likeType: number) {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  if (!hasPurchased.value) {
    alert('只有购买过该商品的用户才能评价')
    return
  }
  if (likeLoading.value) return

  const previousStatus = likeStatus.value
  const previousLikeCount = likeCount.value
  const previousDislikeCount = dislikeCount.value

  // 乐观更新：立即更新本地状态
  if (previousStatus === likeType) {
    // 再次点击相同类型 = 取消
    likeStatus.value = null
    if (likeType === 1) likeCount.value = previousLikeCount - 1
    else dislikeCount.value = previousDislikeCount - 1
  } else {
    // 切换状态或新增
    if (previousStatus === 1) likeCount.value = previousLikeCount - 1
    else if (previousStatus === -1) dislikeCount.value = previousDislikeCount - 1

    likeStatus.value = likeType
    if (likeType === 1) likeCount.value = likeCount.value + 1
    else dislikeCount.value = dislikeCount.value + 1
  }

  likeLoading.value = true
  try {
    const fid = route.params.fid as string
    const res = await likeProduct(fid, likeType)
    const { likeCount: newLike, dislikeCount: newDislike, status } = res.data.data
    // 用服务器返回的数据校准
    likeCount.value = newLike
    dislikeCount.value = newDislike
    likeStatus.value = status.includes('已取消') ? null : likeType
  } catch (err: any) {
    // 请求失败，回滚到之前的状态
    likeStatus.value = previousStatus
    likeCount.value = previousLikeCount
    dislikeCount.value = previousDislikeCount
    alert(getErrorMsg(err, '操作失败'))
  } finally {
    likeLoading.value = false
  }
}

async function handleSubmitReview() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  if (!hasPurchased.value) {
    alert('只有购买过该商品的用户才能评价')
    return
  }
  if (reviewForm.value.starRating < 1 || reviewForm.value.starRating > 5) {
    reviewError.value = '请选择1-5星评分'
    return
  }
  reviewSubmitting.value = true
  reviewError.value = ''
  try {
    const fid = route.params.fid as string
    const formData = new FormData()
    formData.append('fid', fid)
    formData.append('starRating', String(reviewForm.value.starRating))
    if (reviewForm.value.reviewText) {
      formData.append('reviewText', reviewForm.value.reviewText)
    }
    if (reviewForm.value.reviewImages?.length) {
      reviewForm.value.reviewImages.forEach((file) => {
        formData.append('reviewImages', file)
      })
    }
    await createReview(formData)
    reviewForm.value = { starRating: 5, reviewText: '', reviewImages: [] }
    reviewTab.value = 'list'
    myReview.value = null
    await fetchReviewCheck()
    fetchRating()
    fetchReviews()
    alert('评价成功')
  } catch (err: any) {
    reviewError.value = getErrorMsg(err, '评价失败')
  } finally {
    reviewSubmitting.value = false
  }
}

async function handleDeleteReview(reviewId: string) {
  if (!confirm('确定删除这条评价吗？')) return
  try {
    await deleteReview(reviewId)
    myReview.value = null
    await fetchReviewCheck()
    fetchRating()
    fetchReviews()
    alert('删除成功')
  } catch (err: any) {
    alert(getErrorMsg(err, '删除失败'))
  }
}

function changeReviewPage(page: number) {
  reviewPage.value = page
  fetchReviews()
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

function goToCart() {
  router.push('/cart')
}

function openImage(url: string) {
  window.open(url, '_blank')
}

onMounted(async () => {
  await fetchDetail()
  fetchLikeCount()
  fetchLikeStatus()
  await fetchPurchaseStatus()
  fetchReviewCheck()
  fetchRating()
  fetchReviews()
})
</script>

<template>
  <AppLayout>
    <div class="detail-page">
      <div v-if="loading" class="loading-state">
        <div class="spinner"></div>
        <p>加载中...</p>
      </div>

      <template v-else-if="product">
        <div class="detail-content">
          <div class="image-section">
            <div class="main-image">
              <img
                :src="images[currentImageIndex] || ''"
                :alt="product.fname"
                @error="($event.target as HTMLImageElement).src = 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%22400%22 height=%22400%22><rect fill=%22%23eee%22 width=%22400%22 height=%22400%22/><text x=%2250%25%22 y=%2250%25%22 text-anchor=%22middle%22 dy=%22.3em%22 fill=%22%23999%22 font-size=%2220%22>暂无图片</text></svg>'"
              />
            </div>
            <div v-if="images.length > 1" class="thumb-list">
              <div
                v-for="(img, idx) in images"
                :key="idx"
                class="thumb-item"
                :class="{ active: idx === currentImageIndex }"
                @click="currentImageIndex = idx"
              >
                <img :src="img" :alt="`图片${idx + 1}`" />
              </div>
            </div>
          </div>

          <div class="info-section">
            <h1 class="product-name">{{ product.fname }}</h1>
            <p class="product-category">{{ product.category }}</p>

            <div class="price-section">
              <span class="real-price">¥{{ product.realprice }}</span>
              <span v-if="product.normprice > product.realprice" class="norm-price">
                原价 ¥{{ product.normprice }}
              </span>
            </div>

            <div class="like-section">
              <button
                class="btn-like"
                :class="{ active: likeStatus === 1 }"
                :disabled="likeLoading || !userStore.isLoggedIn"
                @click="handleLike(1)"
              >
                👍 赞 {{ likeCount }}
              </button>
              <button
                class="btn-dislike"
                :class="{ active: likeStatus === -1 }"
                :disabled="likeLoading || !userStore.isLoggedIn"
                @click="handleLike(-1)"
              >
                👎 踩 {{ dislikeCount }}
              </button>
            </div>

            <div class="detail-desc">
              <h3>商品详情</h3>
              <p>{{ product.detail }}</p>
            </div>

            <div class="action-section">
              <div class="quantity-control">
                <button class="btn-quantity" @click="quantity > 1 && quantity--">-</button>
                <span class="quantity-num">{{ quantity }}</span>
                <button class="btn-quantity" @click="quantity++">+</button>
              </div>
              <button class="btn-add-cart" :disabled="addingCart" @click="handleAddCart">
                {{ addingCart ? '加入中...' : '加入购物车' }}
              </button>
              <button class="btn-buy" @click="goToCart">去结算</button>
            </div>
          </div>
        </div>

        <div class="review-section">
          <div class="review-header">
            <h2>用户评价</h2>
            <div class="rating-summary">
              <span class="avg-star">{{ rating.avgStar.toFixed(1) }}</span>
              <span class="review-count">({{ rating.reviewCount }}条评价)</span>
            </div>
          </div>

          <div class="rating-distribution">
            <div
              v-for="(count, idx) in [...rating.distribution].reverse()"
              :key="idx"
              class="rating-bar"
            >
              <span class="star-label">{{ 5 - idx }}星</span>
              <div class="bar-track">
                <div
                  class="bar-fill"
                  :style="{ width: rating.reviewCount > 0 ? (count / rating.reviewCount * 100) + '%' : '0%' }"
                ></div>
              </div>
              <span class="bar-count">{{ count }}</span>
            </div>
          </div>

          <div class="review-tabs">
            <button
              class="tab-btn"
              :class="{ active: reviewTab === 'list' }"
              @click="reviewTab = 'list'"
            >
              全部评价
            </button>
            <button
              v-if="!myReview && hasPurchased"
              class="tab-btn"
              :class="{ active: reviewTab === 'write' }"
              @click="reviewTab = 'write'"
            >
              写评价
            </button>
            <button
              v-if="myReview"
              class="tab-btn"
              :class="{ active: reviewTab === 'write' }"
              @click="reviewTab = 'write'"
            >
              查看我的评价
            </button>
          </div>

          <div v-if="reviewTab === 'list'" class="review-list">
            <div v-for="review in reviewList" :key="review.reviewId" class="review-item">
              <div class="review-user">
                <img
                  :src="review.avatar || 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2240%22 height=%2240%22><circle fill=%22%23ddd%22 cx=%2220%22 cy=%2220%22 r=%2220%22/><text x=%2250%25%22 y=%2250%25%22 text-anchor=%22middle%22 dy=%22.3em%22 fill=%22%23999%22 font-size=%2214%22>用户</text></svg>'"
                  class="user-avatar"
                />
                <span class="user-name">{{ review.username }}</span>
                <span class="star-rating">{{ '★'.repeat(review.starRating) }}{{ '☆'.repeat(5 - review.starRating) }}</span>
              </div>
              <p class="review-text">{{ review.reviewText }}</p>
              <div v-if="review.reviewImages" class="review-images">
                <img
                  v-for="(img, idx) in parseReviewImages(review.reviewImages)"
                  :key="idx"
                  :src="img"
                  class="review-img"
                  @click="openImage(img)"
                />
              </div>
              <div class="review-meta">
                <span>{{ review.createTime }}</span>
                <button
                  v-if="review.userId === userStore.userInfo?.userId"
                  class="btn-delete-review"
                  @click="handleDeleteReview(review.reviewId)"
                >
                  删除
                </button>
              </div>
            </div>
            <div v-if="reviewList.length === 0" class="empty-reviews">暂无评价</div>
            <div v-if="reviewTotal > 10" class="pagination">
              <button
                v-for="page in Math.ceil(reviewTotal / 10)"
                :key="page"
                class="page-btn"
                :class="{ active: page === reviewPage }"
                @click="changeReviewPage(page)"
              >
                {{ page }}
              </button>
            </div>
          </div>

          <div v-if="reviewTab === 'write'" class="review-form">
            <div v-if="myReview" class="my-review">
              <h3>我的评价</h3>
              <div class="review-user">
                <span class="star-rating">{{ '★'.repeat(myReview.starRating) }}{{ '☆'.repeat(5 - myReview.starRating) }}</span>
              </div>
              <p class="review-text">{{ myReview.reviewText }}</p>
              <div v-if="myReview.reviewImages" class="review-images">
                <img
                  v-for="(img, idx) in parseReviewImages(myReview.reviewImages)"
                  :key="idx"
                  :src="img"
                  class="review-img"
                />
              </div>
              <button class="btn-delete-review" @click="handleDeleteReview(myReview.reviewId)">
                删除评价
              </button>
            </div>
            <template v-else>
              <div class="form-group">
                <label>评分</label>
                <div class="star-input">
                  <span
                    v-for="star in 5"
                    :key="star"
                    class="star"
                    :class="{ active: star <= reviewForm.starRating }"
                    @click="reviewForm.starRating = star"
                  >
                    ★
                  </span>
                </div>
              </div>
              <div class="form-group">
                <label>评价内容</label>
                <textarea
                  v-model="reviewForm.reviewText"
                  rows="4"
                  placeholder="分享您的使用体验..."
                ></textarea>
              </div>
              <div class="form-group">
                <label>上传图片</label>
                <input
                  type="file"
                  multiple
                  accept="image/*"
                  @change="(e: any) => reviewForm.reviewImages = Array.from(e.target.files)"
                />
              </div>
              <p v-if="reviewError" class="error-msg">{{ reviewError }}</p>
              <button class="btn-submit" :disabled="reviewSubmitting" @click="handleSubmitReview">
                {{ reviewSubmitting ? '提交中...' : '提交评价' }}
              </button>
            </template>
          </div>
        </div>
      </template>

      <div v-else class="empty-state">
        <p>商品不存在或已下架</p>
        <router-link to="/" class="btn-back">返回首页</router-link>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.detail-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.detail-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 40px;
  margin-bottom: 40px;
}

.image-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.main-image {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 12px;
  overflow: hidden;
  background: #f5f5f5;
}

.main-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-list {
  display: flex;
  gap: 8px;
}

.thumb-item {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  border: 2px solid transparent;
  transition: border-color 0.2s;
}

.thumb-item.active {
  border-color: #ff6b35;
}

.thumb-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.info-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.product-name {
  font-size: 28px;
  font-weight: 700;
  color: #333;
  margin: 0;
}

.product-category {
  color: #999;
  font-size: 14px;
  margin: 0;
}

.price-section {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.real-price {
  font-size: 32px;
  font-weight: 700;
  color: #ff6b35;
}

.norm-price {
  font-size: 16px;
  color: #999;
  text-decoration: line-through;
}

.like-section {
  display: flex;
  gap: 12px;
}

.btn-like,
.btn-dislike {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border: 1px solid #ddd;
  border-radius: 20px;
  background: #fff;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
}

.btn-like:hover,
.btn-dislike:hover {
  background: #f5f5f5;
}

.btn-like.active {
  background: #ff6b35;
  color: #fff;
  border-color: #ff6b35;
}

.btn-dislike.active {
  background: #666;
  color: #fff;
  border-color: #666;
}

.btn-like:disabled,
.btn-dislike:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.detail-desc {
  background: #f9f9f9;
  padding: 20px;
  border-radius: 12px;
}

.detail-desc h3 {
  margin: 0 0 12px;
  font-size: 16px;
}

.detail-desc p {
  margin: 0;
  color: #666;
  line-height: 1.6;
}

.action-section {
  display: flex;
  gap: 12px;
  margin-top: auto;
}

.quantity-control {
  display: flex;
  align-items: center;
  border: 1px solid #ddd;
  border-radius: 8px;
  overflow: hidden;
}

.btn-quantity {
  width: 40px;
  height: 40px;
  border: none;
  background: #f5f5f5;
  cursor: pointer;
  font-size: 18px;
}

.quantity-num {
  width: 50px;
  text-align: center;
  font-size: 16px;
}

.btn-add-cart,
.btn-buy {
  flex: 1;
  height: 48px;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.btn-add-cart {
  background: #ff6b35;
  color: #fff;
}

.btn-buy {
  background: #333;
  color: #fff;
}

.btn-add-cart:hover,
.btn-buy:hover {
  opacity: 0.9;
}

.btn-add-cart:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.review-section {
  margin-top: 40px;
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.review-header h2 {
  margin: 0;
  font-size: 20px;
}

.rating-summary {
  display: flex;
  align-items: center;
  gap: 8px;
}

.avg-star {
  font-size: 24px;
  font-weight: 700;
  color: #ff6b35;
}

.review-count {
  color: #999;
}

.rating-distribution {
  margin-bottom: 20px;
  padding: 16px;
  background: #f9f9f9;
  border-radius: 12px;
}

.rating-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.rating-bar:last-child {
  margin-bottom: 0;
}

.star-label {
  width: 40px;
  font-size: 14px;
  color: #666;
}

.bar-track {
  flex: 1;
  height: 8px;
  background: #e0e0e0;
  border-radius: 4px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: #ff6b35;
  border-radius: 4px;
  transition: width 0.3s ease;
}

.bar-count {
  width: 30px;
  text-align: right;
  font-size: 14px;
  color: #999;
}

.review-tabs {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
  border-bottom: 1px solid #eee;
}

.tab-btn {
  padding: 12px 0;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 14px;
  color: #666;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}

.tab-btn.active {
  color: #ff6b35;
  border-bottom-color: #ff6b35;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.review-item {
  padding: 20px;
  background: #f9f9f9;
  border-radius: 12px;
}

.review-user {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
}

.user-name {
  font-weight: 500;
}

.star-rating {
  color: #ff6b35;
}

.review-text {
  margin: 0 0 12px;
  color: #333;
  line-height: 1.6;
}

.review-images {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.review-img {
  width: 100px;
  height: 100px;
  object-fit: cover;
  border-radius: 8px;
  cursor: pointer;
}

.review-meta {
  display: flex;
  justify-content: space-between;
  color: #999;
  font-size: 12px;
}

.btn-delete-review {
  color: #ff6b35;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 12px;
}

.empty-reviews {
  text-align: center;
  padding: 40px;
  color: #999;
}

.pagination {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 20px;
}

.page-btn {
  width: 36px;
  height: 36px;
  border: 1px solid #ddd;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: all 0.2s;
}

.page-btn.active {
  background: #ff6b35;
  color: #fff;
  border-color: #ff6b35;
}

.review-form {
  max-width: 600px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
}

.star-input {
  display: flex;
  gap: 8px;
}

.star {
  font-size: 28px;
  color: #ddd;
  cursor: pointer;
  transition: color 0.2s;
}

.star.active {
  color: #ff6b35;
}

.form-group textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  resize: vertical;
  font-family: inherit;
}

.error-msg {
  color: #ff6b35;
  margin-bottom: 12px;
}

.btn-submit {
  width: 100%;
  height: 48px;
  background: #ff6b35;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.btn-submit:hover {
  opacity: 0.9;
}

.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.my-review {
  padding: 20px;
  background: #f9f9f9;
  border-radius: 12px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #f3f3f3;
  border-top-color: #ff6b35;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
}

.btn-back {
  display: inline-block;
  margin-top: 16px;
  padding: 12px 24px;
  background: #ff6b35;
  color: #fff;
  text-decoration: none;
  border-radius: 8px;
}

@media (max-width: 768px) {
  .detail-content {
    grid-template-columns: 1fr;
  }
}
</style>