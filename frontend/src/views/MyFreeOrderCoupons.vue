<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyFreeOrderCoupons } from '@/api/freeOrder'
import type { FreeOrderCoupon } from '@/types'
import AppLayout from '@/components/AppLayout.vue'
import { getErrorMsg } from '@/utils/error'

const router = useRouter()

const coupons = ref<FreeOrderCoupon[]>([])
const loading = ref(true)
const loadError = ref('')

async function fetchCoupons() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getMyFreeOrderCoupons()
    coupons.value = res.data.data || []
  } catch (err: any) {
    coupons.value = []
    loadError.value = getErrorMsg(err, '加载免单券失败')
  } finally {
    loading.value = false
  }
}

function formatTime(time: string) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

function goToCheckout() {
  router.push('/create-order')
}

onMounted(() => {
  fetchCoupons()
})
</script>

<template>
  <AppLayout>
    <div class="my-coupons-page">
      <div class="content-wrapper">
        <div class="page-header">
          <button class="btn-back" @click="router.push('/free-order')">← 返回</button>
          <h2>我的免单券</h2>
        </div>

        <div v-if="loading" class="loading-state">
          <div class="spinner"></div>
          <p>加载中...</p>
        </div>

        <div v-else-if="loadError" class="error-state">
          <p class="error-icon">🎫</p>
          <p>{{ loadError }}</p>
          <button class="btn-retry" @click="fetchCoupons">重试</button>
        </div>

        <div v-else-if="coupons.length === 0" class="empty-state">
          <div class="empty-icon">🎫</div>
          <h3>暂无免单券</h3>
          <p>快去参加免单活动抢券吧</p>
          <button class="btn-go" @click="router.push('/free-order')">去抢券</button>
        </div>

        <div v-else class="coupon-list">
          <div
            v-for="coupon in coupons"
            :key="coupon.couponNo"
            class="coupon-card"
            :class="{
              used: coupon.status === 'USED',
              expired: coupon.status === 'EXPIRED',
            }"
          >
            <div class="coupon-header">
              <span class="coupon-amount">¥{{ coupon.couponAmount }}</span>
              <span class="coupon-status">
                {{ coupon.status === 'UNUSED' ? '未使用' : coupon.status === 'USED' ? '已使用' : '已过期' }}
              </span>
            </div>
            <div class="coupon-body">
              <p class="coupon-event">{{ coupon.eventName }}</p>
              <p class="coupon-range">金额范围：¥{{ coupon.minAmount }} ~ ¥{{ coupon.maxAmount }}</p>
              <p class="coupon-time">
                获取时间：{{ formatTime(coupon.createTime) }}
              </p>
            </div>
            <div v-if="coupon.status === 'UNUSED'" class="coupon-action">
              <button class="btn-use" @click="goToCheckout">去使用</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.my-coupons-page {
  min-height: calc(100vh - 60px);
  background: #f8f9fa;
}

.content-wrapper {
  max-width: 640px;
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

.loading-state {
  text-align: center;
  padding: 60px 0;
  color: #999;
}

.spinner {
  width: 36px;
  height: 36px;
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

.empty-state {
  text-align: center;
  padding: 60px 20px;
}

.empty-icon {
  font-size: 4rem;
  margin-bottom: 16px;
}

.empty-state h3 {
  margin: 0 0 8px;
  color: #555;
  font-size: 1.1rem;
}

.empty-state p {
  margin: 0 0 20px;
  color: #999;
}

.btn-go {
  padding: 10px 24px;
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 0.9rem;
  cursor: pointer;
}

.coupon-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.coupon-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border: 1.5px solid #ff6b35;
}

.coupon-card.used,
.coupon-card.expired {
  border-color: #e0e0e0;
  opacity: 0.7;
}

.coupon-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: linear-gradient(135deg, #fff5f0 0%, #fff8f4 100%);
}

.coupon-card.used .coupon-header,
.coupon-card.expired .coupon-header {
  background: #f5f5f5;
}

.coupon-amount {
  font-size: 1.8rem;
  font-weight: 700;
  color: #ff6b35;
}

.coupon-card.used .coupon-amount,
.coupon-card.expired .coupon-amount {
  color: #bbb;
}

.coupon-status {
  font-size: 0.8rem;
  color: #ff6b35;
  padding: 4px 10px;
  background: #fff;
  border-radius: 12px;
  font-weight: 600;
}

.coupon-card.used .coupon-status,
.coupon-card.expired .coupon-status {
  color: #999;
  background: #eee;
}

.coupon-body {
  padding: 14px 20px;
}

.coupon-event {
  margin: 0 0 6px;
  font-size: 1rem;
  color: #333;
  font-weight: 600;
}

.coupon-range {
  margin: 0 0 6px;
  font-size: 0.8rem;
  color: #ff6b35;
}

.coupon-time {
  margin: 0;
  font-size: 0.8rem;
  color: #999;
}

.coupon-action {
  padding: 0 20px 16px;
}

.btn-use {
  width: 100%;
  padding: 10px;
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s;
}

.btn-use:hover {
  opacity: 0.9;
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