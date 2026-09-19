<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import type { CsMessage, CsMessageSend, SystemMessage, AdminOperationEvent, OfflineMessageSend, AgentTransferEvent, ChatHistoryEvent } from '@/types'
import { uploadImage } from '@/api/customerService'
import AppLayout from '@/components/AppLayout.vue'

const router = useRouter()
const userStore = useUserStore()

const messages = ref<(CsMessage | SystemMessage)[]>([])
const inputText = ref('')
const connected = ref(false)
const connecting = ref(true)
const chatRef = ref<HTMLElement | null>(null)
const imageInput = ref<HTMLInputElement | null>(null)
const offlineMode = ref(false)
const contactInfo = ref('')
const queueInfo = ref<{ position: number; queueSize: number; estimatedWaitMinutes: number } | null>(null)
const agentType = ref<'AI' | 'HUMAN'>('AI')
const isTransferring = ref(false)
let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let reconnectAttempts = 0
const MAX_RECONNECT = 5
const RECONNECT_BASE_MS = 2000

function connectWebSocket() {
  const userId = localStorage.getItem('userId')
  const userName = userStore.userInfo?.nickname || userStore.username || '用户'
  console.log('[Chat] 准备连接 WebSocket, userId:', userId)
  if (!userId) {
    console.warn('[Chat] userId 为空，无法连接 WebSocket')
    connecting.value = false
    return
  }

  const wsUrl = `ws://${window.location.hostname}:10002/ws/chat?userId=${userId}&role=user&userName=${encodeURIComponent(userName)}`
  console.log('[Chat] 连接地址:', wsUrl)

  ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    console.log('[Chat] WebSocket 连接成功')
    connected.value = true
    connecting.value = false
    reconnectAttempts = 0
  }

  ws.onmessage = (event) => {
    try {
      console.log('[Chat] 收到消息:', event.data)
      const data = JSON.parse(event.data)

      if (data.type === 'chat_history') {
        const history = data as ChatHistoryEvent
        if (history.messages && history.messages.length > 0) {
          messages.value = history.messages.map((msg) => {
            if (msg.msgType === 'SYSTEM' || msg.senderRole === 'system') {
              return {
                type: 'system',
                sessionId: msg.sessionId,
                senderId: msg.senderId,
                senderName: '系统',
                content: msg.content,
                timestamp: msg.createTime,
              } as SystemMessage
            }
            return msg
          })
        }
        scrollToBottom()
        return
      }

      if (data.type === 'admin_operation') {
        const op = data as AdminOperationEvent
        if (op.needApproval) {
          messages.value.push({
            type: 'system',
            sessionId: '',
            senderId: 'system',
            senderName: '系统',
            content: `已提交审批，审批单号: ${op.approvalId || '--'}，请耐心等待`,
            timestamp: op.timestamp,
          })
        } else {
          messages.value.push({
            type: 'system',
            sessionId: '',
            senderId: 'system',
            senderName: '系统',
            content: `${op.operatorName} ${op.message}`,
            timestamp: op.timestamp,
          })
        }
        scrollToBottom()
        return
      }

      if (data.type === 'agent_transfer') {
        const transfer = data as AgentTransferEvent
        isTransferring.value = true
        messages.value.push({
          type: 'system',
          sessionId: transfer.sessionId,
          senderId: 'system',
          senderName: '系统',
          content: transfer.content || '正在为您转接人工客服，请稍候...',
          timestamp: transfer.timestamp,
        })
        scrollToBottom()
        setTimeout(() => {
          agentType.value = transfer.toAgentType
          isTransferring.value = false
        }, 1500)
        return
      }

      if (data.type === 'system') {
        const sys = data as SystemMessage
        if (sys.agentType) {
          agentType.value = sys.agentType
        }
        if (sys.queueInfo) {
          queueInfo.value = sys.queueInfo
        }
        if (sys.allowOfflineMessage) {
          offlineMode.value = true
        }
        messages.value.push(sys)
        scrollToBottom()
        return
      }

      messages.value.push(data)
      scrollToBottom()
    } catch (e) {
      console.error('[Chat] 消息解析失败:', e)
    }
  }

  ws.onerror = (e) => {
    console.error('[Chat] WebSocket 连接错误:', e)
    connected.value = false
    connecting.value = false
  }

  ws.onclose = (e) => {
    console.log('[Chat] WebSocket 连接关闭, code:', e.code, 'reason:', e.reason)
    connected.value = false
    connecting.value = false
    scheduleReconnect()
  }
}

function scheduleReconnect() {
  if (reconnectAttempts >= MAX_RECONNECT) {
    console.warn('[Chat] 已达最大重连次数，放弃重连')
    return
  }
  const delay = RECONNECT_BASE_MS * Math.pow(2, reconnectAttempts)
  reconnectAttempts++
  console.log(`[Chat] ${delay}ms 后第 ${reconnectAttempts} 次重连...`)
  reconnectTimer = setTimeout(() => {
    connecting.value = true
    connectWebSocket()
  }, delay)
}

function sendMessage() {
  if (!ws || ws.readyState !== WebSocket.OPEN) return
  const text = inputText.value.trim()
  if (!text) return

  const msg: CsMessageSend = {
    type: 'message',
    msgType: 'TEXT',
    content: text,
  }

  ws.send(JSON.stringify(msg))
  inputText.value = ''
  scrollToBottom()
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

function goLogin() {
  router.push('/login')
}

function isMine(msg: CsMessage | SystemMessage): boolean {
  if (msg.type === 'system') return false
  const myId = userStore.userInfo?.userId || localStorage.getItem('userId') || ''
  return msg.senderId === myId
}

function getSenderName(msg: CsMessage | SystemMessage): string {
  if (msg.type === 'system') {
    if (msg.senderId === 'ai_agent') return 'AI 助手'
    return '系统'
  }
  if ((msg as CsMessage).senderId === 'ai_agent') return 'AI 助手'
  return msg.senderId === 'system' ? '系统' : ''
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
      }))
    }
  } catch (err) {
    console.error('[Chat] 图片上传失败:', err)
  }
  input.value = ''
}

function sendOrderCard(orderId: string) {
  if (!ws || ws.readyState !== WebSocket.OPEN) return
  ws.send(JSON.stringify({
    type: 'message',
    msgType: 'ORDER_CARD',
    content: orderId,
  }))
}

function sendOfflineMessage() {
  if (!ws || ws.readyState !== WebSocket.OPEN) return
  const text = inputText.value.trim()
  if (!text) return

  const msg: OfflineMessageSend = {
    type: 'offline_message',
    content: text,
    contactInfo: contactInfo.value || undefined,
  }
  ws.send(JSON.stringify(msg))
  inputText.value = ''
  scrollToBottom()
}

function openImage(url: string) {
  window.open(url, '_blank')
}



onMounted(() => {
  if (!userStore.isLoggedIn) return
  connectWebSocket()
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
  <AppLayout>
    <div class="chat-page">
      <div class="content-wrapper">
        <div v-if="!userStore.isLoggedIn" class="login-prompt">
          <p>请先登录后使用客服功能</p>
          <button class="btn-login" @click="goLogin">去登录</button>
        </div>

        <template v-else>
          <div class="chat-header">
            <h2>
              <span v-if="agentType === 'AI'" class="agent-badge ai">AI 助手</span>
              <span v-else class="agent-badge human">人工客服</span>
              <span class="agent-label">{{ agentType === 'AI' ? '为您服务' : '为您服务' }}</span>
            </h2>
            <span class="connection-status" :class="{ connected }">
              {{ connecting ? '连接中...' : connected ? '已连接' : '连接断开' }}
            </span>
          </div>

          <div v-if="queueInfo && agentType === 'HUMAN'" class="queue-banner">
            <span class="queue-icon">⏳</span>
            前面还有 <strong>{{ queueInfo.position }}</strong> 人排队，共 {{ queueInfo.queueSize }} 人等待，预计等待 <strong>{{ queueInfo.estimatedWaitMinutes }}</strong> 分钟
          </div>

          <div v-if="offlineMode" class="offline-banner">
            <span class="offline-icon">🌙</span>
            当前暂无客服在线，您可以发送离线留言
          </div>

          <div v-if="isTransferring" class="transfer-overlay">
            <div class="transfer-box">
              <span class="transfer-spinner"></span>
              <p>正在转接人工客服...</p>
              <p class="transfer-hint">请稍候，即将为您接通</p>
            </div>
          </div>

          <div ref="chatRef" class="chat-messages">
            <div v-if="messages.length === 0" class="empty-chat">
              <p class="chat-icon">🤖</p>
              <p v-if="agentType === 'AI'">您好，我是 AI 客服助手，可以帮您查询订单、修改地址、退单、查询商品等。请问有什么可以帮您的？</p>
              <p v-else>欢迎使用在线客服，有什么可以帮您？</p>
            </div>
            <div
              v-for="(msg, idx) in messages"
              :key="idx"
              class="msg-item"
              :class="{
                mine: isMine(msg),
                system: msg.type === 'system',
              }"
            >
              <div class="msg-bubble" :class="{ system: msg.type === 'system' || (msg as any).msgType === 'SYSTEM', ai: (msg as CsMessage).senderId === 'ai_agent' }">
                <div v-if="getSenderName(msg)" class="msg-sender">{{ getSenderName(msg) }}</div>
                <template v-if="(msg as any).msgType === 'IMAGE'">
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
                <div v-else class="msg-content">{{ (msg as any).content }}</div>
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
            <input
              v-if="offlineMode"
              v-model="contactInfo"
              class="contact-input"
              type="text"
              placeholder="联系方式（手机/邮箱，选填）"
            />
            <textarea
              v-model="inputText"
              :placeholder="offlineMode ? '输入离线留言...' : '输入消息...'"
              rows="2"
              :disabled="!connected"
              @keydown="onKeydown"
            ></textarea>
            <button
              v-if="offlineMode"
              class="btn-send"
              :disabled="!connected || !inputText.trim()"
              @click="sendOfflineMessage"
            >
              留言
            </button>
            <template v-else>
              <button
                class="btn-image"
                :disabled="!connected"
                title="发送图片"
                @click="imageInput?.click()"
              >
                🖼️
              </button>
              <button
                class="btn-send"
                :disabled="!connected || !inputText.trim()"
                @click="sendMessage"
              >
                发送
              </button>
            </template>
          </div>

          </template>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.chat-page {
  height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
}

.content-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  max-width: 700px;
  width: 100%;
  margin: 0 auto;
  padding: 16px 20px;
  overflow: hidden;
  position: relative;
}

.login-prompt {
  text-align: center;
  padding: 60px 20px;
}

.login-prompt p {
  margin: 0 0 16px;
  color: #666;
  font-size: 1.05rem;
}

.btn-login {
  padding: 10px 28px;
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 0.95rem;
  cursor: pointer;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid #eee;
  margin-bottom: 12px;
}

.chat-header h2 {
  margin: 0;
  font-size: 1.2rem;
  color: #333;
}

.connection-status {
  font-size: 0.8rem;
  padding: 4px 10px;
  border-radius: 12px;
  background: #fff0f0;
  color: #e53e3e;
}

.connection-status.connected {
  background: #e8f5e9;
  color: #4caf50;
}

.queue-banner {
  padding: 10px 14px;
  margin-bottom: 8px;
  background: #fff8e1;
  border: 1px solid #ffcc02;
  border-radius: 8px;
  font-size: 0.85rem;
  color: #795548;
  display: flex;
  align-items: center;
  gap: 6px;
}

.queue-icon {
  font-size: 1rem;
}

.offline-banner {
  padding: 10px 14px;
  margin-bottom: 8px;
  background: #e3f2fd;
  border: 1px solid #90caf9;
  border-radius: 8px;
  font-size: 0.85rem;
  color: #1565c0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.offline-icon {
  font-size: 1rem;
}

.contact-input {
  padding: 8px 12px;
  border: 1.5px solid #e0e0e0;
  border-radius: 6px;
  font-size: 0.82rem;
  outline: none;
  width: 180px;
  box-sizing: border-box;
}

.contact-input:focus {
  border-color: #ff6b35;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty-chat {
  text-align: center;
  padding: 40px 0;
  color: #999;
}

.chat-icon {
  font-size: 3rem;
  margin: 0 0 12px;
}

.msg-item {
  display: flex;
  max-width: 75%;
}

.msg-item.mine {
  align-self: flex-end;
}

.msg-item.system {
  align-self: center;
  max-width: 90%;
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

.msg-item.mine .msg-bubble {
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  color: #fff;
}

.msg-content {
  font-size: 0.9rem;
  line-height: 1.5;
  word-break: break-word;
}

.msg-time {
  font-size: 0.7rem;
  margin-top: 4px;
  opacity: 0.7;
}

.msg-sender {
  font-size: 0.75rem;
  font-weight: 600;
  margin-bottom: 2px;
  opacity: 0.8;
}

.chat-input-area {
  display: flex;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid #eee;
  margin-top: 8px;
}

.chat-input-area textarea {
  flex: 1;
  padding: 10px 14px;
  border: 1.5px solid #e0e0e0;
  border-radius: 8px;
  font-size: 0.9rem;
  outline: none;
  resize: none;
  font-family: inherit;
  box-sizing: border-box;
}

.chat-input-area textarea:focus {
  border-color: #ff6b35;
}

.btn-send {
  padding: 0 20px;
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 0.9rem;
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
  border-color: #ff6b35;
}

.card-icon {
  font-size: 1.1rem;
}

/* AI 客服标识样式 */
.agent-badge {
  font-size: 0.75rem;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 12px;
  margin-right: 6px;
}

.agent-badge.ai {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
}

.agent-badge.human {
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  color: #fff;
}

.agent-label {
  font-size: 0.85rem;
  color: #666;
  font-weight: 400;
}

/* AI 消息气泡样式 */
.msg-bubble.ai {
  background: linear-gradient(135deg, #f0f0ff 0%, #e8e8ff 100%);
  border: 1px solid #d0d0f0;
  color: #333;
}

.msg-bubble.ai .msg-sender {
  color: #667eea;
}

/* 转接过渡动画 */
.transfer-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  backdrop-filter: blur(4px);
}

.transfer-box {
  text-align: center;
  padding: 32px 40px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
}

.transfer-box p {
  margin: 0;
  font-size: 1rem;
  color: #333;
  font-weight: 500;
}

.transfer-hint {
  font-size: 0.8rem;
  color: #999;
  margin-top: 8px;
}

.transfer-spinner {
  display: inline-block;
  width: 36px;
  height: 36px;
  border: 3px solid #e0e0e0;
  border-top-color: #667eea;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>