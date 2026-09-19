<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { getOrderList, confirmOrder, cancelOrder } from '@/api/order'
import type { Order } from '@/types'
import AppLayout from '@/components/AppLayout.vue'
import { getErrorMsg } from '@/utils/error'

const router = useRouter()
const orders = ref<Order[]>([])
const loading = ref(true)
const loadError = ref('')
const actionOid = ref<string | null>(null)

const statusMap: Record<number, { label: string; class: string }> = {
  0: { label: '待支付', class: 'status-pending' },
  1: { label: '已支付', class: 'status-paid' },
  2: { label: '已完成', class: 'status-done' },
  3: { label: '已取消', class: 'status-cancel' },
}

function formatTime(time: string) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

function goToDetail(roid: string) {
  router.push(`/order/${roid}`)
}

async function handleConfirm(roid: string) {
  if (!confirm('确认支付该订单？')) return
  actionOid.value = roid
  try {
    await confirmOrder(roid)
    await fetchOrders()
  } catch (err: any) {
    alert(getErrorMsg(err, '支付失败'))
  } finally {
    actionOid.value = null
  }
}

async function handleCancel(roid: string) {
  if (!confirm('确定要取消该订单吗？')) return
  actionOid.value = roid
  try {
    await cancelOrder(roid)
    await fetchOrders()
  } catch (err: any) {
    alert(getErrorMsg(err, '取消失败'))
  } finally {
    actionOid.value = null
  }
}

async function fetchOrders() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getOrderList()
    orders.value = res.data.data
  } catch (err: any) {
    orders.value = []
    loadError.value = getErrorMsg(err, '加载订单失败')
  } finally {
    loading.value = false
  }
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    fetchOrders()
  }
}

onMounted(() => {
  fetchOrders()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <AppLayout>
    <div class="order-list-page">
      <div class="content-wrapper">
        <h2 class="page-title">我的订单</h2>

        <div v-if="loading" class="loading-state">
          <div class="spinner"></div>
          <p>加载中...</p>
        </div>

        <div v-else-if="loadError" class="error-state">
          <p class="error-icon">📋</p>
          <p>{{ loadError }}</p>
          <button class="btn-retry" @click="fetchOrders">重试</button>
        </div>

        <div v-else-if="orders.length === 0" class="empty-state">
          <p class="empty-icon">📋</p>
          <p>暂无订单</p>
          <router-link to="/" class="btn-go-shop">去逛逛</router-link>
        </div>

        <div v-else class="order-list">
          <div
            v-for="order in orders"
            :key="order.roid"
            class="order-card"
            @click="goToDetail(order.roid)"
          >
            <div class="order-header">
              <span class="order-id">订单号：{{ order.roid }}</span>
              <span
                class="order-status"
                :class="statusMap[order.status]?.class || ''"
              >
                {{ statusMap[order.status]?.label || '未知' }}
              </span>
            </div>

            <div class="order-items">
              <div
                v-for="item in order.items"
                :key="item.fid"
                class="order-item"
              >
                <span class="oi-name">{{ item.fname }}</span>
                <span class="oi-num">x{{ item.num }}</span>
                <span class="oi-price">¥{{ (item.dealprice * item.num).toFixed(2) }}</span>
              </div>
            </div>

            <div class="order-footer">
              <div class="order-footer-left">
                <span v-if="(order.discountAmount ?? (order as any).freeOrderMaxAmount ?? 0) > 0" class="coupon-tag">🎫 已使用免单券</span>
                <span class="order-time">{{ formatTime(order.orderTime) }}</span>
              </div>
              <div class="order-actions" @click.stop>
                <button
                  v-if="order.status === 0"
                  class="btn-pay"
                  @click="goToDetail(order.roid)"
                >
                  去支付
                </button>
                <button
                  v-if="order.status === 0"
                  class="btn-cancel"
                  :disabled="actionOid === order.roid"
                  @click="handleCancel(order.roid)"
                >
                  取消订单
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
.order-list-page {
  min-height: calc(100vh - 60px);
}

.content-wrapper {
  max-width: 800px;
  margin: 0 auto;
  padding: 32px 20px;
}

.page-title {
  margin: 0 0 24px;
  font-size: 1.5rem;
  color: #333;
}

.loading-state,
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

.btn-go-shop {
  display: inline-block;
  margin-top: 16px;
  padding: 10px 32px;
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  color: #fff;
  text-decoration: none;
  border-radius: 8px;
  font-weight: 600;
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.order-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: box-shadow 0.2s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.order-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 12px;
}

.order-id {
  font-size: 0.85rem;
  color: #999;
}

.order-status {
  font-size: 0.85rem;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 20px;
}

.status-pending {
  background: #fff7ed;
  color: #f7931e;
}

.status-paid {
  background: #f0fdf4;
  color: #22c55e;
}

.status-done {
  background: #eff6ff;
  color: #3b82f6;
}

.status-cancel {
  background: #fef2f2;
  color: #ef4444;
}

.order-items {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.order-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.9rem;
}

.oi-name {
  flex: 1;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.oi-num {
  color: #999;
  min-width: 24px;
}

.oi-price {
  color: #333;
  font-weight: 600;
  min-width: 50px;
  text-align: right;
}

.order-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.order-footer-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.coupon-tag {
  font-size: 0.8rem;
  color: #38a169;
  background: #f0fdf4;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.order-time {
  font-size: 0.8rem;
  color: #bbb;
}

.order-actions {
  display: flex;
  gap: 8px;
}

.btn-pay,
.btn-cancel {
  padding: 7px 16px;
  border-radius: 6px;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
  font-weight: 600;
}

.btn-pay {
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  color: #fff;
}

.btn-pay:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-cancel {
  background: #fff;
  color: #999;
  border: 1px solid #ddd;
}

.btn-cancel:hover:not(:disabled) {
  border-color: #e53e3e;
  color: #e53e3e;
}

.btn-pay:disabled,
.btn-cancel:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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