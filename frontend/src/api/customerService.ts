import request from './index'
import type {
  ApiResponse,
  CsMessage,
  CommandDefinition,
  CommandResult,
  ChatSession,
  Ticket,
  CreateTicketParams,
  AgentDashboard,
  AgentOnlineParams,
  QuickReply,
  CreateQuickReplyParams,
  OfflineMessage,
  Approval,
} from '@/types'

export function getChatHistory() {
  return request.get<ApiResponse<CsMessage[]>>('/customer-service/history')
}

export function getSessionHistory(sessionId: string, page = 1, size = 50) {
  return request.get<ApiResponse<CsMessage[]>>(
    `/customer-service/history/session/${sessionId}`,
    { params: { page, size } },
  )
}

export function getUserChatHistory(userId: string) {
  return request.get<ApiResponse<CsMessage[]>>(`/customer-service/history/user/${userId}`)
}

export function getActiveUsers() {
  return request.get<ApiResponse<string[]>>('/customer-service/users')
}

export function getActiveSessions() {
  return request.get<ApiResponse<ChatSession[]>>('/customer-service/sessions/active')
}

export function getCommands() {
  return request.get<ApiResponse<CommandDefinition[]>>('/customer-service/commands')
}

export function getWaitingSessions() {
  return request.get<ApiResponse<ChatSession[]>>('/customer-service/sessions/waiting')
}

export function getMySessions() {
  return request.get<ApiResponse<ChatSession[]>>('/customer-service/sessions/my')
}

export function assignSession(sessionId: string) {
  return request.post<ApiResponse<string>>(`/customer-service/agent/assign/${sessionId}`)
}

export function closeSession(sessionId: string) {
  return request.post<ApiResponse<string>>(`/customer-service/agent/close/${sessionId}`)
}

export function executeCommand(data: {
  commandName: string
  targetUserId: string
  params: Record<string, any>
}) {
  return request.post<ApiResponse<any>>('/customer-service/agent/command/execute', data)
}

export function uploadImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<ApiResponse<string>>('/customer-service/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// ========== v3 新增：工单 ==========

export function createTicket(data: CreateTicketParams) {
  return request.post<ApiResponse<Ticket>>('/customer-service/ticket/create', data)
}

export function assignTicket(ticketId: string) {
  return request.post<ApiResponse<Ticket>>(`/customer-service/ticket/assign/${ticketId}`)
}

export function updateTicketStatus(ticketId: string, status: string) {
  return request.post<ApiResponse<Ticket>>(`/customer-service/ticket/status/${ticketId}`, { status })
}

export function resolveTicket(ticketId: string, data: { resolution: string; resolutionType?: string }) {
  return request.post<ApiResponse<Ticket>>(`/customer-service/ticket/resolve/${ticketId}`, data)
}

export function rateTicket(ticketId: string, data: { rating: number; comment?: string }) {
  return request.post<ApiResponse<Ticket>>(`/customer-service/ticket/rate/${ticketId}`, data)
}

export function updateTicketCategory(ticketId: string, data: { category?: string; priority?: string }) {
  return request.post<ApiResponse<Ticket>>(`/customer-service/ticket/category/${ticketId}`, data)
}

export function updateTicketTags(ticketId: string, tags: string) {
  return request.post<ApiResponse<Ticket>>(`/customer-service/ticket/tags/${ticketId}`, { tags })
}

export function getTicketDetail(ticketId: string) {
  return request.get<ApiResponse<Ticket>>(`/customer-service/ticket/detail/${ticketId}`)
}

export function getMyTickets() {
  return request.get<ApiResponse<Ticket[]>>('/customer-service/ticket/my')
}

export function getUserTickets(userId: string) {
  return request.get<ApiResponse<Ticket[]>>(`/customer-service/ticket/user/${userId}`)
}

export function getPendingTickets() {
  return request.get<ApiResponse<Ticket[]>>('/customer-service/ticket/pending')
}

export function getOverdueTickets() {
  return request.get<ApiResponse<Ticket[]>>('/customer-service/ticket/overdue')
}

export function getTicketLogs(ticketId: string) {
  return request.get<ApiResponse<any[]>>(`/customer-service/ticket/logs/${ticketId}`)
}

// ========== v3 新增：客服工作台 ==========

export function agentOnline(data: AgentOnlineParams) {
  return request.post<ApiResponse<AgentDashboard>>('/customer-service/agent/workbench/online', data)
}

export function agentOffline() {
  return request.post<ApiResponse<void>>('/customer-service/agent/workbench/offline')
}

export function updateAgentStatus(status: string) {
  return request.post<ApiResponse<void>>('/customer-service/agent/workbench/status', { status })
}

export function getAgentStatus() {
  return request.get<ApiResponse<AgentDashboard>>('/customer-service/agent/workbench/status')
}

export function getOnlineAgents() {
  return request.get<ApiResponse<any[]>>('/customer-service/agent/workbench/online-agents')
}

export function getAgentMySessions() {
  return request.get<ApiResponse<ChatSession[]>>('/customer-service/agent/workbench/my-sessions')
}

export function getAgentWaitingSessions() {
  return request.get<ApiResponse<ChatSession[]>>('/customer-service/agent/workbench/waiting-sessions')
}

export function getQueueInfo(userId?: string) {
  return request.get<ApiResponse<any>>('/customer-service/agent/workbench/queue-info', { params: { userId } })
}

export function autoAssignSession() {
  return request.post<ApiResponse<any>>('/customer-service/agent/workbench/auto-assign')
}

export function getAgentDashboard() {
  return request.get<ApiResponse<AgentDashboard>>('/customer-service/agent/workbench/dashboard')
}

// ========== v3 新增：会话管理（Agent） ==========

export function transferSession(sessionId: string, targetAgentId: string) {
  return request.post<ApiResponse<any>>(`/customer-service/agent/transfer/${sessionId}`, { targetAgentId })
}

export function bindTicketToSession(sessionId: string, ticketId: string) {
  return request.post<ApiResponse<any>>(`/customer-service/agent/bind-ticket/${sessionId}`, { ticketId })
}

export function setSessionCategory(sessionId: string, data: { category: string; priority?: string }) {
  return request.post<ApiResponse<any>>(`/customer-service/agent/category/${sessionId}`, data)
}

export function setSessionTags(sessionId: string, tags: string) {
  return request.post<ApiResponse<any>>(`/customer-service/agent/tags/${sessionId}`, { tags })
}

export function rateSession(sessionId: string, data: { rating: number; comment?: string }) {
  return request.post<ApiResponse<any>>(`/customer-service/agent/rate/${sessionId}`, data)
}

// ========== v3 新增：快捷回复 ==========

export function createQuickReply(data: CreateQuickReplyParams) {
  return request.post<ApiResponse<QuickReply>>('/customer-service/quick-reply', data)
}

export function updateQuickReply(templateId: string, data: Partial<CreateQuickReplyParams>) {
  return request.put<ApiResponse<QuickReply>>(`/customer-service/quick-reply/${templateId}`, data)
}

export function deleteQuickReply(templateId: string) {
  return request.delete<ApiResponse<void>>(`/customer-service/quick-reply/${templateId}`)
}

export function useQuickReply(templateId: string) {
  return request.post<ApiResponse<void>>(`/customer-service/quick-reply/${templateId}/use`)
}

export function getQuickReplies(params?: { category?: string; keyword?: string }) {
  return request.get<ApiResponse<QuickReply[]>>('/customer-service/quick-reply', { params })
}

// ========== v3 新增：离线留言 ==========

export function createOfflineMessage(data: {
  userId: string
  userName: string
  content: string
  contactInfo?: string
}) {
  return request.post<ApiResponse<OfflineMessage>>('/customer-service/offline-message', data)
}

export function markOfflineMessageRead(msgId: string) {
  return request.post<ApiResponse<void>>(`/customer-service/offline-message/${msgId}/read`)
}

export function getUnreadOfflineMessages() {
  return request.get<ApiResponse<OfflineMessage[]>>('/customer-service/offline-message/unread')
}

export function getUserOfflineMessages(userId: string) {
  return request.get<ApiResponse<OfflineMessage[]>>(`/customer-service/offline-message/user/${userId}`)
}

export function getUnreadOfflineMessageCount() {
  return request.get<ApiResponse<number>>('/customer-service/offline-message/unread-count')
}

// ========== v3 新增：审批 ==========

export function getPendingApprovals() {
  return request.get<ApiResponse<Approval[]>>('/customer-service/approval/pending')
}

export function approveApproval(approvalId: string) {
  return request.post<ApiResponse<CommandResult>>(`/customer-service/approval/${approvalId}/approve`)
}

export function rejectApproval(approvalId: string, reason: string) {
  return request.post<ApiResponse<void>>(`/customer-service/approval/${approvalId}/reject`, { reason })
}

// ========== v3 新增：物流模拟 ==========

export function getLogisticsStatus(roid: string) {
  return request.get<ApiResponse<import('@/types').LogisticsDetail>>(`/customer-service/agent/logistics/${roid}`)
}

export function setLogisticsStatus(data: import('@/types').SetLogisticsParams) {
  return request.post<ApiResponse<any>>('/customer-service/agent/logistics/set', data)
}