package com.example.websocket;

import com.example.agent.AgentFactory;
import com.example.agent.AgentType;
import com.example.agent.HumanAgent;
import com.example.bean.ChatMessage;
import com.example.command.CommandContext;
import com.example.command.CommandRegistry;
import com.example.command.CommandResult;
import com.example.dao.mapper.ChatMessageMapper;
import com.example.message.MessageType;
import com.example.service.AgentStatusService;
import com.example.service.OperationLogService;
import com.example.service.QueueService;
import com.example.service.SensitiveOperationService;
import com.example.service.TicketService;
import com.example.session.ChatSession;
import com.example.session.SessionManager;
import com.example.ticket.TicketCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final ConcurrentHashMap<String, WebSocketSession> USER_SESSIONS = new ConcurrentHashMap<>();

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private AgentFactory agentFactory;

    @Autowired
    private CommandRegistry commandRegistry;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private AgentStatusService agentStatusService;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private QueueService queueService;

    @Autowired
    private SensitiveOperationService sensitiveOperationService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        String role = (String) session.getAttributes().get("role");
        String userName = (String) session.getAttributes().get("userName");
        if (userName == null || userName.isEmpty()) {
            userName = "用户_" + (userId != null ? userId.substring(0, Math.min(8, userId.length())) : "未知");
        }
        log.info("WebSocket连接已建立: sessionId={}, userId={}, role={}, userName={}",
                session.getId(), userId, role, userName);

        if ("admin".equals(role)) {
            agentFactory.getHumanAgent().registerAdminSession(userId, session);
            agentStatusService.agentOnline(userId, userName, AgentType.HUMAN, "DEFAULT", 5);
            log.info("客服上线: adminId={}", userId);

            try {
                List<ChatSession> waitingSessions = sessionManager.getWaitingSessions();
                List<ChatSession> mySessions = sessionManager.getActiveSessionsByAgent(userId);
                Map<String, Object> initData = new HashMap<>();
                initData.put("type", MessageType.USER_LIST.name());
                initData.put("waitingSessions", waitingSessions);
                initData.put("activeSessions", mySessions);
                initData.put("queueSize", waitingSessions.size());
                initData.put("onlineAgentCount", agentStatusService.getOnlineAgents().size());
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(initData)));
            } catch (Exception e) {
                log.error("发送初始化数据失败: {}", e.getMessage());
            }
        } else {
            USER_SESSIONS.put(userId, session);
            ChatSession chatSession = sessionManager.createSession(userId, userName);
            sessionManager.assignAgent(chatSession.getSessionId(), "ai_agent", AgentType.AI);
            agentFactory.getAiAgent().registerUserSession(userId, session);
            log.info("用户连接（AI接待）: userId={}, sessionId={}", userId, chatSession.getSessionId());

            try {
                sendChatHistory(userId, session);
            } catch (Exception e) {
                log.error("发送历史消息失败: {}", e.getMessage());
            }

            String welcomeMsg = "您好，我是AI客服助手，可以帮您查询订单、修改地址、退单、查询商品等。请问有什么可以帮您的？";
            saveSystemMessage(chatSession.getSessionId(), userId, welcomeMsg);

            try {
                Map<String, Object> aiWelcome = new HashMap<>();
                aiWelcome.put("type", "system");
                aiWelcome.put("sessionId", chatSession.getSessionId());
                aiWelcome.put("senderId", "ai_agent");
                aiWelcome.put("senderName", "AI 客服");
                aiWelcome.put("agentType", "AI");
                aiWelcome.put("content", welcomeMsg);
                aiWelcome.put("timestamp", LocalDateTime.now().toString());
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(aiWelcome)));

                notifyAdminsUserOnline(userId, userName, chatSession.getSessionId());
            } catch (Exception e) {
                log.error("发送AI欢迎消息失败: {}", e.getMessage());
            }
        }
    }

    private void sendChatHistory(String userId, WebSocketSession session) {
        try {
            log.info("开始查询历史消息: userId={}", userId);
            List<ChatMessage> historyMessages = chatMessageMapper.findRecentByUserId(
                    userId, LocalDateTime.now().minusDays(7));

            if (historyMessages != null && !historyMessages.isEmpty()) {
                Map<String, Object> historyMsg = new HashMap<>();
                historyMsg.put("type", "chat_history");
                historyMsg.put("messages", historyMessages);
                historyMsg.put("count", historyMessages.size());
                historyMsg.put("timestamp", LocalDateTime.now().toString());
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(historyMsg)));
                log.info("发送历史消息: userId={}, count={}", userId, historyMessages.size());
            } else {
                log.info("无历史消息: userId={}", userId);
            }
        } catch (Exception e) {
            log.error("查询历史消息失败: userId={}", userId, e);
        }
    }

    private void notifyAdminsUserOnline(String userId, String userName, String sessionId) {
        HumanAgent humanAgent = agentFactory.getHumanAgent();
        for (WebSocketSession adminSession : humanAgent.getAllAdminSessions().values()) {
            if (adminSession.isOpen()) {
                try {
                    Map<String, Object> event = new HashMap<>();
                    event.put("type", MessageType.USER_ONLINE.name());
                    event.put("userId", userId);
                    event.put("userName", userName);
                    event.put("sessionId", sessionId);
                    event.put("queueSize", queueService.getQueueSize());
                    synchronized (adminSession) {
                        adminSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
                    }
                } catch (Exception e) {
                    log.error("通知客服用户上线失败", e);
                }
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            Map<String, Object> msgMap = objectMapper.readValue(payload, Map.class);

            String userId = (String) session.getAttributes().get("userId");
            String role = (String) session.getAttributes().get("role");
            String userName = (String) session.getAttributes().get("userName");
            String content = (String) msgMap.get("content");
            String msgType = (String) msgMap.get("type");

            if (msgType == null) {
                msgType = "message";
            }

            if ("command".equals(msgType)) {
                handleCommandMessage(userId, role, userName, msgMap, session);
                return;
            }

            if ("offline_message".equals(msgType)) {
                handleOfflineMessageRequest(userId, userName, msgMap, session);
                return;
            }

            if (content == null || content.isEmpty()) {
                return;
            }

            ChatSession chatSession;
            if ("admin".equals(role)) {
                String sessionId = (String) msgMap.get("sessionId");
                if (sessionId != null) {
                    chatSession = sessionManager.getSession(sessionId);
                } else {
                    String targetUserId = (String) msgMap.get("targetUserId");
                    chatSession = sessionManager.findByUserId(targetUserId);
                }
            } else {
                chatSession = sessionManager.findByUserId(userId);
                if (chatSession == null) {
                    chatSession = sessionManager.createSession(userId, userName);
                }
            }

            String chatMsgType = (String) msgMap.get("msgType");
            if (chatMsgType == null) {
                chatMsgType = "TEXT";
            }

            ChatMessage chatMsg = new ChatMessage();
            chatMsg.setMsgId(UUID.randomUUID().toString().replace("-", ""));
            chatMsg.setSenderId(userId);
            chatMsg.setSenderRole(role);
            chatMsg.setContent(content);
            chatMsg.setMsgType(chatMsgType.toUpperCase());
            chatMsg.setIsRead(0);
            chatMsg.setCreateTime(LocalDateTime.now());

            if (chatSession != null) {
                chatMsg.setSessionId(chatSession.getSessionId());
            }

            if ("admin".equals(role)) {
                String targetUserId = (String) msgMap.get("targetUserId");
                if (targetUserId == null && chatSession != null) {
                    targetUserId = chatSession.getUserId();
                }
                chatMsg.setReceiverId(targetUserId);
                chatMsg.setReceiverRole("user");
                chatMessageMapper.insert(chatMsg);

                if (targetUserId != null && !targetUserId.isEmpty()) {
                    WebSocketSession userSession = USER_SESSIONS.get(targetUserId);
                    if (userSession != null && userSession.isOpen()) {
                        userSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(chatMsg)));
                    }
                }
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(chatMsg)));
            } else {
                String targetAdminId = (String) msgMap.get("targetAdminId");
                if (targetAdminId == null && chatSession != null) {
                    targetAdminId = chatSession.getAgentId();
                }
                chatMsg.setReceiverId(targetAdminId);
                chatMsg.setReceiverRole("admin");
                chatMessageMapper.insert(chatMsg);

                if (chatSession != null && chatSession.getAgentType() == AgentType.AI) {
                    agentFactory.getAiAgent().handleMessage(chatMsg, chatSession);
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(chatMsg)));
                    return;
                }

                HumanAgent humanAgent = agentFactory.getHumanAgent();
                if (targetAdminId != null && !targetAdminId.isEmpty()) {
                    WebSocketSession adminSession = humanAgent.getAdminSession(targetAdminId);
                    if (adminSession != null && adminSession.isOpen()) {
                        synchronized (adminSession) {
                            adminSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(chatMsg)));
                        }
                    }
                } else {
                    Map<String, WebSocketSession> allAdmins = humanAgent.getAllAdminSessions();
                    if (allAdmins.isEmpty()) {
                        try {
                            Map<String, Object> noAgentMsg = new HashMap<>();
                            noAgentMsg.put("type", "system");
                            noAgentMsg.put("senderId", "system");
                            noAgentMsg.put("content", "当前暂无客服在线，您可以发送离线留言，我们将在上线后第一时间回复您。");
                            noAgentMsg.put("allowOfflineMessage", true);
                            noAgentMsg.put("timestamp", LocalDateTime.now().toString());
                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(noAgentMsg)));
                        } catch (Exception e) {
                            log.error("发送无客服提示失败", e);
                        }
                    } else {
                        for (WebSocketSession adminSession : allAdmins.values()) {
                            if (adminSession.isOpen()) {
                                synchronized (adminSession) {
                                    adminSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(chatMsg)));
                                }
                            }
                        }
                    }
                }
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(chatMsg)));
            }

        } catch (Exception e) {
            log.error("处理消息失败: {}", e.getMessage(), e);
        }
    }

    private void handleOfflineMessageRequest(String userId, String userName,
                                             Map<String, Object> msgMap, WebSocketSession session) {
        String content = (String) msgMap.get("content");
        String contactInfo = (String) msgMap.get("contactInfo");
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", "system");
            response.put("senderId", "system");
            response.put("content", "您的留言已收到，客服将在工作时间内尽快回复您，感谢您的耐心等待。");
            response.put("timestamp", LocalDateTime.now().toString());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (Exception e) {
            log.error("发送离线留言确认失败", e);
        }
    }

    private void handleCommandMessage(String userId, String role, String userName,
                                      Map<String, Object> msgMap, WebSocketSession session) {
        String commandName = (String) msgMap.get("commandName");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) msgMap.get("commandParams");
        if (params == null) {
            params = new HashMap<>();
        }

        String targetUserId = (String) msgMap.get("targetUserId");
        String sessionId = (String) msgMap.get("sessionId");
        if (targetUserId == null && "admin".equals(role) && sessionId != null) {
            ChatSession chatSession = sessionManager.getSession(sessionId);
            if (chatSession != null) {
                targetUserId = chatSession.getUserId();
            }
        }
        if (targetUserId == null) {
            targetUserId = userId;
        }

        boolean needApproval = sensitiveOperationService.requiresApproval(commandName, params);
        if (needApproval) {
            String approvalId = sensitiveOperationService.createApproval(
                    sessionId, commandName, params, userId, userName, targetUserId);
            try {
                Map<String, Object> approvalResponse = new HashMap<>();
                approvalResponse.put("type", "command_result");
                approvalResponse.put("commandName", commandName);
                approvalResponse.put("success", false);
                approvalResponse.put("message", "此操作需要审批，已生成审批单: " + approvalId);
                approvalResponse.put("needApproval", true);
                approvalResponse.put("approvalId", approvalId);
                approvalResponse.put("timestamp", LocalDateTime.now().toString());
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(approvalResponse)));
            } catch (Exception e) {
                log.error("发送审批请求失败: {}", e.getMessage());
            }
            return;
        }

        CommandContext context = CommandContext.builder()
                .userId(targetUserId)
                .userName(userName)
                .agentId(userId)
                .sessionId(sessionId)
                .params(params)
                .build();

        CommandResult result = commandRegistry.execute(commandName, context);

        operationLogService.log(
                sessionId, sessionId,
                userId, userName, role,
                commandName, params, result,
                targetUserId);

        Map<String, Object> response = new HashMap<>();
        response.put("type", "command_result");
        response.put("commandName", commandName);
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        response.put("data", result.getData());
        response.put("displayType", result.getDisplayType());
        response.put("timestamp", LocalDateTime.now().toString());

        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (Exception e) {
            log.error("发送命令结果给客服失败: {}", e.getMessage());
        }

        if (result.isSuccess() && result.getData() != null && sessionId != null) {
            try {
                Map<String, Object> userResponse = new HashMap<>(response);
                userResponse.put("type", "admin_operation");
                userResponse.put("operatorName", userName);
                String userResponseJson = objectMapper.writeValueAsString(userResponse);

                WebSocketSession userSession = USER_SESSIONS.get(targetUserId);
                if (userSession != null && userSession.isOpen()) {
                    userSession.sendMessage(new TextMessage(userResponseJson));
                }

                saveSystemMessage(sessionId, userId,
                        "客服 " + userName + " 执行了操作：" + commandName + " → " + result.getMessage());
            } catch (Exception e) {
                log.error("发送命令结果给用户失败: {}", e.getMessage());
            }
        }
    }

    private void saveSystemMessage(String sessionId, String senderId, String content) {
        try {
            ChatMessage sysMsg = new ChatMessage();
            sysMsg.setMsgId(UUID.randomUUID().toString().replace("-", ""));
            sysMsg.setSessionId(sessionId);
            sysMsg.setSenderId(senderId);
            sysMsg.setSenderRole("system");
            sysMsg.setContent(content);
            sysMsg.setMsgType("SYSTEM");
            sysMsg.setIsRead(0);
            sysMsg.setCreateTime(LocalDateTime.now());
            chatMessageMapper.insert(sysMsg);
        } catch (Exception e) {
            log.error("保存系统消息失败: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        String role = (String) session.getAttributes().get("role");

        if ("admin".equals(role)) {
            agentFactory.getHumanAgent().removeAdminSession(userId);
            agentStatusService.agentOffline(userId);
            log.info("客服下线: adminId={}", userId);
        } else {
            USER_SESSIONS.remove(userId);
            ChatSession chatSession = sessionManager.findByUserId(userId);
            if (chatSession != null) {
                sessionManager.closeSession(chatSession.getSessionId());
            }
            log.info("用户断开: userId={}", userId);

            HumanAgent humanAgent = agentFactory.getHumanAgent();
            for (WebSocketSession adminSession : humanAgent.getAllAdminSessions().values()) {
                if (adminSession.isOpen()) {
                    try {
                        Map<String, Object> event = new HashMap<>();
                        event.put("type", MessageType.USER_OFFLINE.name());
                        event.put("userId", userId);
                        event.put("queueSize", queueService.getQueueSize());
                        synchronized (adminSession) {
                            adminSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
                        }
                    } catch (Exception e) {
                        log.warn("通知客服用户下线失败（连接已关闭）: userId={}", userId);
                    }
                }
            }
        }
    }
}