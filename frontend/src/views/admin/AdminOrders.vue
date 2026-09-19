<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getAdminOrders, updateOrderStatus } from '@/api/admin'
import type { Order } from '@/types'
import AdminLayout from '@/views/admin/AdminLayout.vue'
import { getErrorMsg } from '@/utils/error'

const orders = ref<Order[]>([])
const loading = ref(true)
const loadError = ref('')
const actionOid = ref<string | null>(null)

const statusFilter = ref<number | null>(null)

const statusMap: Record<number, { label: string; css: string }> = {
  0: { label: '待支付', css: 'tag-pending' },
  1: { label: '已支付', css: 'tag-paid' },
  2: { label: '已完成', css: 'tag-done' },
  3: { label: '已取消', css: 'tag-cancel' },
  4: { label: '已退单', css: 'tag-refund' },
}

const filteredOrders = computed(() => {
  if (statusFilter.value === null) return orders.value
  return orders.value.filter((o) => o.status === statusFilter.value)
})

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

function totalAmount(order: Order) {
  return order.items.reduce((sum, item) => sum + item.dealprice * item.num, 0).toFixed(2)
}

async function fetchOrders() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getAdminOrders()
    orders.value = res.data.data
  } catch (err: any) {
    orders.value = []
    loadError.value = getErrorMsg(err, '加载订单失败')
  } finally {
    loading.value = false
  }
}

async function handleConfirmOrder(roid: string) {
  if (!confirm('确认该订单已支付？')) return
  actionOid.value = roid
  try {
    await updateOrderStatus(roid, 1)
    await fetchOrders()
  } catch (err: any) {
    alert(getErrorMsg(err, '操作失败'))
  } finally {
    actionOid.value = null
  }
}

async function handleCompleteOrder(roid: string) {
  if (!confirm('确认完成该订单？')) return
  actionOid.value = roid
  try {
    await updateOrderStatus(roid, 2)
    await fetchOrders()
  } catch (err: any) {
    alert(getErrorMsg(err, '操作失败'))
  } finally {
    actionOid.value = null
  }
}

async function handleCancelOrder(roid: string) {
  if (!confirm('确认取消该订单？')) return
  actionOid.value = roid
  try {
    await updateOrderStatus(roid, 3)
    await fetchOrders()
  } catch (err: any) {
    alert(getErrorMsg(err, '操作失败'))
  } finally {
    actionOid.value = null
  }
}

async function handleRefundOrder(roid: string) {
  if (!confirm('确认退单？退款将在 1-3 个工作日内退回原支付账户。')) return
  actionOid.value = roid
  try {
    await updateOrderStatus(roid, 4)
    await fetchOrders()
  } catch (err: any) {
    alert(getErrorMsg(err, '操作失败'))
  } finally {
    actionOid.value = null
  }
}

onMounted(() => {
  fetchOrders()
})
</script>

<template>
  <AdminLayout>
    <div class="toolbar">
      <div class="filter-bar">
        <button
          class="btn btn-sm"
          :class="statusFilter === null ? 'btn-primary' : 'btn-outline'"
          @click="statusFilter = null"
        >
          全部
        </button>
        <button
          class="btn btn-sm"
          :class="statusFilter === 0 ? 'btn-primary' : 'btn-outline'"
          @click="statusFilter = 0"
        >
          待支付
        </button>
        <button
          class="btn btn-sm"
          :class="statusFilter === 1 ? 'btn-primary' : 'btn-outline'"
          @click="statusFilter = 1"
        >
          已支付
        </button>
        <button
          class="btn btn-sm"
          :class="statusFilter === 2 ? 'btn-primary' : 'btn-outline'"
          @click="statusFilter = 2"
        >
          已完成
        </button>
        <button
          class="btn btn-sm"
          :class="statusFilter === 3 ? 'btn-primary' : 'btn-outline'"
          @click="statusFilter = 3"
        >
          已取消
        </button>
        <button
          class="btn btn-sm"
          :class="statusFilter === 4 ? 'btn-primary' : 'btn-outline'"
          @click="statusFilter = 4"
        >
          已退单
        </button>
      </div>
    </div>

    <div v-if="loading" class="state-box">
      <p>加载中...</p>
    </div>

    <div v-else-if="loadError" class="state-box error-box">
      <p>⚠️ {{ loadError }}</p>
      <button class="btn-retry" @click="fetchOrders">重试</button>
    </div>

    <div v-else-if="filteredOrders.length === 0" class="state-box">
      <p>暂无订单</p>
    </div>

    <div v-else class="order-list">
      <div v-for="order in filteredOrders" :key="order.roid" class="card order-card">
        <div class="order-head">
          <div class="order-meta">
            <span class="order-id">#{{ order.roid }}</span>
            <span class="order-user">{{ order.uname }}（{{ order.userid }}）</span>
            <span class="order-time">{{ formatTime(order.orderTime) }}</span>
          </div>
          <div class="order-right">
            <span class="order-total">¥{{ totalAmount(order) }}</span>
            <span class="tag" :class="statusMap[order.status]?.css">
              {{ statusMap[order.status]?.label || '未知' }}
            </span>
          </div>
        </div>

        <div class="order-items">
          <div v-for="item in order.items" :key="item.fid" class="order-item">
            <span class="oi-name">{{ item.fname }}</span>
            <span class="oi-num">x{{ item.num }}</span>
            <span class="oi-price">¥{{ (item.dealprice * item.num).toFixed(2) }}</span>
          </div>
        </div>

        <div v-if="order.note" class="order-note">
          <span class="note-label">备注：</span>{{ order.note }}
        </div>

        <div class="order-actions">
          <button
            v-if="order.status === 0"
            class="btn btn-sm btn-primary"
            :disabled="actionOid === order.roid"
            @click="handleConfirmOrder(order.roid)"
          >
            确认支付
          </button>
          <button
            v-if="order.status === 1"
            class="btn btn-sm btn-success"
            :disabled="actionOid === order.roid"
            @click="handleCompleteOrder(order.roid)"
          >
            完成订单
          </button>
          <button
            v-if="order.status === 0 || order.status === 1"
            class="btn btn-sm btn-danger"
            :disabled="actionOid === order.roid"
            @click="handleCancelOrder(order.roid)"
          >
            取消订单
          </button>
          <button
            v-if="order.status === 1 || order.status === 2"
            class="btn btn-sm btn-warning"
            :disabled="actionOid === order.roid"
            @click="handleRefundOrder(order.roid)"
          >
            退单
          </button>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}

.filter-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card {
  background: #fff;
  border-radius: 8px;
}

.order-card {
  padding: 16px 20px;
}

.order-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f5f5f5;
}

.order-meta {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
  font-size: 0.85rem;
  color: #666;
}

.order-id {
  font-family: monospace;
  font-weight: 600;
  color: #333;
}

.order-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.order-total {
  font-size: 1rem;
  font-weight: 700;
  color: #333;
}

.order-items {
  padding: 10px 0;
}

.order-item {
  display: flex;
  align-items: center;
  padding: 4px 0;
  font-size: 0.9rem;
}

.oi-name {
  flex: 1;
  color: #333;
}

.oi-num {
  color: #888;
  margin-right: 16px;
  min-width: 36px;
}

.oi-price {
  color: #e53e3e;
  font-weight: 500;
  min-width: 60px;
  text-align: right;
}

.order-note {
  padding: 8px 0;
  font-size: 0.85rem;
  color: #888;
  border-top: 1px solid #f5f5f5;
}

.note-label {
  color: #999;
}

.order-actions {
  display: flex;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #f5f5f5;
}

.state-box {
  text-align: center;
  padding: 60px 20px;
  color: #999;
  background: #fff;
  border-radius: 8px;
}

.tag {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 500;
}

.tag-pending {
  background: #fff7e6;
  color: #d48806;
}

.tag-paid {
  background: #e6f0ff;
  color: #4a90d9;
}

.tag-done {
  background: #e6f7e9;
  color: #389e0d;
}

.tag-cancel {
  background: #fff1f0;
  color: #cf1322;
}

.tag-refund {
  background: #fff7e6;
  color: #d48806;
  border: 1px solid #ffd591;
}

.btn {
  padding: 8px 18px;
  border: none;
  border-radius: 6px;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.15s;
  font-weight: 500;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: #4a90d9;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: #3a7bc8;
}

.btn-sm {
  padding: 4px 12px;
  font-size: 0.8rem;
}

.btn-outline {
  background: #fff;
  color: #4a90d9;
  border: 1px solid #4a90d9;
}

.btn-outline:hover:not(:disabled) {
  background: #e8f0fe;
}

.btn-success {
  background: #52c41a;
  color: #fff;
}

.btn-success:hover:not(:disabled) {
  background: #49b016;
}

.btn-danger {
  background: #ff4d4f;
  color: #fff;
}

.btn-danger:hover:not(:disabled) {
  background: #e53e3e;
}

.btn-warning {
  background: #faad14;
  color: #fff;
}

.btn-warning:hover:not(:disabled) {
  background: #e8a30e;
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