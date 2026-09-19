package com.example.agent;

import com.example.bean.ChatMessage;
import com.example.command.Command;
import com.example.command.CommandContext;
import com.example.command.CommandResult;
import com.example.session.ChatSession;
import com.example.session.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class HumanAgent implements Agent {

    private static final ConcurrentHashMap<String, WebSocketSession> ADMIN_SESSIONS = new ConcurrentHashMap<>();

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ObjectMapper objectMapper;

    public void registerAdminSession(String adminId, WebSocketSession session) {
        ADMIN_SESSIONS.put(adminId, session);
        log.info("客服上线: adminId={}", adminId);
    }

    public void removeAdminSession(String adminId) {
        ADMIN_SESSIONS.remove(adminId);
        log.info("客服下线: adminId={}", adminId);
    }

    public WebSocketSession getAdminSession(String adminId) {
        return ADMIN_SESSIONS.get(adminId);
    }

    public Map<String, WebSocketSession> getAllAdminSessions() {
        return new HashMap<>(ADMIN_SESSIONS);
    }

    @Override
    public void handleMessage(ChatMessage message, ChatSession session) {
        String agentId = session.getAgentId();
        if (agentId == null) {
            broadcastToAllAdmins(message);
            return;
        }
        WebSocketSession adminSession = ADMIN_SESSIONS.get(agentId);
        if (adminSession != null && adminSession.isOpen()) {
            try {
                Map<String, Object> msgData = new HashMap<>();
                msgData.put("type", "message");
                msgData.put("sessionId", session.getSessionId());
                msgData.put("senderId", message.getSenderId());
                msgData.put("senderName", session.getUserName());
                msgData.put("senderRole", message.getSenderRole());
                msgData.put("content", message.getContent());
                msgData.put("timestamp", message.getCreateTime().toString());
                adminSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(msgData)));
            } catch (Exception e) {
                log.error("推送消息给客服失败: adminId={}", agentId, e);
            }
        }
    }

    private void broadcastToAllAdmins(ChatMessage message) {
        for (WebSocketSession adminSession : ADMIN_SESSIONS.values()) {
            if (adminSession.isOpen()) {
                try {
                    ChatSession session = sessionManager.findByUserId(message.getSenderId());
                    Map<String, Object> msgData = new HashMap<>();
                    msgData.put("type", "message");
                    msgData.put("sessionId", session != null ? session.getSessionId() : null);
                    msgData.put("senderId", message.getSenderId());
                    msgData.put("senderName", session != null ? session.getUserName() : "未知用户");
                    msgData.put("senderRole", message.getSenderRole());
                    msgData.put("content", message.getContent());
                    msgData.put("timestamp", message.getCreateTime().toString());
                    adminSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(msgData)));
                } catch (Exception e) {
                    log.error("广播消息给客服失败", e);
                }
            }
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
        return AgentType.HUMAN;
    }
}