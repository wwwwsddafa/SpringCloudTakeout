package com.example.agent;

import com.example.bean.ChatMessage;
import com.example.command.Command;
import com.example.command.CommandContext;
import com.example.command.CommandRegistry;
import com.example.command.CommandResult;
import com.example.dao.mapper.ChatMessageMapper;
import com.example.service.QueueService;
import com.example.session.ChatSession;
import com.example.session.SessionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class AiAgent implements Agent {

    private static final ConcurrentHashMap<String, WebSocketSession> USER_SESSIONS = new ConcurrentHashMap<>();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CommandRegistry commandRegistry;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private QueueService queueService;

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    public void registerUserSession(String userId, WebSocketSession session) {
        USER_SESSIONS.put(userId, session);
        log.info("AI客服用户会话注册: userId={}", userId);
    }

    public void removeUserSession(String userId) {
        USER_SESSIONS.remove(userId);
        log.info("AI客服用户会话移除: userId={}", userId);
    }

    @Override
    public void handleMessage(ChatMessage message, ChatSession session) {
        WebSocketSession userSession = USER_SESSIONS.get(message.getSenderId());
        if (userSession != null && userSession.isOpen()) {
            try {
                String aiReply = generateAiReply(message.getContent(), session);
                ChatMessage reply = new ChatMessage();
                reply.setMsgId(java.util.UUID.randomUUID().toString().replace("-", ""));
                reply.setSenderId("ai_agent");
                reply.setSenderRole("admin");
                reply.setReceiverId(message.getSenderId());
                reply.setReceiverRole("user");
                reply.setContent(aiReply);
                reply.setSessionId(session.getSessionId());
                reply.setMsgType("TEXT");
                reply.setIsRead(0);
                reply.setCreateTime(java.time.LocalDateTime.now());
                chatMessageMapper.insert(reply);
                userSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(reply)));
            } catch (Exception e) {
                log.error("AI回复失败: userId={}", message.getSenderId(), e);
                sendFallbackReply(message.getSenderId(), userSession, session);
            }
        }
    }

    private String generateAiReply(String userMessage, ChatSession session) {
        try {
            Map<String, Object> requestBody = buildChatRequest(userMessage, session);
            String json = objectMapper.writeValueAsString(requestBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            String response = restTemplate.postForObject(
                    aiServiceUrl + "/api/chat", entity, String.class);

            Map<String, Object> result = objectMapper.readValue(
                    response, new TypeReference<Map<String, Object>>() {});

            String reply = (String) result.get("reply");

            @SuppressWarnings("unchecked")
            Map<String, String> action = (Map<String, String>) result.get("action");
            if (action != null && "transfer_to_human".equals(action.get("type"))) {
                String reason = action.getOrDefault("reason", "AI 无法处理");
                log.info("AI 请求转人工: sessionId={}, reason={}", session.getSessionId(), reason);
                triggerTransferToHuman(session, reply, reason);
            }

            return reply != null ? reply : "抱歉，我暂时无法处理您的问题，正在为您转接人工客服。";
        } catch (Exception e) {
            log.error("调用AI服务失败: {}", e.getMessage());
            return fallbackReply(userMessage, session);
        }
    }

    private Map<String, Object> buildChatRequest(String userMessage, ChatSession session) {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("session_id", session.getSessionId());
        request.put("user_id", session.getUserId());
        request.put("user_name", session.getUserName() != null ? session.getUserName() : "用户");
        request.put("message", userMessage);

        java.util.List<Map<String, String>> history = buildHistory(session.getSessionId());
        request.put("history", history);

        return request;
    }

    private java.util.List<Map<String, String>> buildHistory(String sessionId) {
        java.util.List<Map<String, String>> history = new java.util.ArrayList<>();
        try {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ChatMessage> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            wrapper.eq("session_id", sessionId)
                    .in("msg_type", "TEXT", "text")
                    .orderByDesc("create_time")
                    .last("LIMIT 12");
            java.util.List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);

            java.util.Collections.reverse(messages);
            for (ChatMessage msg : messages) {
                Map<String, String> item = new java.util.LinkedHashMap<>();
                if ("admin".equals(msg.getSenderRole()) || "ai_agent".equals(msg.getSenderId())) {
                    item.put("role", "assistant");
                } else {
                    item.put("role", "user");
                }
                item.put("content", msg.getContent() != null ? msg.getContent() : "");
                history.add(item);
            }
        } catch (Exception e) {
            log.warn("构建历史对话失败: {}", e.getMessage());
        }
        return history;
    }

    private void triggerTransferToHuman(ChatSession session, String aiReply, String reason) {
        try {
            if (session.getAgentId() != null && !"ai_agent".equals(session.getAgentId())) {
                return;
            }

            sessionManager.transferSession(session.getSessionId(), null);

            String assignedAgentId = queueService.autoAssignToAgent();
            String message;
            if (assignedAgentId != null) {
                message = "已为您转接人工客服，请稍候...";
            } else {
                message = "正在为您转接人工客服，当前无空闲客服，请稍候...";
            }

            WebSocketSession userSession = USER_SESSIONS.get(session.getUserId());
            if (userSession != null && userSession.isOpen()) {
                Map<String, Object> transferMsg = new java.util.HashMap<>();
                transferMsg.put("type", "agent_transfer");
                transferMsg.put("sessionId", session.getSessionId());
                transferMsg.put("fromAgentType", "AI");
                transferMsg.put("toAgentType", "HUMAN");
                transferMsg.put("reason", reason);
                transferMsg.put("content", message);
                transferMsg.put("timestamp", java.time.LocalDateTime.now().toString());
                userSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(transferMsg)));
            }
            log.info("AI 转人工完成: sessionId={}, userId={}, assignedAgent={}", session.getSessionId(), session.getUserId(), assignedAgentId);
        } catch (Exception e) {
            log.error("AI 转人工失败: sessionId={}", session.getSessionId(), e);
        }
    }

    private String fallbackReply(String userMessage, ChatSession session) {
        String lowerMsg = userMessage.toLowerCase().trim();
        if (lowerMsg.contains("订单") || lowerMsg.contains("order")) {
            return "您好！我注意到您在咨询订单相关的问题。您可以提供订单号，我可以帮您查询订单详情、修改地址或处理退单。";
        }
        if (lowerMsg.contains("商品") || lowerMsg.contains("产品")) {
            return "您好！如果您想了解某个商品的详细信息，请告诉我商品名称或编号，我帮您查询。";
        }
        if (lowerMsg.contains("退款") || lowerMsg.contains("退单")) {
            return "您好！如果您需要退单，请提供订单号，我帮您处理。";
        }
        if (lowerMsg.contains("转人工") || lowerMsg.contains("人工客服")) {
            return "好的，正在为您转接人工客服，请稍候...";
        }
        return "您好！我是AI客服助手，可以帮您查询订单、修改地址、退单、查询商品等。请问有什么可以帮您的？";
    }

    /**
     * 通知用户 AI 已转人工 —— 供 AgentController.aiTransferToHuman 调用
     */
    public void notifyUserTransfer(String userId, String sessionId, String reason, String assignedAgentId) {
        WebSocketSession userSession = USER_SESSIONS.get(userId);
        if (userSession != null && userSession.isOpen()) {
            try {
                String message = assignedAgentId != null
                        ? "已为您转接人工客服，请稍候..."
                        : "正在为您转接人工客服，当前无空闲客服，请稍候...";

                Map<String, Object> transferMsg = new java.util.HashMap<>();
                transferMsg.put("type", "agent_transfer");
                transferMsg.put("sessionId", sessionId);
                transferMsg.put("fromAgentType", "AI");
                transferMsg.put("toAgentType", "HUMAN");
                transferMsg.put("reason", reason);
                transferMsg.put("content", message);
                transferMsg.put("timestamp", java.time.LocalDateTime.now().toString());
                userSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(transferMsg)));
            } catch (Exception e) {
                log.error("通知用户转人工失败: userId={}", userId, e);
            }
        }
    }

    private void sendFallbackReply(String userId, WebSocketSession session, ChatSession chatSession) {
        try {
            ChatMessage reply = new ChatMessage();
            reply.setMsgId(java.util.UUID.randomUUID().toString().replace("-", ""));
            reply.setSenderId("ai_agent");
            reply.setSenderRole("admin");
            reply.setReceiverId(userId);
            reply.setReceiverRole("user");
            reply.setContent("抱歉，AI 服务暂时不可用，正在为您转接人工客服...");
            reply.setSessionId(chatSession != null ? chatSession.getSessionId() : null);
            reply.setMsgType("TEXT");
            reply.setIsRead(0);
            reply.setCreateTime(java.time.LocalDateTime.now());
            chatMessageMapper.insert(reply);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(reply)));
        } catch (Exception e) {
            log.error("发送兜底回复失败", e);
        }
    }

    @Override
    public CommandResult executeCommand(Command command, CommandContext context) {
        return command.execute(context);
    }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(
                AgentCapability.builder().name("queryOrder").displayName("查询订单").description("根据订单号查询订单详情").build(),
                AgentCapability.builder().name("listUserOrders").displayName("用户订单列表").description("查询当前用户的所有订单").build(),
                AgentCapability.builder().name("modifyAddress").displayName("修改地址").description("修改订单收货地址").build(),
                AgentCapability.builder().name("refundOrder").displayName("退单").description("对已支付订单进行退单").build(),
                AgentCapability.builder().name("cancelOrder").displayName("取消订单").description("取消待支付订单").build(),
                AgentCapability.builder().name("queryProduct").displayName("查询商品").description("查询商品详情").build(),
                AgentCapability.builder().name("searchProduct").displayName("搜索商品").description("搜索商品").build(),
                AgentCapability.builder().name("queryUserInfo").displayName("查询用户信息").description("查询用户基本信息").build()
        );
    }

    @Override
    public AgentType getType() {
        return AgentType.AI;
    }
}