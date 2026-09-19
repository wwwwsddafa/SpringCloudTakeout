<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import AdminLayout from '@/views/admin/AdminLayout.vue'
import {
  getCommands,
  assignSession,
  closeSession,
  getSessionHistory,
  uploadImage,
  getLogisticsStatus,
  setLogisticsStatus,
} from '@/api/customerService'
import type {
  CsMessage,
  CsMessageSend,
  CommandResult,
  SystemMessage,
  ChatSession,
  UserListEvent,
  UserOnlineEvent,
  UserOfflineEvent,
  CommandDefinition,
  LogisticsData,
  LogisticsTimelineItem,
  LogisticsDetail,
  AvailableStatus,
} from '@/types'
import { getErrorMsg } from '@/utils/error'

const router = useRouter()
const userStore = useUserStore()

const sessions = ref<ChatSession[]>([])
const activeSessionId = ref<string>('')
const messages = ref<(CsMessage | SystemMessage | CommandResult)[]>([])
const inputText = ref('')
const connected = ref(false)
const connecting = ref(true)
const chatRef = ref<HTMLElement | null>(null)
const commands = ref<CommandDefinition[]>([])
const commandLoading = ref(false)
const commandResult = ref('')
const commandResultCard = ref<CommandResult | null>(null)
const imageInput = ref<HTMLInputElement | null>(null)
const queueSize = ref(0)
const onlineAgentCount = ref(0)
const showLogisticsModal = ref(false)
const logisticsRoid = ref('')
const logisticsDetail = ref<LogisticsDetail | null>(null)
const logisticsLoading = ref(false)
const logisticsMsg = ref('')
let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let reconnectAttempts = 0
const MAX_RECONNECT = 5
const RECONNECT_BASE_MS = 2000

const currentSession = computed(() =>
  sessions.value.find((s) => s.sessionId === activeSessionId.value),
)

const waitingSessions = computed(() =>
  sessions.value.filter((s) => s.status === 'WAITING'),
)

const activeSessions = computed(() =>
  sessions.value.filter((s) => s.status === 'ACTIVE'),
)

const closedSessions = computed(() =>
  sessions.value.filter((s) => s.status === 'CLOSED'),
)

function connectWebSocket() {
  const userId = localStorage.getItem('userId')
  const userName = userStore.userInfo?.nickname || userStore.username || '管理员'
  console.log('[AdminChat] 准备连接 WebSocket, userId:', userId)
  if (!userId) {
    console.warn('[AdminChat] userId 为空，无法连接 WebSocket')
    connecting.value = false
    return
  }

  const wsUrl = `ws://${window.location.hostname}:10002/ws/chat?userId=${userId}&role=admin&userName=${encodeURIComponent(userName)}`
  console.log('[AdminChat] 连接地址:', wsUrl)

  ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    console.log('[AdminChat] WebSocket 连接成功')
    connected.value = true
    connecting.value = false
    reconnectAttempts = 0
  }

  ws.onmessage = (event) => {
    try {
      console.log('[AdminChat] 收到:', event.data)
      const data = JSON.parse(event.data)

      switch (data.type) {
        case 'USER_LIST':
          handleUserList(data as UserListEvent)
          break
        case 'USER_ONLINE':
          handleUserOnline(data as UserOnlineEvent)
          break
        case 'USER_OFFLINE':
          handleUserOffline(data as UserOfflineEvent)
          break
        case 'command_result':
          handleCommandResult(data as CommandResult)
          break
        case 'system':
          messages.value.push(data as SystemMessage)
          break
        default:
          if (data.sessionId && data.sessionId === activeSessionId.value) {
            messages.value.push(data as CsMessage)
          }
          scrollToBottom()
      }
    } catch (e) {
      console.error('[AdminChat] 消息解析失败:', e)
    }
  }

  ws.onerror = (e) => {
    console.error('[AdminChat] WebSocket 连接错误:', e)
    connected.value = false
    connecting.value = false
  }

  ws.onclose = (e) => {
    console.log('[AdminChat] WebSocket 连接关闭, code:', e.code, 'reason:', e.reason)
    connected.value = false
    connecting.value = false
    scheduleReconnect()
  }
}

function scheduleReconnect() {
  if (reconnectAttempts >= MAX_RECONNECT) {
    console.warn('[AdminChat] 已达最大重连次数，放弃重连')
    return
  }
  const delay = RECONNECT_BASE_MS * Math.pow(2, reconnectAttempts)
  reconnectAttempts++
  console.log(`[AdminChat] ${delay}ms 后第 ${reconnectAttempts} 次重连...`)
  reconnectTimer = setTimeout(() => {
    connecting.value = true
    connectWebSocket()
  }, delay)
}

function handleUserList(data: UserListEvent) {
  const all: ChatSession[] = []
  if (data.waitingSessions) all.push(...data.waitingSessions)
  if (data.activeSessions) all.push(...data.activeSessions)
  sessions.value = all
  if (data.queueSize !== undefined) queueSize.value = data.queueSize
  if (data.onlineAgentCount !== undefined) onlineAgentCount.value = data.onlineAgentCount
}

function handleUserOnline(data: UserOnlineEvent) {
  const exists = sessions.value.find((s) => s.sessionId === data.sessionId)
  if (!exists) {
    sessions.value.push({
      sessionId: data.sessionId,
      userId: data.userId,
      userName: data.userName,
      status: 'WAITING',
      createTime: new Date().toISOString(),
      unreadCount: 0,
    })
  }
}

function handleUserOffline(data: UserOfflineEvent) {
  sessions.value = sessions.value.filter((s) => s.userId !== data.userId)
  if (currentSession.value?.userId === data.userId) {
    activeSessionId.value = ''
    messages.value = []
  }
}

function handleCommandResult(data: CommandResult) {
  if (data.needApproval) {
    commandResult.value = `⏳ 已提交审批，审批单号: ${data.approvalId || '--'}`
    commandResultCard.value = null
    setTimeout(() => { commandResult.value = '' }, 5000)
    return
  }
  commandResult.value = data.success ? `✅ ${data.message}` : `❌ ${data.message}`
  if (data.data) {
    commandResultCard.value = data
  }
  setTimeout(() => {
    commandResult.value = ''
  }, 5000)
}

async function selectSession(session: ChatSession) {
  activeSessionId.value = session.sessionId
  commandResult.value = ''
  try {
    const res = await getSessionHistory(session.sessionId)
    messages.value = res.data.data || []
    scrollToBottom()
  } catch {
    messages.value = []
  }
}

async function handleAssign(sessionId: string) {
  try {
    await assignSession(sessionId)
    const s = sessions.value.find((s) => s.sessionId === sessionId)
    if (s) s.status = 'ACTIVE'
  } catch (e: any) {
    console.error('[AdminChat] 接入失败:', e)
  }
}

async function handleClose(sessionId: string) {
  try {
    await closeSession(sessionId)
    const s = sessions.value.find((s) => s.sessionId === sessionId)
    if (s) s.status = 'CLOSED'
    if (activeSessionId.value === sessionId) {
      activeSessionId.value = ''
      messages.value = []
    }
  } catch (e: any) {
    console.error('[AdminChat] 关闭失败:', e)
  }
}

function sendMessage() {
  if (!ws || ws.readyState !== WebSocket.OPEN) return
  if (!activeSessionId.value) return
  const text = inputText.value.trim()
  if (!text) return

  const msg: CsMessageSend = {
    type: 'message',
    msgType: 'TEXT',
    content: text,
    sessionId: activeSessionId.value,
    targetUserId: currentSession.value?.userId,
  }

  ws.send(JSON.stringify(msg))
  inputText.value = ''
  scrollToBottom()
}

async function handleImageSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    const url = await uploadImage(file)
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({
        type: 'message',
        msgType: 'IMAGE',
        content: url,
        sessionId: activeSessionId.value,
        targetUserId: currentSession.value?.userId,
      }))
    }
  } catch (err) {
    console.error('[AdminChat] 图片上传失败:', err)
  }
  input.value = ''
}

async function handleCommand(cmd: CommandDefinition) {
  if (!activeSessionId.value || !currentSession.value || !ws || ws.readyState !== WebSocket.OPEN) return
  commandLoading.value = true
  commandResult.value = ''
  commandResultCard.value = null

  const params: Record<string, any> = {}
  for (const p of cmd.params) {
    if (p.required) {
      const val = prompt(`请输入${p.displayName}（${p.description}）：`)
      if (!val) {
        commandLoading.value = false
        return
      }
      params[p.name] = val
    }
  }

  ws.send(JSON.stringify({
    type: 'command',
    commandName: cmd.name,
    commandParams: params,
    sessionId: activeSessionId.value,
    targetUserId: currentSession.value.userId,
  }))
  commandLoading.value = false
}

function scrollToBottom() {
  nextTick(() => {
    if (chatRef.value) {
      chatRef.value.scrollTop = chatRef.value.scrollHeight
    }
  })
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

function formatTime(time?: string) {
  if (!time) return ''
  return new Date(time).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function isMine(msg: CsMessage | SystemMessage | CommandResult): boolean {
  if (msg.type === 'system' || msg.type === 'command_result') return false
  const myId = userStore.userInfo?.userId || localStorage.getItem('userId') || ''
  return (msg as CsMessage).senderId === myId
}

function getSenderLabel(msg: CsMessage | SystemMessage | CommandResult): string {
  if (msg.type === 'command_result') return '操作结果'
  if (msg.type === 'system') return '系统'
  return ''
}

function getMsgContent(msg: CsMessage | SystemMessage | CommandResult): string {
  if (msg.type === 'command_result') return (msg as CommandResult).message
  return (msg as CsMessage | SystemMessage).content
}

function getStatusIcon(status: string): string {
  if (status === 'WAITING') return '🟡'
  if (status === 'ACTIVE') return '🟢'
  return '⚪'
}

function openImage(url: string) {
  window.open(url, '_blank')
}

function getStatusLabel(status: string): string {
  if (status === 'WAITING') return '等待中'
  if (status === 'ACTIVE') return '进行中'
  return '已关闭'
}

const orderStatusMap: Record<number, string> = {
  0: '待支付',
  1: '已支付',
  2: '已完成',
  3: '已取消',
  4: '已退单',
}

function renderResultData(data: CommandResult): string {
  if (!data.data) return ''
  switch (data.commandName) {
    case 'queryOrder':
      return `订单号: ${data.data.roid}\n用户: ${data.data.uname}\n地址: ${data.data.address}\n电话: ${data.data.tel}\n状态: ${orderStatusMap[data.data.status] || '未知'}\n${(data.data.items || []).map((i: any) => `${i.fname} x${i.num} ¥${i.dealprice}`).join('\n')}`
    case 'queryProduct':
      return `商品: ${data.data.fname}\n原价: ¥${data.data.normprice}\n现价: ¥${data.data.realprice}\n描述: ${data.data.detail || '无'}`
    case 'queryUserInfo':
      return `用户名: ${data.data.username}\n电话: ${data.data.tel}\n邮箱: ${data.data.email || '无'}`
    case 'listUserOrders':
      return (data.data || []).map((o: any) => `${o.roid} | ${orderStatusMap[o.status] || '未知'} | ${o.orderTime || ''}`).join('\n')
    case 'searchProduct':
      return (data.data.records || []).map((p: any) => `${p.fname} ¥${p.realprice}`).join('\n')
    case 'queryOrderLogistics':
      return `订单号: ${data.data.roid}\n物流公司: ${data.data.logisticsCompany || '--'}\n运单号: ${data.data.trackingNo || '--'}\n状态: ${data.data.logisticsStatus || '--'}`
    case 'addOrderNote':
      return `订单号: ${data.data.roid}\n备注: ${data.data.note || '--'}`
    default:
      return JSON.stringify(data.data, null, 2)
  }
}

async function loadCommands() {
  try {
    const res = await getCommands()
    commands.value = res.data.data || []
  } catch {
    console.warn('[AdminChat] 获取命令列表失败')
  }
}

function getLogisticsStatusColor(status: string): string {
  const map: Record<string, string> = {
    MERCHANT_ACCEPTED: '#f0ad4e',
    PREPARING: '#f0ad4e',
    RIDER_ARRIVED: '#f0ad4e',
    PICKED_UP: '#f0ad4e',
    DELIVERING: '#5bc0de',
    DELIVERED: '#5cb85c',
  }
  return map[status] || '#999'
}

function formatLogisticsTime(time: string): string {
  if (!time) return ''
  const d = new Date(time)
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function isLogisticsNodeActive(item: LogisticsTimelineItem, idx: number, timeline: LogisticsTimelineItem[]): boolean {
  return idx === timeline.length - 1
}

function isLogisticsNodeDone(item: LogisticsTimelineItem, idx: number, timeline: LogisticsTimelineItem[]): boolean {
  return idx < timeline.length - 1
}

function getLogisticsData(card: CommandResult): LogisticsData | null {
  if (card.commandName !== 'queryOrderLogistics' || !card.data) return null
  return card.data as LogisticsData
}

async function openLogisticsSimulator(roid: string) {
  logisticsRoid.value = roid
  logisticsLoading.value = true
  logisticsMsg.value = ''
  showLogisticsModal.value = true
  try {
    const res = await getLogisticsStatus(roid)
    logisticsDetail.value = res.data.data
  } catch (err: any) {
    logisticsMsg.value = '获取物流状态失败：' + (getErrorMsg(err, '未知错误'))
    logisticsDetail.value = null
  } finally {
    logisticsLoading.value = false
  }
}

async function pushLogisticsStatus(status: string) {
  logisticsLoading.value = true
  logisticsMsg.value = ''
  try {
    await setLogisticsStatus({
      roid: logisticsRoid.value,
      status,
    })
    logisticsMsg.value = `物流状态已推进`
    const res = await getLogisticsStatus(logisticsRoid.value)
    logisticsDetail.value = res.data.data
  } catch (err: any) {
    logisticsMsg.value = '操作失败：' + (getErrorMsg(err, '未知错误'))
  } finally {
    logisticsLoading.value = false
  }
}

function getLogisticsStatusText(status: string): string {
  const map: Record<string, string> = {
    SUBMITTED: '订单已提交',
    MERCHANT_ACCEPTED: '商家已接单',
    PREPARING: '商家备餐中',
    RIDER_ARRIVED: '骑手已到店',
    PICKED_UP: '骑手已取餐',
    DELIVERING: '配送中',
    DELIVERED: '已送达',
    CANCELLED: '已取消',
  }
  return map[status] || status
}

onMounted(() => {
  if (!userStore.isLoggedIn || userStore.userInfo?.role?.toUpperCase() !== 'ADMIN') {
    console.warn('[AdminChat] 非管理员，跳转首页, role:', userStore.userInfo?.role)
    router.push('/')
    return
  }
  connectWebSocket()
  loadCommands()
})

onUnmounted(() => {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (ws) {
    ws.close()
    ws = null
  }
})
</script>

<template>
  <AdminLayout>
    <div class="admin-chat-workspace">
      <aside class="session-panel">
        <div class="panel-header">
          <h3>会话列表</h3>
          <span class="connection-badge" :class="{ connected }">
            {{ connecting ? '连接中...' : connected ? '在线' : '离线' }}
          </span>
        </div>

        <div v-if="queueSize > 0 || onlineAgentCount > 0" class="queue-stats">
          <span class="stat-item">排队: {{ queueSize }}</span>
          <span class="stat-item">在线客服: {{ onlineAgentCount }}</span>
        </div>

        <div v-if="waitingSessions.length > 0" class="session-group">
          <div class="group-title">等待接入</div>
          <div
            v-for="s in waitingSessions"
            :key="s.sessionId"
            class="session-item"
            :class="{ active: activeSessionId === s.sessionId }"
            @click="selectSession(s)"
          >
            <div class="session-info">
              <span class="session-name">{{ s.userName }}</span>
              <span class="session-time">{{ formatTime(s.createTime) }}</span>
            </div>
            <button class="btn-assign" @click.stop="handleAssign(s.sessionId)">接入</button>
          </div>
        </div>

        <div v-if="activeSessions.length > 0" class="session-group">
          <div class="group-title">进行中</div>
          <div
            v-for="s in activeSessions"
            :key="s.sessionId"
            class="session-item"
            :class="{ active: activeSessionId === s.sessionId }"
            @click="selectSession(s)"
          >
            <div class="session-info">
              <span class="session-name">
                {{ s.userName }}
                <span v-if="s.unreadCount > 0" class="unread-badge">{{ s.unreadCount }}</span>
              </span>
              <span class="session-time">{{ formatTime(s.createTime) }}</span>
            </div>
            <button class="btn-close-session" @click.stop="handleClose(s.sessionId)">关闭</button>
          </div>
        </div>

        <div v-if="closedSessions.length > 0" class="session-group">
          <div class="group-title">已关闭</div>
          <div
            v-for="s in closedSessions"
            :key="s.sessionId"
            class="session-item closed"
            :class="{ active: activeSessionId === s.sessionId }"
            @click="selectSession(s)"
          >
            <div class="session-info">
              <span class="session-name">{{ s.userName }}</span>
              <span class="session-time">{{ formatTime(s.createTime) }}</span>
            </div>
          </div>
        </div>

        <div v-if="sessions.length === 0" class="empty-sessions">
          <p>暂无会话</p>
        </div>
      </aside>

      <div class="chat-panel">
        <div v-if="!activeSessionId" class="no-chat-selected">
          <p class="chat-icon">💬</p>
          <p>选择一个会话开始聊天</p>
        </div>

        <template v-else>
          <div class="chat-header">
            <h3>{{ currentSession?.userName || '未知用户' }}</h3>
            <span class="session-status">{{ getStatusLabel(currentSession?.status || '') }}</span>
          </div>

          <div ref="chatRef" class="chat-messages">
            <div v-if="messages.length === 0" class="empty-chat">
              <p>暂无消息</p>
            </div>
            <div
              v-for="(msg, idx) in messages"
              :key="idx"
              class="msg-item"
              :class="{
                mine: isMine(msg),
                system: msg.type === 'system' || msg.type === 'command_result',
              }"
            >
              <div
                class="msg-bubble"
                :class="{
                  system: msg.type === 'system' || (msg as any).msgType === 'SYSTEM',
                  command: msg.type === 'command_result',
                }"
              >
                <div v-if="getSenderLabel(msg)" class="msg-sender">{{ getSenderLabel(msg) }}</div>
                <template v-if="msg.type === 'command_result' && (msg as CommandResult).data && (msg as CommandResult).displayType !== 'TEXT'">
                  <pre class="msg-command-data">{{ renderResultData(msg as CommandResult) }}</pre>
                </template>
                <template v-else-if="(msg as any).msgType === 'IMAGE'">
                  <img
                    :src="(msg as any).content"
                    class="msg-image"
                    loading="lazy"
                    @click="openImage((msg as any).content)"
                  />
                </template>
                <template v-else-if="(msg as any).msgType === 'ORDER_CARD'">
                  <div class="msg-order-card">
                    <span class="card-icon">📦</span>
                    <span>订单号：{{ (msg as any).content }}</span>
                  </div>
                </template>
                <template v-else-if="(msg as any).msgType === 'PRODUCT_CARD'">
                  <div class="msg-product-card">
                    <span class="card-icon">🍽️</span>
                    <span>{{ (msg as any).content }}</span>
                  </div>
                </template>
                <div v-else class="msg-content">{{ getMsgContent(msg) }}</div>
                <div class="msg-time">{{ formatTime((msg as any).createTime || (msg as any).timestamp) }}</div>
              </div>
            </div>
          </div>

          <div class="chat-input-area">
            <input
              ref="imageInput"
              type="file"
              accept="image/*"
              hidden
              @change="handleImageSelect"
            />
            <button
              class="btn-image"
              :disabled="!connected"
              title="发送图片"
              @click="imageInput?.click()"
            >
              🖼️
            </button>
            <textarea
              v-model="inputText"
              placeholder="输入回复..."
              rows="2"
              :disabled="!connected"
              @keydown="onKeydown"
            ></textarea>
            <button
              class="btn-send"
              :disabled="!connected || !inputText.trim()"
              @click="sendMessage"
            >
              发送
            </button>
          </div>
        </template>
      </div>

      <aside class="command-panel">
        <div class="panel-header">
          <h3>操作面板</h3>
        </div>

        <div v-if="commandResult" class="command-result">{{ commandResult }}</div>

        <div v-if="commands.length === 0" class="empty-commands">
          <p>暂无可用命令</p>
        </div>

        <div class="command-list">
          <button
            v-for="cmd in commands"
            :key="cmd.name"
            class="command-btn"
            :disabled="!activeSessionId || commandLoading"
            :title="cmd.description"
            @click="handleCommand(cmd)"
          >
            {{ cmd.displayName }}
          </button>
        </div>
      </aside>
    </div>
  <div v-if="commandResultCard" class="modal-overlay" @click.self="commandResultCard = null">
      <div class="modal-dialog" :class="{ 'modal-dialog-wide': commandResultCard.commandName === 'queryOrderLogistics' }">
        <div class="modal-header">
          <h3>{{ commandResultCard.commandName === 'queryOrderLogistics' ? '📦 配送状态' : '查询结果' }}</h3>
          <button class="modal-close" @click="commandResultCard = null">&times;</button>
        </div>
        <div class="modal-body">
          <template v-if="getLogisticsData(commandResultCard)">
            <div class="logistics-card">
              <div class="logistics-info">
                <div class="logistics-row">
                  <span class="logistics-label">订单号</span>
                  <span class="logistics-value">{{ getLogisticsData(commandResultCard)!.orderId }}</span>
                </div>
                <div class="logistics-row">
                  <span class="logistics-label">配送公司</span>
                  <span class="logistics-value">{{ getLogisticsData(commandResultCard)!.logisticsCompany }}</span>
                </div>
                <div class="logistics-row">
                  <span class="logistics-label">运单号</span>
                  <span class="logistics-value">{{ getLogisticsData(commandResultCard)!.trackingNumber }}</span>
                </div>
                <div class="logistics-row">
                  <span class="logistics-label">骑手</span>
                  <span class="logistics-value">{{ getLogisticsData(commandResultCard)!.riderName }} {{ getLogisticsData(commandResultCard)!.riderPhone }}</span>
                </div>
                <div class="logistics-row">
                  <span class="logistics-label">当前状态</span>
                  <span class="logistics-value">
                    <span class="logistics-status-dot" :style="{ background: getLogisticsStatusColor(getLogisticsData(commandResultCard)!.status) }"></span>
                    {{ getLogisticsData(commandResultCard)!.statusText }}
                  </span>
                </div>
                <div class="logistics-row">
                  <span class="logistics-label">预计送达</span>
                  <span class="logistics-value">{{ getLogisticsData(commandResultCard)!.estimatedDelivery }}</span>
                </div>
              </div>
              <div class="logistics-timeline">
                <div class="timeline-title">配送轨迹</div>
                <div
                  v-for="(item, idx) in getLogisticsData(commandResultCard)!.timeline"
                  :key="idx"
                  class="timeline-node"
                  :class="{
                    'timeline-node-done': isLogisticsNodeDone(item, idx, getLogisticsData(commandResultCard)!.timeline),
                    'timeline-node-active': isLogisticsNodeActive(item, idx, getLogisticsData(commandResultCard)!.timeline),
                  }"
                >
                  <div class="timeline-dot" :style="isLogisticsNodeActive(item, idx, getLogisticsData(commandResultCard)!.timeline) ? { background: getLogisticsStatusColor(getLogisticsData(commandResultCard)!.status) } : {}"></div>
                  <div class="timeline-line" v-if="idx < getLogisticsData(commandResultCard)!.timeline.length - 1"></div>
                  <div class="timeline-content">
                    <div class="timeline-time">{{ formatLogisticsTime(item.time) }}</div>
                    <div class="timeline-title-text">{{ item.title }}</div>
                    <div class="timeline-desc">{{ item.description }}</div>
                  </div>
                </div>
              </div>
            </div>
          </template>
          <template v-else>
            <div class="modal-meta">
              <div class="meta-row">
                <span class="meta-label">操作</span>
                <span class="meta-value">{{ commandResultCard.commandName }}</span>
              </div>
              <div class="meta-row">
                <span class="meta-label">结果</span>
                <span class="meta-value" :class="commandResultCard.success ? 'success' : 'fail'">
                  {{ commandResultCard.success ? '✅ 成功' : '❌ 失败' }}
                </span>
              </div>
              <div class="meta-row" v-if="commandResultCard.displayType">
                <span class="meta-label">类型</span>
                <span class="meta-value">{{ commandResultCard.displayType === 'TABLE' ? '表格' : '卡片' }}</span>
              </div>
            </div>
            <pre class="modal-data">{{ renderResultData(commandResultCard) }}</pre>
          </template>
        </div>
        <div class="modal-footer">
          <button class="modal-btn" @click="commandResultCard = null">关闭</button>
          <button
            v-if="getLogisticsData(commandResultCard)"
            class="modal-btn modal-btn-primary"
            @click="() => { const roid = getLogisticsData(commandResultCard)!.orderId; commandResultCard = null; openLogisticsSimulator(roid) }"
          >
            物流模拟
          </button>
        </div>
      </div>
    </div>

    <div v-if="showLogisticsModal" class="modal-overlay" @click.self="showLogisticsModal = false">
      <div class="modal-dialog modal-dialog-wide">
        <div class="modal-header">
          <h3>物流模拟</h3>
          <button class="modal-close" @click="showLogisticsModal = false">&times;</button>
        </div>
        <div class="modal-body">
          <div v-if="logisticsLoading" class="logistics-loading">
            <p>加载中...</p>
          </div>

          <div v-if="logisticsMsg" class="logistics-msg" :class="{ 'logistics-msg-error': logisticsMsg.includes('失败') }">
            {{ logisticsMsg }}
          </div>

          <div v-if="logisticsDetail && !logisticsLoading" class="logistics-card">
            <div class="logistics-info">
              <div class="logistics-row">
                <span class="logistics-label">订单号</span>
                <span class="logistics-value">{{ logisticsDetail.orderId }}</span>
              </div>
              <div class="logistics-row">
                <span class="logistics-label">当前状态</span>
                <span class="logistics-value">
                  <span class="logistics-status-dot" :style="{ background: getLogisticsStatusColor(logisticsDetail.status) }"></span>
                  {{ logisticsDetail.statusText }}
                </span>
              </div>
              <div class="logistics-row">
                <span class="logistics-label">状态说明</span>
                <span class="logistics-value">{{ logisticsDetail.statusDescription }}</span>
              </div>
              <div class="logistics-row">
                <span class="logistics-label">配送公司</span>
                <span class="logistics-value">{{ logisticsDetail.logisticsCompany || '--' }}</span>
              </div>
              <div class="logistics-row">
                <span class="logistics-label">运单号</span>
                <span class="logistics-value">{{ logisticsDetail.trackingNumber || '--' }}</span>
              </div>
              <div class="logistics-row">
                <span class="logistics-label">骑手</span>
                <span class="logistics-value">{{ logisticsDetail.riderName || '--' }} {{ logisticsDetail.riderPhone || '' }}</span>
              </div>
              <div class="logistics-row">
                <span class="logistics-label">是否模拟</span>
                <span class="logistics-value">{{ logisticsDetail.isSimulated ? '是' : '否（默认状态）' }}</span>
              </div>
              <div class="logistics-row">
                <span class="logistics-label">更新时间</span>
                <span class="logistics-value">{{ logisticsDetail.updateTime || '--' }}</span>
              </div>
            </div>

            <div v-if="logisticsDetail.availableStatuses && logisticsDetail.availableStatuses.length > 0" class="logistics-actions">
              <div class="logistics-actions-title">可推进状态：</div>
              <div class="logistics-actions-list">
                <button
                  v-for="item in logisticsDetail.availableStatuses"
                  :key="item.code"
                  class="btn btn-sm btn-primary"
                  :disabled="logisticsLoading"
                  @click="pushLogisticsStatus(item.code)"
                >
                  {{ item.text }}
                </button>
              </div>
            </div>
            <div v-else class="logistics-actions">
              <div class="logistics-actions-title">当前已是最终状态，无法继续推进</div>
            </div>

            <div class="logistics-timeline">
              <div class="timeline-title">配送轨迹</div>
              <div
                v-for="(item, idx) in logisticsDetail.timeline"
                :key="idx"
                class="timeline-node"
                :class="{
                  'timeline-node-done': isLogisticsNodeDone(item, idx, logisticsDetail.timeline),
                  'timeline-node-active': isLogisticsNodeActive(item, idx, logisticsDetail.timeline),
                }"
              >
                <div class="timeline-dot" :style="isLogisticsNodeActive(item, idx, logisticsDetail.timeline) ? { background: getLogisticsStatusColor(logisticsDetail.status) } : {}"></div>
                <div class="timeline-line" v-if="idx < logisticsDetail.timeline.length - 1"></div>
                <div class="timeline-content">
                  <div class="timeline-time">{{ formatLogisticsTime(item.time) }}</div>
                  <div class="timeline-title-text">{{ item.title }}</div>
                  <div class="timeline-desc">{{ item.description }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="modal-btn" @click="showLogisticsModal = false">关闭</button>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<style scoped>
.admin-chat-workspace {
  display: flex;
  height: calc(100vh - 60px);
  overflow: hidden;
}

.session-panel {
  width: 240px;
  min-width: 240px;
  border-right: 1px solid #e0e0e0;
  background: #fafafa;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  padding: 14px 16px;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-header h3 {
  margin: 0;
  font-size: 0.95rem;
  color: #333;
}

.connection-badge {
  font-size: 0.7rem;
  padding: 2px 8px;
  border-radius: 10px;
  background: #fff0f0;
  color: #e53e3e;
}

.connection-badge.connected {
  background: #e8f5e9;
  color: #4caf50;
}

.queue-stats {
  display: flex;
  gap: 12px;
  padding: 8px 16px;
  font-size: 0.75rem;
  color: #666;
  border-bottom: 1px solid #e0e0e0;
}

.stat-item {
  background: #f5f5f5;
  padding: 2px 8px;
  border-radius: 4px;
}

.session-group {
  padding: 0 8px;
  margin-bottom: 8px;
}

.group-title {
  padding: 8px 8px 4px;
  font-size: 0.75rem;
  font-weight: 600;
  color: #999;
  text-transform: uppercase;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 2px;
}

.session-item:hover {
  background: #f0f0f0;
}

.session-item.active {
  background: #e3f2fd;
}

.session-item.closed {
  opacity: 0.6;
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-name {
  display: block;
  font-size: 0.85rem;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-time {
  font-size: 0.7rem;
  color: #aaa;
}

.unread-badge {
  display: inline-block;
  background: #e53e3e;
  color: #fff;
  font-size: 0.65rem;
  padding: 1px 6px;
  border-radius: 10px;
  margin-left: 4px;
}

.btn-assign {
  padding: 4px 10px;
  background: #4a90d9;
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 0.75rem;
  cursor: pointer;
  white-space: nowrap;
}

.btn-assign:hover {
  background: #357abd;
}

.btn-close-session {
  padding: 4px 8px;
  background: transparent;
  color: #999;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.7rem;
  cursor: pointer;
  white-space: nowrap;
}

.btn-close-session:hover {
  color: #e53e3e;
  border-color: #e53e3e;
}

.empty-sessions {
  padding: 40px 16px;
  text-align: center;
  color: #999;
  font-size: 0.85rem;
}

.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.no-chat-selected {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #aaa;
}

.chat-icon {
  font-size: 3rem;
  margin: 0 0 12px;
}

.chat-header {
  padding: 14px 20px;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-header h3 {
  margin: 0;
  font-size: 1rem;
  color: #333;
}

.session-status {
  font-size: 0.75rem;
  padding: 2px 10px;
  border-radius: 10px;
  background: #f0f0f0;
  color: #666;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.empty-chat {
  text-align: center;
  color: #aaa;
  font-size: 0.85rem;
  padding: 40px 0;
}

.msg-item {
  display: flex;
  max-width: 70%;
}

.msg-item.mine {
  align-self: flex-end;
}

.msg-item.system {
  align-self: center;
  max-width: 85%;
}

.msg-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  background: #f0f0f0;
  color: #333;
}

.msg-bubble.system {
  background: #e3f2fd;
  color: #1565c0;
  text-align: center;
  border-radius: 8px;
  padding: 8px 16px;
}

.msg-bubble.command {
  background: #fff3e0;
  color: #e65100;
  text-align: center;
  border-radius: 8px;
  padding: 8px 16px;
}

.msg-item.mine .msg-bubble {
  background: #4a90d9;
  color: #fff;
}

.msg-content {
  font-size: 0.85rem;
  line-height: 1.5;
  word-break: break-word;
}

.msg-time {
  font-size: 0.65rem;
  margin-top: 4px;
  opacity: 0.7;
}

.msg-sender {
  font-size: 0.7rem;
  font-weight: 600;
  margin-bottom: 2px;
  opacity: 0.8;
}

.chat-input-area {
  display: flex;
  gap: 10px;
  padding: 12px 20px;
  border-top: 1px solid #e0e0e0;
}

.chat-input-area textarea {
  flex: 1;
  padding: 10px 14px;
  border: 1.5px solid #e0e0e0;
  border-radius: 8px;
  font-size: 0.85rem;
  outline: none;
  resize: none;
  font-family: inherit;
  box-sizing: border-box;
}

.chat-input-area textarea:focus {
  border-color: #4a90d9;
}

.btn-send {
  padding: 0 20px;
  background: #4a90d9;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s;
  white-space: nowrap;
}

.btn-send:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.command-panel {
  width: 200px;
  min-width: 200px;
  border-left: 1px solid #e0e0e0;
  background: #fafafa;
  display: flex;
  flex-direction: column;
  padding: 0 12px;
  overflow-y: auto;
}

.command-result {
  padding: 8px 12px;
  margin: 8px 0;
  border-radius: 6px;
  font-size: 0.8rem;
  background: #e8f5e9;
  color: #2e7d32;
  text-align: center;
  animation: fadeOut 0.3s ease 5s forwards;
}

@keyframes fadeOut {
  to { opacity: 0; }
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.modal-dialog {
  width: 480px;
  max-width: 90vw;
  max-height: 80vh;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.18);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #eee;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.05rem;
  color: #333;
}

.modal-close {
  width: 28px;
  height: 28px;
  border: none;
  background: #f0f0f0;
  border-radius: 50%;
  font-size: 1rem;
  cursor: pointer;
  color: #666;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.modal-close:hover {
  background: #e0e0e0;
}

.modal-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}

.modal-meta {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f8f9fa;
  border-radius: 8px;
}

.meta-row {
  display: flex;
  gap: 12px;
  margin-bottom: 6px;
  font-size: 0.82rem;
}

.meta-row:last-child {
  margin-bottom: 0;
}

.meta-label {
  color: #999;
  font-weight: 500;
  min-width: 36px;
}

.meta-value {
  color: #333;
}

.meta-value.success {
  color: #22c55e;
  font-weight: 600;
}

.meta-value.fail {
  color: #ef4444;
  font-weight: 600;
}

.modal-data {
  margin: 0;
  padding: 14px 16px;
  background: #fafafa;
  border: 1px solid #eee;
  border-radius: 8px;
  font-size: 0.85rem;
  line-height: 1.8;
  color: #333;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow-y: auto;
}

.modal-footer {
  padding: 12px 20px;
  border-top: 1px solid #eee;
  display: flex;
  justify-content: flex-end;
}

.modal-btn {
  padding: 8px 28px;
  background: #4a90d9;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 0.9rem;
  cursor: pointer;
  transition: opacity 0.2s;
}

.modal-btn:hover {
  opacity: 0.9;
}

.result-card,
.result-card-header,
.result-card-title,
.result-card-type,
.result-card-type.card,
.result-card-type.table,
.result-card-body {
  display: none;
}

.empty-commands {
  text-align: center;
  color: #aaa;
  font-size: 0.8rem;
  padding: 20px 0;
}

.command-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 0;
}

.command-btn {
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 0.82rem;
  color: #333;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
}

.command-btn:hover:not(:disabled) {
  border-color: #4a90d9;
  color: #4a90d9;
  background: #f0f7ff;
}

.command-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn-image {
  padding: 0 12px;
  background: #f5f5f5;
  border: 1.5px solid #e0e0e0;
  border-radius: 8px;
  font-size: 1.2rem;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-image:hover:not(:disabled) {
  background: #e8e8e8;
}

.btn-image:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.msg-image {
  max-width: 240px;
  max-height: 240px;
  border-radius: 8px;
  cursor: pointer;
  object-fit: cover;
}

.msg-order-card,
.msg-product-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 0.85rem;
  cursor: pointer;
}

.msg-order-card:hover,
.msg-product-card:hover {
  border-color: #4a90d9;
}

.msg-command-data {
  padding: 0;
  margin: 0 0 4px;
  font-size: 0.78rem;
  line-height: 1.5;
  color: #333;
  white-space: pre-wrap;
  word-break: break-all;
  background: transparent;
}

/* ========== 物流卡片 ========== */

.modal-dialog-wide {
  width: 560px;
}

.logistics-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.logistics-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px;
  background: #fafafa;
  border-radius: 10px;
  border: 1px solid #eee;
}

.logistics-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.85rem;
}

.logistics-label {
  color: #999;
  font-weight: 500;
}

.logistics-value {
  color: #333;
  text-align: right;
  max-width: 60%;
  word-break: break-all;
}

.logistics-status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}

.logistics-timeline {
  padding: 0 4px;
}

.timeline-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #eee;
}

.timeline-node {
  position: relative;
  display: flex;
  gap: 12px;
  padding-left: 24px;
  padding-bottom: 16px;
}

.timeline-node:last-child {
  padding-bottom: 0;
}

.timeline-dot {
  position: absolute;
  left: 0;
  top: 4px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ddd;
  border: 2px solid #ccc;
  z-index: 1;
}

.timeline-node-done .timeline-dot {
  background: #4a90d9;
  border-color: #4a90d9;
}

.timeline-node-active .timeline-dot {
  width: 12px;
  height: 12px;
  top: 3px;
  left: -1px;
  border-width: 3px;
  box-shadow: 0 0 0 4px rgba(74, 144, 217, 0.2);
}

.timeline-line {
  position: absolute;
  left: 4px;
  top: 18px;
  bottom: 0;
  width: 2px;
  background: #e0e0e0;
}

.timeline-node-done .timeline-line {
  background: #4a90d9;
}

.timeline-content {
  flex: 1;
  min-width: 0;
}

.timeline-time {
  font-size: 0.72rem;
  color: #999;
  margin-bottom: 2px;
}

.timeline-title-text {
  font-size: 0.85rem;
  color: #333;
  font-weight: 500;
  margin-bottom: 2px;
}

.timeline-node-done .timeline-title-text {
  color: #666;
}

.timeline-node-active .timeline-title-text {
  color: #4a90d9;
  font-weight: 600;
}

.timeline-desc {
  font-size: 0.78rem;
  color: #999;
  line-height: 1.4;
}

.logistics-actions {
  padding: 14px 0;
  margin-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.logistics-actions-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: #333;
  margin-bottom: 10px;
}

.logistics-actions-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.logistics-loading {
  text-align: center;
  padding: 40px 0;
  color: #999;
}

.logistics-msg {
  padding: 10px 16px;
  margin-bottom: 12px;
  background: #e8f5e9;
  color: #2e7d32;
  border-radius: 6px;
  font-size: 0.85rem;
}

.logistics-msg-error {
  background: #fff1f0;
  color: #cf1322;
}

.modal-btn-primary {
  background: #4a90d9;
  color: #fff;
}

.modal-btn-primary:hover {
  background: #3a7bc8;
}
</style>