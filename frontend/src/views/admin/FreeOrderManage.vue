<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { createFreeOrderEvent, deleteFreeOrderEvent, endFreeOrderEvent, getFreeOrderEventDetail, getFreeOrderEventList, updateFreeOrderEvent } from '@/api/freeOrder'
import type { FreeOrderEvent, FreeOrderEventDetail } from '@/types'
import AdminLayout from '@/views/admin/AdminLayout.vue'
import { getErrorMsg } from '@/utils/error'

const events = ref<FreeOrderEvent[]>([])
const loading = ref(true)
const loadError = ref('')
const page = ref(1)
const total = ref(0)

const showCreateForm = ref(false)
const createForm = ref({
  eventName: '',
  minAmount: 0,
  maxAmount: 0,
  startTime: '',
  endTime: '',
  couponCount: 10,
})
const creating = ref(false)
const errorMsg = ref('')

const showEditForm = ref(false)
const editEventId = ref('')
const editForm = ref({
  eventName: '',
  minAmount: 0,
  maxAmount: 0,
  startTime: '',
  endTime: '',
  couponCount: 10,
})
const editing = ref(false)
const editErrorMsg = ref('')

const showDetail = ref(false)
const detailEvent = ref<FreeOrderEventDetail | null>(null)
const detailLoading = ref(false)

async function fetchEvents() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getFreeOrderEventList(page.value, 10)
    events.value = res.data.data.records
    total.value = res.data.data.total
  } catch (err: any) {
    events.value = []
    loadError.value = getErrorMsg(err, '加载免单活动失败')
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!createForm.value.eventName || !createForm.value.startTime || !createForm.value.endTime) {
    errorMsg.value = '请填写完整信息'
    return
  }
  creating.value = true
  errorMsg.value = ''
  try {
    await createFreeOrderEvent({
      eventName: createForm.value.eventName,
      minAmount: createForm.value.minAmount,
      maxAmount: createForm.value.maxAmount,
      startTime: createForm.value.startTime,
      endTime: createForm.value.endTime,
      couponCount: createForm.value.couponCount,
    })
    alert('创建成功')
    showCreateForm.value = false
    createForm.value = {
      eventName: '',
      minAmount: 0,
      maxAmount: 0,
      startTime: '',
      endTime: '',
      couponCount: 10,
    }
    await fetchEvents()
  } catch (err: any) {
    errorMsg.value = getErrorMsg(err, '创建失败')
  } finally {
    creating.value = false
  }
}

async function handleEnd(eventId: string) {
  if (!confirm('确定要结束该活动吗？')) return
  try {
    await endFreeOrderEvent(eventId)
    alert('活动已结束')
    await fetchEvents()
  } catch (err: any) {
    alert(getErrorMsg(err, '操作失败'))
  }
}

function handleEdit(event: FreeOrderEvent) {
  editEventId.value = event.eventId
  editForm.value = {
    eventName: event.eventName,
    minAmount: event.minAmount,
    maxAmount: event.maxAmount,
    startTime: '',
    endTime: '',
    couponCount: event.totalCount,
  }
  editErrorMsg.value = ''
  showEditForm.value = true
  showCreateForm.value = false
}

async function handleUpdate() {
  if (!editForm.value.eventName) {
    editErrorMsg.value = '请填写活动名称'
    return
  }
  editing.value = true
  editErrorMsg.value = ''
  try {
    const data: Partial<FreeOrderEvent> = {
      eventName: editForm.value.eventName,
      minAmount: editForm.value.minAmount,
      maxAmount: editForm.value.maxAmount,
    }
    if (editForm.value.startTime) {
      data.startTime = editForm.value.startTime
    }
    if (editForm.value.endTime) {
      data.endTime = editForm.value.endTime
    }
    await updateFreeOrderEvent(editEventId.value, data)
    alert('修改成功')
    showEditForm.value = false
    await fetchEvents()
  } catch (err: any) {
    editErrorMsg.value = getErrorMsg(err, '修改失败')
  } finally {
    editing.value = false
  }
}

async function handleDelete(eventId: string) {
  if (!confirm('确定要删除该活动吗？此操作不可撤销。')) return
  try {
    await deleteFreeOrderEvent(eventId)
    alert('删除成功')
    await fetchEvents()
  } catch (err: any) {
    alert(getErrorMsg(err, '删除失败'))
  }
}

async function handleDetail(eventId: string) {
  showDetail.value = true
  detailLoading.value = true
  detailEvent.value = null
  try {
    const res = await getFreeOrderEventDetail(eventId)
    detailEvent.value = res.data.data
  } catch (err: any) {
    alert(getErrorMsg(err, '获取详情失败'))
    showDetail.value = false
  } finally {
    detailLoading.value = false
  }
}

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

function getStatusText(status: string) {
  const map: Record<string, string> = {
    pending: '未开始',
    active: '进行中',
    ended: '已结束',
    cancelled: '已取消',
  }
  return map[status] || status
}

onMounted(() => {
  fetchEvents()
})
</script>

<template>
  <AdminLayout>
    <div class="toolbar">
      <span></span>
      <button class="btn btn-primary" @click="showCreateForm = !showCreateForm">
        {{ showCreateForm ? '取消' : '+ 创建活动' }}
      </button>
    </div>

    <div v-if="showCreateForm" class="card form-card">
      <h4 class="form-title">创建免单活动</h4>
      <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

      <div class="form-row">
        <div class="form-group">
          <label>活动名称</label>
          <input v-model="createForm.eventName" type="text" class="input" placeholder="请输入活动名称" />
        </div>
        <div class="form-group">
          <label>最低免单金额</label>
          <input v-model.number="createForm.minAmount" type="number" class="input" placeholder="0" />
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>开始时间</label>
          <input v-model="createForm.startTime" type="datetime-local" class="input" />
        </div>
        <div class="form-group">
          <label>结束时间</label>
          <input v-model="createForm.endTime" type="datetime-local" class="input" />
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>优惠券数量</label>
          <input v-model.number="createForm.couponCount" type="number" class="input" placeholder="10" />
        </div>
        <div class="form-group">
          <label>最高免单金额</label>
          <input v-model.number="createForm.maxAmount" type="number" class="input" placeholder="0" />
        </div>
      </div>

      <button class="btn btn-primary" :disabled="creating" @click="handleCreate">
        {{ creating ? '创建中...' : '创建活动' }}
      </button>
    </div>

    <div v-if="loading" class="state-box">
      <p>加载中...</p>
    </div>

    <div v-else-if="loadError" class="state-box error-box">
      <p>⚠️ {{ loadError }}</p>
      <button class="btn-retry" @click="fetchEvents">重试</button>
    </div>

    <div v-else-if="events.length === 0" class="state-box">
      <p>暂无免单活动</p>
    </div>

    <div v-else class="card">
      <table class="table">
        <thead>
          <tr>
            <th>活动名称</th>
            <th>最低金额</th>
            <th>最高金额</th>
            <th>开始时间</th>
            <th>结束时间</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="event in events" :key="event.eventId">
            <td>{{ event.eventName }}</td>
            <td>¥{{ event.minAmount }}</td>
            <td>¥{{ event.maxAmount }}</td>
            <td>{{ formatTime(event.startTime) }}</td>
            <td>{{ formatTime(event.endTime) }}</td>
            <td>
              <span class="tag" :class="'tag-' + event.status">
                {{ getStatusText(event.status) }}
              </span>
            </td>
            <td>
              <button
                v-if="event.status === 'pending'"
                class="btn btn-sm btn-primary"
                @click="handleEdit(event)"
              >
                编辑
              </button>
              <button
                v-if="event.status === 'pending'"
                class="btn btn-sm btn-danger"
                @click="handleDelete(event.eventId)"
              >
                删除
              </button>
              <button
                v-if="event.status === 'active'"
                class="btn btn-sm btn-danger"
                @click="handleEnd(event.eventId)"
              >
                结束活动
              </button>
              <button
                class="btn btn-sm btn-outline"
                @click="handleDetail(event.eventId)"
              >
                详情
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showEditForm" class="card form-card">
      <h4 class="form-title">编辑免单活动</h4>
      <div v-if="editErrorMsg" class="error-msg">{{ editErrorMsg }}</div>

      <div class="form-row">
        <div class="form-group">
          <label>活动名称</label>
          <input v-model="editForm.eventName" type="text" class="input" placeholder="请输入活动名称" />
        </div>
        <div class="form-group">
          <label>最低免单金额</label>
          <input v-model.number="editForm.minAmount" type="number" class="input" placeholder="0" />
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>最高免单金额</label>
          <input v-model.number="editForm.maxAmount" type="number" class="input" placeholder="0" />
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>开始时间（留空不修改）</label>
          <input v-model="editForm.startTime" type="datetime-local" class="input" />
        </div>
        <div class="form-group">
          <label>结束时间（留空不修改）</label>
          <input v-model="editForm.endTime" type="datetime-local" class="input" />
        </div>
      </div>

      <div class="form-actions">
        <button class="btn btn-primary" :disabled="editing" @click="handleUpdate">
          {{ editing ? '保存中...' : '保存修改' }}
        </button>
        <button class="btn btn-cancel" @click="showEditForm = false">取消</button>
      </div>
    </div>

    <div v-if="showDetail" class="modal-overlay" @click.self="showDetail = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3>活动详情</h3>
          <button class="btn-close" @click="showDetail = false">×</button>
        </div>
        <div v-if="detailLoading" class="modal-body">
          <p class="loading-text">加载中...</p>
        </div>
        <div v-else-if="detailEvent" class="modal-body">
          <div class="detail-row">
            <span class="detail-label">活动名称</span>
            <span class="detail-value">{{ detailEvent.eventName }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">最低免单金额</span>
            <span class="detail-value">¥{{ detailEvent.minAmount }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">最高免单金额</span>
            <span class="detail-value">¥{{ detailEvent.maxAmount }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">活动时间</span>
            <span class="detail-value">{{ formatTime(detailEvent.startTime) }} - {{ formatTime(detailEvent.endTime) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">状态</span>
            <span class="detail-value">
              <span class="tag" :class="'tag-' + detailEvent.status">{{ getStatusText(detailEvent.status) }}</span>
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">总名额</span>
            <span class="detail-value">{{ detailEvent.totalCount }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">已抢人数</span>
            <span class="detail-value highlight">{{ detailEvent.grabCount }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">剩余名额</span>
            <span class="detail-value highlight">{{ detailEvent.remainCount }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">创建时间</span>
            <span class="detail-value">{{ formatTime(detailEvent.createTime) }}</span>
          </div>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.form-card {
  padding: 24px;
  margin-bottom: 16px;
}

.form-title {
  margin: 0 0 16px;
  font-size: 1rem;
  color: #333;
}

.error-msg {
  background: #fff1f0;
  color: #cf1322;
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 0.9rem;
  margin-bottom: 16px;
  border: 1px solid #ffa39e;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 12px;
}

.form-group {
  margin-bottom: 14px;
}

.form-group label {
  display: block;
  font-size: 0.85rem;
  font-weight: 600;
  color: #555;
  margin-bottom: 6px;
}

.input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 0.9rem;
  outline: none;
  box-sizing: border-box;
  font-family: inherit;
  transition: border-color 0.15s;
}

.input:focus {
  border-color: #4a90d9;
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.table thead {
  background: #fafafa;
}

.table th {
  padding: 12px 14px;
  text-align: left;
  font-weight: 600;
  color: #555;
  border-bottom: 1px solid #f0f0f0;
}

.table td {
  padding: 12px 14px;
  border-bottom: 1px solid #f5f5f5;
  color: #333;
}

.table tbody tr:hover {
  background: #fafbff;
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
  background: #f5f5f5;
  color: #888;
}

.tag-active {
  background: #e6f7e9;
  color: #389e0d;
}

.tag-ended {
  background: #fff1f0;
  color: #cf1322;
}

.tag-cancelled {
  background: #fff7e6;
  color: #d48806;
}

.no-action {
  color: #ccc;
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

.btn-danger {
  background: #ff4d4f;
  color: #fff;
}

.btn-danger:hover:not(:disabled) {
  background: #e53e3e;
}

.btn-outline {
  background: #fff;
  color: #4a90d9;
  border: 1px solid #4a90d9;
}

.btn-outline:hover:not(:disabled) {
  background: #f0f7ff;
}

.btn-cancel {
  background: #f5f5f5;
  color: #666;
}

.btn-cancel:hover:not(:disabled) {
  background: #e8e8e8;
}

.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-card {
  background: #fff;
  border-radius: 12px;
  width: 480px;
  max-width: 90vw;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.15);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px 0;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.15rem;
  color: #333;
}

.btn-close {
  background: none;
  border: none;
  font-size: 1.5rem;
  color: #999;
  cursor: pointer;
  padding: 0;
  line-height: 1;
}

.btn-close:hover {
  color: #333;
}

.modal-body {
  padding: 20px 24px 24px;
}

.loading-text {
  text-align: center;
  color: #999;
  padding: 20px 0;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
}

.detail-row:last-child {
  border-bottom: none;
}

.detail-label {
  color: #888;
  font-size: 0.9rem;
}

.detail-value {
  color: #333;
  font-weight: 500;
  font-size: 0.9rem;
}

.detail-value.highlight {
  color: #ff6b35;
  font-weight: 600;
}

.btn-sm {
  margin-right: 6px;
}

.btn-sm:last-child {
  margin-right: 0;
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