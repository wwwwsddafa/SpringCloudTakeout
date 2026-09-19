﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getOrderDetail, confirmOrder, cancelOrder, alipayPay } from '@/api/order'
import type { Order } from '@/types'
import AppLayout from '@/components/AppLayout.vue'
import { getErrorMsg } from '@/utils/error'

const route = useRoute()
const router = useRouter()

const order = ref<Order | null>(null)
const loading = ref(true)
const actionLoading = ref(false)
const pollingTimer = ref<ReturnType<typeof setInterval> | null>(null)

const itemTotal = computed(() =>
  order.value
    ? order.value.items.reduce((sum, i) => sum + i.dealprice * i.num, 0)
    : 0
)

const totalAmount = computed(() => order.value?.totalAmount ?? itemTotal.value)

const discountAmount = computed(() => {
  if (order.value?.discountAmount != null) return order.value.discountAmount
  const legacy = (order.value as any)?.freeOrderMaxAmount
  return legacy ?? 0
})

const payAmount = computed(() => {
  if (order.value?.payAmount != null) return order.value.payAmount
  return totalAmount.value - discountAmount.value
})

const statusMap: Record<number, { label: string; class: string }> = {
  0: { label: '待支付', class: 'status-pending' },
  1: { label: '已支付', class: 'status-paid' },
  2: { label: '已完成', class: 'status-done' },
  3: { label: '已取消', class: 'status-cancel' },
}

function formatTime(time: string | null) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

async function fetchDetail() {
  loading.value = true
  try {
    const roid = route.params.roid as string
    const res = await getOrderDetail(roid)
    order.value = res.data.data
  } catch (err: any) {
    alert(getErrorMsg(err, '订单不存在'))
    router.push('/order/list')
  } finally {
    loading.value = false
  }
}

function startPolling() {
  if (!order.value || order.value.status !== 0) return

  pollingTimer.value = setInterval(async () => {
    if (!order.value || order.value.status !== 0) {
      stopPolling()
      return
    }
    try {
      const roid = route.params.roid as string
      const res = await getOrderDetail(roid)
      order.value = res.data.data
      if (order.value.status !== 0) {
        stopPolling()
      }
    } catch (err) {
      console.error('轮询订单状态失败:', err)
    }
  }, 1500)
}

function stopPolling() {
  if (pollingTimer.value) {
    clearInterval(pollingTimer.value)
    pollingTimer.value = null
  }
}

async function handleConfirm() {
  if (!order.value) return
  if (!confirm('确认支付该订单？')) return
  actionLoading.value = true
  try {
    await confirmOrder(order.value.roid)
    await fetchDetail()
  } catch (err: any) {
    alert(getErrorMsg(err, '支付失败'))
  } finally {
    actionLoading.value = false
  }
}

async function handleAlipayPay() {
  if (!order.value) return
  if (!confirm('确认使用支付宝支付该订单？')) return
  actionLoading.value = true
  try {
    const res = await alipayPay(order.value.roid)
    const formHtml = res.data.data
    const payWindow = window.open('', '_blank')
    if (payWindow) {
      payWindow.document.write(formHtml)
      payWindow.document.close()
    }
  } catch (err: any) {
    alert(getErrorMsg(err, '创建支付失败'))
  } finally {
    actionLoading.value = false
  }
}

async function handleCancel() {
  if (!order.value) return
  if (!confirm('确定要取消该订单吗？')) return
  actionLoading.value = true
  try {
    await cancelOrder(order.value.roid)
    await fetchDetail()
  } catch (err: any) {
    alert(getErrorMsg(err, '取消失败'))
  } finally {
    actionLoading.value = false
  }
}

async function handleRefresh() {
  await fetchDetail()
  if (order.value?.status === 0) {
    stopPolling()
    startPolling()
  }
}

function goBack() {
  router.push('/order/list')
}

onMounted(async () => {
  await fetchDetail()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <AppLayout>
    <div class="detail-page">
      <div class="content-wrapper">
        <div class="page-header">
          <button class="btn-back" @click="goBack">← 返回订单列表</button>
        </div>

        <div v-if="loading" class="loading-state">
          <div class="spinner"></div>
          <p>加载中...</p>
        </div>

        <template v-else-if="order">
          <div class="detail-card">
            <div class="card-header">
              <h2>订单详情</h2>
              <span
                class="order-status"
                :class="statusMap[order.status]?.class || ''"
              >
                {{ statusMap[order.status]?.label || '未知' }}
              </span>
            </div>

            <div class="info-section">
              <h3>订单信息</h3>
              <div class="info-grid">
                <div class="info-item">
                  <span class="label">订单编号</span>
                  <span class="value">{{ order.roid }}</span>
                </div>
                <div class="info-item">
                  <span class="label">交易流水号</span>
                  <span class="value">{{ order.tradeno }}</span>
                </div>
                <div class="info-item">
                  <span class="label">下单时间</span>
                  <span class="value">{{ formatTime(order.orderTime) }}</span>
                </div>
                <div class="info-item">
                  <span class="label">支付时间</span>
                  <span class="value">{{ formatTime(order.payTime) }}</span>
                </div>
                <div class="info-item">
                  <span class="label">配送方式</span>
                  <span class="value">{{ order.deliveryType === 'now' ? '立即配送' : order.deliveryType }}</span>
                </div>
                <div class="info-item">
                  <span class="label">支付方式</span>
                  <span class="value">{{ order.payment === 'alipay' ? '支付宝' : order.payment === 'wechat' ? '微信支付' : order.payment }}</span>
                </div>
              </div>
            </div>

            <div class="info-section">
              <h3>收货信息</h3>
              <div class="info-grid">
                <div class="info-item">
                  <span class="label">收货人</span>
                  <span class="value">{{ order.uname }}</span>
                </div>
                <div class="info-item">
                  <span class="label">联系电话</span>
                  <span class="value">{{ order.tel }}</span>
                </div>
                <div class="info-item full-width">
                  <span class="label">收货地址</span>
                  <span class="value">{{ order.address }}</span>
                </div>
                <div v-if="order.ps" class="info-item full-width">
                  <span class="label">备注</span>
                  <span class="value">{{ order.ps }}</span>
                </div>
              </div>
            </div>

            <div class="info-section">
              <h3>商品明细</h3>
              <div class="items-table">
                <div class="table-header">
                  <span class="col-name">商品名称</span>
                  <span class="col-price">单价</span>
                  <span class="col-num">数量</span>
                  <span class="col-subtotal">小计</span>
                </div>
                <div
                  v-for="item in order.items"
                  :key="item.fid"
                  class="table-row"
                >
                  <span class="col-name">{{ item.fname }}</span>
                  <span class="col-price">¥{{ item.dealprice }}</span>
                  <span class="col-num">x{{ item.num }}</span>
                  <span class="col-subtotal">¥{{ (item.dealprice * item.num).toFixed(2) }}</span>
                </div>
                <div class="table-footer">
                  <span class="total-label">商品合计</span>
                  <span class="total-amount">
                    ¥{{ totalAmount.toFixed(2) }}
                  </span>
                </div>
                <div v-if="discountAmount > 0" class="table-footer discount-row">
                  <span class="total-label">免单券抵扣</span>
                  <span class="discount-amount">
                    -¥{{ discountAmount.toFixed(2) }}
                  </span>
                </div>
                <div v-if="discountAmount > 0" class="table-footer final-row">
                  <span class="total-label">实付金额</span>
                  <span class="final-amount">
                    ¥{{ payAmount.toFixed(2) }}
                  </span>
                </div>
              </div>
            </div>

            <div v-if="order.status === 0" class="action-bar">
              <button
                class="btn-pay"
                :disabled="actionLoading"
                @click="handleAlipayPay"
              >
                支付宝支付
              </button>
              <button
                class="btn-cancel"
                :disabled="actionLoading"
                @click="handleCancel"
              >
                取消订单
              </button>
            </div>
          </div>
        </template>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.detail-page {
  min-height: calc(100vh - 60px);
}

.content-wrapper {
  max-width: 800px;
  margin: 0 auto;
  padding: 32px 20px;
}

.page-header {
  margin-bottom: 20px;
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

.detail-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  border-bottom: 1px solid #f0f0f0;
}

.card-header h2 {
  margin: 0;
  font-size: 1.3rem;
  color: #333;
}

.order-status {
  font-size: 0.9rem;
  font-weight: 600;
  padding: 4px 14px;
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

.info-section {
  padding: 24px;
  border-bottom: 1px solid #f0f0f0;
}

.info-section:last-of-type {
  border-bottom: none;
}

.info-section h3 {
  margin: 0 0 16px;
  font-size: 1rem;
  color: #555;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item.full-width {
  grid-column: 1 / -1;
}

.label {
  font-size: 0.8rem;
  color: #999;
}

.value {
  font-size: 0.95rem;
  color: #333;
}

.items-table {
  font-size: 0.9rem;
}

.table-header {
  display: flex;
  padding: 8px 0;
  border-bottom: 1px solid #e0e0e0;
  color: #999;
  font-size: 0.85rem;
}

.table-row {
  display: flex;
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
}

.col-name {
  flex: 1;
}

.col-price,
.col-num,
.col-subtotal {
  width: 70px;
  text-align: right;
}

.col-subtotal {
  width: 80px;
  font-weight: 600;
  color: #333;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding-top: 12px;
  gap: 12px;
}

.total-label {
  font-size: 0.95rem;
  color: #555;
}

.total-amount {
  font-size: 1.4rem;
  font-weight: 700;
  color: #ff6b35;
}

.discount-row .total-label {
  color: #555;
}

.discount-amount {
  font-size: 1.1rem;
  font-weight: 600;
  color: #38a169;
}

.final-row {
  border-top: 1px dashed #e0e0e0;
  margin-top: 4px;
  padding-top: 12px;
}

.final-amount {
  font-size: 1.4rem;
  font-weight: 700;
  color: #e53e3e;
}

.action-bar {
  display: flex;
  gap: 12px;
  padding: 20px 24px;
  border-top: 1px solid #f0f0f0;
  justify-content: flex-end;
}

.btn-pay,
.btn-cancel {
  padding: 12px 28px;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
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
  border: 1.5px solid #ddd;
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

@media (max-width: 768px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>