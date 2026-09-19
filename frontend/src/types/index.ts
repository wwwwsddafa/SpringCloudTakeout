export interface ApiResponse<T = any> {
  code: number
  msg: string
  data: T
}

// ========== 用户相关 ==========
export interface CaptchaData {
  captchaKey: string
  captchaImage: string
}

export interface UserInfo {
  userId: string
  username: string
  nickname?: string
  email?: string
  avatar?: string | null
  role: 'GUEST' | 'USER' | 'ADMIN'
}

export interface LoginData {
  token: string
  userInfo: UserInfo & { jti: string }
}

export interface RegisterParams {
  username: string
  password: string
  email?: string
  captcha: string
  captchaKey: string
}

export interface LoginParams {
  username: string
  password: string
  captcha: string
  captchaKey: string
}

// ========== 商品相关 ==========
export interface Product {
  fid: string
  fname: string
  normprice: number
  realprice: number
  detail: string
  fphoto: string
  category: string
  status: number
  createTime?: string
  updateTime?: string
}

export interface PageResult<T> {
  total: number
  records: T[]
}

// ========== 购物车相关 ==========
export interface CartItem {
  fid: string
  fname: string
  realprice: number
  fphoto: string
  num: number
}

export interface AddCartParams {
  fid: string
  fname: string
  realprice: number
  fphoto: string
  num: number
}

// ========== 订单相关 ==========
export interface OrderItem {
  roid: string
  fid: string
  fname: string
  dealprice: number
  num: number
}

export interface Order {
  roid: string
  userid: string
  uname: string
  address: string
  tel: string
  orderTime: string
  deliveryType: string
  payment: string
  ps: string
  status: number
  tradeno: string
  payTime: string | null
  cancelTime: string | null
  items: OrderItem[]
  totalAmount: number
  discountAmount: number
  payAmount: number
}

// ========== 管理员相关 ==========
export interface AdminProductQuery {
  page?: number
  size?: number
  keyword?: string
}

export interface AddProductFormData {
  fname: string
  normprice: number
  realprice: number
  detail?: string
  category?: string
  photo: File[]
}

export interface UpdateProductParams {
  fid: string
  fname?: string
  normprice?: number
  realprice?: number
  detail?: string
  category?: string
}

// ========== 搜索相关 ==========
export interface SearchHighlight {
  [field: string]: string[]
}

export interface SearchResultItem extends Product {
  likeCount: number
  dislikeCount: number
  highlights: SearchHighlight
}

export interface SearchResult {
  total: number
  records: SearchResultItem[]
}

// ========== 点赞/踩相关 ==========
export interface LikeStatusResult {
  likeStatus: number | null
}

// ========== 评价相关 ==========
export interface Review {
  reviewId: string
  fid: string
  userId: string
  username?: string
  avatar?: string
  starRating: number
  reviewText: string
  reviewImages: string
  status: number
  createTime: string
  updateTime: string
}

export interface ReviewListResult {
  total: number
  records: Review[]
}

export interface ReviewRating {
  avgStar: number
  reviewCount: number
  distribution: number[]
}

export interface CreateReviewParams {
  fid: string
  starRating: number
  reviewText?: string
  reviewImages?: string
}

// ========== 免单相关 ==========
export interface FreeOrderEvent {
  eventId: string
  eventName: string
  totalCount: number
  remainCount: number
  minAmount: number
  maxAmount: number
  startTime: string
  endTime: string
  status: 'pending' | 'active' | 'ended' | 'cancelled'
  createTime: string
  adminId: string
  grabCount?: number
}

export interface FreeOrderEventDetail extends FreeOrderEvent {
  grabCount: number
}

export interface FreeOrderEventCurrent {
  hasEvent: boolean
  eventId?: string
  eventName?: string
  minAmount?: number
  maxAmount?: number
  startTime?: string
  endTime?: string
  status?: 'pending' | 'active' | 'ended'
  remainCount?: number
  serverTime?: string
}

export interface FreeOrderGrabResult {
  freeOrderNo: string
  couponAmount: number
  minAmount: number
  maxAmount: number
  eventName: string
}

export interface FreeOrderCoupon {
  couponNo: string
  eventName: string
  couponAmount: number
  minAmount: number
  maxAmount: number
  createTime: string
  status: 'UNUSED' | 'USED' | 'EXPIRED'
}

export interface CreateFreeOrderEventParams {
  eventName: string
  minAmount: number
  maxAmount: number
  startTime: string
  endTime: string
  couponCount: number
}

// ========== 客服相关 ==========

export interface CsMessage {
  type?: 'message'
  msgId: string
  senderId: string
  senderRole: string
  receiverId?: string
  receiverRole?: string
  content: string
  createTime: string
  sessionId: string
  msgType?: string
  isRead?: number
}

export interface CsMessageSend {
  type: 'message'
  msgType?: string
  content: string
  targetAdminId?: string
  sessionId?: string
  targetUserId?: string
}

export interface CommandSend {
  type: 'command'
  commandName: string
  commandParams: Record<string, any>
  sessionId: string
  targetUserId?: string
}

export interface CommandResult {
  type: 'command_result'
  commandName: string
  success: boolean
  message: string
  data: any
  displayType?: string
  timestamp: string
  needApproval?: boolean
  approvalId?: string
}

export interface AdminOperationEvent {
  type: 'admin_operation'
  commandName: string
  success: boolean
  message: string
  data: any
  displayType?: string
  operatorName: string
  timestamp: string
}

export interface SystemMessage {
  type: 'system'
  sessionId: string
  senderId: string
  senderName: string
  content: string
  timestamp: string
  queueInfo?: QueueInfo
  allowOfflineMessage?: boolean
  agentType?: 'AI' | 'HUMAN'
}

export interface QueueInfo {
  position: number
  queueSize: number
  estimatedWaitMinutes: number
  onlineAgentCount: number
}

export interface OfflineMessageSend {
  type: 'offline_message'
  content: string
  contactInfo?: string
}

export interface UserOnlineEvent {
  type: 'USER_ONLINE'
  userId: string
  userName: string
  sessionId: string
}

export interface UserOfflineEvent {
  type: 'USER_OFFLINE'
  userId: string
}

export interface ChatSession {
  sessionId: string
  userId: string
  userName: string
  agentId?: string
  agentType?: string
  status: string
  createTime: string
  unreadMessageIds?: string[]
  unreadCount: number
}

export interface UserListEvent {
  type: 'USER_LIST'
  waitingSessions: ChatSession[]
  activeSessions: ChatSession[]
  queueSize?: number
  onlineAgentCount?: number
}

export interface CommandParam {
  name: string
  displayName: string
  type: string
  required: boolean
  description: string
}

export interface CommandDefinition {
  name: string
  displayName: string
  description: string
  params: CommandParam[]
}

export interface AgentTransferEvent {
  type: 'agent_transfer'
  sessionId: string
  fromAgentType: 'AI' | 'HUMAN'
  toAgentType: 'AI' | 'HUMAN'
  reason: string
  content: string
  timestamp: string
}

export interface ChatHistoryEvent {
  type: 'chat_history'
  messages: CsMessage[]
  count: number
  timestamp: string
}

export type WsEvent = CsMessage | SystemMessage | CommandResult | AdminOperationEvent | UserOnlineEvent | UserOfflineEvent | UserListEvent | AgentTransferEvent | ChatHistoryEvent

// ========== 订单相关补充 ==========
export interface CreateOrderParams {
  orderItems: { fid: string; quantity: number }[]
  address: string
  tel: string
  deliveryType?: string
  payment?: string
  remark?: string
  freeOrderNo?: string
  originalAmount: number
  expectAmount: number
}

// ========== 客服 v3 新增：工单 ==========

export interface Ticket {
  ticketId: string
  sessionId?: string
  userId: string
  userName: string
  agentId?: string
  agentName?: string
  category: string
  priority: string
  status: string
  title: string
  description: string
  source: string
  tags: string
  resolution?: string
  resolutionType?: string
  rating?: number
  comment?: string
  slaDeadline?: string
  createTime: string
  updateTime: string
}

export interface CreateTicketParams {
  sessionId?: string
  userId: string
  userName?: string
  category?: string
  priority?: string
  title: string
  description?: string
  source?: string
}

// ========== 客服 v3 新增：客服工作台 ==========

export interface AgentStatus {
  agentId: string
  agentName: string
  status: string
  currentSessions: number
  maxSessions: number
}

export interface AgentDashboard {
  agentStatus: AgentStatus
  activeSessions: number
  waitingCount: number
  onlineAgentCount: number
}

export interface AgentOnlineParams {
  agentName: string
  skillGroup?: string
  maxSessions?: number
}

// ========== 客服 v3 新增：快捷回复 ==========

export interface QuickReply {
  templateId: string
  title: string
  content: string
  category: string
  skillGroup: string
  useCount: number
  creatorId: string
  createTime: string
}

export interface CreateQuickReplyParams {
  title: string
  content: string
  category?: string
  skillGroup?: string
}

// ========== 客服 v3 新增：离线留言 ==========

export interface OfflineMessage {
  msgId: string
  userId: string
  userName: string
  content: string
  contactInfo: string
  isRead: number
  createTime: string
}

// ========== 客服 v3 新增：审批 ==========

export interface Approval {
  approvalId: string
  sessionId: string
  commandName: string
  operatorId: string
  operatorName: string
  targetUserId: string
  params: Record<string, any>
  status: string
  approverId?: string
  reason?: string
  createTime: string
  updateTime: string
}

// ========== 客服 v3 新增：配送状态 ==========

export interface LogisticsTimelineItem {
  time: string
  title: string
  description: string
}

export interface LogisticsData {
  orderId: string
  logisticsCompany: string
  trackingNumber: string
  riderName: string
  riderPhone: string
  status: string
  statusText: string
  estimatedDelivery: string
  timeline: LogisticsTimelineItem[]
}

export interface AvailableStatus {
  code: string
  text: string
}

export interface LogisticsDetail {
  orderId: string
  logisticsCompany: string
  trackingNumber: string
  riderName: string
  riderPhone: string
  status: string
  statusText: string
  statusDescription: string
  isSimulated: boolean
  updateTime: string
  timeline: LogisticsTimelineItem[]
  availableStatuses: AvailableStatus[]
}

export interface SetLogisticsParams {
  roid: string
  status: string
  logisticsCompany?: string
  trackingNumber?: string
  riderName?: string
  riderPhone?: string
}