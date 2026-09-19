<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { getCurrentFreeOrderEvent, grabFreeOrder } from '@/api/freeOrder'
import { useUserStore } from '@/stores/user'
import type { FreeOrderEventCurrent } from '@/types'
import AppLayout from '@/components/AppLayout.vue'
import { getErrorMsg } from '@/utils/error'

const router = useRouter()
const userStore = useUserStore()

const events = ref<FreeOrderEventCurrent[]>([])
const loading = ref(true)
const loadError = ref('')
const countdowns = reactive<Record<string, { text: string; label: string }>>({})
const grabbingMap = ref<Record<string, boolean>>({})
const errorMap = ref<Record<string, string>>({})
let timer: ReturnType<typeof setInterval> | null = null

async function fetchEvents() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getCurrentFreeOrderEvent()
    const data = res.data.data
    events.value = Array.isArray(data) ? data : data ? [data] : []
  } catch (err: any) {
    events.value = []
    loadError.value = getErrorMsg(err, '加载免单活动失败')
  } finally {
    loading.value = false
  }
}

function updateCountdown() {
  const activeIds: Record<string, boolean> = {}
  for (const evt of events.value) {
    if (!evt.eventId || !evt.startTime || !evt.serverTime) continue

    activeIds[evt.eventId] = true
    const startTime = new Date(evt.startTime).getTime()
    const now = new Date(evt.serverTime).getTime() + (Date.now() - new Date(evt.serverTime).getTime())

    if (evt.status === 'pending') {
      const diff = startTime - now
      if (diff <= 0) {
        fetchEvents()
        return
      }
      const hours = Math.floor(diff / 3600000)
      const minutes = Math.floor((diff % 3600000) / 60000)
      const seconds = Math.floor((diff % 60000) / 1000)
      countdowns[evt.eventId] = {
        text: `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`,
        label: '距开始',
      }
    } else if (evt.status === 'active' && evt.endTime) {
      const endTime = new Date(evt.endTime).getTime()
      const diff = endTime - now
      if (diff <= 0) {
        fetchEvents()
        return
      }
      const hours = Math.floor(diff / 3600000)
      const minutes = Math.floor((diff % 3600000) / 60000)
      const seconds = Math.floor((diff % 60000) / 1000)
      countdowns[evt.eventId] = {
        text: `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`,
        label: '距结束',
      }
    }
  }
  for (const key of Object.keys(countdowns)) {
    if (!activeIds[key]) {
      delete countdowns[key]
    }
  }
}

async function handleGrab(evt: FreeOrderEventCurrent) {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  if (!evt.hasEvent || evt.status !== 'active') return
  grabbingMap.value = { ...grabbingMap.value, [evt.eventId!]: true }
  errorMap.value = { ...errorMap.value, [evt.eventId!]: '' }
  try {
    const res = await grabFreeOrder()
    const result = res.data.data
    alert(`抢券成功！券面值：¥${result.couponAmount}（金额范围 ¥${result.minAmount}~¥${result.maxAmount}）`)
    await fetchEvents()
  } catch (err: any) {
    errorMap.value = { ...errorMap.value, [evt.eventId!]: getErrorMsg(err, '抢单失败') }
  } finally {
    grabbingMap.value = { ...grabbingMap.value, [evt.eventId!]: false }
  }
}

function goToMyCoupons() {
  router.push('/free-order/my-coupons')
}

function formatTime(time: string) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(() => {
  fetchEvents()
  timer = setInterval(updateCountdown, 1000)
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})
</script>

<template>
  <AppLayout>
    <div class="free-order-page">
      <div class="content-wrapper">
        <div class="page-header">
          <h2>免单活动</h2>
          <button class="btn-my-coupons" @click="goToMyCoupons">我的免单券</button>
        </div>

        <div v-if="loading" class="loading-state">
          <div class="spinner"></div>
          <p>加载中...</p>
        </div>

        <div v-else-if="loadError" class="error-state">
          <p class="error-icon">🎉</p>
          <p>{{ loadError }}</p>
          <button class="btn-retry" @click="fetchEvents">重试</button>
        </div>

        <div v-else-if="events.length === 0" class="empty-state">
          <div class="empty-icon">🎉</div>
          <h3>当前没有正在进行或即将开始的免单活动</h3>
          <p>请关注后续活动通知</p>
        </div>

        <div v-else>
          <div
            v-for="evt in events"
            :key="evt.eventId"
            class="event-card"
          >
            <div class="event-header">
              <h3>{{ evt.eventName }}</h3>
              <span class="event-status" :class="evt.status">
                {{ evt.status === 'pending' ? '即将开始' : evt.status === 'active' ? '进行中' : '已结束' }}
              </span>
            </div>

            <div class="event-info">
              <div class="info-item">
                <span class="info-label">最低免单金额</span>
                <span class="info-value amount">¥{{ evt.minAmount }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">最高免单金额</span>
                <span class="info-value amount">¥{{ evt.maxAmount }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">活动时间</span>
                <span class="info-value">
                  {{ formatTime(evt.startTime!) }} - {{ formatTime(evt.endTime!) }}
                </span>
              </div>
              <div v-if="evt.status === 'active' && evt.remainCount !== undefined" class="info-item">
                <span class="info-label">剩余名额</span>
                <span class="info-value remain">{{ evt.remainCount }} 个</span>
              </div>
            </div>

            <div v-if="countdowns[evt.eventId!]" class="countdown-area">
              <div class="countdown-label">{{ countdowns[evt.eventId!]?.label }}</div>
              <div class="countdown-timer">{{ countdowns[evt.eventId!]?.text }}</div>
            </div>

            <div v-if="errorMap[evt.eventId!]" class="error-msg">{{ errorMap[evt.eventId!] }}</div>

            <div class="event-actions">
              <button
                v-if="evt.status === 'active'"
                class="btn-grab"
                :disabled="grabbingMap[evt.eventId!] || !userStore.isLoggedIn"
                @click="handleGrab(evt)"
              >
                {{ grabbingMap[evt.eventId!] ? '抢单中...' : '立即抢单' }}
              </button>
              <button
                v-else-if="evt.status === 'pending'"
                class="btn-grab disabled"
                disabled
              >
                活动未开始
              </button>
              <button
                v-else
                class="btn-grab disabled"
                disabled
              >
                活动已结束
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.free-order-page {
  min-height: calc(100vh - 60px);
  background: #f8f9fa;
}

.content-wrapper {
  max-width: 600px;
  margin: 0 auto;
  padding: 32px 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 1.5rem;
  color: #333;
}

.btn-my-coupons {
  padding: 8px 16px;
  background: #fff;
  color: #ff6b35;
  border: 1.5px solid #ff6b35;
  border-radius: 8px;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-my-coupons:hover {
  background: #fff5f0;
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
  margin: 0;
  color: #999;
}

.event-card {
  background: #fff;
  border-radius: 16px;
  padding: 32px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  margin-bottom: 20px;
}

.event-card:last-child {
  margin-bottom: 0;
}

.event-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.event-header h3 {
  margin: 0;
  font-size: 1.3rem;
  color: #333;
}

.event-status {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 0.8rem;
  font-weight: 600;
}

.event-status.pending {
  background: #fff3e0;
  color: #ff9800;
}

.event-status.active {
  background: #e8f5e9;
  color: #4caf50;
}

.event-status.ended {
  background: #f5f5f5;
  color: #999;
}

.event-info {
  margin-bottom: 24px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;
}

.info-item:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 0.9rem;
  color: #999;
}

.info-value {
  font-size: 0.95rem;
  color: #333;
}

.info-value.amount {
  font-size: 1.3rem;
  font-weight: 700;
  color: #ff6b35;
}

.info-value.remain {
  color: #4caf50;
  font-weight: 600;
}

.countdown-area {
  text-align: center;
  padding: 24px 0;
  background: linear-gradient(135deg, #fff5f0 0%, #fff8f4 100%);
  border-radius: 12px;
  margin-bottom: 24px;
}

.countdown-label {
  font-size: 0.9rem;
  color: #999;
  margin-bottom: 8px;
}

.countdown-timer {
  font-size: 2.8rem;
  font-weight: 700;
  color: #ff6b35;
  font-family: 'Courier New', monospace;
  letter-spacing: 4px;
}

.error-msg {
  background: #fff0f0;
  color: #e53e3e;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 0.9rem;
  margin-bottom: 16px;
  text-align: center;
}

.event-actions {
  text-align: center;
}

.btn-grab {
  width: 100%;
  padding: 16px;
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  color: #fff;
  border: none;
  border-radius: 12px;
  font-size: 1.15rem;
  font-weight: 700;
  cursor: pointer;
  transition: opacity 0.2s;
  letter-spacing: 2px;
}

.btn-grab:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-grab:disabled,
.btn-grab.disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: #ccc;
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